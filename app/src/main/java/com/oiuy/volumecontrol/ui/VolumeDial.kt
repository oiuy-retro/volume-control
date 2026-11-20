package com.oiuy.volumecontrol.ui

import android.os.Build
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
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oiuy.volumecontrol.volume.DialMath
import com.oiuy.volumecontrol.volume.TickDial
import kotlin.math.abs
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
 * Look: dark and flat (Windows 10 Calculator/Calendar style). Only the major
 * marks and the knob pointer give off light, in the system accent color; the
 * minor marks are lit by their distance to them ([DialGlow]), so the pointer's
 * light sweeps across the bezel as the knob turns. The center readout glows too.
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

    val glow = LocalGlowColor.current
    val density = LocalDensity.current
    val bloomRadiusPx = with(density) { 9.dp.toPx() }
    val readoutGlowPx = with(density) { 22.dp.toPx() }
    val hairlinePx = with(density) { 1.dp.toPx() }
    val bloomMarginPx = with(density) { 24.dp.roundToPx() }

    // The readout follows the unit actually written, so it changes once per tick.
    val currentTick = TickDial.tickIndex(level, maxUnits)
    val displayPercent = formatFinePercent(DialMath.fractionalPercentOf(currentTick.toFloat(), maxUnits))
    // Drawing reads these through State so the draw cache is not rebuilt each frame.
    val indicatorState = rememberUpdatedState(TickDial.indicatorDegrees(level, unitsPerTurn, maxUnits))
    val progressState = rememberUpdatedState(
        if (maxUnits > 0) TickDial.clampLevel(level, maxUnits) / maxUnits else 0f,
    )
    // API 31+: the light sources get a real blur layer. Older versions draw stacked halo strokes.
    val useBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .padding(24.dp)
            .semantics { contentDescription = "Volume dial, $displayPercent percent" },
        contentAlignment = Alignment.Center,
    ) {
        // Dark base: bezel line, minor marks, progress, knob face. Takes the touch input.
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
                    // Reveal-style lit border: bright at the pointer, fading over the pointer's reach.
                    val revealBrush = Brush.sweepGradient(
                        colorStops = revealStops(FluentDark.Border, glow),
                        center = c,
                    )
                    val readoutLight = Brush.radialGradient(
                        colors = listOf(glow.copy(alpha = 0.16f), glow.copy(alpha = 0.05f), Color.Transparent),
                        center = c,
                        radius = geo.radius * 0.5f,
                    )
                    val hairline = Stroke(width = hairlinePx)
                    val arcStroke = Stroke(width = hairlinePx * 2f, cap = StrokeCap.Round)
                    val arcTopLeft = Offset(c.x - geo.arcRadius, c.y - geo.arcRadius)
                    val arcSize = Size(geo.arcRadius * 2f, geo.arcRadius * 2f)
                    val minorLit = lerp(glow, Color.White, 0.25f)
                    val progressColor = glow.copy(alpha = 0.55f)
                    onDrawBehind {
                        val pointer = indicatorState.value

                        // Outer bezel line and knob edge, lit where the pointer is.
                        rotate(degrees = pointer, pivot = c) {
                            drawCircle(brush = revealBrush, radius = geo.radius * 0.985f, center = c, style = hairline)
                        }

                        // Stationary marks. Minor marks are dark unless a light is near.
                        for (i in 0 until geo.marks) {
                            if (geo.major[i]) continue
                            val b = DialGlow.minorBrightness(
                                geo.staticLight[i],
                                DialGlow.pointerLight(geo.markDegrees[i], pointer),
                            )
                            drawLine(
                                color = lerp(FluentDark.MarkUnlit, minorLit, b),
                                start = geo.inner(i, c),
                                end = geo.outer(i, c),
                                strokeWidth = geo.minorWidth,
                                cap = StrokeCap.Round,
                            )
                        }

                        // Progress over the whole range: a thin, unlit track with a dim accent line.
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

                        // Knob: flat dark face with a hairline edge lit by the pointer.
                        drawCircle(color = FluentDark.Knob, radius = geo.knobRadius, center = c)
                        drawCircle(brush = readoutLight, radius = geo.radius * 0.5f, center = c)
                        rotate(degrees = pointer, pivot = c) {
                            drawCircle(brush = revealBrush, radius = geo.knobRadius, center = c, style = hairline)
                        }
                    }
                },
        )
        if (useBlur) {
            // Bloom: only the light sources, blurred, over the dark base so the knob
            // face does not hide the pointer's glow.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    // Grow the layer into the dial's padding so the blur is not cut off at the edge.
                    .layout { measurable, constraints ->
                        val w = constraints.maxWidth + 2 * bloomMarginPx
                        val h = constraints.maxHeight + 2 * bloomMarginPx
                        val placeable = measurable.measure(Constraints.fixed(w, h))
                        layout(constraints.maxWidth, constraints.maxHeight) {
                            placeable.place(-bloomMarginPx, -bloomMarginPx)
                        }
                    }
                    .graphicsLayer {
                        renderEffect = BlurEffect(bloomRadiusPx, bloomRadiusPx, TileMode.Decal)
                    }
                    .drawWithCache {
                        val geo = DialGeometry(size, unitsPerTurn, inset = bloomMarginPx.toFloat())
                        onDrawBehind {
                            drawLightSources(geo, glow, indicatorState.value, bloom = true, haloFallback = false)
                        }
                    },
            )
        }
        // Sharp cores of the light sources on top.
        Box(
            modifier = Modifier
                .matchParentSize()
                .drawWithCache {
                    val geo = DialGeometry(size, unitsPerTurn)
                    onDrawBehind {
                        drawLightSources(geo, glow, indicatorState.value, bloom = false, haloFallback = !useBlur)
                    }
                },
        )

        Text(
            text = "$displayPercent%",
            style = MaterialTheme.typography.displayLarge.copy(
                fontWeight = FontWeight.Light,
                fontSize = 56.sp,
                letterSpacing = (-1).sp,
                shadow = Shadow(color = glow.copy(alpha = 0.85f), offset = Offset.Zero, blurRadius = readoutGlowPx),
            ),
            color = lerp(glow, Color.White, 0.6f),
        )
    }
}

