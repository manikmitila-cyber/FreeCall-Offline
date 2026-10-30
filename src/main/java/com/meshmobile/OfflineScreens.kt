package com.meshmobile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
private val dateTimeFormat = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
private val dayFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

private val Green = Color(0xFF2FBF71)
private val Red = Color(0xFFE5484D)

private fun dayKey(t: Long): Int {
    val c = Calendar.getInstance().apply { timeInMillis = t }
    return c.get(Calendar.YEAR) * 1000 + c.get(Calendar.DAY_OF_YEAR)
}

private fun dayLabel(t: Long): String {
    val today = dayKey(System.currentTimeMillis())
    val k = dayKey(t)
    return when (k) {
        today -> "Today"
        today - 1 -> "Yesterday"
        else -> dayFormat.format(Date(t))
    }
}

private fun clock(seconds: Long): String =
    String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60)

// ---------------------------------------------------------------------------
// Root: Nearby / Calls / Settings tabs, chat, call screens
// ---------------------------------------------------------------------------

@Composable
fun OfflineHome(nearby: NearbyManager) {
    val peers by nearby.peers.collectAsState()
    val callState by nearby.callState.collectAsState()
    val callPeerId by nearby.callPeerId.collectAsState()
    var openId by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableStateOf(0) }

    val callPeer = peers.firstOrNull { it.endpointId == callPeerId }
    val open = peers.firstOrNull { it.endpointId == openId }

    val screen = when {
        callState == CallState.INCOMING -> 3
        callState == CallState.OUTGOING || callState == CallState.ACTIVE -> 2
        open != null -> 1
        else -> 0
    }

    Crossfade(targetState = screen, modifier = Modifier.fillMaxSize(), label = "screen") { s ->
        when (s) {
            3 -> IncomingCallScreen(nearby, callPeer)
            2 -> CallScreen(nearby, callPeer, callState)
            1 -> if (open != null) ChatScreen(nearby, open) { openId = null }
            else -> HomeTabs(nearby, peers, tab, { tab = it }) { openId = it }
        }
    }
}

@Composable
private fun HomeTabs(
    nearby: NearbyManager,
    peers: List<Peer>,
    tab: Int,
    onTab: (Int) -> Unit,
    onOpen: (String) -> Unit
) {
    val unread by nearby.unread.collectAsState()
    val total = unread.values.sum()

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            Tab(
                selected = tab == 0,
                onClick = { onTab(0) },
                text = { Text(if (total > 0) "Nearby ($total)" else "Nearby") }
            )
            Tab(selected = tab == 1, onClick = { onTab(1) }, text = { Text("Calls") })
            Tab(selected = tab == 2, onClick = { onTab(2) }, text = { Text("Settings") })
        }
        when (tab) {
            0 -> NearbyTab(nearby, peers, unread, onOpen)
            1 -> CallsTab(nearby)
            else -> SettingsTab(nearby)
        }
    }
}

// ---------------------------------------------------------------------------
// Small building blocks
// ---------------------------------------------------------------------------

@Composable
private fun Avatar(name: String, size: Dp, online: Boolean = false) {
    val palette = listOf(
        Color(0xFF0F5C4D), Color(0xFF8A6A12), Color(0xFF3B5BA5), Color(0xFF9C4A6B), Color(0xFF5B6B2F)
    )
    val bg = palette[(name.hashCode() and 0x7fffffff) % palette.size]
    val initials = name.trim().split(" ")
        .filter { it.isNotEmpty() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifEmpty { "?" }

    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(size).clip(CircleShape).background(bg),
            contentAlignment = Alignment.Center
        ) {
            Text(
                initials,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value / 2.5f).sp
            )
        }
        if (online) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(size / 4)
                    .clip(CircleShape)
                    .background(Green)
            )
        }
    }
}

/** Soft pulsing ring behind [content] (searching / ringing). */
@Composable
private fun Pulse(size: Dp, content: @Composable () -> Unit) {
    val transition = rememberInfiniteTransition()
    val scale by transition.animateFloat(
        1f, 1.6f, infiniteRepeatable(tween(1400), RepeatMode.Restart)
    )
    val alpha by transition.animateFloat(
        0.45f, 0f, infiniteRepeatable(tween(1400), RepeatMode.Restart)
    )
    Box(Modifier.size(size * 1.7f), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(size)
                .scale(scale)
                .alpha(alpha)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        )
        content()
    }
}

