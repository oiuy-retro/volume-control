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
import com.oiuy.volumecontrol.volume.TickDial
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Large tactile dial where every bezel mark is one output unit.
 *
 * The bezel shows [unitsPerTurn] stationary marks; one full turn moves
 * [unitsPerTurn] units, so each mark the indicator passes is one volume
 * change (Samsung fine mode: 50 per turn, 3 turns for 150). Twelve o'clock is
 * the landmark for every full turn. Rotation is hard-stopped at 0 and
 * [maxUnits].
 *
 * Finger deltas always update a continuous **raw** level first. A tick magnet
 * ([TickDial.resolveMagnet]) may overlay the drawn level onto a tick; it never
 * rebuilds the raw level. Each tick crossed gives one light haptic; every full
 * turn gives a strong one.
 */
@Composable
fun VolumeDial(
    /** Drawn level in units (raw, or magnet-snapped overlay). */
    level: Float,
    maxUnits: Int,
    unitsPerTurn: Int,
    /** Apply rotation to the raw level; must return the new **raw** level. */
    onRotateDegrees: (Float) -> Float,
    /** Publish the level to draw (raw or magnet-snapped). */
    onReportedLevel: (Float) -> Unit = {},
    /** Current raw level (not the possibly-snapped drawn level). */
    currentRawLevel: () -> Float = { level },
    onTickCrossed: () -> Unit = {},
    onDragStart: () -> Unit = {},
    onDragEnd: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val context = LocalContext.current
    val tickHaptics = remember(context) { DialTickHaptics(context) }
    var lastAngle by remember { mutableFloatStateOf(Float.NaN) }
    var rawDragLevel by remember { mutableFloatStateOf(level) }
    var lastTick by remember { mutableIntStateOf(TickDial.tickIndex(level, maxUnits)) }
    var wasAtMajor by remember { mutableStateOf(false) }
    var lastSnapped by remember { mutableFloatStateOf(Float.NaN) }
    var magnetHolding by remember { mutableStateOf(false) }

    val latestMax = rememberUpdatedState(maxUnits)
    val latestPerTurn = rememberUpdatedState(unitsPerTurn)
    val latestOnRotate = rememberUpdatedState(onRotateDegrees)
    val latestOnReported = rememberUpdatedState(onReportedLevel)
    val latestRawLevel = rememberUpdatedState(currentRawLevel)
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

    // The readout follows the unit actually written, so it changes once per tick.
    val currentTick = TickDial.tickIndex(level, maxUnits)
    val displayPercent = formatFinePercent(DialMath.fractionalPercentOf(currentTick.toFloat(), maxUnits))
    val indicatorDegrees = TickDial.indicatorDegrees(level, unitsPerTurn, maxUnits)
    val progressFraction = if (maxUnits > 0) TickDial.clampLevel(level, maxUnits) / maxUnits else 0f

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
                            // Always seed from the raw level, never a snapped drawn value.
                            rawDragLevel = latestRawLevel.value()
                            lastTick = TickDial.tickIndex(rawDragLevel, latestMax.value)
                            wasAtMajor = TickDial.isMajorTick(lastTick, latestPerTurn.value, latestMax.value)
                            lastSnapped = Float.NaN
                            magnetHolding = false
                            latestOnDragStart.value()
                            val center = Offset(size.width / 2f, size.height / 2f)
                            lastAngle = DialMath.angleDegrees(offset.x - center.x, offset.y - center.y)
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
                            val pos = change.position
                            val angle = DialMath.angleDegrees(pos.x - center.x, pos.y - center.y)
                            if (!lastAngle.isNaN()) {
                                val max = latestMax.value
                                val perTurn = latestPerTurn.value
                                val rawDelta = DialMath.shortestAngleDelta(lastAngle, angle)
                                // Clamp against the raw level (hard stops at 0 and max).
                                val applied = TickDial.clampRotationDegrees(rawDelta, rawDragLevel, perTurn, max)
                                if (applied != 0f) {
                                    val previousRaw = rawDragLevel
                                    val raw = latestOnRotate.value(applied)
                                    rawDragLevel = raw
                                    val magnet = TickDial.resolveMagnet(
                                        previousRaw = previousRaw,
                                        raw = raw,
                                        lastSnapped = lastSnapped,
                                        holding = magnetHolding,
                                        maxUnits = max,
                                    )
                                    lastSnapped = magnet.lastSnapped
                                    magnetHolding = magnet.holding
                                    latestOnReported.value(magnet.reportedLevel)

                                    val tick = TickDial.tickIndex(raw, max)
                                    val crossed = DialMath.ticksCrossed(lastTick, tick)
                                    val nowAtMajor = TickDial.isMajorTick(tick, perTurn, max)
                                    if (crossed != 0) {
                                        val major = (nowAtMajor && !wasAtMajor) ||
                                            TickDial.crossedIncludesMajor(lastTick, tick, perTurn, max)
                                        tickHaptics.onTickStep(view, crossed, major = major)
                                        latestOnTick.value()
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

            drawCircle(color = trackColor, radius = radius * 0.98f)
            drawCircle(
                color = ringColor.copy(alpha = 0.35f),
                radius = radius * 0.98f,
                style = Stroke(width = stroke * 0.5f),
            )

            // Stationary bezel: one mark per output unit in a turn.
            val marks = unitsPerTurn.coerceAtLeast(1)
            val markDegrees = TickDial.degreesPerTick(marks)
            val bezelOuter = radius * 0.96f
            val minorInner = radius * 0.88f
            val majorInner = radius * 0.84f
            val noonInner = radius * 0.80f
            for (i in 0 until marks) {
                val noon = i == 0
                // Longer reference mark every 5 units when a turn has more than 5.
                val major = marks > BEZEL_GROUP && i % BEZEL_GROUP == 0
                val rad = Math.toRadians((i * markDegrees - 90f).toDouble())
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
                    else -> stroke * 0.18f
                }
                drawLine(
                    color = color,
                    start = Offset(size.center.x + cx * inner, size.center.y + cy * inner),
                    end = Offset(size.center.x + cx * bezelOuter, size.center.y + cy * bezelOuter),
                    strokeWidth = width,
                    cap = StrokeCap.Round,
                )
            }

            rotate(degrees = indicatorDegrees, pivot = size.center) {
                drawCircle(color = dialColor, radius = radius * 0.72f)
                drawLine(
                    color = indicatorColor,
                    start = Offset(size.center.x, size.center.y - radius * 0.56f),
                    end = Offset(size.center.x, size.center.y - radius * 0.42f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
                drawCircle(color = indicatorColor.copy(alpha = 0.2f), radius = radius * 0.18f)
            }

            // Progress arc for the whole range (all turns).
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = progressFraction * 360f,
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

private const val BEZEL_GROUP = 5

/** One decimal place. */
internal fun formatFinePercent(percent: Float): String {
    val clamped = percent.coerceIn(0f, 100f)
    val tenths = (clamped * 10f).roundToInt()
    val whole = tenths / 10
    val frac = tenths % 10
    return "$whole.$frac"
}
