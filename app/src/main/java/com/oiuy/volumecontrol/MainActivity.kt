package com.oiuy.volumecontrol

import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import com.oiuy.volumecontrol.effect.GlobalEffectSession
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.oiuy.volumecontrol.ui.VolumeControlTheme
import com.oiuy.volumecontrol.ui.VolumeDial
import com.oiuy.volumecontrol.hybrid.HybridVolumeController
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.oiuy.volumecontrol.widget.VolumeDialWidgetReceiver
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import com.oiuy.volumecontrol.effect.GainMath
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import com.oiuy.volumecontrol.hybrid.CalibrationSession
import com.oiuy.volumecontrol.hybrid.CalibrationSide
import com.oiuy.volumecontrol.hybrid.CurveSource
import com.oiuy.volumecontrol.hybrid.HybridReadout

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VolumeControlTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val context = LocalContext.current
                    val controller = remember { HybridVolumeController(context) }
                    var percent by remember { mutableFloatStateOf(controller.fractionalPercent()) }
                    var readout by remember { mutableStateOf(controller.readout()) }
                    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                        controller.onResume()
                        percent = controller.fractionalPercent()
                        readout = controller.readout()
                    }

                    DisposableEffect(controller) {
                        controller.onChanged = {
                            percent = controller.fractionalPercent()
                            readout = controller.readout()
                        }
                        controller.start()
                        readout = controller.readout()
                        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                            override fun onChange(selfChange: Boolean, uri: Uri?) {
                                controller.syncFromSystem()
                                percent = controller.fractionalPercent()
                                readout = controller.readout()
                                VolumeDialWidgetReceiver.requestUpdate(context)
                            }
                        }
                        contentResolver.registerContentObserver(
                            Settings.System.CONTENT_URI,
                            true,
                            observer,
                        )
                        onDispose {
                            contentResolver.unregisterContentObserver(observer)
                            controller.stop()
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .safeDrawingPadding(),
                        contentAlignment = Alignment.Center,
                    ) {
                      Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                      ) {
                        VolumeDial(
                            percent = percent,
                            enabled = !readout.diagnostic,
                            centerLabel = readout.calibration?.let { "C " + GainMath.formatDb(it.cutDb) },
                            onRotateDegrees = { degrees ->
                                // Always advance the raw accumulator from finger deltas.
                                controller.adjustByDegrees(degrees)
                            },
                            onReportedPercent = { reported ->
                                // One output write per finger event, after magnet resolution.
                                // The raw accumulator is never rebuilt from this overlay.
                                controller.setOutputPercent(reported)
                                percent = reported
                                readout = controller.readout()
                            },
                            currentRawPercent = { controller.fractionalPercent() },
                            onDragStart = { controller.beginDrag() },
                            onDragEnd = {
                                controller.endDrag()
                                // The final snapped output is retained; only the display follows raw.
                                percent = controller.fractionalPercent()
                                // Widget refresh once per gesture, not every drag pixel.
                                readout = controller.readout()
                                VolumeDialWidgetReceiver.requestUpdate(context)
                            },
                        )
                        Text(
                            text = readoutText(readout.copy(effect = GlobalEffectSession.state)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp),
                        )
                        val cal = readout.calibration
                        if (cal != null) {
                            CalibrationPanel(readout, cal, controller)
                        } else {
                            TextButton(onClick = { controller.reattach() }) {
                                Text("Enable / reattach effect (cut may briefly release)")
                            }
                            if (!readout.diagnostic) {
                                Button(onClick = { controller.beginCalibration() }) {
                                    Text("Calibrate steps for this output")
                                }
                                Button(onClick = { controller.beginDiagnostic() }) {
                                    Text("Test effect at fixed Android level: \u221212 dB")
                                }
                            } else {
                                Text("TEST MODE: dial locked. App does not change Android level. " +
                                    "Compare the same playing audio; hardware buttons still work.",
                                    modifier = Modifier.padding(horizontal = 24.dp))
                                Button(onClick = { controller.toggleDiagnosticCut() }) {
                                    Text(if (GlobalEffectSession.state.requestedDb < -0.05f)
                                        "Test 0 dB (louder)" else "Test \u221212 dB (or backend floor)")
                                }
                                TextButton(onClick = { controller.endDiagnostic() }) {
                                    Text("End test (keep current cut)")
                                }
                            }
                        }
                      }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalibrationPanel(r: HybridReadout, cal: CalibrationSession, controller: HybridVolumeController) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 24.dp),
    ) {
        Text(calibrationText(r, cal), textAlign = TextAlign.Center)
        Button(onClick = { controller.toggleCalibrationSide() }) {
            Text(if (cal.side == CalibrationSide.A) "Play B (level ${cal.pair + 1} with cut)"
                else "Play A (level ${cal.pair}, no cut)")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { controller.stepCalibrationPair(-1) }, enabled = cal.pair > 1) {
                Text("Lower pair")
            }
            OutlinedButton(onClick = { controller.stepCalibrationPair(1) }, enabled = cal.pair < r.maxIndex - 1) {
                Text("Higher pair (louder)")
            }
        }
        Button(onClick = { controller.saveCalibrationStep() }) { Text("Save this step") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { controller.resetCalibration() }) { Text("Reset this output") }
            TextButton(onClick = { controller.endCalibration() }) { Text("Done") }
        }
    }
}

