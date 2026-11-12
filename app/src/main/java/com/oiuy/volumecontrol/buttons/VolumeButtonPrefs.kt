package com.oiuy.volumecontrol.buttons

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import androidx.core.content.edit
import com.oiuy.volumecontrol.volume.VolumeButtonSteps

/** Volume button settings, stored in plain SharedPreferences (no other data is kept). */
class VolumeButtonPrefs(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) = prefs.edit { putBoolean(KEY_ENABLED, value) }

    var stepSize: Int
        get() = VolumeButtonSteps.clampStep(prefs.getInt(KEY_STEP, VolumeButtonSteps.DEFAULT_STEP))
        set(value) = prefs.edit { putInt(KEY_STEP, VolumeButtonSteps.clampStep(value)) }

    /** Calls [onChange] whenever the switch or step changes. Keep the returned listener alive. */
    fun observe(onChange: () -> Unit): SharedPreferences.OnSharedPreferenceChangeListener {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> onChange() }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        return listener
    }

    fun stopObserving(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    companion object {
        private const val FILE = "volume_buttons"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_STEP = "step_size"

        /** Whether the Volume Control accessibility service is turned on in Android settings. */
        fun isServiceEnabled(context: Context): Boolean {
            val component = ComponentName(context, VolumeButtonService::class.java)
            val setting = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            )
            return isAccessibilityServiceListed(setting, component.packageName, component.className)
        }
    }
}
