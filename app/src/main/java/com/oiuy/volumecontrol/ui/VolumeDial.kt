package com.oiuy.volumecontrol.ui

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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
 * Look: dark and flat (Windows 10 Calculator/Calendar style). The major marks
 * and the knob pointer are solid accent lines. Minor marks, the bezel line and
 * the knob edge are dim and get a faint, wide Reveal highlight centred on the
 * pointer ([DialGlow]). No blur, halo or glow.
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

    val accent = LocalGlowColor.current
    val hairlinePx = with(LocalDensity.current) { 1.dp.toPx() }

    // The readout follows the unit actually written, so it changes once per tick.
    val currentTick = TickDial.tickIndex(level, maxUnits)
    val displayPercent = formatFinePercent(DialMath.fractionalPercentOf(currentTick.toFloat(), maxUnits))
    // Drawing reads these through State so the draw cache is not rebuilt each frame.
    val indicatorState = rememberUpdatedState(TickDial.indicatorDegrees(level, unitsPerTurn, maxUnits))
    val progressState = rememberUpdatedState(
        if (maxUnits > 0) TickDial.clampLevel(level, maxUnits) / maxUnits else 0f,
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .padding(24.dp)
            .semantics { contentDescription = "Volume dial, $displayPercent percent" },
        contentAlignment = Alignment.Center,
    ) {
        Box(
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
                                        unitsPerTurn = perTurn,
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
                }
                .drawWithCache {
                    val geo = DialGeometry(size, unitsPerTurn)
                    val c = size.center
                    // Reveal color: a neutral light gray with a touch of the accent.
                    val revealColor = lerp(FluentDark.RevealNeutral, accent, 0.3f)
                    // Reveal on the ring lines, centred on twelve o'clock and rotated to the pointer.
                    val ringReveal = Brush.sweepGradient(
                        colorStops = revealStops(FluentDark.Border, revealColor),
                        center = c,
                    )
                    val hairline = Stroke(width = hairlinePx)
                    val arcStroke = Stroke(width = hairlinePx * 2f, cap = StrokeCap.Round)
                    val arcTopLeft = Offset(c.x - geo.arcRadius, c.y - geo.arcRadius)
                    val arcSize = Size(geo.arcRadius * 2f, geo.arcRadius * 2f)
                    val progressColor = accent.copy(alpha = 0.6f)
                    onDrawBehind {
                        val pointer = indicatorState.value

                        // Outer bezel line and knob edge: hairlines with the reveal highlight.
                        rotate(degrees = pointer, pivot = c) {
                            drawCircle(brush = ringReveal, radius = geo.radius * 0.985f, center = c, style = hairline)
                        }

                        // Minor marks: dim gray, faintly brighter near the pointer.
                        for (i in 0 until geo.marks) {
                            if (geo.major[i]) continue
                            drawLine(
                                color = lerp(FluentDark.MarkDim, revealColor, DialGlow.reveal(geo.markDegrees[i], pointer)),
                                start = geo.inner(i, c),
                                end = geo.outer(i, c),
                                strokeWidth = geo.minorWidth,
                                cap = StrokeCap.Round,
                            )
                        }

                        // Major marks: solid accent lines.
                        for (i in 0 until geo.marks) {
                            if (!geo.major[i]) continue
                            drawLine(
                                color = accent,
                                start = geo.inner(i, c),
                                end = geo.outer(i, c),
                                strokeWidth = geo.width(i),
                                cap = StrokeCap.Round,
                            )
                        }

                        // Progress over the whole range: a thin track with a thin accent line.
                        drawCircle(color = FluentDark.Border, radius = geo.arcRadius, center = c, style = hairline)
                        drawArc(
                            color = progressColor,
                            startAngle = -90f,
                            sweepAngle = progressState.value * 360f,
                            useCenter = false,
                            style = arcStroke,
                            topLeft = arcTopLeft,
                            size = arcSize,
                        )

                        // Knob: flat dark face, hairline edge with the reveal highlight, solid accent pointer.
                        drawCircle(color = FluentDark.Knob, radius = geo.knobRadius, center = c)
                        rotate(degrees = pointer, pivot = c) {
                            drawCircle(brush = ringReveal, radius = geo.knobRadius, center = c, style = hairline)
                            drawLine(
                                color = accent,
                                start = Offset(c.x, c.y - geo.pointerOuter),
                                end = Offset(c.x, c.y - geo.pointerInner),
                                strokeWidth = geo.pointerWidth,
                                cap = StrokeCap.Round,
                            )
                        }
                    }
                },
        )

        Text(
            text = "$displayPercent%",
            style = MaterialTheme.typography.displayLarge.copy(
                fontWeight = FontWeight.Light,
                fontSize = 56.sp,
                letterSpacing = (-1).sp,
            ),
            color = FluentDark.Text,
        )
    }
}

