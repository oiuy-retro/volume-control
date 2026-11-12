package com.oiuy.volumecontrol

import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.oiuy.volumecontrol.buttons.VolumeButtonPrefs
import com.oiuy.volumecontrol.buttons.VolumeButtonService
import com.oiuy.volumecontrol.ui.VolumeButtonSettings
import com.oiuy.volumecontrol.ui.VolumeControlTheme
import com.oiuy.volumecontrol.ui.VolumeDial
import com.oiuy.volumecontrol.volume.MediaVolumeController
import com.oiuy.volumecontrol.volume.VolumeMode
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
                    val lifecycleOwner = LocalLifecycleOwner.current
                    val controller = remember { MediaVolumeController(context) }
                    var level by remember { mutableFloatStateOf(controller.level()) }
                    var maxUnits by remember { mutableIntStateOf(controller.maxUnits) }
                    var unitsPerTurn by remember { mutableIntStateOf(controller.unitsPerTurn) }
                    var status by remember { mutableStateOf(controller.statusText()) }
                    val dragging = remember { AtomicBoolean(false) }
                    val buttonPrefs = remember { VolumeButtonPrefs(context) }
                    var buttonServiceOn by remember { mutableStateOf(VolumeButtonPrefs.isServiceEnabled(context)) }
                    var buttonFeatureOn by remember { mutableStateOf(buttonPrefs.enabled) }
                    var buttonStep by remember { mutableIntStateOf(buttonPrefs.stepSize) }
                    var fineMode by remember { mutableStateOf(controller.mode == VolumeMode.SAMSUNG_FINE) }

                    // Reads only: rebase the dial on the system value.
                    fun syncFromSystem() {
                        if (dragging.get()) return
                        controller.syncFromSystem()
                        level = controller.level()
                        maxUnits = controller.maxUnits
                        unitsPerTurn = controller.unitsPerTurn
                        status = controller.statusText()
                        fineMode = controller.mode == VolumeMode.SAMSUNG_FINE
                    }

                    DisposableEffect(Unit) {
                        controller.onModeChanged = {
                            level = controller.level()
                            maxUnits = controller.maxUnits
                            unitsPerTurn = controller.unitsPerTurn
                            status = controller.statusText()
                            fineMode = controller.mode == VolumeMode.SAMSUNG_FINE
                        }
                        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                            override fun onChange(selfChange: Boolean, uri: Uri?) {
                                // Not while dragging: a stale read would reset the fine level.
                                if (dragging.get()) return
                                syncFromSystem()
                                VolumeDialWidgetReceiver.requestUpdate(context)
                            }
                        }
                        contentResolver.registerContentObserver(
                            Settings.System.CONTENT_URI,
                            true,
                            observer,
                        )
                        // Samsung fine changes between whole steps may not touch Settings.
                        val volumeReceiver = object : BroadcastReceiver() {
                            override fun onReceive(c: Context, intent: Intent) {
                                if (dragging.get()) return
                                syncFromSystem()
                                VolumeDialWidgetReceiver.requestUpdate(context)
                            }
                        }
                        ContextCompat.registerReceiver(
                            context,
                            volumeReceiver,
                            IntentFilter(VOLUME_CHANGED_ACTION).apply {
                                // Sent by this app's volume button service after a fine change.
                                addAction(VolumeButtonService.ACTION_FINE_VOLUME_CHANGED)
                            },
                            ContextCompat.RECEIVER_NOT_EXPORTED,
                        )
                        val lifecycleObserver = LifecycleEventObserver { _, event ->
                            when (event) {
                                Lifecycle.Event.ON_RESUME -> {
                                    syncFromSystem()
                                    buttonServiceOn = VolumeButtonPrefs.isServiceEnabled(context)
                                }
                                Lifecycle.Event.ON_PAUSE -> controller.flush()
                                else -> Unit
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
                        onDispose {
                            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
                            context.unregisterReceiver(volumeReceiver)
                            contentResolver.unregisterContentObserver(observer)
                            controller.onModeChanged = null
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .safeDrawingPadding(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        VolumeDial(
                            // Shrinks only if the settings card would not fit below it.
                            modifier = Modifier.weight(1f, fill = false),
                            level = level,
                            maxUnits = maxUnits,
                            unitsPerTurn = unitsPerTurn,
                            onRotateDegrees = { degrees ->
                                // Always advance the raw level from finger deltas.
                                controller.adjustByDegrees(degrees)
                            },
                            onReportedLevel = { reported ->
                                // Magnet overlay is drawing only; output follows the raw level's unit.
                                level = reported
                                status = controller.statusText()
                            },
                            currentRawLevel = { controller.level() },
                            onTickCrossed = {
                                controller.showSystemVolumeUi()
                            },
                            onDragStart = { dragging.set(true) },
                            onDragEnd = {
                                dragging.set(false)
                                controller.flush()
                                level = controller.level()
                                status = controller.statusText()
                                // Widget refresh once per gesture, not every drag pixel.
                                VolumeDialWidgetReceiver.requestUpdate(context)
                            },
                        )
                        Text(
                            text = status,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp),
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        VolumeButtonSettings(
                            serviceEnabled = buttonServiceOn,
                            featureEnabled = buttonFeatureOn,
                            fineAvailable = fineMode,
                            fineMax = maxUnits,
                            stepSize = buttonStep,
                            onFeatureEnabledChange = { on ->
                                buttonPrefs.enabled = on
                                buttonFeatureOn = on
                            },
                            onOpenAccessibilitySettings = {
                                try {
                                    context.startActivity(
                                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                    )
                                } catch (_: ActivityNotFoundException) {
                                    // No Accessibility settings screen; nothing to open.
                                }
                            },
                            onStepSizeChange = { step ->
                                if (step != buttonStep) {
                                    buttonPrefs.stepSize = step
                                    buttonStep = step
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    private companion object {
        const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"
    }
}
