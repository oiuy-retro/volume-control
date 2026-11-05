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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.oiuy.volumecontrol.ui.VolumeControlTheme
import com.oiuy.volumecontrol.ui.VolumeDial
import com.oiuy.volumecontrol.volume.DialMath
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
                    var percent by remember { mutableFloatStateOf(controller.fractionalPercent()) }
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
                        VolumeDial(
                            percent = percent,
                            onRotateDegrees = { degrees, allowSnap ->
                                val continuous = controller.adjustByDegrees(degrees)
                                percent = if (allowSnap) {
                                    val snapped = DialMath.snapToWholePercentIfNear(continuous)
                                    if (snapped != continuous) {
                                        controller.setFractionalPercent(snapped)
                                    } else {
                                        continuous
                                    }
                                } else {
                                    continuous
                                }
                                percent
                            },
                            onTickCrossed = {
                                controller.showSystemVolumeUi()
                            },
                            onDragStart = { dragging.set(true) },
                            onDragEnd = {
                                dragging.set(false)
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
