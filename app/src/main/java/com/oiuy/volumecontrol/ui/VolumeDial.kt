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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oiuy.volumecontrol.volume.DialMath
import kotlin.math.min

/**
 * Large tactile dial. Visual rotation tracks the finger 1:1.
 * Accumulated rotation maps to STREAM_MUSIC: 360° → ±10% of max.
 */
@Composable
fun VolumeDial(
    percent: Int,
    onRotateDegrees: (Float) -> Int,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    var visualRotation by remember { mutableFloatStateOf(0f) }
    var lastAngle by remember { mutableFloatStateOf(Float.NaN) }
    var lastStepBucket by remember { mutableIntStateOf(percent / 10) }

    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val dialColor = MaterialTheme.colorScheme.primary
    val indicatorColor = MaterialTheme.colorScheme.onPrimary
    val ringColor = MaterialTheme.colorScheme.secondary

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .padding(24.dp)
            .semantics { contentDescription = "Volume dial, $percent percent" },
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
                                val newPercent = onRotateDegrees(delta)
                                val bucket = newPercent / 10
                                if (bucket != lastStepBucket) {
                                    lastStepBucket = bucket
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
                // Indicator tick at top of dial face (12 o'clock relative to rotation)
                val tickStart = Offset(size.center.x, size.center.y - radius * 0.68f)
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

            // Progress arc for current percent (static reference)
            val sweep = (percent.coerceIn(0, 100) / 100f) * 360f
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
            text = "$percent%",
            style = MaterialTheme.typography.displayLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 56.sp,
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
