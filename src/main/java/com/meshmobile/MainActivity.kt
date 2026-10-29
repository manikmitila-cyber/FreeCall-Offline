package com.meshmobile

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FreeCallOfflineApp(this) }
    }
}

data class UiProfile(
    val userId: String,
    val phone: String,
    val username: String,
    val name: String,
    val photo: String?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FreeCallOfflineApp(context: Context) {
    val prefs = context.getSharedPreferences("freecall_profile", Context.MODE_PRIVATE)
    var phone by remember { mutableStateOf(prefs.getString("phone", "") ?: "") }
    var username by remember { mutableStateOf(prefs.getString("username", "") ?: "") }
    var name by remember { mutableStateOf(prefs.getString("name", "") ?: "") }
    var photo by remember { mutableStateOf(prefs.getString("photo", null)) }
    var saved by remember { mutableStateOf(phone.isNotBlank() && username.isNotBlank()) }
    var selectedUser by remember { mutableStateOf<UiProfile?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        photo = uri.toString()
        prefs.edit().putString("photo", photo).apply()
    }

    MaterialTheme {
        Scaffold(topBar = { TopAppBar(title = { Text("FreeCall Offline") }) }) { pad ->
            if (!saved) {
                AccountScreen(
                    modifier = Modifier.padding(pad), phone, username, name, photo,
                    onPhone = { phone = it }, onUsername = { username = it }, onName = { name = it },
                    onPhoto = { picker.launch("image/*") },
                    onSave = {
                        if (phone.isNotBlank() && username.isNotBlank()) {
                            val id = "u_" + UUID.randomUUID().toString().replace("-", "").take(12)
                            prefs.edit().putString("phone", phone).putString("username", username)
                                .putString("name", name.ifBlank { username }).putString("userId", id).apply()
                            saved = true
                        }
                    }
                )
            } else {
                HomeScreen(
                    modifier = Modifier.padding(pad),
                    profile = UiProfile(prefs.getString("userId", "u_local") ?: "u_local", phone, username, name.ifBlank { username }, photo),
                    onChangePhoto = { picker.launch("image/*") },
                    onOpenUser = { selectedUser = it }
                )
            }
        }
    }

    selectedUser?.let { user ->
        AlertDialog(
            onDismissRequest = { selectedUser = null },
            title = { Text(user.name) },
            text = { Column { Text("@${user.username}"); Text(user.phone); Spacer(Modifier.height(12.dp)); Text("Mesh status: nearby") } },
            confirmButton = { TextButton(onClick = { selectedUser = null }) { Text("Call") } },
            dismissButton = { TextButton(onClick = { selectedUser = null }) { Text("Message") } }
        )
    }
}

@Composable
private fun AccountScreen(
    modifier: Modifier, phone: String, username: String, name: String, photo: String?,
    onPhone: (String) -> Unit, onUsername: (String) -> Unit, onName: (String) -> Unit,
    onPhoto: () -> Unit, onSave: () -> Unit
) {
    Column(modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        ProfileAvatar(photo, 110)
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onPhoto) { Text("Add profile photo") }
        Spacer(Modifier.height(18.dp))
        OutlinedTextField(phone, onPhone, Modifier.fillMaxWidth(), label = { Text("Phone number") })
        OutlinedTextField(username, onUsername, Modifier.fillMaxWidth(), label = { Text("Username") })
        OutlinedTextField(name, onName, Modifier.fillMaxWidth(), label = { Text("Display name") })
        Spacer(Modifier.height(18.dp))
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text("Create account") }
    }
}

@Composable
private fun HomeScreen(modifier: Modifier, profile: UiProfile, onChangePhoto: () -> Unit, onOpenUser: (UiProfile) -> Unit) {
    val nearby = remember {
        listOf(
            UiProfile("demo1", "", "nearby_user", "Nearby User", null),
            UiProfile("demo2", "", "mesh_friend", "Mesh Friend", null)
        )
    }
    LazyColumn(modifier.fillMaxSize().padding(16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProfileAvatar(profile.photo, 76)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(profile.name, style = MaterialTheme.typography.titleLarge)
                    Text("@${profile.username}")
                    Text(profile.phone)
                }
                TextButton(onClick = onChangePhoto) { Text("Photo") }
            }
            Spacer(Modifier.height(24.dp))
            Text("Nearby / Mesh users", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
        }
        items(nearby) { user ->
            ListItem(
                headlineContent = { Text(user.name) },
                supportingContent = { Text("@${user.username}") },
                leadingContent = { ProfileAvatar(user.photo, 48) },
                trailingContent = { Button(onClick = { onOpenUser(user) }) { Text("Open") } }
            )
        }
    }
}

@Composable
private fun ProfileAvatar(photo: String?, size: Int) {
    if (photo != null) {
        AsyncImage(model = photo, contentDescription = "Profile photo", modifier = Modifier.size(size.dp).clip(CircleShape), contentScale = ContentScale.Crop)
    } else {
        Surface(modifier = Modifier.size(size.dp).clip(CircleShape), color = MaterialTheme.colorScheme.surfaceVariant) {
            Box(contentAlignment = Alignment.Center) { Text("👤") }
        }
    }
}