/**
 * Bezel geometry for one size and mark count, computed once per draw cache
 * (not per frame). Fractions are of the dial radius.
 */
private class DialGeometry(size: Size, unitsPerTurn: Int, inset: Float = 0f) {
    val radius = (min(size.width, size.height) - 2f * inset).coerceAtLeast(0f) / 2f
    val stroke = radius * 0.08f
    val marks = unitsPerTurn.coerceAtLeast(1)
    val knobRadius = radius * 0.72f
    val arcRadius = radius * 0.765f
    val minorWidth = stroke * 0.16f
    val majorWidth = stroke * 0.26f
    val noonWidth = stroke * 0.36f
    val pointerWidth = stroke * 0.36f
    val pointerInner = radius * 0.50f
    val pointerOuter = radius * 0.64f
    private val outerR = radius * 0.95f
    val markDegrees = FloatArray(marks)
    val major = BooleanArray(marks)
    val staticLight = FloatArray(marks)
    private val cosA = FloatArray(marks)
    private val sinA = FloatArray(marks)
    private val innerR = FloatArray(marks)

    init {
        val step = TickDial.degreesPerTick(marks)
        DialGlow.fillStaticLight(marks, staticLight)
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

/** Sweep-gradient stops centred on twelve o'clock (0.75 of a sweep), following [DialGlow.falloff]. */
private fun revealStops(dim: Color, light: Color): Array<Pair<Float, Color>> {
    val steps = 6
    val reach = DialGlow.POINTER_REACH_DEGREES
    val stops = ArrayList<Pair<Float, Color>>()
    stops += 0f to dim
    for (k in -steps..steps) {
        val d = k * reach / steps
        val f = DialGlow.falloff(abs(d), reach)
        stops += (0.75f + d / 360f) to lerp(dim, light.copy(alpha = 0.9f), f)
    }
    stops += 1f to dim
    return stops.toTypedArray()
}

/**
 * The light sources: the major marks and the knob pointer. With [bloom] this
 * draws wide soft strokes for the blurred layer; otherwise it draws the crisp
 * cores, plus stacked halo strokes when [haloFallback] (no blur available).
 */
private fun DrawScope.drawLightSources(
    geo: DialGeometry,
    glow: Color,
    pointerDegrees: Float,
    bloom: Boolean,
    haloFallback: Boolean,
) {
    val c = size.center
    for (i in 0 until geo.marks) {
        if (!geo.major[i]) continue
        val pointerLight = DialGlow.pointerLight(geo.markDegrees[i], pointerDegrees)
        val b = DialGlow.majorBrightness(pointerLight)
        glowLine(
            glow = glow,
            brightness = b,
            whiteness = 0.15f + 0.35f * pointerLight,
            start = geo.inner(i, c),
            end = geo.outer(i, c),
            width = geo.width(i),
            bloom = bloom,
            haloFallback = haloFallback,
        )
    }
    rotate(degrees = pointerDegrees, pivot = c) {
        glowLine(
            glow = glow,
            brightness = 1f,
            whiteness = 0.45f,
            start = Offset(c.x, c.y - geo.pointerOuter),
            end = Offset(c.x, c.y - geo.pointerInner),
            width = geo.pointerWidth,
            bloom = bloom,
            haloFallback = haloFallback,
        )
    }
}

private fun DrawScope.glowLine(
    glow: Color,
    brightness: Float,
    whiteness: Float,
    start: Offset,
    end: Offset,
    width: Float,
    bloom: Boolean,
    haloFallback: Boolean,
) {
    if (bloom) {
        drawLine(glow.copy(alpha = brightness), start, end, strokeWidth = width * 2.6f, cap = StrokeCap.Round)
        return
    }
    if (haloFallback) {
        drawLine(glow.copy(alpha = 0.06f * brightness), start, end, strokeWidth = width * 6f, cap = StrokeCap.Round)
        drawLine(glow.copy(alpha = 0.12f * brightness), start, end, strokeWidth = width * 3.6f, cap = StrokeCap.Round)
        drawLine(glow.copy(alpha = 0.24f * brightness), start, end, strokeWidth = width * 2.2f, cap = StrokeCap.Round)
    }
    drawLine(
        color = lerp(glow, Color.White, whiteness).copy(alpha = brightness),
        start = start,
        end = end,
        strokeWidth = width,
        cap = StrokeCap.Round,
    )
}

/** One decimal place. */
internal fun formatFinePercent(percent: Float): String {
    val clamped = percent.coerceIn(0f, 100f)
    val tenths = (clamped * 10f).roundToInt()
    val whole = tenths / 10
    val frac = tenths % 10
    return "$whole.$frac"
}
