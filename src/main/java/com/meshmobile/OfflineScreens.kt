package com.meshmobile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
private val dateTimeFormat = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())

/** Replaces the dummy "Nearby / Mesh users" list: peers, chat, calls. */
@Composable
fun OfflineHome(nearby: NearbyManager) {
    val peers by nearby.peers.collectAsState()
    val status by nearby.status.collectAsState()
    val unread by nearby.unread.collectAsState()
    val callState by nearby.callState.collectAsState()
    val callPeerId by nearby.callPeerId.collectAsState()
    val calls by nearby.calls.collectAsState()
    var openId by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableStateOf(0) }

    val callPeer = peers.firstOrNull { it.endpointId == callPeerId }
    val open = peers.firstOrNull { it.endpointId == openId }

    if (callState == CallState.OUTGOING || callState == CallState.ACTIVE) {
        CallScreen(nearby, callPeer, callState)
    } else if (open != null) {
        ChatScreen(nearby, open) { openId = null }
    } else {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row {
                if (tab == 0) Button(onClick = { tab = 0 }) { Text("Nearby") }
                else OutlinedButton(onClick = { tab = 0 }) { Text("Nearby") }
                Spacer(Modifier.width(8.dp))
                if (tab == 1) Button(onClick = { tab = 1 }) { Text("Calls") }
                else OutlinedButton(onClick = { tab = 1 }) { Text("Calls") }
            }
            if (tab == 1) {
                if (calls.isEmpty()) Text("No calls yet", Modifier.padding(top = 16.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 16.dp)) {
                    items(calls) { c ->
                        Column {
                            Text(c.peerName)
                            val kind = when {
                                !c.answered && c.outgoing -> "Outgoing, not answered"
                                !c.answered -> "Missed / declined"
                                c.outgoing -> "Outgoing"
                                else -> "Incoming"
                            }
                            val dur = if (c.answered) {
                                " • " + String.format(Locale.US, "%02d:%02d", c.seconds / 60, c.seconds % 60)
                            } else ""
                            Text(
                                kind + " • " + dateTimeFormat.format(Date(c.time)) + dur,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            } else {
            Text("Nearby / Mesh users", Modifier.padding(top = 12.dp))
            Text(status, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            if (peers.isEmpty()) Text("Searching for nearby phones…", Modifier.padding(top = 12.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 12.dp)) {
                items(peers, key = { it.endpointId }) { p ->
                    val count = unread[p.uid] ?: 0
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name)
                            if (p.username.isNotBlank()) {
                                Text("@" + p.username, style = MaterialTheme.typography.bodySmall)
                            }
                            Text(
                                if (p.connected) "Connected" else "Found, not connected",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (count > 0) {
                            Badge { Text(count.toString()) }
                            Spacer(Modifier.width(8.dp))
                        }
                        if (p.connected) Button(onClick = { openId = p.endpointId }) { Text("Open") }
                        else OutlinedButton(onClick = { nearby.connect(p.endpointId) }) { Text("Connect") }
                    }
                }
            }
            }
        }
    }

    if (callState == CallState.INCOMING) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Incoming call") },
            text = { Text(callPeer?.name ?: "Unknown") },
            confirmButton = { TextButton(onClick = { nearby.acceptCall() }) { Text("Answer") } },
            dismissButton = { TextButton(onClick = { nearby.rejectCall() }) { Text("Decline") } }
        )
    }
}

@Composable
fun CallScreen(nearby: NearbyManager, peer: Peer?, state: CallState) {
    val muted by nearby.muted.collectAsState()
    val speaker by nearby.speaker.collectAsState()
    val startedAt by nearby.callStartTime.collectAsState()
    var elapsed by remember { mutableStateOf(0L) }

    LaunchedEffect(startedAt, state) {
        while (state == CallState.ACTIVE && startedAt > 0L) {
            elapsed = (System.currentTimeMillis() - startedAt) / 1000
            delay(500)
        }
    }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(peer?.name ?: "Unknown", style = MaterialTheme.typography.headlineMedium)
        Text(
            if (state == CallState.ACTIVE) {
                String.format(Locale.US, "%02d:%02d", elapsed / 60, elapsed % 60)
            } else "Calling…",
            modifier = Modifier.padding(top = 8.dp, bottom = 32.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { nearby.toggleMute() }) {
                Text(if (muted) "Unmute" else "Mute")
            }
            OutlinedButton(onClick = { nearby.toggleSpeaker() }) {
                Text(if (speaker) "Speaker: on" else "Speaker: off")
            }
        }
        Button(onClick = { nearby.endCall() }, modifier = Modifier.padding(top = 24.dp)) {
            Text(if (state == CallState.ACTIVE) "End call" else "Cancel")
        }
    }
}

@Composable
fun ChatScreen(nearby: NearbyManager, peer: Peer, onBack: () -> Unit) {
    val all by nearby.messages.collectAsState()
    val msgs = all.filter { it.peerUid == peer.uid }
    var text by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    var confirmClear by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) nearby.sendImage(peer.endpointId, uri)
    }

    DisposableEffect(peer.uid) {
        nearby.setActiveChat(peer.uid)
        onDispose { nearby.setActiveChat(null) }
    }

    LaunchedEffect(msgs.size) {
        if (msgs.isNotEmpty()) listState.animateScrollToItem(msgs.lastIndex)
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Back") }
            Text(peer.name, Modifier.weight(1f))
            TextButton(onClick = { confirmClear = true }) { Text("Clear") }
            Button(onClick = { nearby.startCall(peer.endpointId) }) { Text("Call") }
        }
        LazyColumn(
            Modifier.weight(1f).padding(vertical = 8.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(msgs) { m ->
                Box(
                    Modifier.fillMaxWidth(),
                    contentAlignment = if (m.mine) Alignment.CenterEnd else Alignment.CenterStart
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (m.mine) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Column(Modifier.widthIn(max = 280.dp).padding(horizontal = 12.dp, vertical = 8.dp)) {
                            if (m.imagePath != null) {
                                AsyncImage(
                                    model = File(m.imagePath),
                                    contentDescription = "Photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(220.dp).clip(RoundedCornerShape(12.dp))
                                )
                            } else {
                                Text(m.text)
                            }
                            Text(
                                timeFormat.format(Date(m.time)) +
                                    if (m.mine && m.imagePath == null) (if (m.delivered) "  ✓✓" else "  ✓") else "",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { picker.launch("image/*") }) { Text("Photo") }
            OutlinedTextField(text, { text = it }, Modifier.weight(1f), placeholder = { Text("Message") })
            Button(
                onClick = {
                    if (text.isNotBlank()) {
                        nearby.sendMessage(peer.endpointId, text)
                        text = ""
                    }
                },
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text("Send")
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear chat?") },
            text = { Text("All messages and photos with " + peer.name + " will be deleted from this phone.") },
            confirmButton = {
                TextButton(onClick = {
                    nearby.clearChat(peer.uid)
                    confirmClear = false
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
        )
    }
}
