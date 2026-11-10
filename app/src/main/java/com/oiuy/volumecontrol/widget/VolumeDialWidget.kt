package com.oiuy.volumecontrol.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.oiuy.volumecontrol.MainActivity
import com.oiuy.volumecontrol.R
import com.oiuy.volumecontrol.diagnostic.AndroidPublicVolumePort
import com.oiuy.volumecontrol.diagnostic.PortResult
import com.oiuy.volumecontrol.volume.DialMath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Home-screen widget. In the 0.1.5 diagnostic build it is read-only: it shows
 * the public media volume percent and opens the app. The + / − controls are
 * removed, and their action classes below are kept only so widgets placed by
 * an earlier build do nothing when tapped instead of changing volume.
 */
class VolumeDialWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val percent = when (val read = AndroidPublicVolumePort(context).read()) {
            is PortResult.Ok -> DialMath.percentOf(read.value.index, read.value.max)
            is PortResult.Failed -> 0
        }
        provideContent {
            GlanceTheme {
                VolumeDialWidgetContent(percent = percent)
            }
        }
    }
}

@Composable
private fun VolumeDialWidgetContent(percent: Int) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = GlanceModifier
                .size(96.dp)
                .background(ImageProvider(R.drawable.widget_preview))
                .clickable(actionStartActivity<MainActivity>()),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "$percent%",
                style = TextStyle(
                    color = ColorProvider(Color.White),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
        Spacer(GlanceModifier.height(8.dp))
        Text(
            text = "Diagnostic build",
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 12.sp,
            ),
        )
    }
}

/** Kept for widgets placed by earlier builds. Diagnostic build: no volume change. */
class IncreaseVolumeAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        // Diagnostic build: intentionally no volume change. Refresh the display only.
        VolumeDialWidget().update(context, glanceId)
    }
}

/** Kept for widgets placed by earlier builds. Diagnostic build: no volume change. */
class DecreaseVolumeAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        // Diagnostic build: intentionally no volume change. Refresh the display only.
        VolumeDialWidget().update(context, glanceId)
    }
}

class VolumeDialWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = VolumeDialWidget()

    override fun onReceive(context: Context, intent: android.content.Intent) {
        super.onReceive(context, intent)
        if (intent.action == "android.media.VOLUME_CHANGED_ACTION") {
            requestUpdate(context)
        }
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        fun requestUpdate(context: Context) {
            val appContext = context.applicationContext
            scope.launch {
                val manager = GlanceAppWidgetManager(appContext)
                val widget = VolumeDialWidget()
                manager.getGlanceIds(VolumeDialWidget::class.java).forEach { id ->
                    widget.update(appContext, id)
                }
            }
        }
    }
}
