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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.oiuy.volumecontrol.ui.VolumeControlTheme
import com.oiuy.volumecontrol.ui.VolumeDial
import com.oiuy.volumecontrol.volume.MediaVolumeController
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
                    val controller = remember { MediaVolumeController(context) }
                    var level by remember { mutableIntStateOf(controller.currentVolume) }
                    var maxLevel by remember { mutableIntStateOf(controller.maxVolume) }
                    val dragging = remember { AtomicBoolean(false) }

                    DisposableEffect(Unit) {
                        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                            override fun onChange(selfChange: Boolean, uri: Uri?) {
                                // Always show the real level (keys, other apps, Bluetooth
                                // device reports). Re-anchoring the hidden dial target is
                                // left to the next drag frame while dragging.
                                if (!dragging.get()) controller.syncFromSystem()
                                level = controller.currentVolume
                                maxLevel = controller.maxVolume
                                if (!dragging.get()) VolumeDialWidgetReceiver.requestUpdate(context)
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
                        VolumeDial(
                            level = level,
                            maxLevel = maxLevel,
                            clampRotation = { controller.clampRotationDegrees(it) },
                            onRotateDegrees = { degrees ->
                                val result = controller.adjustByDegrees(degrees)
                                level = result.toIndex
                                maxLevel = result.maxIndex
                                result
                            },
                            onLevelChanged = { controller.showSystemVolumeUi() },
                            onDragStart = {
                                dragging.set(true)
                                controller.beginGesture()
                                level = controller.currentVolume
                                maxLevel = controller.maxVolume
                            },
                            onDragEnd = {
                                dragging.set(false)
                                level = controller.currentVolume
                                maxLevel = controller.maxVolume
                                // Widget refresh once per gesture, not every drag pixel.
                                VolumeDialWidgetReceiver.requestUpdate(context)
                            },
                        )
                    }
                }
            }
        }
    }
}
