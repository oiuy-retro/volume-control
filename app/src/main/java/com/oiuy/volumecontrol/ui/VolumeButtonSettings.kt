package com.oiuy.volumecontrol.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.oiuy.volumecontrol.volume.VolumeButtonSteps
import kotlin.math.roundToInt

/**
 * Volume button settings: an on/off row and the fine units moved per press.
 * When the accessibility service is not on, tapping the row opens Android's
 * Accessibility settings instead of toggling.
 */
@Composable
fun VolumeButtonSettings(
    serviceEnabled: Boolean,
    featureEnabled: Boolean,
    fineAvailable: Boolean,
    fineMax: Int,
    stepSize: Int,
    onFeatureEnabledChange: (Boolean) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onStepSizeChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = serviceEnabled && featureEnabled && fineAvailable
    val status = when {
        !fineAvailable -> "Needs Samsung fine volume. The buttons work normally on this phone."
        !serviceEnabled -> "Off. Tap to turn on Volume Control buttons in Accessibility settings."
        !featureEnabled -> "Paused. The buttons use Android's normal steps."
        else -> "On. " + VolumeButtonSteps.stepLabel(stepSize, fineMax) + "."
    }
    val onRowClick = {
        if (!serviceEnabled) onOpenAccessibilitySettings() else onFeatureEnabledChange(!featureEnabled)
    }

    Surface(
        onClick = onRowClick,
        enabled = fineAvailable,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Volume buttons",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = active,
                    enabled = fineAvailable,
                    onCheckedChange = { onRowClick() },
                    modifier = Modifier.padding(start = 16.dp),
                )
            }
            if (fineAvailable) {
                Text(
                    text = "Step: " + VolumeButtonSteps.stepLabel(stepSize, fineMax),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Slider(
                    value = VolumeButtonSteps.clampStep(stepSize).toFloat(),
                    onValueChange = { onStepSizeChange(VolumeButtonSteps.clampStep(it.roundToInt())) },
                    valueRange = VolumeButtonSteps.MIN_STEP.toFloat()..VolumeButtonSteps.MAX_STEP.toFloat(),
                    steps = VolumeButtonSteps.MAX_STEP - VolumeButtonSteps.MIN_STEP - 1,
                    modifier = Modifier.semantics {
                        contentDescription = "Volume button step size"
                    },
                )
                Text(
                    text = "Works only while the screen is on.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
