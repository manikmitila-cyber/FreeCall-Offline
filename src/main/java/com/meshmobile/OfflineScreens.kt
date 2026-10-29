package com.meshmobile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Drop this in place of the dummy "Nearby / Mesh users" list. */
@Composable
fun OfflineHome(nearby: NearbyManager) {
    val peers by nearby.peers.collectAsState()
    val status by nearby.status.collectAsState()
    val callState by nearby.callState.collectAsState()
    val callPeerId by nearby.callPeerId.collectAsState()
    var openId by remember { mutableStateOf<String?>(null) }

    val callPeer = peers.firstOrNull { it.endpointId == callPeerId }
    val open = peers.firstOrNull { it.endpointId == openId }

    if (open != null) {
        ChatScreen(nearby, open, callState, callPeerId) { openId = null }
    } else {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text("Nearby / Mesh users")
            Text(status, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            if (peers.isEmpty()) Text("Searching for nearby phones…", Modifier.padding(top = 12.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 12.dp)) {
                items(peers, key = { it.endpointId }) { p ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name)
                            Text(if (p.connected) "Connected" else "Found, not connected")
                        }
                        if (p.connected) Button(onClick = { openId = p.endpointId }) { Text("Open") }
                        else OutlinedButton(onClick = { nearby.connect(p.endpointId) }) { Text("Connect") }
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
fun ChatScreen(
    nearby: NearbyManager,
    peer: Peer,
    callState: CallState,
    callPeerId: String?,
    onBack: () -> Unit
) {
    val all by nearby.messages.collectAsState()
    val msgs = all.filter { it.peerId == peer.endpointId }
    var text by remember { mutableStateOf("") }
    val inCallWithPeer = callState != CallState.IDLE && callPeerId == peer.endpointId

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Back") }
            Text(peer.name, Modifier.weight(1f))
            if (inCallWithPeer) {
                Button(onClick = { nearby.endCall() }) {
                    Text(if (callState == CallState.ACTIVE) "End call" else "Cancel")
                }
            } else {
                Button(onClick = { nearby.startCall(peer.endpointId) }, enabled = callState == CallState.IDLE) {
                    Text("Call")
                }
            }
        }
        LazyColumn(Modifier.weight(1f).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(msgs) { m ->
                Text(
                    (if (m.mine) "You: " else peer.name + ": ") + m.text,
                    Modifier.fillMaxWidth()
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(text, { text = it }, Modifier.weight(1f), placeholder = { Text("Message") })
            Button(onClick = { nearby.sendMessage(peer.endpointId, text); text = "" }, Modifier.padding(start = 8.dp)) {
                Text("Send")
            }
        }
    }
}
