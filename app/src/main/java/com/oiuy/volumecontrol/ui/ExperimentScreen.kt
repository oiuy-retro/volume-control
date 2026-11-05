package com.oiuy.volumecontrol.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.oiuy.volumecontrol.effect.EffectState
import com.oiuy.volumecontrol.effect.GainMath

/** Status lines shown under the dial. Pure so it can be unit-tested. */
object ExperimentText {
    const val TITLE = "EXPERIMENT: global output effect (session 0)"
    const val NOTE_VOLUME = "This build does not change Android media volume."
    const val NOTE_UNVERIFIED =
        "Audible effect is not verified. Android deprecated session 0 effects; " +
            "this device, some apps, or some outputs (including Bluetooth) may ignore it."

    fun backendLine(s: EffectState): String =
        "Backend: " + (s.backend?.label ?: "none (effect off)")

    fun requestedLine(s: EffectState): String =
        if (s.active) "Requested: ${GainMath.formatDb(s.requestedDb)} (limit ${GainMath.formatDb(s.floorDb)})"
        else "Requested: none"

    fun confirmedLine(s: EffectState): String {
        if (!s.active) return "Effect API reports: nothing (not attached)"
        val c = s.confirmedDb
        if (c == null || c.isEmpty()) return "Effect API reports: not confirmed"
        val lo = c.min()
        val hi = c.max()
        val v = if (kotlin.math.abs(hi - lo) < 0.051f) GainMath.formatDb(lo)
        else "${GainMath.formatDb(lo)} to ${GainMath.formatDb(hi)}"
        val match = if (s.confirmedMatchesRequest) "" else " (differs from request)"
        return "Effect API reports: $v on ${c.size} ${if (s.backend?.name == "EQUALIZER") "band(s)" else "channel(s)"}$match"
    }

    fun controlLine(s: EffectState): String = when {
        !s.active -> "Control: none"
        !s.enabledReported -> "Control: effect reports disabled"
        s.hasControl -> "Control: this app"
        else -> "Control: lost to another app"
    }
}

@Composable
fun ExperimentScreen(
    state: EffectState,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
    onReset: () -> Unit,
    onRequestDb: (Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            ExperimentText.TITLE,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.error,
        )
        GainDial(
            requestedDb = state.requestedDb,
            floorDb = state.floorDb,
            enabled = state.active,
            onRequestDb = onRequestDb,
            onDragEnd = onDragEnd,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.active) {
                Button(onClick = onDisable) { Text("Disable and release") }
                OutlinedButton(onClick = onReset) { Text("Reset to 0 dB") }
            } else {
                Button(onClick = onEnable) { Text("Enable experiment") }
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val body = MaterialTheme.typography.bodyMedium
            Text(ExperimentText.backendLine(state), style = body)
            if (state.active) Text(state.backendDetail, style = MaterialTheme.typography.bodySmall)
            Text(ExperimentText.requestedLine(state), style = body)
            Text(ExperimentText.confirmedLine(state), style = body)
            Text(ExperimentText.controlLine(state), style = body)
            state.message?.let {
                Text(it, style = body, color = MaterialTheme.colorScheme.error)
            }
            state.attempts.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            Text(ExperimentText.NOTE_UNVERIFIED, style = MaterialTheme.typography.bodySmall)
            Text(ExperimentText.NOTE_VOLUME, style = MaterialTheme.typography.bodySmall)
        }
    }
}
