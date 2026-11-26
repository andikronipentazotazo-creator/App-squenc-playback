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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewWeek
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.PannerDsp
import com.example.model.AudioHardwareCapability
import com.example.model.ChannelType
import com.example.model.LiveStereoPreset
import com.example.model.MeterLevels
import com.example.model.MixerChannelState
import com.example.model.OutputRouteOption
import com.example.model.SongConfig
import com.example.model.TransportState
import com.example.model.VoiceCueItem
import com.example.model.builtInLiveStereoPresets
import com.example.ui.components.ConsoleToggleButton
import com.example.ui.components.StereoPanControlRow
import com.example.ui.components.StereoPanKnob
import com.example.ui.components.StereoPeakMeter
import com.example.ui.components.VerticalDawFader
import com.example.ui.theme.BackingIndigo
import com.example.ui.theme.ClickCyan
import com.example.ui.theme.ClipRed
import com.example.ui.theme.ConsoleBorder
import com.example.ui.theme.ConsoleSurface
import com.example.ui.theme.CountInViolet
import com.example.ui.theme.CueAmber
import com.example.ui.theme.ExtraPink
import com.example.ui.theme.MeterGreen
import com.example.ui.theme.MidiTeal
import com.example.ui.theme.SoloGold
import com.example.ui.theme.StripElevated
import com.example.ui.theme.StripSurface
import com.example.ui.theme.StudioBg
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun MixerScreen(
    song: SongConfig,
    transport: TransportState,
    meters: MeterLevels,
    hardware: AudioHardwareCapability,
    onVolumeChange: (String, Float) -> Unit,
    onPanChange: (String, Float) -> Unit,
    onToggleMute: (String) -> Unit,
    onToggleSolo: (String) -> Unit,
    onOutputRouteChange: (String, OutputRouteOption) -> Unit,
    onApplyPreset: (LiveStereoPreset) -> Unit,
    onAddTrack: (String) -> Unit,
    onRemoveTrack: (String) -> Unit,
    onImportWav: (String, Uri) -> Unit,
    onSeekToBar: (Int) -> Unit,
    onAuditionCue: (VoiceCueItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var pendingWavChannelId by remember { mutableStateOf<String?>(null) }
    var showAddTrackDialog by remember { mutableStateOf(false) }
    var showTimelineSidebar by remember { mutableStateOf(true) }

    val wavPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        val targetChannelId = pendingWavChannelId
        if (uri != null && targetChannelId != null) {
            onImportWav(targetChannelId, uri)
        }
        pendingWavChannelId = null
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(StudioBg)
    ) {
        val canShowTimelineColumn = maxWidth >= 820.dp

        Column(modifier = Modifier.fillMaxSize()) {
            // Quick Live Stereo Output Presets + Add Track + Tablet Timeline Toggle
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = ClickCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "PRESET IEM:",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                items(builtInLiveStereoPresets, key = { it.id }) { preset ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(StripSurface)
                            .border(1.dp, ConsoleBorder, RoundedCornerShape(8.dp))
                            .clickable { onApplyPreset(preset) }
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                            .testTag("mixer_preset_${preset.id}")
                    ) {
                        Text(
                            text = preset.name,
                            style = MaterialTheme.typography.labelMedium,
                            color = TextPrimary
                        )
                    }
                }

                item {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(ClickCyan.copy(alpha = 0.16f))
                            .border(1.dp, ClickCyan.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                            .clickable { showAddTrackDialog = true }
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                            .testTag("add_audio_track_button")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Tambah Track Audio",
                                tint = ClickCyan,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Tambah Track",
                                style = MaterialTheme.typography.labelMedium,
                                color = ClickCyan,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                if (canShowTimelineColumn) {
                    item {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (showTimelineSidebar) BackingIndigo.copy(alpha = 0.25f) else StripSurface)
                                .border(1.dp, if (showTimelineSidebar) BackingIndigo else ConsoleBorder, RoundedCornerShape(8.dp))
                                .clickable { showTimelineSidebar = !showTimelineSidebar }
                                .padding(horizontal = 10.dp, vertical = 7.dp)
                                .testTag("toggle_timeline_column_button")
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.ViewWeek,
                                    contentDescription = "Tampilkan Kolom Timeline",
                                    tint = if (showTimelineSidebar) ClickCyan else TextSecondary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = if (showTimelineSidebar) "Kolom Timeline: ON" else "Kolom Timeline: OFF",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (showTimelineSidebar) ClickCyan else TextSecondary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Honest Hardware Status Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(ConsoleSurface)
                    .border(1.dp, ConsoleBorder, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (hardware.supportsMultiChannelRouting) {
                        "USB Multi-Channel (${hardware.maxOutputChannels}ch): ${hardware.activeDeviceName}"
                    } else {
                        "Stereo Output (${hardware.activeDeviceName}) • Kiri (L) / Tengah (C) / Kanan (R) Panning"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (hardware.supportsMultiChannelRouting) MeterGreen else TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "Master: ${PannerDsp.gainToDbLabel(meters.masterLeft.coerceAtLeast(meters.masterRight))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (meters.isClipping) ClipRed else ClickCyan
                )
            }

            Spacer(Modifier.height(8.dp))

            // Main Workspace: Vertical Channel Strips + Optional Tablet Landscape Timeline Column
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LazyRow(
                    modifier = Modifier
                        .weight(if (canShowTimelineColumn && showTimelineSidebar) 0.67f else 1f)
                        .fillMaxHeight(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(song.channels, key = { it.id }) { channel ->
                        val peaks = meters.channelPeaks[channel.id] ?: (0f to 0f)
                        MixerChannelStrip(
                            channel = channel,
                            leftPeak = peaks.first,
                            rightPeak = peaks.second,
                            supportsMultiChannel = hardware.supportsMultiChannelRouting,
                            onVolumeChange = { onVolumeChange(channel.id, it) },
                            onPanChange = { onPanChange(channel.id, it) },
                            onToggleMute = { onToggleMute(channel.id) },
                            onToggleSolo = { onToggleSolo(channel.id) },
                            onOutputRouteChange = { onOutputRouteChange(channel.id, it) },
                            onPickWav = {
                                pendingWavChannelId = channel.id
                                wavPickerLauncher.launch(arrayOf("audio/*"))
                            },
                            onRemoveTrack = if (channel.type == ChannelType.ADDITIONAL) {
                                { onRemoveTrack(channel.id) }
                            } else null
                        )
                    }
                }

                if (canShowTimelineColumn && showTimelineSidebar) {
                    TimelinePanelColumn(
                        song = song,
                        transport = transport,
                        onSeekToBar = onSeekToBar,
                        onAuditionCue = onAuditionCue,
                        modifier = Modifier.weight(0.33f)
                    )
                }
            }
        }
    }

    if (showAddTrackDialog) {
        var trackName by remember { mutableStateOf("Synth Pad Layer") }
        AlertDialog(
            onDismissRequest = { showAddTrackDialog = false },
            title = { Text("Tambah Track Audio") },
            text = {
                OutlinedTextField(
                    value = trackName,
                    onValueChange = { trackName = it },
                    label = { Text("Nama Channel") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onAddTrack(trackName)
                        showAddTrackDialog = false
                    }
                ) { Text("Tambah") }
            },
            dismissButton = {
                TextButton(onClick = { showAddTrackDialog = false }) { Text("Batal") }
            }
        )
    }
}

@Composable
private fun MixerChannelStrip(
    channel: MixerChannelState,
    leftPeak: Float,
    rightPeak: Float,
    supportsMultiChannel: Boolean,
    onVolumeChange: (Float) -> Unit,
    onPanChange: (Float) -> Unit,
    onToggleMute: () -> Unit,
    onToggleSolo: () -> Unit,
    onOutputRouteChange: (OutputRouteOption) -> Unit,
    onPickWav: () -> Unit,
    onRemoveTrack: (() -> Unit)?
) {
    val accent = when (channel.type) {
        ChannelType.BACKING -> BackingIndigo
        ChannelType.METRONOME -> ClickCyan
        ChannelType.VOICE_CUE -> CueAmber
        ChannelType.COUNT_IN -> CountInViolet
        ChannelType.MIDI -> MidiTeal
        ChannelType.ADDITIONAL -> ExtraPink
    }

    var routeMenuOpen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .width(132.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(14.dp))
            .background(StripSurface)
            .border(1.dp, ConsoleBorder, RoundedCornerShape(14.dp))
            .padding(8.dp)
            .testTag("channel_strip_${channel.id}"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accent)
        )

        Spacer(Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = channel.name,
                style = MaterialTheme.typography.titleSmall,
                fontSize = 12.sp,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (onRemoveTrack != null) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Hapus ${channel.name}",
                    tint = TextMuted,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable(onClick = onRemoveTrack)
                )
            }
        }

        if (channel.type == ChannelType.BACKING || channel.type == ChannelType.ADDITIONAL) {
            Row(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(ConsoleSurface)
                    .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                    .clickable(onClick = onPickWav)
                    .padding(horizontal = 6.dp, vertical = 4.dp)
                    .testTag("import_wav_${channel.id}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AudioFile,
                    contentDescription = "Import Lagu Audio",
                    tint = accent,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = channel.customAudioName ?: "Import Lagu",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        // Output Bus Pill
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(StripElevated)
                    .border(1.dp, ConsoleBorder, RoundedCornerShape(6.dp))
                    .clickable { routeMenuOpen = true }
                    .padding(horizontal = 6.dp, vertical = 5.dp)
                    .testTag("route_button_${channel.id}"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = channel.outputRoute.shortLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (channel.outputRoute.requiresMultiChannel && !supportsMultiChannel) {
                        CueAmber
                    } else {
                        accent
                    },
                    maxLines = 1
                )
            }
            DropdownMenu(
                expanded = routeMenuOpen,
                onDismissRequest = { routeMenuOpen = false }
            ) {
                OutputRouteOption.entries.forEach { option ->
                    val enabled = !option.requiresMultiChannel || supportsMultiChannel
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = if (enabled) option.displayName else "${option.displayName} (USB 4ch req.)",
                                color = if (enabled) TextPrimary else TextMuted
                            )
                        },
                        enabled = enabled,
                        onClick = {
                            routeMenuOpen = false
                            onOutputRouteChange(option)
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        StereoPanKnob(
            pan = channel.pan,
            accentColor = accent,
            onResetCenter = { onPanChange(0f) }
        )
        StereoPanControlRow(
            pan = channel.pan,
            accentColor = accent,
            testTagPrefix = channel.id,
            onPanChange = onPanChange
        )

        Spacer(Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ConsoleToggleButton(
                label = "M",
                isActive = channel.isMuted,
                activeColor = ClipRed,
                testTag = "mute_${channel.id}",
                onClick = onToggleMute,
                modifier = Modifier.weight(1f)
            )
            ConsoleToggleButton(
                label = "S",
                isActive = channel.isSolo,
                activeColor = SoloGold,
                testTag = "solo_${channel.id}",
                onClick = onToggleSolo,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            VerticalDawFader(
                value = channel.volumeGain,
                accentColor = accent,
                testTag = "fader_${channel.id}",
                onValueChange = onVolumeChange
            )
            Spacer(Modifier.width(8.dp))
            StereoPeakMeter(
                leftPeak = leftPeak,
                rightPeak = rightPeak
            )
        }

        Spacer(Modifier.height(4.dp))

        Text(
            text = PannerDsp.gainToDbLabel(channel.volumeGain),
            style = MaterialTheme.typography.labelMedium,
            color = TextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(StudioBg)
                .padding(vertical = 3.dp)
        )
    }
}
