package com.meshmobile

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

data class Peer(val endpointId: String, val name: String, val uid: String, val connected: Boolean)
data class ChatMessage(val peerId: String, val text: String, val mine: Boolean, val time: Long = System.currentTimeMillis())
enum class CallState { IDLE, OUTGOING, INCOMING, ACTIVE }

/**
 * Internet-free messaging + voice calls between nearby phones
 * (Bluetooth / Wi-Fi Direct via Google Nearby Connections).
 *
 * Protocol (BYTES payloads): "M|<text>" message, "CR" call request,
 * "CA" call accepted, "CX" call rejected/ended. Voice = STREAM payload of 16 kHz mono PCM16.
 */
class NearbyManager(context: Context, private val displayName: String) {

    private val app = context.applicationContext
    private val client = Nearby.getConnectionsClient(app)
    private val myUid = UUID.randomUUID().toString().take(8)

    private val peerMap = ConcurrentHashMap<String, Peer>()
    private val _peers = MutableStateFlow<List<Peer>>(emptyList())
    val peers: StateFlow<List<Peer>> = _peers

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _callState = MutableStateFlow(CallState.IDLE)
    val callState: StateFlow<CallState> = _callState
    private val _callPeerId = MutableStateFlow<String?>(null)
    val callPeerId: StateFlow<String?> = _callPeerId

    @Volatile private var audioRunning = false
    @Volatile var muted = false
    private var playInput: InputStream? = null
    private var savedAudioMode = AudioManager.MODE_NORMAL

    // ---------- lifecycle ----------

    fun start() {
        client.startAdvertising(
            "$displayName|$myUid", SERVICE_ID, connectionCallback,
            AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        ).addOnFailureListener { Log.w(TAG, "advertising failed", it) }

        client.startDiscovery(
            SERVICE_ID, discoveryCallback,
            DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
        ).addOnFailureListener { Log.w(TAG, "discovery failed", it) }
    }

    fun stop() {
        endCall()
        client.stopAdvertising()
        client.stopDiscovery()
        client.stopAllEndpoints()
        peerMap.clear()
        publishPeers()
    }

    /** Retry connecting to a peer that is visible but not connected. */
    fun connect(peerId: String) {
        client.requestConnection("$displayName|$myUid", peerId, connectionCallback)
            .addOnFailureListener { Log.w(TAG, "connect failed", it) }
    }

    // ---------- messages ----------

    fun sendMessage(peerId: String, text: String) {
        if (text.isBlank()) return
        send(peerId, "M|$text")
        _messages.update { it + ChatMessage(peerId, text, mine = true) }
    }

    // ---------- calls ----------

    fun startCall(peerId: String) {
        if (_callState.value != CallState.IDLE) return
        _callPeerId.value = peerId
        _callState.value = CallState.OUTGOING
        send(peerId, "CR")
    }

    fun acceptCall() {
        val p = _callPeerId.value ?: return
        if (_callState.value != CallState.INCOMING) return
        send(p, "CA")
        _callState.value = CallState.ACTIVE
        startAudio(p)
    }

    fun rejectCall() = endCall()

    fun endCall() {
        val p = _callPeerId.value
        if (p != null && _callState.value != CallState.IDLE) send(p, "CX")
        teardownCall()
    }

    fun setSpeaker(on: Boolean) {
        (app.getSystemService(Context.AUDIO_SERVICE) as AudioManager).isSpeakerphoneOn = on
    }

    // ---------- Nearby callbacks ----------

    private val discoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(id: String, info: DiscoveredEndpointInfo) {
            val (name, uid) = parseName(info.endpointName)
            if (uid == myUid) return
            peerMap[id] = Peer(id, name, uid, connected = false)
            publishPeers()
            // Only one side initiates, to avoid both phones requesting at once.
            if (myUid < uid) connect(id)
        }

