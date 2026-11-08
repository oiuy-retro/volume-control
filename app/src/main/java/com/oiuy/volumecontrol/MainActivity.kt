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
import com.oiuy.volumecontrol.hybrid.HybridMath
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
import java.util.concurrent.atomic.AtomicBoolean

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
                    val controller = remember { HybridVolumeController(context).also { it.start() } }
                    var percent by remember { mutableFloatStateOf(controller.fractionalPercent()) }
                    var readout by remember { mutableStateOf(controller.readout()) }
                    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                        GlobalEffectSession.refresh()
                        controller.reloadCurve()
                        controller.syncFromSystem()
                        percent = controller.fractionalPercent()
                        readout = controller.readout()
                    }
                    val dragging = remember { AtomicBoolean(false) }

                    DisposableEffect(Unit) {
                        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                            override fun onChange(selfChange: Boolean, uri: Uri?) {
                                // Do not syncFromSystem while the user is dragging the dial.
                                // Our own setStreamVolume would otherwise reset the fine
                                // fractional level and freeze / jump the dial.
                                if (dragging.get()) return
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
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .safeDrawingPadding(),
                        contentAlignment = Alignment.Center,
                    ) {
                      Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                      ) {
                        VolumeDial(
                            percent = percent,
                            onRotateDegrees = { degrees ->
                                // Always advance the raw accumulator from finger deltas.
                                controller.adjustByDegrees(degrees)
                            },
                            onReportedPercent = { reported ->
                                val raw = controller.fractionalPercent()
                                if (kotlin.math.abs(reported - raw) > 1e-4f) {
                                    // Magnet overlay: STREAM_MUSIC follows snapped value;
                                    // raw fractionalLevel is preserved for the next delta.
                                    controller.setOutputPercent(reported)
                                }
                                percent = reported
                                readout = controller.readout()
                            },
                            currentRawPercent = { controller.fractionalPercent() },
                            onTickCrossed = {
                                controller.showSystemVolumeUi()
                            },
                            onDragStart = { dragging.set(true) },
                            onDragEnd = {
                                dragging.set(false)
                                // Ensure stream matches raw when the gesture ends.
                                percent = controller.fractionalPercent()
                                // Widget refresh once per gesture, not every drag pixel.
                                readout = controller.readout()
                                VolumeDialWidgetReceiver.requestUpdate(context)
                            },
                        )
                        Text(
                            text = readoutText(readout),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp),
                        )
                      }
                    }
                }
            }
        }
    }
}

internal fun readoutText(r: com.oiuy.volumecontrol.hybrid.HybridReadout): String = buildString {
    append("Android level ${r.streamIndex} of ${r.maxIndex}")
    if (r.backendName != null) {
        append(", effect ${HybridMath.formatDb(r.attenuationDb)} (${r.backendName})")
        if (r.curveEstimated) append("\nStep sizes estimated: this output does not report its volume curve.")
    }
    r.message?.let { append("\n").append(it) }
}
