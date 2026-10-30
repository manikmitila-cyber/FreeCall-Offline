package com.meshmobile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val CallGreen = Color(0xFF2FBF71)

/** Keep the last 11 digits so +880, 880 and 0 prefixes all match. */
fun normalizePhone(raw: String): String = raw.filter { it.isDigit() }.takeLast(11)

@Composable
fun DialTab(nearby: NearbyManager) {
    val peers by nearby.peers.collectAsState()
    var digits by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("Enter a registered number to call") }

    fun press(k: String) {
        if (digits.length < 15) digits += k
        message = ""
    }

    fun call() {
        val want = normalizePhone(digits)
        if (want.length < 6) {
            message = "Enter a valid number"
            return
        }
        val target = peers.firstOrNull { normalizePhone(it.phone) == want }
        when {
            target == null -> message = "Not nearby. Make sure that phone has FreeCall Offline open."
            target.connected -> nearby.startCall(target.endpointId)
            else -> {
                nearby.connect(target.endpointId)
                message = "Connecting to ${target.name}…"
            }
        }
    }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))
        Text(
            if (digits.isEmpty()) " " else digits,
            fontSize = 32.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp)
        )
        Spacer(Modifier.weight(1f))
        listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("*", "0", "#")
        ).forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                modifier = Modifier.padding(vertical = 6.dp)
            ) {
                row.forEach { k -> KeyButton(k) { press(k) } }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.size(64.dp))
            Box(
                Modifier.size(72.dp).clip(CircleShape).background(CallGreen).clickable { call() },
                contentAlignment = Alignment.Center
            ) {
                Text("📞", fontSize = 26.sp)
            }
            Box(
                Modifier.size(64.dp).clip(CircleShape).clickable(enabled = digits.isNotEmpty()) {
                    digits = digits.dropLast(1)
                },
                contentAlignment = Alignment.Center
            ) {
                Text("⌫", fontSize = 22.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun KeyButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
    }
}
