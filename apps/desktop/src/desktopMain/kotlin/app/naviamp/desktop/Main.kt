package app.naviamp.desktop

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.background
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Naviamp",
    ) {
        NaviampApp()
    }
}

@Composable
@Preview
fun NaviampApp() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF101114)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                ConnectionPanel()
                NowPlayingPanel()
            }
        }
    }
}

@Composable
private fun ConnectionPanel() {
    var serverUrl by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var connectionStatus by remember { mutableStateOf<String?>(null) }
    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedLabelColor = Color(0xFFE3E7EF),
        unfocusedLabelColor = Color(0xFFB9BDC7),
        cursorColor = Color.White,
        focusedBorderColor = Color(0xFF8EA7D8),
        unfocusedBorderColor = Color(0xFF59606D),
    )

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Naviamp", color = Color.White, style = MaterialTheme.typography.headlineMedium)
        Text("Connect to Navidrome", color = Color(0xFFB9BDC7))

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
            onClick = {
                connectionStatus = if (serverUrl.isBlank() || username.isBlank() || password.isBlank()) {
                    "Enter a server URL, username, and password."
                } else {
                    "Connection form works. Navidrome login is the next MVP step."
                }
            },
        ) {
            Text("Connect")
        }

        connectionStatus?.let {
            Text(it, color = Color(0xFFB9BDC7))
        }
    }
}

@Composable
private fun NowPlayingPanel() {
    var progress by remember { mutableFloatStateOf(0.35f) }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .background(Color(0xFF43536B)),
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text("Nothing Playing", color = Color.White, fontWeight = FontWeight.SemiBold)
                Text("Queue will appear here after connection", color = Color(0xFFB9BDC7))
                Spacer(modifier = Modifier.height(8.dp))
                Text("ReplayGain: planned | Gapless: planned | Crossfade: planned", color = Color(0xFF8F96A3))
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
