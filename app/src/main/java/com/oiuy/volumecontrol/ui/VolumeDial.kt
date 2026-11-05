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
import com.oiuy.volumecontrol.volume.DialMath
import com.oiuy.volumecontrol.volume.LevelChange
import com.oiuy.volumecontrol.volume.VolumeLevelText
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Large rotary dial for Android media volume.
 *
 * The stationary bezel has one detent mark per real `STREAM_MUSIC` level
 * (count read from the system, not hardcoded). The knob indicator, progress
 * arc, readout, and haptic pulse move only when the actual level changes.
 *
 * Finger rotation still accumulates continuously (hidden, 10% of the range per
 * full turn) so turning stays slow and deliberate, but that hidden target is
 * not displayed and does not cause haptics. Hard stops at level 0 and max.
 */
@Composable
fun VolumeDial(
    level: Int,
    maxLevel: Int,
    /** Hard-stop clamp of a rotation delta; returns the portion that may apply. */
    clampRotation: (Float) -> Float,
    /** Apply rotation; returns the actual level change read back from the system. */
    onRotateDegrees: (Float) -> LevelChange,
    onLevelChanged: (LevelChange) -> Unit = {},
    onDragStart: () -> Unit = {},
    onDragEnd: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val context = LocalContext.current
    val haptics = remember(context) { DialTickHaptics(context) }
    var lastAngle by remember { mutableFloatStateOf(Float.NaN) }

    val latestClamp = rememberUpdatedState(clampRotation)
    val latestOnRotate = rememberUpdatedState(onRotateDegrees)
    val latestOnLevelChanged = rememberUpdatedState(onLevelChanged)
    val latestOnDragStart = rememberUpdatedState(onDragStart)
    val latestOnDragEnd = rememberUpdatedState(onDragEnd)

    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val dialColor = MaterialTheme.colorScheme.primary
    val indicatorColor = MaterialTheme.colorScheme.onPrimary
    val ringColor = MaterialTheme.colorScheme.secondary
    val bezelMark = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.60f)
    val bezelNoon = MaterialTheme.colorScheme.secondary

    val sweepDegrees = DialMath.levelSweepDegrees(level, maxLevel)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .padding(24.dp)
            .semantics { contentDescription = VolumeLevelText.contentDescription(level, maxLevel) },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
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
                            val pos = change.position
                            val angle = DialMath.angleDegrees(
                                pos.x - center.x,
                                pos.y - center.y,
                            )
                            if (!lastAngle.isNaN()) {
                                val rawDelta = DialMath.shortestAngleDelta(lastAngle, angle)
                                val applied = latestClamp.value(rawDelta)
                                if (applied != 0f) {
                                    val result = latestOnRotate.value(applied)
                                    if (result.changed) {
                                        haptics.onLevelChanged(view, result.steps)
                                        latestOnLevelChanged.value(result)
                                    }
                                }
                                // Blocked motion past a hard stop does nothing.
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

            // Stationary bezel: one detent mark per real level step. Noon is
            // both level 0 and the max level.
            if (maxLevel > 0) {
                val bezelOuter = radius * 0.96f
                val markInner = radius * 0.86f
                val noonInner = radius * 0.80f
                val markWidth = (stroke * 0.24f).coerceAtMost(radius * 6.28f / maxLevel * 0.35f)
                for (i in 0 until maxLevel) {
                    val noon = i == 0
                    val deg = DialMath.levelSweepDegrees(i, maxLevel)
                    val rad = Math.toRadians((deg - 90f).toDouble())
                    val cx = cos(rad).toFloat()
                    val cy = sin(rad).toFloat()
                    val inner = if (noon) noonInner else markInner
                    drawLine(
                        color = if (noon) bezelNoon else bezelMark,
                        start = Offset(size.center.x + cx * inner, size.center.y + cy * inner),
                        end = Offset(size.center.x + cx * bezelOuter, size.center.y + cy * bezelOuter),
                        strokeWidth = if (noon) stroke * 0.42f else markWidth,
                        cap = StrokeCap.Round,
                    )
                }
            }

            // Knob + indicator point at the actual level.
            rotate(degrees = sweepDegrees, pivot = size.center) {
                drawCircle(color = dialColor, radius = radius * 0.72f)
                drawLine(
                    color = indicatorColor,
                    start = Offset(size.center.x, size.center.y - radius * 0.66f),
                    end = Offset(size.center.x, size.center.y - radius * 0.56f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }

            // Progress arc for the actual level.
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = sweepDegrees,
                useCenter = false,
                style = Stroke(width = stroke * 0.35f, cap = StrokeCap.Round),
                topLeft = Offset(size.center.x - radius * 0.92f, size.center.y - radius * 0.92f),
                size = androidx.compose.ui.geometry.Size(radius * 1.84f, radius * 1.84f),
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = VolumeLevelText.levelOf(level, maxLevel),
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Text(
                text = VolumeLevelText.percentLabel(level, maxLevel),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}
