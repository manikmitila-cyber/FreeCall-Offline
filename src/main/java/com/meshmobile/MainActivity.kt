package com.meshmobile

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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

        setContent {
            FreeCallOfflineApp(this)
        }
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

    val prefs = context.getSharedPreferences(
        "freecall_profile",
        Context.MODE_PRIVATE
    )

    var phone by remember {
        mutableStateOf(
            prefs.getString("phone", "") ?: ""
        )
    }

    var username by remember {
        mutableStateOf(
            prefs.getString("username", "") ?: ""
        )
    }

    var name by remember {
        mutableStateOf(
            prefs.getString("name", "") ?: ""
        )
    }

    var photo by remember {
        mutableStateOf(
            prefs.getString("photo", null)
        )
    }

    var saved by remember {
        mutableStateOf(
            phone.isNotBlank() && username.isNotBlank()
        )
    }

    val picker =
        rememberLauncherForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri: Uri? ->

            uri ?: return@rememberLauncherForActivityResult

            photo = uri.toString()

            prefs.edit()
                .putString("photo", photo)
                .apply()
        }

    val nearby = remember(saved) {
        if (saved) NearbyManager(context, name.ifBlank { username }) else null
    }

    val permLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { result ->
            if (result.values.all { it }) nearby?.start()
        }

    LaunchedEffect(nearby) {
        if (nearby != null) {
            permLauncher.launch(NearbyManager.requiredPermissions())
        }
    }

    DisposableEffect(nearby) {
        onDispose { nearby?.stop() }
    }

    MaterialTheme {

        Scaffold(

            topBar = {

                TopAppBar(
                    title = {
                        Text("FreeCall Offline")
                    }
                )
            }

        ) { padding ->

            if (!saved) {

                AccountScreen(

                    modifier = Modifier.padding(padding),

                    phone = phone,
                    username = username,
                    name = name,
                    photo = photo,

                    onPhone = {
                        phone = it
                    },

                    onUsername = {
                        username = it
                    },

                    onName = {
                        name = it
                    },

                    onPhoto = {
                        picker.launch("image/*")
                    },

                    onSave = {

                        if (
                            phone.isNotBlank() &&
                            username.isNotBlank()
                        ) {

                            val id =
                                "u_" +
                                    UUID.randomUUID()
                                        .toString()
                                        .replace("-", "")
                                        .take(12)

                            prefs.edit()
                                .putString("phone", phone)
                                .putString("username", username)
                                .putString(
                                    "name",
                                    name.ifBlank { username }
                                )
                                .putString("userId", id)
                                .apply()

                            saved = true
                        }
                    }
                )

            } else {

                Column(
                    Modifier
                        .padding(padding)
                        .fillMaxSize()
                ) {

                    HomeScreen(

                        modifier = Modifier,

                        profile = UiProfile(

                            userId =
                                prefs.getString(
                                    "userId",
                                    "u_local"
                                ) ?: "u_local",

                            phone = phone,

                            username = username,

                            name =
                                name.ifBlank {
                                    username
                                },

                            photo = photo
                        ),

                        onChangePhoto = {
                            picker.launch("image/*")
                        }
                    )

                    if (nearby != null) {
                        OfflineHome(nearby)
                    }
                }
            }
        }
    }
}


@Composable
private fun AccountScreen(

    modifier: Modifier,

    phone: String,
    username: String,
    name: String,
    photo: String?,

    onPhone: (String) -> Unit,
    onUsername: (String) -> Unit,
    onName: (String) -> Unit,

    onPhoto: () -> Unit,
    onSave: () -> Unit

) {

    Column(

        modifier
            .fillMaxSize()
            .padding(20.dp),

        horizontalAlignment =
            Alignment.CenterHorizontally
    ) {

        ProfileAvatar(
            photo = photo,
            size = 110
        )

        Spacer(
            Modifier.height(10.dp)
        )

        OutlinedButton(
            onClick = onPhoto
        ) {

            Text("Add profile photo")
        }

        Spacer(
            Modifier.height(18.dp)
        )

        OutlinedTextField(

            value = phone,

            onValueChange = onPhone,

            modifier = Modifier.fillMaxWidth(),

            label = {
                Text("Phone number")
            }
        )

        Spacer(
            Modifier.height(8.dp)
        )

        OutlinedTextField(

            value = username,

            onValueChange = onUsername,

            modifier = Modifier.fillMaxWidth(),

            label = {
                Text("Username")
            }
        )

        Spacer(
            Modifier.height(8.dp)
        )

        OutlinedTextField(

            value = name,

            onValueChange = onName,

            modifier = Modifier.fillMaxWidth(),

            label = {
                Text("Display name")
            }
        )

        Spacer(
            Modifier.height(18.dp)
        )

        Button(

            onClick = onSave,

            modifier = Modifier.fillMaxWidth()
        ) {

            Text("Create account")
        }
    }
}

@Composable
private fun HomeScreen(

    modifier: Modifier,

    profile: UiProfile,

    onChangePhoto: () -> Unit

) {

    Row(

        modifier
            .fillMaxWidth()
            .padding(16.dp),

        verticalAlignment =
            Alignment.CenterVertically
    ) {

        ProfileAvatar(
            photo = profile.photo,
            size = 76
        )

        Spacer(
            Modifier.width(14.dp)
        )

        Column(
            Modifier.weight(1f)
        ) {

            Text(
                profile.name,
                style =
                    MaterialTheme
                        .typography
                        .titleLarge
            )

            Text(
                "@${profile.username}"
            )

            Text(
                profile.phone
            )
        }

        TextButton(
            onClick = onChangePhoto
        ) {

            Text("Photo")
        }
    }
}


@Composable
private fun ProfileAvatar(
    photo: String?,
    size: Int
) {

    if (photo != null) {

        AsyncImage(

            model = photo,

            contentDescription =
                "Profile photo",

            modifier =
                Modifier
                    .size(size.dp)
                    .clip(CircleShape),

            contentScale =
                ContentScale.Crop
        )

    } else {

        Surface(

            modifier =
                Modifier
                    .size(size.dp)
                    .clip(CircleShape),

            color =
                MaterialTheme
                    .colorScheme
                    .surfaceVariant

        ) {

            Box(
                contentAlignment =
                    Alignment.Center
            ) {

                Text("👤")
            }
        }
    }
}