        override fun onEndpointLost(id: String) {
            val p = peerMap[id] ?: return
            if (!p.connected) {
                peerMap.remove(id)
                publishPeers()
            }
        }
    }

    private val connectionCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(id: String, info: ConnectionInfo) {
            val (name, uid) = parseName(info.endpointName)
            peerMap[id] = Peer(id, name, uid, connected = false)
            publishPeers()
            // NOTE: auto-accepts. For production, show info.authenticationDigits and ask the user.
            client.acceptConnection(id, payloadCallback)
        }

        override fun onConnectionResult(id: String, result: ConnectionResolution) {
            val p = peerMap[id] ?: return
            peerMap[id] = p.copy(connected = result.status.statusCode == ConnectionsStatusCodes.STATUS_OK)
            publishPeers()
        }

        override fun onDisconnected(id: String) {
            peerMap[id]?.let { peerMap[id] = it.copy(connected = false) }
            publishPeers()
            if (_callPeerId.value == id) teardownCall()
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(id: String, payload: Payload) {
            when (payload.type) {
                Payload.Type.BYTES -> payload.asBytes()?.let { onControl(id, String(it, Charsets.UTF_8)) }
                Payload.Type.STREAM -> {
                    val input = payload.asStream()?.asInputStream() ?: return
                    if (_callState.value == CallState.IDLE || _callPeerId.value != id) {
                        runCatching { input.close() }
                    } else {
                        startPlayback(input)
                    }
                }
                else -> Unit
            }
        }

        override fun onPayloadTransferUpdate(id: String, update: PayloadTransferUpdate) = Unit
    }

    private fun onControl(from: String, s: String) {
        when {
            s.startsWith("M|") ->
                _messages.update { it + ChatMessage(from, s.substring(2), mine = false) }

            s == "CR" ->
                if (_callState.value == CallState.IDLE) {
                    _callPeerId.value = from
                    _callState.value = CallState.INCOMING
                } else send(from, "CX")

            s == "CA" ->
                if (_callState.value == CallState.OUTGOING && _callPeerId.value == from) {
                    _callState.value = CallState.ACTIVE
                    startAudio(from)
                }

            s == "CX" -> if (_callPeerId.value == from) teardownCall()
        }
    }

    // ---------- audio ----------

    @SuppressLint("MissingPermission")
    private fun startAudio(peerId: String) {
        audioRunning = true
        val am = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        savedAudioMode = am.mode
        am.mode = AudioManager.MODE_IN_COMMUNICATION
        am.isSpeakerphoneOn = true

        val pipe = ParcelFileDescriptor.createPipe()
        client.sendPayload(peerId, Payload.fromStream(pipe[0]))
        val out = ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])

        thread(name = "fc-capture") {
            val min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val rec = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION, RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, FRAME * 4)
            )
            val buf = ByteArray(FRAME)
            try {
                rec.startRecording()
                while (audioRunning) {
                    val n = rec.read(buf, 0, buf.size)
                    if (n <= 0) continue
                    if (muted) buf.fill(0)
                    out.write(buf, 0, n)
                }
            } catch (e: Exception) {
                Log.w(TAG, "capture stopped", e)
            } finally {
                runCatching { rec.stop() }
                rec.release()
                runCatching { out.close() }
            }
        }
    }

    private fun startPlayback(input: InputStream) {
        runCatching { playInput?.close() }
        playInput = input
        thread(name = "fc-play") {
            val min = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
                )
                .setAudioFormat(
                    AudioFormat.Builder().setSampleRate(RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
                )
                .setBufferSizeInBytes(maxOf(min, FRAME * 4))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build()
            val buf = ByteArray(FRAME)
            try {
                track.play()
                while (_callState.value != CallState.IDLE) {
                    val n = input.read(buf)
                    if (n < 0) break
                    if (n > 0) track.write(buf, 0, n)
                }
            } catch (e: Exception) {
                Log.w(TAG, "playback stopped", e)
            } finally {
                runCatching { track.stop() }
                track.release()
            }
        }
    }

    private fun teardownCall() {
        audioRunning = false
        _callState.value = CallState.IDLE
        _callPeerId.value = null
        runCatching { playInput?.close() }
        playInput = null
        val am = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.mode = savedAudioMode
        am.isSpeakerphoneOn = false
    }

    // ---------- helpers ----------

    private fun send(peerId: String, text: String) {
        client.sendPayload(peerId, Payload.fromBytes(text.toByteArray(Charsets.UTF_8)))
    }

    private fun publishPeers() {
        _peers.value = peerMap.values.sortedBy { it.name.lowercase() }
    }

    private fun parseName(raw: String): Pair<String, String> {
        val i = raw.lastIndexOf('|')
        return if (i < 0) raw to raw else raw.substring(0, i) to raw.substring(i + 1)
    }

    companion object {
        private const val TAG = "NearbyManager"
        private const val SERVICE_ID = "com.meshmobile.freecall"
        private val STRATEGY = Strategy.P2P_CLUSTER
        private const val RATE = 16000
        private const val FRAME = 640 // 20 ms of 16 kHz mono PCM16

        fun requiredPermissions(): Array<String> {
            val p = mutableListOf(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 31) {
                p += Manifest.permission.BLUETOOTH_SCAN
                p += Manifest.permission.BLUETOOTH_ADVERTISE
                p += Manifest.permission.BLUETOOTH_CONNECT
            }
            if (Build.VERSION.SDK_INT <= 32) p += Manifest.permission.ACCESS_FINE_LOCATION
            if (Build.VERSION.SDK_INT >= 33) p += Manifest.permission.NEARBY_WIFI_DEVICES
            return p.toTypedArray()
        }
    }
}
