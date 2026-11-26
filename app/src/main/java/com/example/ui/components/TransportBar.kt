package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.PannerDsp
import com.example.model.MeterLevels
import com.example.model.ProjectConfig
import com.example.model.SongConfig
import com.example.model.TransportState
import com.example.ui.theme.BackingIndigo
import com.example.ui.theme.ClickCyan
import com.example.ui.theme.ClipRed
import com.example.ui.theme.ConsoleBorder
import com.example.ui.theme.ConsoleSurface
import com.example.ui.theme.CueAmber
import com.example.ui.theme.MeterGreen
import com.example.ui.theme.StripElevated
import com.example.ui.theme.StripSurface
import com.example.ui.theme.StudioBg
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun StudioTransportHeader(
    projects: List<ProjectConfig>,
    songs: List<SongConfig>,
    activeProject: ProjectConfig?,
    activeSong: SongConfig?,
    transport: TransportState,
    meters: MeterLevels,
    isImportingAudio: Boolean,
    resolveFileName: (Uri) -> String?,
    onSelectProject: (String) -> Unit,
    onSelectSong: (String) -> Unit,
    onCreateProject: (String) -> Unit,
    onCreateSong: (String, Int) -> Unit,
    onImportSong: (Uri, String, Int, Boolean) -> Unit,
    onTogglePlay: () -> Unit,
    onPlayWithCountIn: () -> Unit,
    onStop: () -> Unit,
    onChangeBpm: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var projectMenuExpanded by remember { mutableStateOf(false) }
    var songMenuExpanded by remember { mutableStateOf(false) }
    var showNewProjectDialog by remember { mutableStateOf(false) }
    var showNewSongDialog by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var pendingImportFileName by remember { mutableStateOf("") }

    val songAudioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingImportFileName = resolveFileName(uri) ?: "Imported_Track.mp3"
            pendingImportUri = uri
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .background(ConsoleSurface)
            .border(width = 1.dp, color = ConsoleBorder)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        val isWideLandscape = maxWidth >= 720.dp

        Column(modifier = Modifier.fillMaxWidth()) {
            if (isWideLandscape) {
                // Sleek Single-Row Tablet Landscape Transport Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ProjectDropdownChip(
                        activeProject = activeProject,
                        projects = projects,
                        expanded = projectMenuExpanded,
                        onExpandChange = { projectMenuExpanded = it },
                        onSelectProject = onSelectProject,
                        onOpenNewProject = { showNewProjectDialog = true },
                        modifier = Modifier.widthIn(min = 120.dp, max = 175.dp)
                    )

                    SongDropdownChip(
                        activeSong = activeSong,
                        songs = songs,
                        expanded = songMenuExpanded,
                        onExpandChange = { songMenuExpanded = it },
                        onSelectSong = onSelectSong,
                        onImportClick = { songAudioPicker.launch(arrayOf("audio/*")) },
                        onOpenNewSong = { showNewSongDialog = true },
                        modifier = Modifier.widthIn(min = 130.dp, max = 190.dp)
                    )

                    ImportSongChip(onClick = { songAudioPicker.launch(arrayOf("audio/*")) })

                    Spacer(Modifier.weight(1f))

                    TransportButtonsRow(
                        transport = transport,
                        onTogglePlay = onTogglePlay,
                        onPlayWithCountIn = onPlayWithCountIn,
                        onStop = onStop
                    )

                    BarBeatReadoutBox(
                        transport = transport,
                        modifier = Modifier.width(156.dp)
                    )

                    BpmStepperBox(
                        bpm = activeSong?.bpm ?: 120,
                        onChangeBpm = onChangeBpm
                    )

                    SafeLimitChip(isClipping = meters.isClipping)
                }
            } else {
                // Clean Two-Row Phone Portrait Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ProjectDropdownChip(
                        activeProject = activeProject,
                        projects = projects,
                        expanded = projectMenuExpanded,
                        onExpandChange = { projectMenuExpanded = it },
                        onSelectProject = onSelectProject,
                        onOpenNewProject = { showNewProjectDialog = true },
                        modifier = Modifier.weight(1f)
                    )

                    SongDropdownChip(
                        activeSong = activeSong,
                        songs = songs,
                        expanded = songMenuExpanded,
                        onExpandChange = { songMenuExpanded = it },
                        onSelectSong = onSelectSong,
                        onImportClick = { songAudioPicker.launch(arrayOf("audio/*")) },
                        onOpenNewSong = { showNewSongDialog = true },
                        modifier = Modifier.weight(1f)
                    )

                    ImportSongChip(onClick = { songAudioPicker.launch(arrayOf("audio/*")) })

                    SafeLimitChip(isClipping = meters.isClipping)
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TransportButtonsRow(
                        transport = transport,
                        onTogglePlay = onTogglePlay,
                        onPlayWithCountIn = onPlayWithCountIn,
                        onStop = onStop
                    )

                    BarBeatReadoutBox(
                        transport = transport,
                        modifier = Modifier.weight(1f)
                    )

                    BpmStepperBox(
                        bpm = activeSong?.bpm ?: 120,
                        onChangeBpm = onChangeBpm
                    )
                }
            }

            if (isImportingAudio) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = ClickCyan,
                    trackColor = StripSurface
                )
            }
        }
    }

    val chosenUri = pendingImportUri
    if (chosenUri != null) {
        var title by remember(chosenUri) {
            mutableStateOf(pendingImportFileName.substringBeforeLast('.').ifBlank { "Imported Song" })
        }
        var bpmText by remember(chosenUri) {
            mutableStateOf("${activeSong?.bpm ?: 120}")
        }
        AlertDialog(
            onDismissRequest = { pendingImportUri = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AudioFile, contentDescription = null, tint = ClickCyan)
                    Spacer(Modifier.width(8.dp))
                    Text("Import Lagu ke Mixer")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "File: $pendingImportFileName",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Judul Lagu") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = bpmText,
                        onValueChange = { bpmText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Tempo (BPM)") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = {
                            onImportSong(chosenUri, title, bpmText.toIntOrNull() ?: 120, true)
                            pendingImportUri = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ClickCyan),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Import sebagai Lagu Baru", color = StudioBg)
                    }
                    OutlinedButton(
                        onClick = {
                            onImportSong(chosenUri, title, bpmText.toIntOrNull() ?: 120, false)
                            pendingImportUri = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Ganti Backing Track Lagu Ini")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingImportUri = null }) { Text("Batal") }
            }
        )
    }

    if (showNewProjectDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewProjectDialog = false },
            title = { Text("Buat Project Live Baru") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nama Project / Setlist") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onCreateProject(name)
                        showNewProjectDialog = false
                    }
                ) { Text("Buat") }
            },
            dismissButton = {
                TextButton(onClick = { showNewProjectDialog = false }) { Text("Batal") }
            }
        )
    }

    if (showNewSongDialog) {
        var name by remember { mutableStateOf("") }
        var bpmText by remember { mutableStateOf("120") }
        AlertDialog(
            onDismissRequest = { showNewSongDialog = false },
            title = { Text("Buat Lagu Baru") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Judul Lagu") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = bpmText,
                        onValueChange = { bpmText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Tempo (BPM)") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val bpm = bpmText.toIntOrNull() ?: 120
                        onCreateSong(name, bpm)
                        showNewSongDialog = false
                    }
                ) { Text("Tambah") }
            },
            dismissButton = {
                TextButton(onClick = { showNewSongDialog = false }) { Text("Batal") }
            }
        )
    }
}

