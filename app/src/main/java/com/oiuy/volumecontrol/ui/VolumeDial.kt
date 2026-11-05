package com.oiuy.volumecontrol.ui

import android.os.SystemClock

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Large tactile dial. Stationary bezel ticks stay fixed; only the knob and
 * indicator rotate. Indicator angle follows fine volume so twelve o'clock is
 * every 10% landmark (0, 10, 20, …, 100). One full turn still changes volume
 * by ±10% of max. Rotation is hard-stopped at 0% and 100%.
 *
 * Near each whole percent (thicker bezel mark), when drag speed is below
 * [DialMath.SNAP_MAX_SPEED_DEG_PER_MS], the value may snap into a ±0.2%
 * detent. Fast spins stay continuous. Major haptics fire on entering a snap
 * or crossing a major tick; fine haptics on other tick crossings.
 */
@Composable
fun VolumeDial(
    percent: Float,
    /**
     * Apply [degrees] of rotation. Second arg is true when the gesture is slow
     * enough that the caller may snap to a whole percent.
     */
    onRotateDegrees: (degrees: Float, allowSnap: Boolean) -> Float,
    onTickCrossed: () -> Unit = {},
    onDragStart: () -> Unit = {},
    onDragEnd: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val context = LocalContext.current
    val tickHaptics = remember(context) { DialTickHaptics(context) }
    var lastAngle by remember { mutableFloatStateOf(Float.NaN) }
    var dragPercent by remember { mutableFloatStateOf(percent) }
    var lastTick by remember { mutableIntStateOf(DialMath.tickIndexFromPercent(percent)) }
    var wasAtMajor by remember { mutableStateOf(false) }
    var lastDragUptimeMs by remember { mutableStateOf(0L) }

    val latestPercent = rememberUpdatedState(percent)
    val latestOnRotate = rememberUpdatedState(onRotateDegrees)
    val latestOnTick = rememberUpdatedState(onTickCrossed)
    val latestOnDragStart = rememberUpdatedState(onDragStart)
    val latestOnDragEnd = rememberUpdatedState(onDragEnd)

    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val dialColor = MaterialTheme.colorScheme.primary
    val indicatorColor = MaterialTheme.colorScheme.onPrimary
    val ringColor = MaterialTheme.colorScheme.secondary
    val bezelMinor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.40f)
    val bezelMajor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
    val bezelNoon = MaterialTheme.colorScheme.secondary

    val displayPercent = formatFinePercent(percent)
    val indicatorDegrees = DialMath.indicatorDegreesFromPercent(percent)

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
                            dragPercent = latestPercent.value
                            lastTick = DialMath.tickIndexFromPercent(dragPercent)
                            // Treat an already-snapped whole percent as "at major".
                            wasAtMajor = DialMath.snapToWholePercentIfNear(dragPercent) ==
                                dragPercent && DialMath.isInWholePercentSnapZone(dragPercent)
                            lastDragUptimeMs = SystemClock.uptimeMillis()
                            latestOnDragStart.value()
                            val center = Offset(size.width / 2f, size.height / 2f)
                            lastAngle = DialMath.angleDegrees(
                                offset.x - center.x,
                                offset.y - center.y,
                            )
                        },
                        onDragEnd = {
                            lastAngle = Float.NaN
                            latestOnDragEnd.value()
                        },
                        onDragCancel = {
                            lastAngle = Float.NaN
                            latestOnDragEnd.value()
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val angle = DialMath.angleDegrees(
                                change.position.x - center.x,
                                change.position.y - center.y,
                            )
                            if (!lastAngle.isNaN()) {
                                val rawDelta = DialMath.shortestAngleDelta(lastAngle, angle)
                                val nowMs = SystemClock.uptimeMillis()
                                val dtMs = (nowMs - lastDragUptimeMs).coerceAtLeast(1L)
                                lastDragUptimeMs = nowMs
                                // Angular speed from this frame's raw delta (deg/ms).
                                val speedDegPerMs = abs(rawDelta) / dtMs.toFloat()
                                val allowSnap = DialMath.isSnapSpeed(speedDegPerMs)
                                // Hard-stop: ignore rotation that would pass 0% or 100%.
                                val applied = DialMath.clampRotationDegrees(rawDelta, dragPercent)
                                if (applied != 0f) {
                                    val newPercent = latestOnRotate.value(applied, allowSnap)
                                    dragPercent = newPercent
                                    val tick = DialMath.tickIndexFromPercent(newPercent)
                                    val crossed = DialMath.ticksCrossed(lastTick, tick)
                                    // "At major" only when we actually snapped onto a whole percent.
                                    val nowAtMajor = allowSnap &&
                                        DialMath.isInWholePercentSnapZone(newPercent) &&
                                        newPercent == DialMath.nearestWholePercent(newPercent)
                                    val enteredMajor = nowAtMajor && !wasAtMajor
                                    val includesMajor =
                                        crossed != 0 && DialMath.crossedIncludesMajor(lastTick, tick)
                                    // One haptic per frame: major if we entered a detent or
                                    // crossed a major tick; otherwise fine on tick change.
                                    if (enteredMajor || includesMajor) {
                                        tickHaptics.onTickStep(
                                            view,
                                            ticksCrossed = if (crossed != 0) crossed else 1,
                                            major = true,
                                        )
                                        latestOnTick.value()
                                    } else if (crossed != 0) {
                                        tickHaptics.onTickStep(view, crossed, major = false)
                                        latestOnTick.value()
                                    }
                                    if (crossed != 0) {
                                        lastTick = tick
                                    }
                                    wasAtMajor = nowAtMajor
                                }
                                // Blocked movement past a limit does not fire haptics.
                            }
                            lastAngle = angle
                        },
                    )
                },
        ) {
            val radius = min(size.width, size.height) / 2f
            val stroke = radius * 0.08f

            // Outer ring / track
            drawCircle(
                color = trackColor,
                radius = radius * 0.98f,
            )
            drawCircle(
                color = ringColor.copy(alpha = 0.35f),
                radius = radius * 0.98f,
                style = Stroke(width = stroke * 0.5f),
            )

            // Stationary bezel ticks (do not rotate with the indicator).
            val bezelOuter = radius * 0.96f
            val minorInner = radius * 0.88f
            val majorInner = radius * 0.84f
            val noonInner = radius * 0.80f
            for (i in 0 until DialMath.TICKS_PER_TURN) {
                val major = i % DialMath.MAJOR_TICK_EVERY == 0
                val noon = i == 0
                val rad = Math.toRadians((i * DialMath.DEGREES_PER_TICK - 90f).toDouble())
                val cx = cos(rad).toFloat()
                val cy = sin(rad).toFloat()
                val inner = when {
                    noon -> noonInner
                    major -> majorInner
                    else -> minorInner
                }
                val color = when {
                    noon -> bezelNoon
                    major -> bezelMajor
                    else -> bezelMinor
                }
                val width = when {
                    noon -> stroke * 0.42f
                    major -> stroke * 0.28f
                    else -> stroke * 0.14f
                }
                drawLine(
                    color = color,
                    start = Offset(size.center.x + cx * inner, size.center.y + cy * inner),
                    end = Offset(size.center.x + cx * bezelOuter, size.center.y + cy * bezelOuter),
                    strokeWidth = width,
                    cap = StrokeCap.Round,
                )
            }

            // Rotating knob + indicator only
            rotate(degrees = indicatorDegrees, pivot = size.center) {
                drawCircle(
                    color = dialColor,
                    radius = radius * 0.72f,
                )
                val tickStart = Offset(size.center.x, size.center.y - radius * 0.56f)
                val tickEnd = Offset(size.center.x, size.center.y - radius * 0.42f)
                drawLine(
                    color = indicatorColor,
                    start = tickStart,
                    end = tickEnd,
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
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
