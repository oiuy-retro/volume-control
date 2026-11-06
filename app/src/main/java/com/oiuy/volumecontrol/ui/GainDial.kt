package com.oiuy.volumecontrol.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import com.oiuy.volumecontrol.effect.GainMath
import com.oiuy.volumecontrol.volume.DialMath
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Experimental attenuation dial. Same rotary look as the volume dial, but it
 * only changes a requested dB value for the global output effect. It never
 * touches Android media volume. Inactive (ignores touches) when [enabled] is false.
 */
@Composable
fun GainDial(
    requestedDb: Float,
    floorDb: Float,
    enabled: Boolean,
    /** Called with the new requested dB (already clamped and on the 0.1 dB grid). */
    onRequestDb: (Float) -> Unit,
    onDragEnd: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val context = LocalContext.current
    val haptics = remember(context) { DialTickHaptics(context) }
    var lastAngle by remember { mutableFloatStateOf(Float.NaN) }
    var dragDb by remember { mutableFloatStateOf(requestedDb) }

    val latestDb = rememberUpdatedState(requestedDb)
    val latestFloor = rememberUpdatedState(floorDb)
    val latestEnabled = rememberUpdatedState(enabled)
    val latestOnRequest = rememberUpdatedState(onRequestDb)
    val latestOnDragEnd = rememberUpdatedState(onDragEnd)

    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val dialColor = MaterialTheme.colorScheme.tertiary
    val indicatorColor = MaterialTheme.colorScheme.onTertiary
    val ringColor = MaterialTheme.colorScheme.tertiary
    val bezelMinor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.40f)
    val bezelMajor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .padding(24.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .semantics {
                contentDescription = if (enabled) {
                    "Experimental attenuation dial, requested ${GainMath.formatDb(requestedDb)}"
                } else {
                    "Experimental attenuation dial, effect off"
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            dragDb = latestDb.value
                            val c = Offset(size.width / 2f, size.height / 2f)
                            lastAngle = DialMath.angleDegrees(offset.x - c.x, offset.y - c.y)
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
                            val c = Offset(size.width / 2f, size.height / 2f)
                            val angle = DialMath.angleDegrees(
                                change.position.x - c.x,
                                change.position.y - c.y,
                            )
                            if (latestEnabled.value && !lastAngle.isNaN()) {
                                val raw = DialMath.shortestAngleDelta(lastAngle, angle)
                                val applied = GainMath.clampRotationDegrees(raw, dragDb, latestFloor.value)
                                if (applied != 0f) {
                                    val before = dragDb
                                    dragDb = (dragDb + GainMath.dbDeltaFromDegrees(applied))
                                        .coerceIn(minOf(0f, latestFloor.value), 0f)
                                    val q = GainMath.clampAttenuation(dragDb, latestFloor.value)
                                    if (q != latestDb.value) latestOnRequest.value(q)
                                    val crossed = GainMath.detentsCrossed(before, dragDb)
                                    if (crossed != 0) haptics.onTickStep(view, crossed, major = true)
                                }
                            }
                            lastAngle = angle
                        },
                    )
                },
        ) {
            val radius = min(size.width, size.height) / 2f
            val stroke = radius * 0.08f
            drawCircle(color = trackColor, radius = radius * 0.98f)

            // Stationary bezel: a mark every 0.5 dB, longer at each whole dB.
            val marks = (GainMath.DB_PER_FULL_TURN * 2).toInt()
            for (i in 0 until marks) {
                val major = i % 2 == 0
                val rad = Math.toRadians((i * 360f / marks - 90f).toDouble())
                val cx = cos(rad).toFloat()
                val cy = sin(rad).toFloat()
                val inner = if (major) radius * 0.84f else radius * 0.89f
                drawLine(
                    color = if (major) bezelMajor else bezelMinor,
                    start = Offset(size.center.x + cx * inner, size.center.y + cy * inner),
                    end = Offset(size.center.x + cx * radius * 0.96f, size.center.y + cy * radius * 0.96f),
                    strokeWidth = if (major) stroke * 0.28f else stroke * 0.14f,
                    cap = StrokeCap.Round,
                )
            }

            rotate(degrees = GainMath.knobDegrees(requestedDb), pivot = size.center) {
                drawCircle(color = dialColor, radius = radius * 0.72f)
                drawLine(
                    color = indicatorColor,
                    start = Offset(size.center.x, size.center.y - radius * 0.66f),
                    end = Offset(size.center.x, size.center.y - radius * 0.56f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }

            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = GainMath.ringSweepDegrees(requestedDb, floorDb),
                useCenter = false,
                style = Stroke(width = stroke * 0.35f, cap = StrokeCap.Round),
                topLeft = Offset(size.center.x - radius * 0.92f, size.center.y - radius * 0.92f),
                size = androidx.compose.ui.geometry.Size(radius * 1.84f, radius * 1.84f),
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (enabled) GainMath.formatDb(requestedDb) else "Off",
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onTertiary,
            )
            Text(
                text = if (enabled) "requested attenuation" else "effect not active",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onTertiary,
            )
        }
    }
}
