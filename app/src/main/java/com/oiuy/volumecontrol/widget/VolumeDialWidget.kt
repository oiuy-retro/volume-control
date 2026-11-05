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
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
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
import com.oiuy.volumecontrol.effect.BackendKind
import com.oiuy.volumecontrol.effect.GainMath
import com.oiuy.volumecontrol.effect.GlobalEffectSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Experimental build widget: read-only status of the global output effect.
 * It has no +/- controls and never changes Android media volume. Tap opens
 * the app, where the effect can be enabled, adjusted, or released.
 */
class VolumeDialWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val s = GlobalEffectSession.state
        val main = if (s.active) GainMath.formatDb(s.requestedDb) else "Off"
        val sub = when {
            !s.active -> "experiment"
            s.backend == BackendKind.DYNAMICS_PROCESSING -> "gain (exp.)"
            else -> "EQ bands (exp.)"
        }
        provideContent {
            GlanceTheme {
                VolumeDialWidgetContent(main = main, sub = sub)
            }
        }
    }
}

@Composable
private fun VolumeDialWidgetContent(main: String, sub: String) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .padding(8.dp)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = GlanceModifier
                .size(96.dp)
                .background(ImageProvider(R.drawable.widget_preview)),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = main,
                    style = TextStyle(
                        color = ColorProvider(Color.White),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Text(
                    text = sub,
                    style = TextStyle(color = ColorProvider(Color.White), fontSize = 11.sp),
                )
            }
        }
        Spacer(GlanceModifier.height(6.dp))
        Text(
            text = "Experimental: tap to open",
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 12.sp),
        )
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
