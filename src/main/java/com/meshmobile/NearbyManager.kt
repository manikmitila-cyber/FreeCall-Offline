package com.meshmobile

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.Log
import com.google.android.gms.common.api.ApiException
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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

data class Peer(
    val endpointId: String,
    val name: String,
    val username: String,
    val uid: String,
    val connected: Boolean,
    val phone: String = ""
)

/** peerUid is the peer's stable id (survives app restarts), not the session endpoint id. */
data class ChatMessage(
    val peerUid: String,
    val text: String,
    val mine: Boolean,
    val time: Long = System.currentTimeMillis(),
    val id: String = UUID.randomUUID().toString().take(8),
    val delivered: Boolean = false,
    val imagePath: String? = null
)

data class CallRecord(
    val peerName: String,
    val outgoing: Boolean,
    val answered: Boolean,
    val time: Long,
    val seconds: Long
)

data class Ident(val name: String, val user: String, val phone: String, val uid: String)

enum class CallState { IDLE, OUTGOING, INCOMING, ACTIVE }

/**
 * Internet-free messaging + voice calls between nearby phones
 * (Bluetooth / Wi-Fi Direct via Google Nearby Connections).
 *
 * Protocol (BYTES payloads): "M|<text>" message, "CR" call request,
 * "CA" call accepted, "CX" call rejected/ended. Voice = STREAM payload of 16 kHz mono PCM16.
 */
