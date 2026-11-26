package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.PannerDsp
import com.example.ui.theme.ClickCyan
import com.example.ui.theme.ClipRed
import com.example.ui.theme.ConsoleBorder
import com.example.ui.theme.ConsoleSurface
import com.example.ui.theme.CueAmber
import com.example.ui.theme.MeterGreen
import com.example.ui.theme.MeterYellow
import com.example.ui.theme.StripElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun StereoPanKnob(
    pan: Float,
    accentColor: Color,
    onResetCenter: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(46.dp)
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { onResetCenter() })
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 4.dp.toPx()
            val inset = strokeWidth / 2f + 2.dp.toPx()
            val arcSize = Size(size.width - inset * 2f, size.height - inset * 2f)
            val topLeft = Offset(inset, inset)

            // Background track arc (-225 deg to +45 deg => 270 deg sweep, center at -90 deg)
            drawArc(
                color = ConsoleBorder,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Active pan sweep from Center (-90 deg / 270 deg)
            val normalized = (pan.coerceIn(-100f, 100f) / 100f) // -1..+1
            val sweep = normalized * 135f
            val activeColor = when {
                pan < -5f -> ClickCyan
                pan > 5f -> CueAmber
                else -> accentColor
            }
            if (kotlin.math.abs(sweep) > 1f) {
                drawArc(
                    color = activeColor,
                    startAngle = 270f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }

            // Inner knob cap
            val center = Offset(size.width / 2f, size.height / 2f)
            val capRadius = (size.minDimension / 2f) - 8.dp.toPx()
            drawCircle(
                color = StripElevated,
                radius = capRadius,
                center = center
            )
            drawCircle(
                color = ConsoleBorder,
                radius = capRadius,
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )

            // Needle line
            val needleAngleRad = ((-90f + sweep) * PI / 180.0)
            val endX = center.x + (capRadius - 2.dp.toPx()) * cos(needleAngleRad).toFloat()
            val endY = center.y + (capRadius - 2.dp.toPx()) * sin(needleAngleRad).toFloat()
            drawLine(
                color = activeColor,
                start = center,
                end = Offset(endX, endY),
                strokeWidth = 2.5.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun StereoPanControlRow(
    pan: Float,
    accentColor: Color,
    testTagPrefix: String,
    onPanChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            PanQuickChip(
                label = "L",
                selected = pan <= -95f,
                activeColor = ClickCyan,
                testTag = "${testTagPrefix}_pan_left",
                onClick = { onPanChange(-100f) }
            )
            PanQuickChip(
                label = "C",
                selected = kotlin.math.abs(pan) < 3f,
                activeColor = MeterGreen,
                testTag = "${testTagPrefix}_pan_center",
                onClick = { onPanChange(0f) }
            )
            PanQuickChip(
                label = "R",
                selected = pan >= 95f,
                activeColor = CueAmber,
                testTag = "${testTagPrefix}_pan_right",
                onClick = { onPanChange(100f) }
            )
        }

        Slider(
            value = pan.coerceIn(-100f, 100f),
            onValueChange = { onPanChange(it.roundToInt().toFloat()) },
            valueRange = -100f..100f,
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .testTag("${testTagPrefix}_pan_slider"),
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor.copy(alpha = 0.7f),
                inactiveTrackColor = ConsoleBorder
            )
        )

        Text(
            text = PannerDsp.formatPanLabel(pan),
            style = MaterialTheme.typography.labelMedium,
            color = when {
                pan < -5f -> ClickCyan
                pan > 5f -> CueAmber
                else -> TextPrimary
            },
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun PanQuickChip(
    label: String,
    selected: Boolean,
    activeColor: Color,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) activeColor.copy(alpha = 0.22f) else ConsoleSurface)
            .border(
                width = 1.dp,
                color = if (selected) activeColor else ConsoleBorder,
                shape = RoundedCornerShape(6.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (selected) activeColor else TextSecondary
        )
    }
}

@Composable
fun StereoPeakMeter(
    leftPeak: Float,
    rightPeak: Float,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .width(16.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(4.dp))
            .background(StudioMeterBg)
            .padding(2.dp)
    ) {
        val segments = 16
        val gap = 2.dp.toPx()
        val segHeight = ((size.height - gap * (segments - 1)) / segments).coerceAtLeast(1f)
        val barWidth = (size.width - 2.dp.toPx()) / 2f

        for (i in 0 until segments) {
            val threshold = (i + 1).toFloat() / segments
            val y = size.height - ((i + 1) * segHeight + i * gap)
            val segColor = when {
                threshold > 0.88f -> ClipRed
                threshold > 0.68f -> MeterYellow
                else -> MeterGreen
            }

            val leftLit = leftPeak >= threshold * 0.85f
            val rightLit = rightPeak >= threshold * 0.85f

            drawRoundRect(
                color = if (leftLit) segColor else segColor.copy(alpha = 0.14f),
                topLeft = Offset(0f, y),
                size = Size(barWidth, segHeight),
                cornerRadius = CornerRadius(1.dp.toPx())
            )
            drawRoundRect(
                color = if (rightLit) segColor else segColor.copy(alpha = 0.14f),
                topLeft = Offset(barWidth + 2.dp.toPx(), y),
                size = Size(barWidth, segHeight),
                cornerRadius = CornerRadius(1.dp.toPx())
            )
        }
    }
}