@Composable
private fun ProjectDropdownChip(
    activeProject: ProjectConfig?,
    projects: List<ProjectConfig>,
    expanded: Boolean,
    onExpandChange: (Boolean) -> Unit,
    onSelectProject: (String) -> Unit,
    onOpenNewProject: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(StripSurface)
                .border(1.dp, ConsoleBorder, RoundedCornerShape(8.dp))
                .clickable { onExpandChange(true) }
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .testTag("project_selector_button"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.FolderOpen,
                contentDescription = "Pilih Project",
                tint = ClickCyan,
                modifier = Modifier.size(15.dp)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = activeProject?.name ?: "Project",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandChange(false) }
        ) {
            projects.forEach { proj ->
                DropdownMenuItem(
                    text = { Text(proj.name) },
                    onClick = {
                        onExpandChange(false)
                        onSelectProject(proj.id)
                    }
                )
            }
            DropdownMenuItem(
                text = { Text("+ Project Baru") },
                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                onClick = {
                    onExpandChange(false)
                    onOpenNewProject()
                }
            )
        }
    }
}

@Composable
private fun SongDropdownChip(
    activeSong: SongConfig?,
    songs: List<SongConfig>,
    expanded: Boolean,
    onExpandChange: (Boolean) -> Unit,
    onSelectSong: (String) -> Unit,
    onImportClick: () -> Unit,
    onOpenNewSong: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(StripSurface)
                .border(1.dp, ConsoleBorder, RoundedCornerShape(8.dp))
                .clickable { onExpandChange(true) }
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .testTag("song_selector_button"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = "Pilih Lagu",
                tint = CueAmber,
                modifier = Modifier.size(15.dp)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = activeSong?.name ?: "Song",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandChange(false) }
        ) {
            songs.forEach { song ->
                DropdownMenuItem(
                    text = { Text("${song.name} (${song.bpm} BPM)") },
                    onClick = {
                        onExpandChange(false)
                        onSelectSong(song.id)
                    }
                )
            }
            DropdownMenuItem(
                text = { Text("Import Lagu (MP3 / WAV / M4A)") },
                leadingIcon = { Icon(Icons.Default.UploadFile, contentDescription = null, tint = ClickCyan) },
                onClick = {
                    onExpandChange(false)
                    onImportClick()
                }
            )
            DropdownMenuItem(
                text = { Text("+ Buat Lagu Kosong") },
                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                onClick = {
                    onExpandChange(false)
                    onOpenNewSong()
                }
            )
        }
    }
}

