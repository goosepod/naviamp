package app.naviamp.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.naviamp.domain.InternetRadioStation

@Composable
fun InternetRadioPanel(
    appColors: AppColors,
    stations: List<InternetRadioStation>,
    status: String?,
    onPlayStation: (InternetRadioStation) -> Unit,
    onNewStation: () -> Unit,
    onEditStation: (InternetRadioStation) -> Unit,
    onDeleteStation: (InternetRadioStation) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Internet Radio", color = appColors.primaryText, style = MaterialTheme.typography.titleMedium)
            Button(onClick = onNewStation, modifier = Modifier.height(32.dp)) {
                Text("New station", fontSize = 12.sp)
            }
        }
        status?.let {
            Text(it, color = appColors.secondaryText, fontSize = 12.sp)
        }
        if (stations.isEmpty()) {
            Text("Saved internet radio stations will appear here.", color = appColors.secondaryText, fontSize = 12.sp)
        }
        stations.sortedBy { it.name.lowercase() }.forEach { station ->
            InternetRadioStationRow(
                appColors = appColors,
                station = station,
                onPlay = { onPlayStation(station) },
                onEdit = { onEditStation(station) },
                onDelete = { onDeleteStation(station) },
            )
        }
    }
}

@Composable
private fun InternetRadioStationRow(
    appColors: AppColors,
    station: InternetRadioStation,
    onPlay: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    MediaRow(appColors = appColors, onClick = onPlay) {
        DetailActionIconButton(appColors, TransportIcons.Play, "Play station", true, onPlay)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                station.name,
                color = appColors.primaryText,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                station.homePageUrl ?: station.streamUrl,
                color = appColors.secondaryText,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        RowOverflowMenu(
            appColors = appColors,
            items = listOf(
                RowMenuItem("Edit station", NavigationIcons.Edit, onEdit),
                RowMenuItem("Delete station", NavigationIcons.Trash, onDelete),
            ),
        )
    }
}

@Composable
fun InternetRadioStationDialog(
    initialStation: InternetRadioStation?,
    onDismiss: () -> Unit,
    onConfirm: (InternetRadioStation) -> Unit,
) {
    var name by remember(initialStation?.id) { mutableStateOf(initialStation?.name.orEmpty()) }
    var streamUrl by remember(initialStation?.id) { mutableStateOf(initialStation?.streamUrl.orEmpty()) }
    var homePageUrl by remember(initialStation?.id) { mutableStateOf(initialStation?.homePageUrl.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialStation == null) "New station" else "Edit station") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Station name") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = streamUrl,
                    onValueChange = { streamUrl = it },
                    label = { Text("Stream URL") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = homePageUrl,
                    onValueChange = { homePageUrl = it },
                    label = { Text("Website URL optional") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && streamUrl.isNotBlank(),
                onClick = {
                    onConfirm(
                        InternetRadioStation(
                            id = initialStation?.id ?: streamUrl.trim(),
                            name = name.trim(),
                            streamUrl = streamUrl.trim(),
                            homePageUrl = homePageUrl.trim().takeIf { it.isNotBlank() },
                        ),
                    )
                },
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
fun DeleteInternetRadioStationDialog(
    station: InternetRadioStation,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete station") },
        text = { Text("Delete ${station.name}? This removes the server internet radio station.") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
