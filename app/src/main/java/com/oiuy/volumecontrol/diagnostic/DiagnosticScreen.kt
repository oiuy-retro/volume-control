package com.oiuy.volumecontrol.diagnostic

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DiagnosticScreen(
    state: DiagnosticSnapshot,
    testRunning: Boolean,
    onRefresh: () -> Unit,
    onTest: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Samsung fine volume diagnostic", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Diagnostic build. The dial and the widget + / − buttons are turned off. " +
                    "This screen only reads volume, except the test button below.",
                style = MaterialTheme.typography.bodyMedium,
            )

            Section("Device") {
                Line("Manufacturer", state.device.manufacturer)
                Line("Model", state.device.model)
                Line("Device", state.device.device)
                Line("Android", "${state.device.release} (API ${state.device.sdkInt})")
                Line("Build", state.device.buildDisplay)
            }

            Section("Android media volume (public API)") {
                when (val p = state.publicVolume) {
                    is PortResult.Ok -> {
                        Line("Index", p.value.index.toString())
                        Line("Min", p.value.min?.toString() ?: "Not available below Android 9")
                        Line("Max", p.value.max.toString())
                        Line("Muted", if (p.value.muted) "Yes" else "No")
                        Line("Media output", p.value.route)
                    }
                    is PortResult.Failed -> Line("Error", p.reason)
                }
            }

            Section("Samsung fine volume") {
                when (val a = state.fineAvailability) {
                    is PortResult.Ok -> Line(
                        "Methods",
                        "Samsung fine getter (int) and setter (int, int, int) found",
                    )
                    is PortResult.Failed -> Line("Methods", a.reason)
                }
                when (val f = state.fineValue) {
                    null -> Line("Fine value", "Not read")
                    is PortResult.Ok -> Line("Fine value", f.value.toString())
                    is PortResult.Failed -> Line("Fine value error", f.reason)
                }
                Text(
                    "The fine value is the raw number Samsung's method returns for music. " +
                        "Its scale on this device has not been verified. Readings refresh when " +
                        "volume or the output changes; a changed number is not proof of an audible change.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                Text("Refresh readings (read-only)")
            }
            Button(
                onClick = onTest,
                enabled = state.canTest && !testRunning,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Test one fine step quieter")
            }
            Text(
                "Each tap reads the current fine value and asks Samsung's method for one less, once. " +
                    "It does not restore the old value and does not fall back to normal volume steps. " +
                    "The button is off when the fine value is 0, media is muted, or the methods are unavailable.",
                style = MaterialTheme.typography.bodySmall,
            )

            Section("Last test (kept until the next tap)") {
                LastTest(state.lastTest)
            }
        }
    }
}

@Composable
private fun LastTest(outcome: TestOutcome?) {
    when (outcome) {
        null -> Text("No test run yet.")
        is TestOutcome.NotAttempted -> {
            Line("Result", "No call made")
            Line("Reason", outcome.reason)
        }
        is TestOutcome.Attempted -> {
            Line("Fine before", outcome.fineBefore.toString())
            Line("Requested", outcome.requested.toString())
            when (val c = outcome.call) {
                is PortResult.Ok -> Line("Call", "Returned without error")
                is PortResult.Failed -> Line("Call error", c.reason)
            }
            when (val f = outcome.fineAfter) {
                is PortResult.Ok -> Line("Fine after", f.value.toString())
                is PortResult.Failed -> Line("Fine after error", f.reason)
            }
            Line("Index before", publicIndex(outcome.publicBefore))
            Line("Index after", publicIndex(outcome.publicAfter))
            Line("Output before", publicRoute(outcome.publicBefore))
            Line("Output after", publicRoute(outcome.publicAfter))
            Line(
                "Summary",
                when {
                    outcome.readBackMatches -> "Read-back equals the requested value."
                    outcome.call is PortResult.Failed -> "The call failed. Volume may be unchanged."
                    outcome.fineAfter is PortResult.Ok &&
                        (outcome.fineAfter as PortResult.Ok<Int>).value == outcome.fineBefore ->
                        "No change was read back."
                    else -> "Read-back differs from the requested value."
                },
            )
        }
    }
}

private fun publicIndex(p: PortResult<PublicVolume>): String = when (p) {
    is PortResult.Ok -> "${p.value.index} of ${p.value.max}"
    is PortResult.Failed -> "Error: ${p.reason}"
}

private fun publicRoute(p: PortResult<PublicVolume>): String = when (p) {
    is PortResult.Ok -> p.value.route
    is PortResult.Failed -> "Error: ${p.reason}"
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun Line(label: String, value: String) {
    Text("$label: $value", style = MaterialTheme.typography.bodyMedium)
}