@Composable
private fun ImportSongChip(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(BackingIndigo.copy(alpha = 0.22f))
            .border(1.dp, BackingIndigo, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 8.dp)
            .testTag("import_song_button"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.UploadFile,
            contentDescription = "Import Lagu",
            tint = ClickCyan,
            modifier = Modifier.size(15.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "Import",
            style = MaterialTheme.typography.labelSmall,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SafeLimitChip(isClipping: Boolean) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(StripSurface)
            .border(1.dp, if (isClipping) ClipRed else ConsoleBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 7.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(if (isClipping) ClipRed else MeterGreen)
        )
        Text(
            text = if (isClipping) "LIM" else "OK",
            style = MaterialTheme.typography.labelSmall,
            color = if (isClipping) ClipRed else MeterGreen,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun TransportButtonsRow(
    transport: TransportState,
    onTogglePlay: () -> Unit,
    onPlayWithCountIn: () -> Unit,
    onStop: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (transport.isPlaying) MeterGreen else ClickCyan)
                .clickable(onClick = onTogglePlay)
                .testTag("transport_play_button"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (transport.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (transport.isPlaying) "Pause" else "Play",
                tint = StudioBg,
                modifier = Modifier.size(24.dp)
            )
        }

        Box(
            modifier = Modifier
                .height(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (transport.isCountInActive) CueAmber else StripElevated)
                .border(1.dp, ConsoleBorder, RoundedCornerShape(10.dp))
                .clickable(onClick = onPlayWithCountIn)
                .padding(horizontal = 9.dp)
                .testTag("transport_count_in_button"),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Replay,
                    contentDescription = "Count-In and Play",
                    tint = if (transport.isCountInActive) StudioBg else CueAmber,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    text = "1-4",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (transport.isCountInActive) StudioBg else TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(StripElevated)
                .border(1.dp, ConsoleBorder, RoundedCornerShape(10.dp))
                .clickable(onClick = onStop)
                .testTag("transport_stop_button"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Stop,
                contentDescription = "Stop Playback",
                tint = TextPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun BarBeatReadoutBox(
    transport: TransportState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(StudioBg)
            .border(1.dp, ConsoleBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = String.format("%02d:%02d", transport.currentBar, transport.currentBeat),
                style = MaterialTheme.typography.labelLarge,
                fontSize = 15.sp,
                color = if (transport.isCountInActive) CueAmber else ClickCyan
            )
            Text(
                text = if (transport.isCountInActive) "COUNT-IN" else transport.activeSection.displayName.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = BackingIndigo,
                fontWeight = FontWeight.Bold
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "CLK ${PannerDsp.formatPanLabel(transport.activeMetronomePan)}",
                style = MaterialTheme.typography.labelSmall,
                color = ClickCyan
            )
            Text(
                text = "CUE ${PannerDsp.formatPanLabel(transport.activeVoiceCuePan)}",
                style = MaterialTheme.typography.labelSmall,
                color = CueAmber
            )
        }
    }
}

@Composable
private fun BpmStepperBox(
    bpm: Int,
    onChangeBpm: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(StripSurface)
            .border(1.dp, ConsoleBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "−",
                style = MaterialTheme.typography.titleMedium,
                color = TextSecondary,
                modifier = Modifier
                    .clickable { onChangeBpm(bpm - 2) }
                    .padding(horizontal = 5.dp)
                    .testTag("bpm_decrease_button")
            )
            Text(
                text = "$bpm",
                style = MaterialTheme.typography.labelLarge,
                color = TextPrimary
            )
            Text(
                text = "+",
                style = MaterialTheme.typography.titleMedium,
                color = TextSecondary,
                modifier = Modifier
                    .clickable { onChangeBpm(bpm + 2) }
                    .padding(horizontal = 5.dp)
                    .testTag("bpm_increase_button")
            )
        }
        Text(
            text = "BPM",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
    }
}
