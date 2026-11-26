package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.audio.PannerDsp
import com.example.model.AutomationCurveType
import com.example.model.PanAutomationPoint
import com.example.model.SongConfig
import com.example.model.TransportState
import com.example.ui.components.StereoPanControlRow
import com.example.ui.theme.BackingIndigo
import com.example.ui.theme.ClickCyan
import com.example.ui.theme.ClipRed
import com.example.ui.theme.ConsoleBorder
import com.example.ui.theme.ConsoleSurface
import com.example.ui.theme.CueAmber
import com.example.ui.theme.MeterGreen
import com.example.ui.theme.MidiTeal
import com.example.ui.theme.StripElevated
import com.example.ui.theme.StripSurface
import com.example.ui.theme.StudioBg
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AutomationScreen(
    song: SongConfig,
    transport: TransportState,
    onToggleAutomation: (Boolean) -> Unit,
    onAddPoint: (String, Int, Float, Float, AutomationCurveType, String?) -> Unit,
    onUpdatePoint: (PanAutomationPoint) -> Unit,
    onDeletePoint: (String) -> Unit,
    onApplySweepPreset: (String, Float, Float) -> Unit,
    onApplyChorusJump: (String, Float, Float) -> Unit,
    onSeekToBar: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTargetId by remember(song.id) { mutableStateOf("voice_cue") }
    var selectedPointId by remember(song.id) { mutableStateOf<String?>(null) }

    val lanePoints = remember(song.automationPoints, selectedTargetId, song.beatsPerBar) {
        song.automationPoints
            .filter { it.targetId == selectedTargetId }
            .sortedBy { PannerDsp.pointPosition(it, song.beatsPerBar) }
    }

    val selectedPoint = lanePoints.firstOrNull { it.id == selectedPointId } ?: lanePoints.firstOrNull()

    val laneColor = when (selectedTargetId) {
        "voice_cue" -> CueAmber
        "metronome" -> ClickCyan
        "midi" -> MidiTeal
        else -> BackingIndigo
    }

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
                // Left Column: Header, Lanes, Section Bar Strip, Interactive Timeline Canvas
                Column(
                    modifier = Modifier
                        .weight(0.62f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AutomationHeaderCard(song.automationEnabled, onToggleAutomation)
                    AutomationLaneSelectorRow(
                        song = song,
                        selectedTargetId = selectedTargetId,
                        laneColor = laneColor,
                        onSelectLane = {
                            selectedTargetId = it
                            selectedPointId = null
                        }
                    )
                    SectionSeekStrip(song, transport, onSeekToBar)
                    AutomationTimelineCanvas(
                        song = song,
                        transport = transport,
                        points = lanePoints,
                        selectedPointId = selectedPoint?.id,
                        accentColor = laneColor,
                        onSelectPoint = { selectedPointId = it },
                        onDragPoint = { pt, newBar, newBeat, newPan ->
                            onUpdatePoint(pt.copy(bar = newBar, beat = newBeat, panValue = newPan))
                        },
                        onTapAddPoint = { bar, beat, pan ->
                            onAddPoint(selectedTargetId, bar, beat, pan, AutomationCurveType.LINEAR, null)
                        },
                        canvasHeightDp = 240
                    )
                }

                // Right Column: Quick Presets + Selected Point Inspector
                LazyColumn(
                    modifier = Modifier
                        .weight(0.38f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        AutomationQuickActionsCard(
                            transport = transport,
                            selectedTargetId = selectedTargetId,
                            laneColor = laneColor,
                            onAddPoint = onAddPoint,
                            onApplySweepPreset = onApplySweepPreset,
                            onApplyChorusJump = onApplyChorusJump
                        )
                    }
                    if (selectedPoint != null) {
                        item {
                            AutomationPointInspectorCard(
                                song = song,
                                selectedPoint = selectedPoint,
                                laneColor = laneColor,
                                onUpdatePoint = onUpdatePoint,
                                onDeletePoint = onDeletePoint
                            )
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { AutomationHeaderCard(song.automationEnabled, onToggleAutomation) }
                item {
                    AutomationLaneSelectorRow(
                        song = song,
                        selectedTargetId = selectedTargetId,
                        laneColor = laneColor,
                        onSelectLane = {
                            selectedTargetId = it
                            selectedPointId = null
                        }
                    )
                }
                item { SectionSeekStrip(song, transport, onSeekToBar) }
                item {
                    AutomationTimelineCanvas(
                        song = song,
                        transport = transport,
                        points = lanePoints,
                        selectedPointId = selectedPoint?.id,
                        accentColor = laneColor,
                        onSelectPoint = { selectedPointId = it },
                        onDragPoint = { pt, newBar, newBeat, newPan ->
                            onUpdatePoint(pt.copy(bar = newBar, beat = newBeat, panValue = newPan))
                        },
                        onTapAddPoint = { bar, beat, pan ->
                            onAddPoint(selectedTargetId, bar, beat, pan, AutomationCurveType.LINEAR, null)
                        },
                        canvasHeightDp = 210
                    )
                }
                item {
                    AutomationQuickActionsCard(
                        transport = transport,
                        selectedTargetId = selectedTargetId,
                        laneColor = laneColor,
                        onAddPoint = onAddPoint,
                        onApplySweepPreset = onApplySweepPreset,
                        onApplyChorusJump = onApplyChorusJump
                    )
                }
                if (selectedPoint != null) {
                    item {
                        AutomationPointInspectorCard(
                            song = song,
                            selectedPoint = selectedPoint,
                            laneColor = laneColor,
                            onUpdatePoint = onUpdatePoint,
                            onDeletePoint = onDeletePoint
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AutomationHeaderCard(
    automationEnabled: Boolean,
    onToggleAutomation: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(StripSurface)
            .border(1.dp, ConsoleBorder, RoundedCornerShape(14.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.Timeline, contentDescription = null, tint = ClickCyan)
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    text = "Otomasi Panning Timeline",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Text(
                    text = "Ketuk grafik untuk tambah titik • Geser titik untuk atur pan & bar",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
        Switch(
            checked = automationEnabled,
            onCheckedChange = onToggleAutomation,
            colors = SwitchDefaults.colors(checkedThumbColor = StudioBg, checkedTrackColor = MeterGreen),
            modifier = Modifier.testTag("automation_enable_switch")
        )
    }
}

@Composable
private fun AutomationLaneSelectorRow(
    song: SongConfig,
    selectedTargetId: String,
    laneColor: Color,
    onSelectLane: (String) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(song.channels, key = { it.id }) { ch ->
            val selected = selectedTargetId == ch.id
            val count = song.automationPoints.count { it.targetId == ch.id }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) laneColor.copy(alpha = 0.2f) else StripSurface)
                    .border(1.dp, if (selected) laneColor else ConsoleBorder, RoundedCornerShape(10.dp))
                    .clickable { onSelectLane(ch.id) }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .testTag("auto_lane_${ch.id}")
            ) {
                Text(
                    text = "${ch.name} ($count)",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) laneColor else TextPrimary
                )
            }
        }
    }
}

@Composable
private fun SectionSeekStrip(
    song: SongConfig,
    transport: TransportState,
    onSeekToBar: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(ConsoleSurface)
            .border(1.dp, ConsoleBorder, RoundedCornerShape(10.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        song.sections.forEach { section ->
            val active = transport.activeSection == section.type
            Column(
                modifier = Modifier
                    .weight(section.lengthBars.toFloat().coerceAtLeast(1f))
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (active) BackingIndigo.copy(alpha = 0.35f) else StripSurface)
                    .clickable { onSeekToBar(section.startBar) }
                    .padding(vertical = 5.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = section.type.shortCode,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (active) ClickCyan else TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "B${section.startBar}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AutomationQuickActionsCard(
    transport: TransportState,
    selectedTargetId: String,
    laneColor: Color,
    onAddPoint: (String, Int, Float, Float, AutomationCurveType, String?) -> Unit,
    onApplySweepPreset: (String, Float, Float) -> Unit,
    onApplyChorusJump: (String, Float, Float) -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = {
                onAddPoint(
                    selectedTargetId,
                    transport.currentBar,
                    transport.currentBeat.toFloat(),
                    0f,
                    AutomationCurveType.LINEAR,
                    null
                )
            },
            colors = ButtonDefaults.buttonColors(containerColor = laneColor),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            modifier = Modifier.testTag("add_automation_point_button")
        ) {
            Icon(Icons.Default.Add, contentDescription = null, tint = StudioBg, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("+ Titik @ Bar ${transport.currentBar}", color = StudioBg, style = MaterialTheme.typography.labelMedium)
        }

        OutlinedButton(
            onClick = { onApplySweepPreset(selectedTargetId, -100f, 100f) },
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            modifier = Modifier.testTag("sweep_lr_button")
        ) {
            Text("Geser Kiri → Kanan", style = MaterialTheme.typography.labelMedium, color = ClickCyan)
        }

        OutlinedButton(
            onClick = { onApplyChorusJump(selectedTargetId, -100f, 100f) },
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            modifier = Modifier.testTag("chorus_jump_button")
        ) {
            Text("Otomatis Kanan di Chorus", style = MaterialTheme.typography.labelMedium, color = CueAmber)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AutomationPointInspectorCard(
    song: SongConfig,
    selectedPoint: PanAutomationPoint,
    laneColor: Color,
    onUpdatePoint: (PanAutomationPoint) -> Unit,
    onDeletePoint: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(StripSurface)
            .border(1.dp, laneColor.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Titik Bar ${selectedPoint.bar}.${selectedPoint.beat.roundToInt()} • ${PannerDsp.formatPanLabel(selectedPoint.panValue)}",
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary
            )
            OutlinedButton(
                onClick = { onDeletePoint(selectedPoint.id) },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.testTag("delete_automation_point_button")
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Hapus Titik", tint = ClipRed, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Hapus", color = ClipRed, style = MaterialTheme.typography.labelSmall)
            }
        }

        Text(
            text = "Posisi Bar: ${selectedPoint.bar} / ${song.totalBars}",
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary
        )
        Slider(
            value = selectedPoint.bar.toFloat(),
            onValueChange = { newBar ->
                onUpdatePoint(selectedPoint.copy(bar = newBar.roundToInt().coerceIn(1, song.totalBars)))
            },
            valueRange = 1f..song.totalBars.toFloat(),
            colors = SliderDefaults.colors(thumbColor = laneColor, activeTrackColor = laneColor)
        )

        StereoPanControlRow(
            pan = selectedPoint.panValue,
            accentColor = laneColor,
            testTagPrefix = "selected_auto_pt",
            onPanChange = { onUpdatePoint(selectedPoint.copy(panValue = it)) }
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AutomationCurveType.entries.forEach { curve ->
                val active = selectedPoint.curveType == curve
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (active) laneColor else ConsoleSurface)
                        .border(1.dp, if (active) laneColor else ConsoleBorder, RoundedCornerShape(8.dp))
                        .clickable { onUpdatePoint(selectedPoint.copy(curveType = curve)) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = curve.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (active) StudioBg else TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Text(
            text = "Ikuti Posisi Cue:",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            song.cues.forEach { cue ->
                val linked = selectedPoint.linkedCueId == cue.id
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (linked) CueAmber else StripElevated)
                        .clickable {
                            onUpdatePoint(
                                selectedPoint.copy(
                                    bar = cue.bar,
                                    beat = cue.beat.toFloat(),
                                    linkedCueId = cue.id
                                )
                            )
                        }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "${cue.label} (B${cue.bar})",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (linked) StudioBg else TextPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun AutomationTimelineCanvas(
    song: SongConfig,
    transport: TransportState,
    points: List<PanAutomationPoint>,
    selectedPointId: String?,
    accentColor: Color,
    onSelectPoint: (String) -> Unit,
    onDragPoint: (PanAutomationPoint, Int, Float, Float) -> Unit,
    onTapAddPoint: (Int, Float, Float) -> Unit,
    canvasHeightDp: Int
) {
    var canvasSize by remember { mutableStateOf(IntSize(1, 1)) }
    var draggingPoint by remember { mutableStateOf<PanAutomationPoint?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ConsoleSurface)
            .border(1.dp, ConsoleBorder, RoundedCornerShape(14.dp))
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("+100 KANAN (R)", style = MaterialTheme.typography.labelSmall, color = CueAmber)
            Text("TENGAH (C)", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Text("-100 KIRI (L)", style = MaterialTheme.typography.labelSmall, color = ClickCyan)
        }

        Spacer(Modifier.height(6.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(canvasHeightDp.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(StudioBg)
                .onSizeChanged { canvasSize = it }
                .pointerInput(points, song.totalBars, song.beatsPerBar) {
                    detectTapGestures { tapOffset ->
                        val w = canvasSize.width.toFloat().coerceAtLeast(1f)
                        val h = canvasSize.height.toFloat().coerceAtLeast(1f)
                        val hitRadiusPx = 28.dp.toPx()

                        val hit = points.firstOrNull { pt ->
                            val pos = PannerDsp.pointPosition(pt, song.beatsPerBar)
                            val x = ((pos - 1f) / song.totalBars.toFloat().coerceAtLeast(1f)) * w
                            val y = ((100f - pt.panValue) / 200f) * h
                            abs(tapOffset.x - x) <= hitRadiusPx && abs(tapOffset.y - y) <= hitRadiusPx
                        }
                        if (hit != null) {
                            onSelectPoint(hit.id)
                        } else {
                            val barFloat = 1f + (tapOffset.x / w) * song.totalBars.toFloat()
                            val bar = barFloat.toInt().coerceIn(1, song.totalBars)
                            val beat = (1f + ((barFloat - bar) * song.beatsPerBar)).coerceIn(1f, song.beatsPerBar.toFloat())
                            val pan = (100f - (tapOffset.y / h) * 200f).roundToInt().toFloat().coerceIn(-100f, 100f)
                            onTapAddPoint(bar, beat, pan)
                        }
                    }
                }
                .pointerInput(points, song.totalBars, song.beatsPerBar) {
                    detectDragGestures(
                        onDragStart = { startOffset ->
                            val w = canvasSize.width.toFloat().coerceAtLeast(1f)
                            val h = canvasSize.height.toFloat().coerceAtLeast(1f)
                            val hitRadiusPx = 32.dp.toPx()
                            val hit = points.firstOrNull { pt ->
                                val pos = PannerDsp.pointPosition(pt, song.beatsPerBar)
                                val x = ((pos - 1f) / song.totalBars.toFloat().coerceAtLeast(1f)) * w
                                val y = ((100f - pt.panValue) / 200f) * h
                                abs(startOffset.x - x) <= hitRadiusPx && abs(startOffset.y - y) <= hitRadiusPx
                            }
                            draggingPoint = hit
                            if (hit != null) onSelectPoint(hit.id)
                        },
                        onDrag = { change, _ ->
                            val pt = draggingPoint ?: return@detectDragGestures
                            change.consume()
                            val w = canvasSize.width.toFloat().coerceAtLeast(1f)
                            val h = canvasSize.height.toFloat().coerceAtLeast(1f)
                            val barFloat = 1f + (change.position.x / w).coerceIn(0f, 1f) * song.totalBars.toFloat()
                            val newBar = barFloat.toInt().coerceIn(1, song.totalBars)
                            val newBeat = (1f + ((barFloat - newBar) * song.beatsPerBar)).coerceIn(1f, song.beatsPerBar.toFloat())
                            val newPan = (100f - (change.position.y / h).coerceIn(0f, 1f) * 200f)
                                .roundToInt().toFloat().coerceIn(-100f, 100f)
                            val updated = pt.copy(bar = newBar, beat = newBeat, panValue = newPan)
                            draggingPoint = updated
                            onDragPoint(pt, newBar, newBeat, newPan)
                        },
                        onDragEnd = { draggingPoint = null },
                        onDragCancel = { draggingPoint = null }
                    )
                }
                .testTag("automation_timeline_canvas")
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                drawLine(
                    color = ConsoleBorder,
                    start = Offset(0f, h / 2f),
                    end = Offset(w, h / 2f),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                )

                for (bar in 1..song.totalBars) {
                    val x = ((bar - 1).toFloat() / song.totalBars.toFloat().coerceAtLeast(1f)) * w
                    val isSectionStart = song.sections.any { it.startBar == bar }
                    drawLine(
                        color = if (isSectionStart) BackingIndigo.copy(alpha = 0.45f) else ConsoleBorder.copy(alpha = 0.3f),
                        start = Offset(x, 0f),
                        end = Offset(x, h),
                        strokeWidth = if (isSectionStart) 1.5.dp.toPx() else 1.dp.toPx()
                    )
                }

                if (points.isNotEmpty()) {
                    val path = Path()
                    val coords = points.map { pt ->
                        val pos = PannerDsp.pointPosition(pt, song.beatsPerBar)
                        val x = ((pos - 1f) / song.totalBars.toFloat().coerceAtLeast(1f)) * w
                        val y = ((100f - pt.panValue) / 200f) * h
                        pt to Offset(x, y)
                    }

                    path.moveTo(0f, coords.first().second.y)
                    path.lineTo(coords.first().second.x, coords.first().second.y)

                    for (i in 0 until coords.size - 1) {
                        val (ptA, pA) = coords[i]
                        val (_, pB) = coords[i + 1]
                        if (ptA.curveType == AutomationCurveType.STEP) {
                            path.lineTo(pB.x, pA.y)
                            path.lineTo(pB.x, pB.y)
                        } else {
                            path.lineTo(pB.x, pB.y)
                        }
                    }

                    path.lineTo(w, coords.last().second.y)

                    drawPath(
                        path = path,
                        color = accentColor,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )

                    coords.forEach { (pt, center) ->
                        val isSelected = pt.id == selectedPointId
                        drawCircle(
                            color = StudioBg,
                            radius = if (isSelected) 10.dp.toPx() else 7.dp.toPx(),
                            center = center
                        )
                        drawCircle(
                            color = if (isSelected) TextPrimary else accentColor,
                            radius = if (isSelected) 9.dp.toPx() else 6.dp.toPx(),
                            center = center,
                            style = Stroke(width = if (isSelected) 3.dp.toPx() else 2.5.dp.toPx())
                        )
                        drawCircle(
                            color = accentColor,
                            radius = 3.5.dp.toPx(),
                            center = center
                        )
                    }
                }

                val playheadBarPos = (transport.currentBar - 1).toFloat() + transport.barProgressFraction
                val playheadX = (playheadBarPos / song.totalBars.toFloat().coerceAtLeast(1f)) * w
                drawLine(
                    color = ClickCyan,
                    start = Offset(playheadX, 0f),
                    end = Offset(playheadX, h),
                    strokeWidth = 2.dp.toPx()
                )
            }
        }
    }
}
