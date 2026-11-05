package com.oiuy.volumecontrol.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oiuy.volumecontrol.volume.DialMath
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Large tactile dial. Visual rotation tracks the finger 1:1.
 * Accumulated rotation maps to STREAM_MUSIC: 360° → ±10% of max.
 *
 * The dial face carries [DialMath.TICKS_PER_TURN] indents (one every
 * [DialMath.DEGREES_PER_TICK] degrees). Each indent is one fine position
 * (0.1% of max). A light haptic fires when the integer tick index derived
 * from the unwrapped accumulated rotation changes, and the system volume
 * panel is shown on the same tick crossings.
 */
@Composable
fun VolumeDial(
    percent: Float,
    onRotateDegrees: (Float) -> Float,
    onTickCrossed: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val context = LocalContext.current
    val tickHaptics = remember(context) { DialTickHaptics(context) }
    // Accumulated, never-wrapped rotation in degrees; drives both drawing and tick indexing.
    var visualRotation by remember { mutableFloatStateOf(0f) }
    var lastAngle by remember { mutableFloatStateOf(Float.NaN) }
    var lastTick by remember { mutableIntStateOf(0) }

    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val dialColor = MaterialTheme.colorScheme.primary
    val indicatorColor = MaterialTheme.colorScheme.onPrimary
    val ringColor = MaterialTheme.colorScheme.secondary

    val displayPercent = formatFinePercent(percent)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .padding(24.dp)
            .semantics { contentDescription = "Volume dial, $displayPercent percent" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val center = Offset(size.width / 2f, size.height / 2f)
                            lastAngle = DialMath.angleDegrees(
                                offset.x - center.x,
                                offset.y - center.y,
                            )
                        },
                        onDragEnd = { lastAngle = Float.NaN },
                        onDragCancel = { lastAngle = Float.NaN },
                        onDrag = { change, _ ->
                            change.consume()
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val angle = DialMath.angleDegrees(
                                change.position.x - center.x,
                                change.position.y - center.y,
                            )
                            if (!lastAngle.isNaN()) {
                                val delta = DialMath.shortestAngleDelta(lastAngle, angle)
                                visualRotation += delta
                                onRotateDegrees(delta)
                                val tick = DialMath.tickIndex(visualRotation)
                                val crossed = DialMath.ticksCrossed(lastTick, tick)
                                if (crossed != 0) {
                                    lastTick = tick
                                    // At most one pulse / UI show per frame, however many ticks crossed.
                                    tickHaptics.onTickStep(view, crossed)
                                    onTickCrossed()
                                }
                            }
                            lastAngle = angle
                        },
                    )
                },
        ) {
            val radius = min(size.width, size.height) / 2f
            val stroke = radius * 0.08f

            // Outer ring
            drawCircle(
                color = trackColor,
                radius = radius * 0.98f,
            )
            drawCircle(
                color = ringColor.copy(alpha = 0.35f),
                radius = radius * 0.98f,
                style = Stroke(width = stroke * 0.5f),
            )

            // Rotating dial body
            rotate(degrees = visualRotation, pivot = size.center) {
                drawCircle(
                    color = dialColor,
                    radius = radius * 0.72f,
                )
                // 100 indents around the dial rim; every 10th is longer.
                val faceRadius = radius * 0.72f
                val outer = faceRadius * 0.95f
                val minorInner = faceRadius * 0.87f
                val majorInner = faceRadius * 0.80f
                val minorColor = indicatorColor.copy(alpha = 0.45f)
                val majorColor = indicatorColor.copy(alpha = 0.85f)
                for (i in 0 until DialMath.TICKS_PER_TURN) {
                    val major = i % 10 == 0
                    val rad = Math.toRadians((i * DialMath.DEGREES_PER_TICK - 90f).toDouble())
                    val cx = cos(rad).toFloat()
                    val cy = sin(rad).toFloat()
                    val inner = if (major) majorInner else minorInner
                    drawLine(
                        color = if (major) majorColor else minorColor,
                        start = Offset(size.center.x + cx * inner, size.center.y + cy * inner),
                        end = Offset(size.center.x + cx * outer, size.center.y + cy * outer),
                        strokeWidth = if (major) stroke * 0.28f else stroke * 0.16f,
                        cap = StrokeCap.Round,
                    )
                }
                // Indicator tick at top of dial face (12 o'clock relative to rotation)
                val tickStart = Offset(size.center.x, size.center.y - radius * 0.56f)
                val tickEnd = Offset(size.center.x, size.center.y - radius * 0.42f)
                drawLine(
                    color = indicatorColor,
                    start = tickStart,
                    end = tickEnd,
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
                // Inner hub
                drawCircle(
                    color = indicatorColor.copy(alpha = 0.2f),
                    radius = radius * 0.18f,
                )
            }

            // Progress arc for current fine percent (static reference)
            val sweep = (percent.coerceIn(0f, 100f) / 100f) * 360f
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = sweep,
                useCenter = false,
                style = Stroke(width = stroke * 0.35f, cap = StrokeCap.Round),
                topLeft = Offset(size.center.x - radius * 0.92f, size.center.y - radius * 0.92f),
                size = androidx.compose.ui.geometry.Size(radius * 1.84f, radius * 1.84f),
            )
        }

        Text(
            text = "$displayPercent%",
            style = MaterialTheme.typography.displayLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 56.sp,
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** One decimal place so each 0.1% tick is visible in the center readout. */
internal fun formatFinePercent(percent: Float): String {
    val clamped = percent.coerceIn(0f, 100f)
    val tenths = (clamped * 10f).roundToInt()
    val whole = tenths / 10
    val frac = tenths % 10
    return "$whole.$frac"
}