private val StudioMeterBg = Color(0xFF0A0C10)

@Composable
fun VerticalDawFader(
    value: Float, // 0f..1.41f
    accentColor: Color,
    testTag: String,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var heightPx by remember { mutableFloatStateOf(1f) }
    val maxRange = 1.41f

    Box(
        modifier = modifier
            .width(42.dp)
            .fillMaxHeight()
            .onSizeChanged { heightPx = it.height.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val frac = (1f - (offset.y / heightPx)).coerceIn(0f, 1f)
                    onValueChange(frac * maxRange)
                }
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, _ ->
                    change.consume()
                    val frac = (1f - (change.position.y / heightPx)).coerceIn(0f, 1f)
                    onValueChange(frac * maxRange)
                }
            }
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val trackX = size.width / 2f
            val padY = 14.dp.toPx()
            val trackTop = padY
            val trackBottom = size.height - padY
            val trackLen = (trackBottom - trackTop).coerceAtLeast(1f)

            // dB tick marks
            val tickFractions = listOf(0f, 0.25f, 0.5f, 0.71f, 0.85f, 1f)
            tickFractions.forEach { tf ->
                val y = trackBottom - tf * trackLen
                drawLine(
                    color = ConsoleBorder,
                    start = Offset(4.dp.toPx(), y),
                    end = Offset(size.width - 4.dp.toPx(), y),
                    strokeWidth = 1.dp.toPx()
                )
            }

            // Center slot groove
            drawRoundRect(
                color = Color(0xFF090B0F),
                topLeft = Offset(trackX - 3.dp.toPx(), trackTop),
                size = Size(6.dp.toPx(), trackLen),
                cornerRadius = CornerRadius(3.dp.toPx())
            )

            // Active fill below thumb
            val norm = (value / maxRange).coerceIn(0f, 1f)
            val thumbY = trackBottom - norm * trackLen
            drawRoundRect(
                color = accentColor.copy(alpha = 0.75f),
                topLeft = Offset(trackX - 2.dp.toPx(), thumbY),
                size = Size(4.dp.toPx(), (trackBottom - thumbY).coerceAtLeast(0f)),
                cornerRadius = CornerRadius(2.dp.toPx())
            )

            // Machined fader cap
            val capW = 30.dp.toPx()
            val capH = 18.dp.toPx()
            val capTop = (thumbY - capH / 2f).coerceIn(0f, size.height - capH)
            drawRoundRect(
                color = StripElevated,
                topLeft = Offset(trackX - capW / 2f, capTop),
                size = Size(capW, capH),
                cornerRadius = CornerRadius(4.dp.toPx())
            )
            drawRoundRect(
                color = accentColor,
                topLeft = Offset(trackX - capW / 2f, capTop),
                size = Size(capW, capH),
                cornerRadius = CornerRadius(4.dp.toPx()),
                style = Stroke(width = 1.2.dp.toPx())
            )
            // Center groove line on fader cap
            drawLine(
                color = TextPrimary,
                start = Offset(trackX - capW / 2f + 4.dp.toPx(), capTop + capH / 2f),
                end = Offset(trackX + capW / 2f - 4.dp.toPx(), capTop + capH / 2f),
                strokeWidth = 1.5.dp.toPx()
            )
        }
    }
}

@Composable
fun ConsoleToggleButton(
    label: String,
    isActive: Boolean,
    activeColor: Color,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isActive) activeColor else ConsoleSurface)
            .border(
                width = 1.dp,
                color = if (isActive) activeColor else ConsoleBorder,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (isActive) Color(0xFF0A0D12) else TextMuted
        )
    }
}