/**
 * Bezel geometry for one size and mark count, computed once per draw cache
 * (not per frame). Fractions are of the dial radius.
 */
private class DialGeometry(size: Size, unitsPerTurn: Int) {
    val radius = min(size.width, size.height) / 2f
    private val stroke = radius * 0.08f
    val marks = unitsPerTurn.coerceAtLeast(1)
    val knobRadius = radius * 0.72f
    val arcRadius = radius * 0.765f
    val minorWidth = stroke * 0.16f
    private val majorWidth = stroke * 0.24f
    private val noonWidth = stroke * 0.32f
    val pointerWidth = stroke * 0.30f
    val pointerInner = radius * 0.50f
    val pointerOuter = radius * 0.64f
    private val outerR = radius * 0.95f
    val markDegrees = FloatArray(marks)
    val major = BooleanArray(marks)
    private val cosA = FloatArray(marks)
    private val sinA = FloatArray(marks)
    private val innerR = FloatArray(marks)

    init {
        val step = TickDial.degreesPerTick(marks)
        for (i in 0 until marks) {
            markDegrees[i] = i * step
            // Major mark every TickDial.MAJOR_MARK_SPACING_UNITS units: the same marks
            // that have the magnet and the strong haptic.
            major[i] = TickDial.isDrawnMajorMark(i, marks)
            val rad = Math.toRadians((i * step - 90f).toDouble())
            cosA[i] = cos(rad).toFloat()
            sinA[i] = sin(rad).toFloat()
            innerR[i] = when {
                i == 0 -> radius * 0.80f
                major[i] -> radius * 0.84f
                else -> radius * 0.885f
            }
        }
    }

    fun width(i: Int): Float = when {
        i == 0 -> noonWidth
        major[i] -> majorWidth
        else -> minorWidth
    }

    fun inner(i: Int, c: Offset) = Offset(c.x + cosA[i] * innerR[i], c.y + sinA[i] * innerR[i])
    fun outer(i: Int, c: Offset) = Offset(c.x + cosA[i] * outerR, c.y + sinA[i] * outerR)
}

/**
 * Sweep-gradient stops for the reveal on the ring lines, centred on twelve
 * o'clock (0.75 of a sweep) and following [DialGlow.reveal].
 */
private fun revealStops(dim: Color, light: Color): Array<Pair<Float, Color>> {
    val steps = 10
    val reach = DialGlow.REVEAL_REACH_DEGREES
    val stops = ArrayList<Pair<Float, Color>>()
    stops += 0f to dim
    for (k in -steps..steps) {
        val d = k * reach / steps
        stops += (0.75f + d / 360f) to lerp(dim, light, DialGlow.reveal(d, 0f))
    }
    stops += 1f to dim
    return stops.toTypedArray()
}

/** One decimal place. */
internal fun formatFinePercent(percent: Float): String {
    val clamped = percent.coerceIn(0f, 100f)
    val tenths = (clamped * 10f).roundToInt()
    val whole = tenths / 10
    val frac = tenths % 10
    return "$whole.$frac"
}
