package com.example.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.audio.PannerDsp
import com.example.model.AudioHardwareCapability
import com.example.model.LiveStereoPreset
import com.example.model.MixerChannelState
import com.example.model.OutputRouteOption
import com.example.model.SongConfig
import com.example.model.builtInLiveStereoPresets
import com.example.ui.theme.BackingIndigo
import com.example.ui.theme.ClickCyan
import com.example.ui.theme.ConsoleBorder
import com.example.ui.theme.ConsoleSurface
import com.example.ui.theme.CueAmber
import com.example.ui.theme.MeterGreen
import com.example.ui.theme.StripElevated
import com.example.ui.theme.StripSurface
import com.example.ui.theme.StudioBg
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun RoutingScreen(
    song: SongConfig,
    hardware: AudioHardwareCapability,
    onRefreshDevices: () -> Unit,
    onApplyPreset: (LiveStereoPreset) -> Unit,
    onOutputRouteChange: (String, OutputRouteOption) -> Unit,
    modifier: Modifier = Modifier
) {
    val backingPan = song.channels.firstOrNull { it.id == "backing" }?.pan ?: 0f
    val metroPan = song.channels.firstOrNull { it.id == "metronome" }?.pan ?: -100f
    val cuePan = song.channels.firstOrNull { it.id == "voice_cue" }?.pan ?: 100f

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
                // Left Column: Hardware Info + Output Bus Assignments
                LazyColumn(
                    modifier = Modifier
                        .weight(0.52f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item { HardwareStatusCard(hardware, onRefreshDevices) }
                    item {
                        Text(
                            text = "Routing Output per Channel",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                    }
                    items(song.channels, key = { it.id }) { channel ->
                        ChannelRouteRow(channel, hardware.supportsMultiChannelRouting, onOutputRouteChange)
                    }
                }

                // Right Column: Live Stereo Field Diagram + Live Stereo Config Presets
                LazyColumn(
                    modifier = Modifier
                        .weight(0.48f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item { StereoFieldDiagramCard(backingPan, metroPan, cuePan) }
                    item {
                        Text(
                            text = "Konfigurasi Output Live Stereo (IEM)",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                    }
                    items(builtInLiveStereoPresets, key = { it.id }) { preset ->
                        PresetRouteCard(preset, backingPan, metroPan, cuePan, onApplyPreset)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { HardwareStatusCard(hardware, onRefreshDevices) }
                item { StereoFieldDiagramCard(backingPan, metroPan, cuePan) }
                item {
                    Text(
                        text = "Konfigurasi Output Live Stereo (IEM)",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                }
                items(builtInLiveStereoPresets, key = { it.id }) { preset ->
                    PresetRouteCard(preset, backingPan, metroPan, cuePan, onApplyPreset)
                }
                item {
                    Text(
                        text = "Routing Output per Channel",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                }
                items(song.channels, key = { it.id }) { channel ->
                    ChannelRouteRow(channel, hardware.supportsMultiChannelRouting, onOutputRouteChange)
                }
            }
        }
    }
}

@Composable
private fun HardwareStatusCard(
    hardware: AudioHardwareCapability,
    onRefreshDevices: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(StripSurface)
            .border(
                1.dp,
                if (hardware.supportsMultiChannelRouting) MeterGreen else ConsoleBorder,
                RoundedCornerShape(14.dp)
            )
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(
                    imageVector = if (hardware.isUsbAudioInterface) Icons.Default.Usb else Icons.Default.Cable,
                    contentDescription = null,
                    tint = if (hardware.supportsMultiChannelRouting) MeterGreen else ClickCyan
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        text = hardware.activeDeviceName,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = "${hardware.activeDeviceType} • ${hardware.maxOutputChannels} Output Channel",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            IconButton(
                onClick = onRefreshDevices,
                modifier = Modifier.testTag("refresh_audio_devices_button")
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Scan Ulang Hardware Audio", tint = ClickCyan)
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (hardware.supportsMultiChannelRouting) MeterGreen.copy(alpha = 0.14f)
                    else CueAmber.copy(alpha = 0.12f)
                )
                .border(
                    1.dp,
                    if (hardware.supportsMultiChannelRouting) MeterGreen.copy(alpha = 0.45f)
                    else CueAmber.copy(alpha = 0.45f),
                    RoundedCornerShape(10.dp)
                )
                .padding(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = if (hardware.supportsMultiChannelRouting) Icons.Default.CheckCircle else Icons.Default.Info,
                contentDescription = null,
                tint = if (hardware.supportsMultiChannelRouting) MeterGreen else CueAmber,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (hardware.supportsMultiChannelRouting) {
                    "USB Audio Interface multi-channel (${hardware.maxOutputChannels} ch) terdeteksi. Output fisik terpisah (Out 1-2, Out 3, Out 4) aktif."
                } else {
                    "Mode Output Stereo (2ch): Perangkat hanya memiliki output stereo 2 channel. Pemisahan menggunakan Stereo Panning (Kiri/Kanan) dan BUKAN output fisik terpisah."
                },
                style = MaterialTheme.typography.bodySmall,
                color = TextPrimary
            )
        }

        if (hardware.connectedDevices.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Perangkat Terdeteksi: ${hardware.connectedDevices.joinToString(" • ")}",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun PresetRouteCard(
    preset: LiveStereoPreset,
    backingPan: Float,
    metroPan: Float,
    cuePan: Float,
    onApplyPreset: (LiveStereoPreset) -> Unit
) {
    val isActive = kotlin.math.abs(backingPan - preset.backingPan) < 2f &&
        kotlin.math.abs(metroPan - preset.metronomePan) < 2f &&
        kotlin.math.abs(cuePan - preset.voiceCuePan) < 2f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isActive) ClickCyan.copy(alpha = 0.14f) else StripSurface)
            .border(1.dp, if (isActive) ClickCyan else ConsoleBorder, RoundedCornerShape(12.dp))
            .clickable { onApplyPreset(preset) }
            .padding(12.dp)
            .testTag("routing_preset_${preset.id}"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = preset.name,
                style = MaterialTheme.typography.titleSmall,
                color = if (isActive) ClickCyan else TextPrimary
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = preset.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
        if (isActive) {
            Text(
                text = "AKTIF",
                style = MaterialTheme.typography.labelSmall,
                color = ClickCyan,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ChannelRouteRow(
    channel: MixerChannelState,
    supportsMultiChannel: Boolean,
    onOutputRouteChange: (String, OutputRouteOption) -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(StripSurface)
            .border(1.dp, ConsoleBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = channel.name,
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary
            )
            Text(
                text = "Pan: ${PannerDsp.formatPanLabel(channel.pan)}",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }

        Box {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(StripElevated)
                    .border(1.dp, ConsoleBorder, RoundedCornerShape(8.dp))
                    .clickable { menuOpen = true }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .testTag("routing_bus_${channel.id}")
            ) {
                Text(
                    text = channel.outputRoute.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = ClickCyan
                )
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false }
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
                            menuOpen = false
                            onOutputRouteChange(channel.id, option)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun StereoFieldDiagramCard(
    backingPan: Float,
    metroPan: Float,
    cuePan: Float
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ConsoleSurface)
            .border(1.dp, ConsoleBorder, RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Text(
            text = "Monitor Posisi Stereo (Kiri / Tengah / Kanan)",
            style = MaterialTheme.typography.titleSmall,
            color = TextPrimary
        )
        Spacer(Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(82.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(StudioBg)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                drawLine(
                    color = ConsoleBorder,
                    start = Offset(0f, h / 2f),
                    end = Offset(w, h / 2f),
                    strokeWidth = 2.dp.toPx()
                )
                drawLine(
                    color = TextMuted,
                    start = Offset(w / 2f, 6.dp.toPx()),
                    end = Offset(w / 2f, h - 6.dp.toPx()),
                    strokeWidth = 1.5.dp.toPx()
                )

                fun drawMarker(pan: Float, y: Float, color: Color) {
                    val x = ((pan.coerceIn(-100f, 100f) + 100f) / 200f) * w
                    drawCircle(color = color, radius = 7.dp.toPx(), center = Offset(x, y))
                }

                drawMarker(backingPan, h * 0.25f, BackingIndigo)
                drawMarker(metroPan, h * 0.50f, ClickCyan)
                drawMarker(cuePan, h * 0.75f, CueAmber)
            }
        }

        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Backing: ${PannerDsp.formatPanLabel(backingPan)}", style = MaterialTheme.typography.labelSmall, color = BackingIndigo)
            Text("Click: ${PannerDsp.formatPanLabel(metroPan)}", style = MaterialTheme.typography.labelSmall, color = ClickCyan)
            Text("Cue: ${PannerDsp.formatPanLabel(cuePan)}", style = MaterialTheme.typography.labelSmall, color = CueAmber)
        }
    }
}