class NearbyManager(
    context: Context,
    private val displayName: String,
    private val username: String,
    private val phone: String = ""
) {

    private val app = context.applicationContext
    private val client = Nearby.getConnectionsClient(app)
    private val prefs = app.getSharedPreferences("freecall_nearby", Context.MODE_PRIVATE)
    private val handler = Handler(Looper.getMainLooper())

    /** Called for new incoming messages and calls (used for notifications). */
    var onIncoming: ((String, String) -> Unit)? = null

    /** Stable id for this phone, created once and kept. */
    private val myUid: String = prefs.getString("uid", null)
        ?: UUID.randomUUID().toString().take(8).also { prefs.edit().putString("uid", it).apply() }

    private val peerMap = ConcurrentHashMap<String, Peer>()
    private val _peers = MutableStateFlow<List<Peer>>(emptyList())
    val peers: StateFlow<List<Peer>> = _peers

    private val _messages = MutableStateFlow(loadMessages())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _calls = MutableStateFlow(loadCalls())
    val calls: StateFlow<List<CallRecord>> = _calls

    private val _typing = MutableStateFlow<Set<String>>(emptySet())
    /** Uids of peers who are typing to us right now. */
    val typing: StateFlow<Set<String>> = _typing
    private val typingAt = ConcurrentHashMap<String, Long>()
    private var lastTypingSent = 0L

    private val _sound = MutableStateFlow(prefs.getBoolean("sound", true))
    val sound: StateFlow<Boolean> = _sound

    private val incomingFiles = ConcurrentHashMap<Long, Pair<String, Payload>>()
    private var callOutgoing = false
    private var callAnswered = false
    private var callPeerName = ""

    /** Unread message count per peer uid. */
    private val _unread = MutableStateFlow<Map<String, Int>>(emptyMap())
    val unread: StateFlow<Map<String, Int>> = _unread
    @Volatile private var activeChatUid: String? = null

    private val _callState = MutableStateFlow(CallState.IDLE)
    val callState: StateFlow<CallState> = _callState
    private val _callPeerId = MutableStateFlow<String?>(null)
    val callPeerId: StateFlow<String?> = _callPeerId
    private val _callStart = MutableStateFlow(0L)
    val callStartTime: StateFlow<Long> = _callStart
    private val _muted = MutableStateFlow(false)
    val muted: StateFlow<Boolean> = _muted
    private val _speaker = MutableStateFlow(true)
    val speaker: StateFlow<Boolean> = _speaker

    private val _status = MutableStateFlow("Starting…")
    val status: StateFlow<String> = _status
    private var advStatus = "starting"
    private var discStatus = "starting"

    @Volatile private var audioRunning = false
    private var playInput: InputStream? = null
    private var savedAudioMode = AudioManager.MODE_NORMAL
    private var ringtone: Ringtone? = null

    private val advertisedName: String get() = "$displayName|$username|$phone|$myUid"

    // ---------- lifecycle ----------

    fun start() {
        advStatus = "starting"
        discStatus = "starting"
        publishStatus()

        client.startAdvertising(
            advertisedName, SERVICE_ID, connectionCallback,
            AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        ).addOnSuccessListener {
            advStatus = "ON"
            publishStatus()
        }.addOnFailureListener {
            Log.w(TAG, "advertising failed", it)
            advStatus = if (codeOf(it) == ConnectionsStatusCodes.STATUS_ALREADY_ADVERTISING) "ON" else "FAILED " + describe(it)
            publishStatus()
        }

        client.startDiscovery(
            SERVICE_ID, discoveryCallback,
            DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
        ).addOnSuccessListener {
            discStatus = "ON"
            publishStatus()
        }.addOnFailureListener {
            Log.w(TAG, "discovery failed", it)
            discStatus = if (codeOf(it) == ConnectionsStatusCodes.STATUS_ALREADY_DISCOVERING) "ON" else "FAILED " + describe(it)
            publishStatus()
        }
    }

    fun stop() {
        handler.removeCallbacksAndMessages(null)
        endCall()
        client.stopAdvertising()
        client.stopDiscovery()
        client.stopAllEndpoints()
        peerMap.clear()
        publishPeers()
    }

    /** Retry connecting to a peer that is visible but not connected. */
    fun connect(peerId: String) {
        client.requestConnection(advertisedName, peerId, connectionCallback)
            .addOnFailureListener { Log.w(TAG, "connect failed", it) }
    }

    // ---------- messages ----------

    fun setActiveChat(uid: String?) {
        activeChatUid = uid
        if (uid != null) _unread.update { it - uid }
    }

    fun sendMessage(peerId: String, text: String) {
        if (text.isBlank()) return
        val uid = peerMap[peerId]?.uid ?: peerId
        val msg = ChatMessage(uid, text, mine = true)
        send(peerId, "M|${msg.id}|$text")
        addMessage(msg)
    }

    /** Send a photo (compressed to max 1280 px) to a connected peer. */
    fun sendImage(peerId: String, uri: Uri) {
        thread(name = "fc-img") {
            try {
                val file = prepareImage(uri) ?: return@thread
                client.sendPayload(peerId, Payload.fromFile(file))
                val uid = peerMap[peerId]?.uid ?: peerId
                addMessage(ChatMessage(uid, "", mine = true, imagePath = file.absolutePath))
            } catch (e: Exception) {
                Log.w(TAG, "send image failed", e)
            }
        }
    }

    /** Tell the peer we are typing (sent at most every 2 seconds). */
    fun sendTyping(peerId: String) {
        val now = System.currentTimeMillis()
        if (now - lastTypingSent < 2000) return
        lastTypingSent = now
        send(peerId, "T")
    }

    private fun onTyping(uid: String) {
        typingAt[uid] = System.currentTimeMillis()
        _typing.update { it + uid }
        handler.postDelayed({
            val last = typingAt[uid] ?: 0L
            if (System.currentTimeMillis() - last >= 2900) _typing.update { it - uid }
        }, 3000)
    }

    fun setSound(on: Boolean) {
        _sound.value = on
        prefs.edit().putBoolean("sound", on).apply()
    }

    /** Delete every chat, photo and call record from this phone. */
    fun clearAll() {
        _messages.value.forEach { m -> m.imagePath?.let { runCatching { File(it).delete() } } }
        _messages.value = emptyList()
        saveMessages(emptyList())
        _calls.value = emptyList()
        saveCalls(emptyList())
        _unread.value = emptyMap()
    }

    fun clearChat(uid: String) {
        _messages.value.filter { it.peerUid == uid && it.imagePath != null }
            .forEach { runCatching { File(it.imagePath!!).delete() } }
        _messages.update { list -> list.filter { it.peerUid != uid } }
        saveMessages(_messages.value)
    }

    private fun markDelivered(id: String) {
        _messages.update { list -> list.map { if (it.id == id && it.mine) it.copy(delivered = true) else it } }
        saveMessages(_messages.value)
    }

    private fun prepareImage(uri: Uri): File? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            app.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / sample > 1280 || bounds.outHeight / sample > 1280) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = app.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: return null
            val dir = File(app.filesDir, "images").apply { mkdirs() }
            val out = File(dir, "img_${System.currentTimeMillis()}.jpg")
            FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.JPEG, 75, it) }
            out
        } catch (e: Exception) {
            Log.w(TAG, "prepare image failed", e)
            null
        }
    }

    private fun saveReceivedImage(from: String, payload: Payload) {
        thread(name = "fc-img-rcv") {
            try {
                val pfd = payload.asFile()?.asParcelFileDescriptor() ?: return@thread
                val dir = File(app.filesDir, "images").apply { mkdirs() }
                val out = File(dir, "rcv_${System.currentTimeMillis()}.jpg")
                ParcelFileDescriptor.AutoCloseInputStream(pfd).use { input ->
                    FileOutputStream(out).use { input.copyTo(it) }
                }
                val uid = peerMap[from]?.uid ?: from
                addMessage(ChatMessage(uid, "", mine = false, imagePath = out.absolutePath))
                if (activeChatUid != uid) {
                    _unread.update { it + (uid to ((it[uid] ?: 0) + 1)) }
                    handler.post { beep() }
                }
            } catch (e: Exception) {
                Log.w(TAG, "receive image failed", e)
            }
        }
    }

    private fun addMessage(m: ChatMessage) {
        _messages.update { (it + m).takeLast(MAX_MESSAGES) }
        saveMessages(_messages.value)
        if (!m.mine) {
            val who = peerMap.values.firstOrNull { it.uid == m.peerUid }?.name ?: "FreeCall"
            onIncoming?.invoke(who, if (m.imagePath != null) "📷 Photo" else m.text)
        }
    }

    private fun saveMessages(list: List<ChatMessage>) {
        val arr = JSONArray()
        list.forEach { m ->
            arr.put(
                JSONObject()
                    .put("p", m.peerUid)
                    .put("t", m.text)
                    .put("m", m.mine)
                    .put("ts", m.time)
                    .put("id", m.id)
                    .put("d", m.delivered)
                    .put("i", m.imagePath ?: "")
            )
        }
        prefs.edit().putString("messages", arr.toString()).apply()
    }

    private fun loadMessages(): List<ChatMessage> {
        return try {
            val arr = JSONArray(prefs.getString("messages", "[]"))
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val img = o.optString("i", "")
                ChatMessage(
                    peerUid = o.getString("p"),
                    text = o.getString("t"),
                    mine = o.getBoolean("m"),
                    time = o.getLong("ts"),
                    id = o.optString("id", o.getLong("ts").toString()),
                    delivered = o.optBoolean("d", false),
                    imagePath = if (img.isEmpty()) null else img
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ---------- call history ----------

    private fun recordCall() {
        val start = _callStart.value
        val seconds = if (callAnswered && start > 0L) (System.currentTimeMillis() - start) / 1000 else 0L
        val rec = CallRecord(
            callPeerName, callOutgoing, callAnswered,
            if (start > 0L) start else System.currentTimeMillis(), seconds
        )
        _calls.update { (listOf(rec) + it).take(100) }
        saveCalls(_calls.value)
    }

    private fun saveCalls(list: List<CallRecord>) {
        val arr = JSONArray()
        list.forEach { c ->
            arr.put(
                JSONObject()
                    .put("n", c.peerName)
                    .put("o", c.outgoing)
                    .put("a", c.answered)
                    .put("t", c.time)
                    .put("s", c.seconds)
            )
        }
        prefs.edit().putString("calls", arr.toString()).apply()
    }

    private fun loadCalls(): List<CallRecord> {
        return try {
            val arr = JSONArray(prefs.getString("calls", "[]"))
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                CallRecord(o.getString("n"), o.getBoolean("o"), o.getBoolean("a"), o.getLong("t"), o.getLong("s"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ---------- calls ----------

    fun startCall(peerId: String) {
        if (_callState.value != CallState.IDLE) return
        _callPeerId.value = peerId
        callOutgoing = true
        callAnswered = false
        callPeerName = peerMap[peerId]?.name ?: "Unknown"
        _callState.value = CallState.OUTGOING
        send(peerId, "CR")
    }

    fun acceptCall() {
        val p = _callPeerId.value ?: return
        if (_callState.value != CallState.INCOMING) return
        stopRinging()
        send(p, "CA")
        callAnswered = true
        _callStart.value = System.currentTimeMillis()
        _callState.value = CallState.ACTIVE
        startAudio(p)
    }

    fun rejectCall() = endCall()

    fun endCall() {
        val p = _callPeerId.value
        if (p != null && _callState.value != CallState.IDLE) send(p, "CX")
        teardownCall()
    }

    fun toggleMute() {
        _muted.value = !_muted.value
    }

    fun toggleSpeaker() {
        val on = !_speaker.value
        _speaker.value = on
        setSpeaker(on)
    }

    fun setSpeaker(on: Boolean) {
        (app.getSystemService(Context.AUDIO_SERVICE) as AudioManager).isSpeakerphoneOn = on
    }

    // ---------- Nearby callbacks ----------

    private val discoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(id: String, info: DiscoveredEndpointInfo) {
            val (name, user, phone, uid) = parseName(info.endpointName)
            if (uid == myUid) return
            peerMap[id] = Peer(id, name, user, uid, connected = false, phone = phone)
            publishPeers()
            // Only one side initiates, to avoid both phones requesting at once.
            if (myUid < uid) connect(id)
            else handler.postDelayed({ if (peerMap[id]?.connected == false) connect(id) }, 8000)
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
            val (name, user, phone, uid) = parseName(info.endpointName)
            peerMap[id] = Peer(id, name, user, uid, connected = false, phone = phone)
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
            val p = peerMap[id]
            if (p != null) peerMap[id] = p.copy(connected = false)
            publishPeers()
            if (_callPeerId.value == id) teardownCall()
            // Auto-reconnect: the side with the smaller uid retries once after a short wait.
            if (p != null && myUid < p.uid) {
                handler.postDelayed({
                    if (peerMap[id]?.connected == false) connect(id)
                }, 3000)
            }
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(id: String, payload: Payload) {
            when (payload.type) {
                Payload.Type.BYTES -> payload.asBytes()?.let { b ->
                    if (b.isNotEmpty() && b[0] == VOICE_TAG) {
                        if (_callState.value == CallState.ACTIVE && _callPeerId.value == id) {
                            voiceQueue.offer(b.copyOfRange(1, b.size))
                            while (voiceQueue.size > MAX_QUEUE) voiceQueue.poll()
                        }
                    } else {
                        onControl(id, String(b, Charsets.UTF_8))
                    }
                }
                Payload.Type.FILE -> incomingFiles[payload.id] = id to payload
                else -> Unit
            }
        }

        override fun onPayloadTransferUpdate(id: String, update: PayloadTransferUpdate) {
            when (update.status) {
                PayloadTransferUpdate.Status.SUCCESS -> {
                    val entry = incomingFiles.remove(update.payloadId) ?: return
                    saveReceivedImage(entry.first, entry.second)
                }
                PayloadTransferUpdate.Status.FAILURE,
                PayloadTransferUpdate.Status.CANCELED -> incomingFiles.remove(update.payloadId)
                else -> Unit
            }
        }
    }

    private fun onControl(from: String, s: String) {
        when {
            s.startsWith("M|") -> {
                val parts = s.split("|", limit = 3)
                if (parts.size == 3) {
                    val uid = peerMap[from]?.uid ?: from
                    _typing.update { it - uid }
                    addMessage(ChatMessage(uid, parts[2], mine = false, id = parts[1]))
                    send(from, "A|${parts[1]}")
                    if (activeChatUid != uid) {
                        _unread.update { it + (uid to ((it[uid] ?: 0) + 1)) }
                        beep()
                    }
                }
            }

            s.startsWith("A|") -> markDelivered(s.substring(2))

            s == "T" -> peerMap[from]?.uid?.let { onTyping(it) }

            s == "CR" ->
                if (_callState.value == CallState.IDLE) {
                    _callPeerId.value = from
                    callOutgoing = false
                    callAnswered = false
                    callPeerName = peerMap[from]?.name ?: "Unknown"
                    _callState.value = CallState.INCOMING
                    startRinging()
                    onIncoming?.invoke(callPeerName, "📞 Incoming call")
                } else send(from, "CX")

            s == "CA" ->
                if (_callState.value == CallState.OUTGOING && _callPeerId.value == from) {
                    callAnswered = true
                    _callStart.value = System.currentTimeMillis()
                    _callState.value = CallState.ACTIVE
                    startAudio(from)
                }

            s == "CX" -> if (_callPeerId.value == from) teardownCall()
        }
    }

    // ---------- ringtone / sound ----------

    private fun startRinging() {
        runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(app, uri)?.also {
                if (Build.VERSION.SDK_INT >= 28) it.isLooping = true
                it.play()
            }
        }
    }

    private fun stopRinging() {
        runCatching { ringtone?.stop() }
        ringtone = null
    }

    private fun beep() {
        if (!_sound.value) return
        runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            RingtoneManager.getRingtone(app, uri)?.play()
        }
    }

    // ---------- audio ----------

    private val voiceQueue = java.util.concurrent.LinkedBlockingQueue<ByteArray>()

    /**
     * Voice goes as small BYTES packets (20 ms each), not a STREAM. Packets are sent
     * immediately, and playback keeps only about 40-80 ms of audio buffered, so talk
     * stays close to real time.
     */
    @SuppressLint("MissingPermission")
    private fun startAudio(peerId: String) {
        audioRunning = true
        val am = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        savedAudioMode = am.mode
        am.mode = AudioManager.MODE_IN_COMMUNICATION
        _speaker.value = true
        am.isSpeakerphoneOn = true
        voiceQueue.clear()

        thread(name = "fc-capture") {
            val min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val rec = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION, RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, FRAME * 2)
            )
            val pcm = ByteArray(FRAME)
            val packet = ByteArray(FRAME + 1)
            packet[0] = VOICE_TAG
            try {
                rec.startRecording()
                while (audioRunning) {
                    val n = rec.read(pcm, 0, pcm.size)
                    if (n <= 0) continue
                    if (_muted.value) java.util.Arrays.fill(pcm, 0.toByte())
                    System.arraycopy(pcm, 0, packet, 1, n)
                    client.sendPayload(peerId, Payload.fromBytes(packet.copyOf(n + 1)))
                }
            } catch (e: Exception) {
                Log.w(TAG, "capture stopped", e)
            } finally {
                runCatching { rec.stop() }
                rec.release()
            }
        }

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
                .setBufferSizeInBytes(maxOf(min, FRAME * 2))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build()
            try {
                track.play()
                var primed = false
                while (audioRunning) {
                    if (!primed) {
                        if (voiceQueue.size >= PREBUFFER) primed = true else { Thread.sleep(10); continue }
                    }
                    val chunk = voiceQueue.poll(100, TimeUnit.MILLISECONDS)
                    if (chunk == null) { primed = false; continue }
                    track.write(chunk, 0, chunk.size)
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
        if (_callState.value != CallState.IDLE) recordCall()
        stopRinging()
        audioRunning = false
        _callState.value = CallState.IDLE
        _callPeerId.value = null
        _callStart.value = 0L
        _muted.value = false
        _speaker.value = true
        voiceQueue.clear()
        val am = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.mode = savedAudioMode
        am.isSpeakerphoneOn = false
    }

    // ---------- helpers ----------

    private fun send(peerId: String, text: String) {
        client.sendPayload(peerId, Payload.fromBytes(text.toByteArray(Charsets.UTF_8)))
    }

    private fun publishStatus() {
        _status.value = "Advertising: $advStatus\nDiscovery: $discStatus"
    }

    private fun codeOf(e: Exception): Int? = (e as? ApiException)?.statusCode

    private fun describe(e: Exception): String {
        val c = codeOf(e) ?: return e.message ?: "unknown error"
        return "$c ${ConnectionsStatusCodes.getStatusCodeString(c)}"
    }

    private fun publishPeers() {
        _peers.value = peerMap.values.sortedBy { it.name.lowercase() }
    }

    /** Advertised name: "<name>|<username>|<phone>|<uid>". The name may contain '|', so split from the right. */
    private fun parseName(raw: String): Ident {
        val parts = raw.split('|')
        return if (parts.size >= 4) {
            Ident(
                parts.subList(0, parts.size - 3).joinToString("|"),
                parts[parts.size - 3],
                parts[parts.size - 2],
                parts.last()
            )
        } else Ident(raw, "", "", raw)
    }

    companion object {
        private const val TAG = "NearbyManager"
        private const val SERVICE_ID = "com.meshmobile.freecall"
        private val STRATEGY = Strategy.P2P_CLUSTER
        private const val RATE = 16000
        private const val FRAME = 640 // 20 ms of 16 kHz mono PCM16
        private const val MAX_MESSAGES = 500
        private const val VOICE_TAG: Byte = 1
        private const val MAX_QUEUE = 4
        private const val PREBUFFER = 2

        fun requiredPermissions(): Array<String> {
            val p = mutableListOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
            if (Build.VERSION.SDK_INT >= 31) {
                p += Manifest.permission.BLUETOOTH_SCAN
                p += Manifest.permission.BLUETOOTH_ADVERTISE
                p += Manifest.permission.BLUETOOTH_CONNECT
            }
            if (Build.VERSION.SDK_INT >= 33) p += Manifest.permission.NEARBY_WIFI_DEVICES
            if (Build.VERSION.SDK_INT >= 33) p += Manifest.permission.POST_NOTIFICATIONS
            return p.toTypedArray()
        }
    }
}
