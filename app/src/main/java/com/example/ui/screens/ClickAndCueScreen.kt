package com.example.ui.screens

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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.audio.PannerDsp
import com.example.model.MetronomeSoundType
import com.example.model.SongConfig
import com.example.model.SongSectionType
import com.example.model.VoiceCueItem
import com.example.ui.components.StereoPanControlRow
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClickAndCueScreen(
    song: SongConfig,
    onSelectMetronomeSound: (MetronomeSoundType) -> Unit,
    onUpdateMetronomePanForSound: (MetronomeSoundType, Float) -> Unit,
    onAuditionMetronome: (Boolean) -> Unit,
    onTogglePerCuePan: (Boolean) -> Unit,
    onUpdateCuePan: (String, Float) -> Unit,
    onUpdateCueProfile: (String, String) -> Unit,
    onUpdateCueProfilePan: (String, Float) -> Unit,
    onUpdateSectionCuePan: (SongSectionType, Float) -> Unit,
    onAuditionCue: (VoiceCueItem) -> Unit,
    onAddCue: (String, String, SongSectionType, Int, Int, Float) -> Unit,
    onDeleteCue: (String) -> Unit,
    onImportCueProfileWav: (String, Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddCueDialog by remember { mutableStateOf(false) }
    var pendingProfileId by remember { mutableStateOf<String?>(null) }

    val wavCueLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        val id = pendingProfileId
        if (uri != null && id != null) {
            onImportCueProfileWav(id, uri)
        }
        pendingProfileId = null
    }

    val activeMetroPan = song.metronomePanBySound[song.selectedMetronomeSound] ?: 0f

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(StudioBg)
    ) {
        val isTabletLandscape = maxWidth >= 780.dp

        if (isTabletLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Left Column: Metronome Click Deck + Section Matrix + Sound Profiles
                LazyColumn(
                    modifier = Modifier
                        .weight(0.48f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        MetronomePanCard(
                            song = song,
                            activeMetroPan = activeMetroPan,
                            onSelectMetronomeSound = onSelectMetronomeSound,
                            onUpdateMetronomePanForSound = onUpdateMetronomePanForSound,
                            onAuditionMetronome = onAuditionMetronome
                        )
                    }
                    item {
                        SectionCuePanCard(
                            song = song,
                            onTogglePerCuePan = onTogglePerCuePan,
                            onUpdateSectionCuePan = onUpdateSectionCuePan
                        )
                    }
                    item {
                        CueSoundProfilesCard(
                            song = song,
                            onUpdateCueProfilePan = onUpdateCueProfilePan,
                            onPickCueAudio = { profileId ->
                                pendingProfileId = profileId
                                wavCueLauncher.launch(arrayOf("audio/*"))
                            }
                        )
                    }
                }

                // Right Column: Individual Song Voice Cues
                LazyColumn(
                    modifier = Modifier
                        .weight(0.52f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        VoiceCueListHeader(
                            count = song.cues.size,
                            onAddClick = { showAddCueDialog = true }
                        )
                    }
                    items(song.cues, key = { it.id }) { cue ->
                        VoiceCueCard(
                            cue = cue,
                            song = song,
                            onPanChange = { onUpdateCuePan(cue.id, it) },
                            onProfileChange = { onUpdateCueProfile(cue.id, it) },
                            onAudition = { onAuditionCue(cue) },
                            onDelete = { onDeleteCue(cue.id) }
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    MetronomePanCard(
                        song = song,
                        activeMetroPan = activeMetroPan,
                        onSelectMetronomeSound = onSelectMetronomeSound,
                        onUpdateMetronomePanForSound = onUpdateMetronomePanForSound,
                        onAuditionMetronome = onAuditionMetronome
                    )
                }
                item {
                    SectionCuePanCard(
                        song = song,
                        onTogglePerCuePan = onTogglePerCuePan,
                        onUpdateSectionCuePan = onUpdateSectionCuePan
                    )
                }
                item {
                    CueSoundProfilesCard(
                        song = song,
                        onUpdateCueProfilePan = onUpdateCueProfilePan,
                        onPickCueAudio = { profileId ->
                            pendingProfileId = profileId
                            wavCueLauncher.launch(arrayOf("audio/*"))
                        }
                    )
                }
                item {
                    VoiceCueListHeader(
                        count = song.cues.size,
                        onAddClick = { showAddCueDialog = true }
                    )
                }
                items(song.cues, key = { it.id }) { cue ->
                    VoiceCueCard(
                        cue = cue,
                        song = song,
                        onPanChange = { onUpdateCuePan(cue.id, it) },
                        onProfileChange = { onUpdateCueProfile(cue.id, it) },
                        onAudition = { onAuditionCue(cue) },
                        onDelete = { onDeleteCue(cue.id) }
                    )
                }
            }
        }
    }

    if (showAddCueDialog) {
        var label by remember { mutableStateOf("Pre-Chorus Cue") }
        var phrase by remember { mutableStateOf("Chorus") }
        var selectedSection by remember { mutableStateOf(SongSectionType.CHORUS) }
        var barText by remember { mutableStateOf("8") }
        var panVal by remember { mutableFloatStateOf(100f) }

        AlertDialog(
            onDismissRequest = { showAddCueDialog = false },
            title = { Text("Tambah Voice Cue") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text("Label Cue") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = phrase,
                        onValueChange = { phrase = it },
                        label = { Text("Teks Suara Cue") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = barText,
                        onValueChange = { barText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Posisi Bar (1..${song.totalBars})") },
                        singleLine = true
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SongSectionType.entries.forEach { sec ->
                            val active = selectedSection == sec
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (active) CueAmber else StripElevated)
                                    .clickable { selectedSection = sec }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = sec.displayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (active) StudioBg else TextPrimary
                                )
                            }
                        }
                    }
                    StereoPanControlRow(
                        pan = panVal,
                        accentColor = CueAmber,
                        testTagPrefix = "new_cue_dialog",
                        onPanChange = { panVal = it }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onAddCue(label, phrase, selectedSection, barText.toIntOrNull() ?: 1, 1, panVal)
                        showAddCueDialog = false
                    }
                ) { Text("Tambah") }
            },
            dismissButton = {
                TextButton(onClick = { showAddCueDialog = false }) { Text("Batal") }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetronomePanCard(
    song: SongConfig,
    activeMetroPan: Float,
    onSelectMetronomeSound: (MetronomeSoundType) -> Unit,
    onUpdateMetronomePanForSound: (MetronomeSoundType, Float) -> Unit,
    onAuditionMetronome: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(StripSurface)
            .border(1.dp, ConsoleBorder, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.GraphicEq, contentDescription = null, tint = ClickCyan)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Panning Metronome Click",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = "Tersimpan otomatis per project, lagu & jenis suara click",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            Button(
                onClick = { onAuditionMetronome(true) },
                colors = ButtonDefaults.buttonColors(containerColor = ClickCyan),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.testTag("test_metronome_button")
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Tes Click", tint = StudioBg, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Tes Click", color = StudioBg, style = MaterialTheme.typography.labelMedium)
            }
        }

        Spacer(Modifier.height(10.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetronomeSoundType.entries.forEach { sound ->
                val selected = song.selectedMetronomeSound == sound
                val soundPan = song.metronomePanBySound[sound] ?: 0f
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) ClickCyan.copy(alpha = 0.18f) else ConsoleSurface)
                        .border(
                            width = 1.dp,
                            color = if (selected) ClickCyan else ConsoleBorder,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable { onSelectMetronomeSound(sound) }
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                        .testTag("metro_sound_${sound.name}")
                ) {
                    Text(
                        text = sound.displayName,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (selected) ClickCyan else TextPrimary
                    )
                    Text(
                        text = PannerDsp.formatPanLabel(soundPan),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = "Pan ${song.selectedMetronomeSound.displayName}: ${PannerDsp.formatPanLabel(activeMetroPan)}",
            style = MaterialTheme.typography.labelMedium,
            color = ClickCyan
        )
        Spacer(Modifier.height(6.dp))
        StereoPanControlRow(
            pan = activeMetroPan,
            accentColor = ClickCyan,
            testTagPrefix = "metro_deck",
            onPanChange = { onUpdateMetronomePanForSound(song.selectedMetronomeSound, it) }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SectionCuePanCard(
    song: SongConfig,
    onTogglePerCuePan: (Boolean) -> Unit,
    onUpdateSectionCuePan: (SongSectionType, Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(StripSurface)
            .border(1.dp, ConsoleBorder, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.RecordVoiceOver, contentDescription = null, tint = CueAmber)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Mode Pan Voice Cue",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = if (song.perCuePanEnabled) {
                            "Aktif: Pan unik per Cue & per Section lagu"
                        } else {
                            "Aktif: Mengikuti fader pan Voice Cue di Mixer"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            Switch(
                checked = song.perCuePanEnabled,
                onCheckedChange = onTogglePerCuePan,
                colors = SwitchDefaults.colors(checkedThumbColor = StudioBg, checkedTrackColor = CueAmber),
                modifier = Modifier.testTag("per_cue_pan_switch")
            )
        }

        Spacer(Modifier.height(10.dp))
        Text(
            text = "Atur Pan Cue per Bagian Lagu (Intro / Verse / Chorus / Bridge / Ending)",
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary
        )
        Spacer(Modifier.height(8.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            song.sections.forEach { section ->
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(ConsoleSurface)
                        .border(1.dp, ConsoleBorder, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = section.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = TextPrimary
                    )
                    listOf("L" to -100f, "C" to 0f, "R" to 100f).forEach { (lbl, targetPan) ->
                        val isMatch = kotlin.math.abs(section.defaultCuePan - targetPan) < 10f
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isMatch) CueAmber else StripElevated)
                                .clickable { onUpdateSectionCuePan(section.type, targetPan) }
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                                .testTag("section_pan_${section.type.name}_$lbl")
                        ) {
                            Text(
                                text = lbl,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isMatch) StudioBg else TextSecondary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CueSoundProfilesCard(
    song: SongConfig,
    onUpdateCueProfilePan: (String, Float) -> Unit,
    onPickCueAudio: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(StripSurface)
            .border(1.dp, ConsoleBorder, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Panning per File Suara Cue",
            style = MaterialTheme.typography.titleSmall,
            color = TextPrimary
        )
        song.cueSoundProfiles.forEach { profile ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(ConsoleSurface)
                    .border(1.dp, ConsoleBorder, RoundedCornerShape(10.dp))
                    .padding(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = profile.displayName,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    OutlinedButton(
                        onClick = { onPickCueAudio(profile.id) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.AudioFile, contentDescription = "Import Cue Audio", modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Audio", style = MaterialTheme.typography.labelSmall)
                    }
                }
                StereoPanControlRow(
                    pan = profile.pan,
                    accentColor = CueAmber,
                    testTagPrefix = "profile_${profile.id}",
                    onPanChange = { onUpdateCueProfilePan(profile.id, it) }
                )
            }
        }
    }
}

@Composable
private fun VoiceCueListHeader(count: Int, onAddClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Daftar Voice Cue ($count)",
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary
        )
        Button(
            onClick = onAddClick,
            colors = ButtonDefaults.buttonColors(containerColor = CueAmber),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            modifier = Modifier.testTag("add_voice_cue_button")
        ) {
            Icon(Icons.Default.Add, contentDescription = "Tambah Voice Cue", tint = StudioBg, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Tambah Cue", color = StudioBg, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun VoiceCueCard(
    cue: VoiceCueItem,
    song: SongConfig,
    onPanChange: (Float) -> Unit,
    onProfileChange: (String) -> Unit,
    onAudition: () -> Unit,
    onDelete: () -> Unit
) {
    var profileMenuOpen by remember { mutableStateOf(false) }
    val activeProfile = song.cueSoundProfiles.firstOrNull { it.id == cue.soundProfileId }
    val effectivePan = (cue.pan + (activeProfile?.pan ?: 0f)).coerceIn(-100f, 100f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(StripSurface)
            .border(1.dp, ConsoleBorder, RoundedCornerShape(14.dp))
            .padding(12.dp)
            .testTag("voice_cue_card_${cue.id}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "BAR ${cue.bar}.${cue.beat}",
                        style = MaterialTheme.typography.labelMedium,
                        color = StudioBg,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(BackingIndigo)
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                    Text(
                        text = cue.label,
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${cue.section.displayName} • Pan Efektif: ${PannerDsp.formatPanLabel(effectivePan)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onAudition,
                    modifier = Modifier.testTag("audition_cue_${cue.id}")
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Tes ${cue.label}", tint = MeterGreen)
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag("delete_cue_${cue.id}")
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Hapus ${cue.label}", tint = ClipRed)
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        Box {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(ConsoleSurface)
                    .border(1.dp, ConsoleBorder, RoundedCornerShape(8.dp))
                    .clickable { profileMenuOpen = true }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Suara Cue: ${activeProfile?.displayName ?: cue.soundProfileId}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextPrimary
                )
            }
            DropdownMenu(
                expanded = profileMenuOpen,
                onDismissRequest = { profileMenuOpen = false }
            ) {
                song.cueSoundProfiles.forEach { profile ->
                    DropdownMenuItem(
                        text = { Text("${profile.displayName} (${PannerDsp.formatPanLabel(profile.pan)})") },
                        onClick = {
                            profileMenuOpen = false
                            onProfileChange(profile.id)
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        StereoPanControlRow(
            pan = cue.pan,
            accentColor = CueAmber,
            testTagPrefix = "cue_${cue.id}",
            onPanChange = onPanChange
        )
    }
}
