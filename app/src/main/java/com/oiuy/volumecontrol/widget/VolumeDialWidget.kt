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
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.oiuy.volumecontrol.MainActivity
import com.oiuy.volumecontrol.R
import com.oiuy.volumecontrol.volume.MediaVolumeController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Home-screen widget that mirrors the dial's look (percent + dial face).
 *
 * Android app widgets cannot receive continuous rotation gestures. Interaction
 * is limited to: tap dial to open the app; + / − buttons adjust media volume
 * by 10% of the stream max and refresh the widget.
 */
class VolumeDialWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val percent = MediaVolumeController(context).percent
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
        Row(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "−",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                ),
                modifier = GlanceModifier
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clickable(actionRunCallback<DecreaseVolumeAction>()),
            )
            Spacer(GlanceModifier.width(8.dp))
            Text(
                text = "+",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                ),
                modifier = GlanceModifier
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clickable(actionRunCallback<IncreaseVolumeAction>()),
            )
        }
    }
}

class IncreaseVolumeAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        MediaVolumeController(context).adjustByPercentStep(VolumeActions.EXTRA_PERCENT_STEP)
        VolumeDialWidget().update(context, glanceId)
    }
}

class DecreaseVolumeAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        MediaVolumeController(context).adjustByPercentStep(-VolumeActions.EXTRA_PERCENT_STEP)
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
