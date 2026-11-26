package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.ViewWeek
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.PannerDsp
import com.example.model.ChannelType
import com.example.model.SongConfig
import com.example.model.SongSection
import com.example.model.SongSectionType
import com.example.model.TransportState
import com.example.model.VoiceCueItem
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
import com.example.ui.theme.StripElevated
import com.example.ui.theme.StripSurface
import com.example.ui.theme.StudioBg
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.sin

@Composable
fun TimelineScreen(
    song: SongConfig,
    transport: TransportState,
    onSeekToBar: (Int) -> Unit,
    onAuditionCue: (VoiceCueItem) -> Unit,
    onUpdateTotalBars: (Int) -> Unit,
    onUpdateSectionRange: (String, Int, Int) -> Unit,
    onAddCueAtBar: (Int, SongSectionType) -> Unit,
    modifier: Modifier = Modifier
) {
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
                // Left Main Column: Multi-Track Timeline Arrangement Grid
                Column(
                    modifier = Modifier
                        .weight(0.64f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(16.dp))
                        .background(ConsoleSurface)
                        .border(1.dp, ConsoleBorder, RoundedCornerShape(16.dp))
                        .padding(12.dp)
                ) {
                    TimelineHeaderBar(
                        song = song,
                        transport = transport,
                        onUpdateTotalBars = onUpdateTotalBars
                    )
                    Spacer(Modifier.height(10.dp))
                    MultiTrackTimelineGrid(
                        song = song,
                        transport = transport,
                        onSeekToBar = onSeekToBar,
                        onAuditionCue = onAuditionCue,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Right Column: Section & Cue Timeline Inspector
                TimelineInspectorColumn(
                    song = song,
                    transport = transport,
                    onSeekToBar = onSeekToBar,
                    onAuditionCue = onAuditionCue,
                    onUpdateSectionRange = onUpdateSectionRange,
                    onAddCueAtBar = onAddCueAtBar,
                    modifier = Modifier
                        .weight(0.36f)
                        .fillMaxHeight()
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(ConsoleSurface)
                            .border(1.dp, ConsoleBorder, RoundedCornerShape(16.dp))
                            .padding(12.dp)
                    ) {
                        TimelineHeaderBar(
                            song = song,
                            transport = transport,
                            onUpdateTotalBars = onUpdateTotalBars
                        )
                        Spacer(Modifier.height(10.dp))
                        MultiTrackTimelineGrid(
                            song = song,
                            transport = transport,
                            onSeekToBar = onSeekToBar,
                            onAuditionCue = onAuditionCue,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(340.dp)
                        )
                    }
                }

                item {
                    TimelineInspectorSectionCards(
                        song = song,
                        transport = transport,
                        onSeekToBar = onSeekToBar,
                        onAuditionCue = onAuditionCue,
                        onUpdateSectionRange = onUpdateSectionRange,
                        onAddCueAtBar = onAddCueAtBar
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineHeaderBar(
    song: SongConfig,
    transport: TransportState,
    onUpdateTotalBars: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.ViewWeek, contentDescription = null, tint = ClickCyan)
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    text = "Timeline Lagu & Cue",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Text(
                    text = "Ketuk bar di ruler untuk melompat • ${song.bpm} BPM • ${song.beatsPerBar}/4",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Total Bars adjuster
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(StripSurface)
                .border(1.dp, ConsoleBorder, RoundedCornerShape(10.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "−",
                style = MaterialTheme.typography.titleMedium,
                color = TextSecondary,
                modifier = Modifier
                    .clickable { onUpdateTotalBars(song.totalBars - 4) }
                    .padding(horizontal = 4.dp)
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${song.totalBars}",
                    style = MaterialTheme.typography.labelLarge,
                    color = ClickCyan
                )
                Text(
                    text = "BAR",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
            Text(
                text = "+",
                style = MaterialTheme.typography.titleMedium,
                color = TextSecondary,
                modifier = Modifier
                    .clickable { onUpdateTotalBars(song.totalBars + 4) }
                    .padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
private fun MultiTrackTimelineGrid(
    song: SongConfig,
    transport: TransportState,
    onSeekToBar: (Int) -> Unit,
    onAuditionCue: (VoiceCueItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val barWidth: Dp = 44.dp
    val labelColWidth: Dp = 96.dp
    val totalTrackWidth = barWidth * song.totalBars
    val scrollState = rememberScrollState()
    var rulerWidthPx by remember { mutableStateOf(1f) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(StudioBg)
            .border(1.dp, ConsoleBorder, RoundedCornerShape(12.dp))
    ) {
        // Fixed Left Track Label Column
        Column(
            modifier = Modifier
                .width(labelColWidth)
                .fillMaxHeight()
                .background(StripSurface)
                .border(width = 1.dp, color = ConsoleBorder)
        ) {
            // Bar Ruler Label
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .background(ConsoleSurface)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = "BAR",
                    style = MaterialTheme.typography.labelSmall,
                    color = ClickCyan,
                    fontWeight = FontWeight.Bold
                )
            }

            // Section Row Label
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = "Section",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
            }

            // Track Labels
            song.channels.forEach { ch ->
                val accent = channelAccentColor(ch.type)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .border(width = 0.5.dp, color = ConsoleBorder.copy(alpha = 0.5f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(accent)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = ch.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = if (ch.isMuted) "MUTED" else PannerDsp.formatPanLabel(ch.pan),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        color = if (ch.isMuted) ClipRed else accent
                    )
                }
            }
        }

        // Scrollable Horizontal Timeline Tracks + Playhead
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .horizontalScroll(scrollState)
        ) {
            Box(modifier = Modifier.width(totalTrackWidth).fillMaxHeight()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // 1. Bar Ruler (Tap to seek!)
                    Row(
                        modifier = Modifier
                            .width(totalTrackWidth)
                            .height(34.dp)
                            .background(ConsoleSurface)
                            .onSizeChanged { rulerWidthPx = it.width.toFloat().coerceAtLeast(1f) }
                            .pointerInput(song.totalBars) {
                                detectTapGestures { offset ->
                                    val bar = ((offset.x / rulerWidthPx) * song.totalBars).toInt() + 1
                                    onSeekToBar(bar.coerceIn(1, song.totalBars))
                                }
                            }
                            .testTag("timeline_bar_ruler")
                    ) {
                        for (bar in 1..song.totalBars) {
                            val isCurrent = transport.currentBar == bar
                            Box(
                                modifier = Modifier
                                    .width(barWidth)
                                    .fillMaxHeight()
                                    .border(0.5.dp, ConsoleBorder.copy(alpha = 0.6f))
                                    .background(if (isCurrent) ClickCyan.copy(alpha = 0.22f) else Color.Transparent),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$bar",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isCurrent) ClickCyan else TextSecondary,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    // 2. Song Sections Row
                    Box(
                        modifier = Modifier
                            .width(totalTrackWidth)
                            .height(38.dp)
                            .background(StudioBg)
                    ) {
                        song.sections.forEach { sec ->
                            val leftOffset = barWidth * (sec.startBar - 1).coerceAtLeast(0)
                            val width = barWidth * sec.lengthBars.coerceAtLeast(1)
                            val secColor = sectionColor(sec.type)
                            val isActive = transport.activeSection == sec.type
                            Row(
                                modifier = Modifier
                                    .offset(x = leftOffset)
                                    .width(width)
                                    .fillMaxHeight()
                                    .padding(2.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(secColor.copy(alpha = if (isActive) 0.38f else 0.20f))
                                    .border(1.dp, secColor.copy(alpha = if (isActive) 0.9f else 0.5f), RoundedCornerShape(6.dp))
                                    .clickable { onSeekToBar(sec.startBar) }
                                    .padding(horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = sec.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = PannerDsp.formatPanLabel(sec.defaultCuePan),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 9.sp,
                                    color = secColor
                                )
                            }
                        }
                    }

                    // 3. Channel Lanes
                    song.channels.forEach { ch ->
                        val accent = channelAccentColor(ch.type)
                        Box(
                            modifier = Modifier
                                .width(totalTrackWidth)
                                .height(48.dp)
                                .border(0.5.dp, ConsoleBorder.copy(alpha = 0.4f))
                        ) {
                            // Subtle bar gridlines
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val barPx = barWidth.toPx()
                                for (b in 1..song.totalBars) {
                                    val x = b * barPx
                                    drawLine(
                                        color = ConsoleBorder.copy(alpha = 0.25f),
                                        start = Offset(x, 0f),
                                        end = Offset(x, size.height),
                                        strokeWidth = 1f
                                    )
                                }

                                when (ch.type) {
                                    ChannelType.BACKING, ChannelType.MIDI, ChannelType.ADDITIONAL -> {
                                        // Musical waveform ribbon
                                        val path = Path()
                                        val midY = size.height / 2f
                                        path.moveTo(0f, midY)
                                        var x = 0f
                                        while (x < size.width) {
                                            val amp = (size.height * 0.30f) *
                                                (0.45f + 0.55f * kotlin.math.abs(sin(x * 0.09f + ch.id.hashCode())))
                                            path.lineTo(x, midY - amp)
                                            path.lineTo(x + 4f, midY + amp)
                                            x += 8f
                                        }
                                        drawPath(
                                            path = path,
                                            color = if (ch.isMuted) TextMuted.copy(alpha = 0.25f) else accent.copy(alpha = 0.6f),
                                            style = Stroke(width = 1.6.dp.toPx())
                                        )
                                    }
                                    ChannelType.METRONOME -> {
                                        // Beat click ticks per bar
                                        for (b in 0 until song.totalBars) {
                                            for (beat in 0 until song.beatsPerBar) {
                                                val tickX = b * barPx + (beat * (barPx / song.beatsPerBar)) + 4.dp.toPx()
                                                val tickH = if (beat == 0) size.height * 0.65f else size.height * 0.38f
                                                drawLine(
                                                    color = if (ch.isMuted) TextMuted else accent,
                                                    start = Offset(tickX, size.height - 6.dp.toPx()),
                                                    end = Offset(tickX, size.height - 6.dp.toPx() - tickH),
                                                    strokeWidth = if (beat == 0) 2.5.dp.toPx() else 1.5.dp.toPx()
                                                )
                                            }
                                        }
                                    }
                                    else -> Unit
                                }
                            }

                            if (ch.type == ChannelType.BACKING && ch.customAudioName != null) {
                                Text(
                                    text = "♪ ${ch.customAudioName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextPrimary,
                                    modifier = Modifier
                                        .align(Alignment.CenterStart)
                                        .padding(horizontal = 8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(StripElevated.copy(alpha = 0.85f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }

                            // Voice Cue markers on Voice Cue lane
                            if (ch.type == ChannelType.VOICE_CUE) {
                                song.cues.forEach { cue ->
                                    val cueX = barWidth * (cue.bar - 1).coerceAtLeast(0)
                                    Row(
                                        modifier = Modifier
                                            .offset(x = cueX + 2.dp)
                                            .align(Alignment.CenterStart)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(CueAmber.copy(alpha = 0.25f))
                                            .border(1.dp, CueAmber, RoundedCornerShape(6.dp))
                                            .clickable { onAuditionCue(cue) }
                                            .padding(horizontal = 6.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.RecordVoiceOver,
                                            contentDescription = null,
                                            tint = CueAmber,
                                            modifier = Modifier.size(11.dp)
                                        )
                                        Spacer(Modifier.width(3.dp))
                                        Text(
                                            text = "${cue.spokenPhrase} (${PannerDsp.formatPanLabel(cue.pan)})",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 9.sp,
                                            color = TextPrimary,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Live Vertical Playhead Overlay across all tracks
                val playheadBarPos = (transport.currentBar - 1).toFloat() + transport.barProgressFraction
                val playheadOffset = barWidth * playheadBarPos
                Box(
                    modifier = Modifier
                        .offset(x = playheadOffset)
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(ClickCyan)
                )
            }
        }
    }
}

/**
 * Compact synchronized Timeline column embedded inside Mixer in Tablet Landscape mode
 * so musicians/engineers see Song Sections, Bar Progress, and Cues next to channel faders!
 */
@Composable
fun TimelinePanelColumn(
    song: SongConfig,
    transport: TransportState,
    onSeekToBar: (Int) -> Unit,
    onAuditionCue: (VoiceCueItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(14.dp))
            .background(ConsoleSurface)
            .border(1.dp, ConsoleBorder, RoundedCornerShape(14.dp))
            .padding(10.dp)
            .testTag("mixer_timeline_column")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ViewWeek, contentDescription = null, tint = ClickCyan, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "TIMELINE & CUES",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "Bar ${transport.currentBar}/${song.totalBars}",
                style = MaterialTheme.typography.labelMedium,
                color = ClickCyan
            )
        }

        Spacer(Modifier.height(8.dp))

        // Progress bar across song bars
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(StudioBg)
        ) {
            val frac = (((transport.currentBar - 1) + transport.barProgressFraction) /
                song.totalBars.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxWidth(frac.coerceAtLeast(0.03f))
                    .fillMaxHeight()
                    .background(ClickCyan)
            )
        }

        Spacer(Modifier.height(10.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    text = "Struktur Lagu (Ketuk untuk Lompat)",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }

            items(song.sections, key = { it.id }) { sec ->
                val active = transport.activeSection == sec.type
                val secColor = sectionColor(sec.type)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (active) secColor.copy(alpha = 0.25f) else StripSurface)
                        .border(1.dp, if (active) secColor else ConsoleBorder, RoundedCornerShape(10.dp))
                        .clickable { onSeekToBar(sec.startBar) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = sec.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (active) TextPrimary else TextSecondary
                        )
                        Text(
                            text = "Bar ${sec.startBar} – ${sec.startBar + sec.lengthBars - 1}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }
                    Text(
                        text = PannerDsp.formatPanLabel(sec.defaultCuePan),
                        style = MaterialTheme.typography.labelSmall,
                        color = secColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Voice Cue Triggers",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }

            items(song.cues, key = { it.id }) { cue ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(StripSurface)
                        .border(1.dp, ConsoleBorder, RoundedCornerShape(10.dp))
                        .clickable { onSeekToBar(cue.bar) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "B${cue.bar}.${cue.beat} • ${cue.label}",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Pan: ${PannerDsp.formatPanLabel(cue.pan)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = CueAmber
                        )
                    }
                    IconButton(
                        onClick = { onAuditionCue(cue) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Audition ${cue.label}", tint = MeterGreen, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineInspectorColumn(
    song: SongConfig,
    transport: TransportState,
    onSeekToBar: (Int) -> Unit,
    onAuditionCue: (VoiceCueItem) -> Unit,
    onUpdateSectionRange: (String, Int, Int) -> Unit,
    onAddCueAtBar: (Int, SongSectionType) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(ConsoleSurface)
            .border(1.dp, ConsoleBorder, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            TimelineInspectorSectionCards(
                song = song,
                transport = transport,
                onSeekToBar = onSeekToBar,
                onAuditionCue = onAuditionCue,
                onUpdateSectionRange = onUpdateSectionRange,
                onAddCueAtBar = onAddCueAtBar
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimelineInspectorSectionCards(
    song: SongConfig,
    transport: TransportState,
    onSeekToBar: (Int) -> Unit,
    onAuditionCue: (VoiceCueItem) -> Unit,
    onUpdateSectionRange: (String, Int, Int) -> Unit,
    onAddCueAtBar: (Int, SongSectionType) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Struktur & Cue di Bar ${transport.currentBar}",
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary
            )
            Button(
                onClick = { onAddCueAtBar(transport.currentBar, transport.activeSection) },
                colors = ButtonDefaults.buttonColors(containerColor = CueAmber),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                modifier = Modifier.testTag("timeline_add_cue_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = StudioBg, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text("+ Cue di Bar ${transport.currentBar}", color = StudioBg, style = MaterialTheme.typography.labelSmall)
            }
        }

        // Editable Sections
        song.sections.forEach { sec ->
            val secColor = sectionColor(sec.type)
            val isActive = transport.activeSection == sec.type
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isActive) secColor.copy(alpha = 0.16f) else StripSurface)
                    .border(1.dp, if (isActive) secColor else ConsoleBorder, RoundedCornerShape(10.dp))
                    .clickable { onSeekToBar(sec.startBar) }
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = sec.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary
                    )
                    Text(
                        text = "Mulai Bar ${sec.startBar} • Panjang ${sec.lengthBars} bar",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SmallStepChip("−") { onUpdateSectionRange(sec.id, sec.startBar, (sec.lengthBars - 1).coerceAtLeast(1)) }
                    SmallStepChip("+") { onUpdateSectionRange(sec.id, sec.startBar, sec.lengthBars + 1) }
                }
            }
        }

        // Cues Quick List
        Text(
            text = "Daftar Cue pada Timeline",
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            song.cues.forEach { cue ->
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(StripSurface)
                        .border(1.dp, ConsoleBorder, RoundedCornerShape(8.dp))
                        .clickable { onAuditionCue(cue) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "B${cue.bar}: ${cue.label} (${PannerDsp.formatPanLabel(cue.pan)})",
                        style = MaterialTheme.typography.labelSmall,
                        color = CueAmber
                    )
                }
            }
        }
    }
}

@Composable
private fun SmallStepChip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(StripElevated)
            .border(1.dp, ConsoleBorder, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = TextPrimary)
    }
}

private fun channelAccentColor(type: ChannelType): Color = when (type) {
    ChannelType.BACKING -> BackingIndigo
    ChannelType.METRONOME -> ClickCyan
    ChannelType.VOICE_CUE -> CueAmber
    ChannelType.COUNT_IN -> CountInViolet
    ChannelType.MIDI -> MidiTeal
    ChannelType.ADDITIONAL -> ExtraPink
}

private fun sectionColor(type: SongSectionType): Color = when (type) {
    SongSectionType.INTRO -> ClickCyan
    SongSectionType.VERSE -> BackingIndigo
    SongSectionType.CHORUS -> CueAmber
    SongSectionType.BRIDGE -> CountInViolet
    SongSectionType.ENDING -> MeterGreen
}