@Composable
private fun RoundAction(
    color: Color,
    label: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Button(
            onClick = onClick,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = color),
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.size(68.dp)
        ) { icon() }
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
    }
}

// ---------------------------------------------------------------------------
// Tabs
// ---------------------------------------------------------------------------

@Composable
private fun NearbyTab(
    nearby: NearbyManager,
    peers: List<Peer>,
    unread: Map<String, Int>,
    onOpen: (String) -> Unit
) {
    val status by nearby.status.collectAsState()
    val typing by nearby.typing.collectAsState()
    val ok = status.contains("Advertising: ON") && status.contains("Discovery: ON")

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(if (ok) Green else Red))
            Spacer(Modifier.width(8.dp))
            Text(
                if (ok) "Visible to nearby phones" else status,
                style = MaterialTheme.typography.bodySmall
            )
        }

        if (peers.isEmpty()) {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Pulse(80.dp) { Text("📡", fontSize = 32.sp) }
                Text("Looking for nearby phones…", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Keep Bluetooth, Wi-Fi and Location on, and open FreeCall Offline on the other phone.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp, start = 24.dp, end = 24.dp)
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(peers, key = { it.endpointId }) { p ->
                    val count = unread[p.uid] ?: 0
                    val isTyping = p.uid in typing
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = p.connected) { onOpen(p.endpointId) }
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(p.name, 48.dp, online = p.connected)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(p.name, fontWeight = FontWeight.SemiBold)
                                Text(
                                    when {
                                        isTyping -> "typing…"
                                        !p.connected -> "Found, not connected"
                                        p.username.isNotBlank() -> "@" + p.username
                                        else -> "Connected"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isTyping) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (count > 0) {
                                Badge { Text(count.toString()) }
                                Spacer(Modifier.width(8.dp))
                            }
                            if (p.connected) {
                                IconButton(onClick = { nearby.startCall(p.endpointId) }) {
                                    Icon(Icons.Filled.Call, contentDescription = "Call", tint = Green)
                                }
                            } else {
                                OutlinedButton(onClick = { nearby.connect(p.endpointId) }) { Text("Connect") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CallsTab(nearby: NearbyManager) {
    val calls by nearby.calls.collectAsState()

    if (calls.isEmpty()) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("No calls yet", style = MaterialTheme.typography.titleMedium)
            Text("Your call history will show up here.", style = MaterialTheme.typography.bodySmall)
        }
        return
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(calls) { c ->
            val missed = !c.answered && !c.outgoing
            val kind = when {
                !c.answered && c.outgoing -> "↗ Not answered"
                !c.answered -> "↙ Missed / declined"
                c.outgoing -> "↗ Outgoing"
                else -> "↙ Incoming"
            }
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(c.peerName, 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.peerName, fontWeight = FontWeight.SemiBold)
                        Text(
                            kind + " • " + dateTimeFormat.format(Date(c.time)),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (missed) Red else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (c.answered) Text(clock(c.seconds), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun SettingsTab(nearby: NearbyManager) {
    val sound by nearby.sound.collectAsState()
    var confirm by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Message sounds", fontWeight = FontWeight.SemiBold)
                Text("Play a sound when a new message arrives", style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = sound, onCheckedChange = { nearby.setSound(it) })
        }
        OutlinedButton(onClick = { confirm = true }) { Text("Delete all chats and call history") }
        Text(
            "FreeCall Offline works without internet, using Bluetooth and Wi-Fi Direct. " +
                "Messages, photos and calls go straight between nearby phones and are never sent to a server.",
            style = MaterialTheme.typography.bodySmall
        )
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Delete everything?") },
            text = { Text("All chats, photos and call history will be removed from this phone.") },
            confirmButton = {
                TextButton(onClick = {
                    nearby.clearAll()
                    confirm = false
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } }
        )
    }
}

// ---------------------------------------------------------------------------
// Calls
// ---------------------------------------------------------------------------

@Composable
private fun IncomingCallScreen(nearby: NearbyManager, peer: Peer?) {
    val haptic = LocalHapticFeedback.current

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Incoming call", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(24.dp))
        Pulse(96.dp) { Avatar(peer?.name ?: "?", 96.dp) }
        Spacer(Modifier.height(16.dp))
        Text(peer?.name ?: "Unknown", style = MaterialTheme.typography.headlineMedium)
        val user = peer?.username ?: ""
        if (user.isNotBlank()) Text("@" + user, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(48.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(64.dp)) {
            RoundAction(Red, "Decline", onClick = { nearby.rejectCall() }) {
                Icon(Icons.Filled.Call, contentDescription = null, tint = Color.White, modifier = Modifier.rotate(135f))
            }
            RoundAction(Green, "Answer", onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                nearby.acceptCall()
            }) {
                Icon(Icons.Filled.Call, contentDescription = null, tint = Color.White)
            }
        }
    }
}

@Composable
private fun CallScreen(nearby: NearbyManager, peer: Peer?, state: CallState) {
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

    val name = peer?.name ?: "Unknown"
    val idle = MaterialTheme.colorScheme.secondaryContainer
    val active = MaterialTheme.colorScheme.primary

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (state == CallState.ACTIVE) {
            Box(Modifier.size(164.dp), contentAlignment = Alignment.Center) { Avatar(name, 96.dp) }
        } else {
            Pulse(96.dp) { Avatar(name, 96.dp) }
        }
        Spacer(Modifier.height(16.dp))
        Text(name, style = MaterialTheme.typography.headlineMedium)
        Text(
            if (state == CallState.ACTIVE) clock(elapsed) else "Calling…",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 4.dp)
        )
        Spacer(Modifier.height(48.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            RoundAction(if (muted) active else idle, if (muted) "Unmute" else "Mute", onClick = { nearby.toggleMute() }) {
                Text(if (muted) "🔇" else "🎤", fontSize = 26.sp)
            }
            RoundAction(Red, if (state == CallState.ACTIVE) "End" else "Cancel", onClick = { nearby.endCall() }) {
                Icon(Icons.Filled.Call, contentDescription = null, tint = Color.White, modifier = Modifier.rotate(135f))
            }
            RoundAction(if (speaker) active else idle, if (speaker) "Speaker" else "Earpiece", onClick = { nearby.toggleSpeaker() }) {
                Text(if (speaker) "🔊" else "🔈", fontSize = 26.sp)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Chat
// ---------------------------------------------------------------------------

@Composable
private fun ChatScreen(nearby: NearbyManager, peer: Peer, onBack: () -> Unit) {
    val all by nearby.messages.collectAsState()
    val typing by nearby.typing.collectAsState()
    val haptic = LocalHapticFeedback.current
    val msgs = all.filter { it.peerUid == peer.uid }
    var text by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    var confirmClear by remember { mutableStateOf(false) }
    var viewer by remember { mutableStateOf<String?>(null) }
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

    Column(Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Avatar(peer.name, 40.dp, online = peer.connected)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(peer.name, fontWeight = FontWeight.SemiBold)
                    val isTyping = peer.uid in typing
                    Text(
                        when {
                            isTyping -> "typing…"
                            peer.connected -> "Connected"
                            else -> "Disconnected"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isTyping) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { nearby.startCall(peer.endpointId) }) {
                    Icon(Icons.Filled.Call, contentDescription = "Call", tint = Green)
                }
                IconButton(onClick = { confirmClear = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Clear chat")
                }
            }
        }

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
            state = listState,
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            itemsIndexed(msgs) { i, m ->
                val newDay = i == 0 || dayKey(msgs[i - 1].time) != dayKey(m.time)
                Column {
                    if (newDay) {
                        Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    dayLabel(m.time),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                    Box(
                        Modifier.fillMaxWidth(),
                        contentAlignment = if (m.mine) Alignment.CenterEnd else Alignment.CenterStart
                    ) {
                        Surface(
                            shape = if (m.mine) RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
                            else RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp),
                            color = if (m.mine) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Column(
                                Modifier.widthIn(max = 280.dp).padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.End
                            ) {
                                if (m.imagePath != null) {
                                    AsyncImage(
                                        model = File(m.imagePath),
                                        contentDescription = "Photo",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(220.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { viewer = m.imagePath }
                                    )
                                } else {
                                    SelectionContainer { Text(m.text) }
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
        }

        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { picker.launch("image/*") }) {
                Icon(Icons.Filled.Add, contentDescription = "Send photo")
            }
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    if (it.isNotEmpty()) nearby.sendTyping(peer.endpointId)
                },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message") },
                shape = RoundedCornerShape(24.dp),
                maxLines = 4
            )
            IconButton(
                onClick = {
                    if (text.isNotBlank()) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        nearby.sendMessage(peer.endpointId, text)
                        text = ""
                    }
                }
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }

    val shown = viewer
    if (shown != null) {
        Dialog(
            onDismissRequest = { viewer = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            AsyncImage(
                model = File(shown),
                contentDescription = "Photo",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().clickable { viewer = null }
            )
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