internal fun calibrationText(r: HybridReadout, cal: CalibrationSession): String = buildString {
    append("CALIBRATING ${r.outputName.ifBlank { "this output" }}: level ${cal.pair} \u2192 ${cal.pair + 1}\n")
    append("A = level ${cal.pair}, no cut. B = level ${cal.pair + 1} with cut C.\n")
    append("Now playing: ${cal.side}. Turn the dial to change C (0.5 dB detents) until A and B sound equally loud, then Save.\n")
    append("C = ${GainMath.formatDb(cal.cutDb)} (limit ${GainMath.formatDb(r.floorDb)})")
    if (cal.cutDb <= r.floorDb + 0.01f) {
        append("\nC is at the backend floor. If B is still louder, this step is bigger than the effect can cut; it will still jump.")
    }
    r.savedStepForPair?.let { append("\nSaved for this pair: ${"%.1f".format(it)} dB") }
}

internal fun curveText(r: HybridReadout): String = when (r.curveSource) {
    CurveSource.CALIBRATED -> "Step sizes: calibrated ${r.calibratedSteps} of ${r.calibratablePairs} steps" +
        (if (r.calibratedSteps < r.calibratablePairs) " (others filled from nearest calibrated step)" else "") +
        ". Mute \u2192 level 1 uses a fixed fade."
    CurveSource.MEASURED -> "Step sizes: reported by Android for this output."
    CurveSource.ESTIMATED -> "Step sizes estimated: this output does not report its volume curve (2.5 dB per step assumed). Calibrate for even steps."
}

internal fun readoutText(r: HybridReadout): String = buildString {
    val s = r.effect
    append("Android level ${r.streamIndex} of ${r.maxIndex}")
    append("\n${s.backend?.shortName ?: "No effect"}: requested ${GainMath.formatDb(s.requestedDb)}")
    append("\nAPI read-back: ")
    append(s.confirmedDb?.joinToString { GainMath.formatDb(it) } ?: "not confirmed")
    append("\nAttached: ${s.active}; enabled: ${s.enabledReported}; control: ${s.hasControl}")
    val confirmed = s.active && s.enabledReported && s.hasControl && s.confirmedMatchesRequest
    append(if (confirmed) "\nAPI matches request — not proof of audible application." else
        "\nFine control NOT confirmed; whole levels or held volume only.")
    append("\n").append(curveText(r))
    r.message?.let { append("\n").append(it) }
    s.message?.let { append("\n").append(it) }
    if (s.active && !s.confirmedMatchesRequest) append("\nAPI read-back does not match requested gain.")
    s.attempts.forEach { append("\n").append(it) }
}
