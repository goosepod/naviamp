package app.naviamp.desktop

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import app.naviamp.domain.Album
import app.naviamp.provider.navidrome.NavidromeConnection
import app.naviamp.provider.navidrome.NavidromeProvider
import kotlinx.coroutines.launch

fun main() {
    configureDesktopAppearance()

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Naviamp",
        ) {
            NaviampApp()
        }
    }
}

private fun configureDesktopAppearance() {
    if (System.getProperty("os.name").contains("Mac", ignoreCase = true)) {
        System.setProperty("apple.awt.application.appearance", "system")
    }
}

@Composable
@Preview
fun NaviampApp() {
    val isDark = isSystemInDarkTheme()
    val appColors = if (isDark) AppColors.Dark else AppColors.Light
    val colorScheme = if (isDark) darkColorScheme() else lightColorScheme()

    MaterialTheme(colorScheme = colorScheme) {
        Surface(modifier = Modifier.fillMaxSize(), color = appColors.background) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                ConnectionPanel(appColors)
                NowPlayingPanel(appColors)
            }
        }
    }
}

@Composable
private fun ConnectionPanel(appColors: AppColors) {
    var serverUrl by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var connectionStatus by remember { mutableStateOf<String?>(null) }
    var isConnecting by remember { mutableStateOf(false) }
    var recentlyAddedAlbums by remember { mutableStateOf<List<Album>>(emptyList()) }
    val coroutineScope = rememberCoroutineScope()
    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = appColors.primaryText,
        unfocusedTextColor = appColors.primaryText,
        focusedLabelColor = appColors.primaryText,
        unfocusedLabelColor = appColors.secondaryText,
        cursorColor = appColors.primaryText,
        focusedBorderColor = appColors.accent,
        unfocusedBorderColor = appColors.border,
    )

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Naviamp", color = appColors.primaryText, style = MaterialTheme.typography.headlineMedium)
        Text("Connect to Navidrome", color = appColors.secondaryText)

        OutlinedTextField(
            value = serverUrl,
            onValueChange = { serverUrl = it },
            label = { Text("Server URL") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = textFieldColors,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                colors = textFieldColors,
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.weight(1f),
                colors = textFieldColors,
            )
        }

        Button(
            enabled = !isConnecting,
            onClick = {
                if (serverUrl.isBlank() || username.isBlank() || password.isBlank()) {
                    connectionStatus = "Enter a server URL, username, and password."
                    return@Button
                }

                isConnecting = true
                connectionStatus = "Connecting to Navidrome..."
                recentlyAddedAlbums = emptyList()

                coroutineScope.launch {
                    try {
                        val provider = NavidromeProvider(
                            NavidromeConnection.fromPassword(
                                baseUrl = serverUrl,
                                username = username,
                                password = password,
                            ),
                        )
                        val validation = provider.validateConnection()
                        recentlyAddedAlbums = provider.recentlyAddedAlbums(limit = 5)
                        connectionStatus = buildString {
                            append("Connected")
                            validation.serverVersion?.let { append(" to Navidrome $it") }
                            append(".")
                        }
                    } catch (exception: Exception) {
                        connectionStatus = exception.message ?: "Could not connect to Navidrome."
                    } finally {
                        isConnecting = false
                    }
                }
            },
        ) {
            Text(if (isConnecting) "Connecting" else "Connect")
        }

        connectionStatus?.let {
            Text(it, color = appColors.secondaryText)
        }

        if (recentlyAddedAlbums.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Recently Added", color = appColors.primaryText, fontWeight = FontWeight.SemiBold)
                recentlyAddedAlbums.forEach { album ->
                    Text("${album.title} - ${album.artistName}", color = appColors.secondaryText)
                }
            }
        }
    }
}

@Composable
private fun NowPlayingPanel(appColors: AppColors) {
    var progress by remember { mutableFloatStateOf(0.35f) }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .background(appColors.albumArtPlaceholder),
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text("Nothing Playing", color = appColors.primaryText, fontWeight = FontWeight.SemiBold)
                Text("Queue will appear here after connection", color = appColors.secondaryText)
                Spacer(modifier = Modifier.height(8.dp))
                Text("ReplayGain: planned | Gapless: planned | Crossfade: planned", color = appColors.mutedText)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Slider(
            value = progress,
            onValueChange = { progress = it },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private data class AppColors(
    val background: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val mutedText: Color,
    val border: Color,
    val accent: Color,
    val albumArtPlaceholder: Color,
) {
    companion object {
        val Dark = AppColors(
            background = Color(0xFF101114),
            primaryText = Color.White,
            secondaryText = Color(0xFFB9BDC7),
            mutedText = Color(0xFF8F96A3),
            border = Color(0xFF59606D),
            accent = Color(0xFF8EA7D8),
            albumArtPlaceholder = Color(0xFF43536B),
        )

        val Light = AppColors(
            background = Color(0xFFF8F9FB),
            primaryText = Color(0xFF171A21),
            secondaryText = Color(0xFF4F5663),
            mutedText = Color(0xFF727A86),
            border = Color(0xFFBAC1CC),
            accent = Color(0xFF315D9E),
            albumArtPlaceholder = Color(0xFFD3DBE8),
        )
    }
}
