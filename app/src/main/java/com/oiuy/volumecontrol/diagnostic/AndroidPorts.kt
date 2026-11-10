package com.oiuy.volumecontrol.diagnostic

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

fun currentDeviceInfo(): DeviceInfo = DeviceInfo(
    manufacturer = Build.MANUFACTURER.orEmpty(),
    model = Build.MODEL.orEmpty(),
    device = Build.DEVICE.orEmpty(),
    release = Build.VERSION.RELEASE.orEmpty(),
    sdkInt = Build.VERSION.SDK_INT,
    buildDisplay = Build.DISPLAY.orEmpty(),
)

/** Public AudioManager reads only. No setter is reachable from this class. */
class AndroidPublicVolumePort(context: Context) : PublicVolumePort {
    private val audioManager =
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    override fun read(): PortResult<PublicVolume> = try {
        val stream = AudioManager.STREAM_MUSIC
        PortResult.Ok(
            PublicVolume(
                index = audioManager.getStreamVolume(stream),
                min = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    audioManager.getStreamMinVolume(stream)
                } else {
                    null
                },
                max = audioManager.getStreamMaxVolume(stream),
                muted = audioManager.isStreamMute(stream),
                route = describeMediaRoute(),
            ),
        )
    } catch (e: RuntimeException) {
        PortResult.Failed(describe(e))
    }

    private fun describeMediaRoute(): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return "Not available from public APIs below Android 13."
        }
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val devices = audioManager.getAudioDevicesForAttributes(attributes)
        if (devices.isEmpty()) return "No media output reported."
        // Type names only: product names can contain names the user gave a device.
        return devices.joinToString(", ") { deviceTypeName(it.type) }
    }
}

/**
 * Looks up only two Samsung AudioManager methods with ordinary public
 * [Class.getMethod]: `semGetFineVolume(int)` and `semSetFineVolume(int, int, int)`.
 * No accessibility overrides, no other members, no Binder calls, no hidden-API exemptions.
 * Construct only on Samsung devices.
 */
class ReflectiveSamsungFineVolumePort(context: Context) : FineVolumePort {
    private val audioManager =
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val getter: Method?
    private val setter: Method?
    private val lookupError: String?

    init {
        var g: Method? = null
        var s: Method? = null
        var error: String? = null
        try {
            val int = Int::class.javaPrimitiveType
            g = AudioManager::class.java.getMethod(GETTER, int)
            s = AudioManager::class.java.getMethod(SETTER, int, int, int)
            if (g.returnType != int) {
                error = "$GETTER returns ${g.returnType.name}, expected int."
            }
        } catch (e: NoSuchMethodException) {
            error = "Method not found: ${e.message ?: "unknown"}"
        } catch (e: SecurityException) {
            error = describe(e)
        } catch (e: LinkageError) {
            error = "${e.javaClass.simpleName}: ${e.message ?: "no message"}"
        }
        if (error != null) {
            g = null
            s = null
        }
        getter = g
        setter = s
        lookupError = error
    }

    override fun availability(): PortResult<Unit> =
        if (getter != null && setter != null) PortResult.Ok(Unit) else PortResult.Failed(lookupError ?: "Unavailable.")

    override fun readFine(): PortResult<Int> {
        val method = getter ?: return PortResult.Failed(lookupError ?: "Unavailable.")
        return guarded {
            when (val value = method.invoke(audioManager, AudioManager.STREAM_MUSIC)) {
                is Int -> PortResult.Ok(value)
                else -> PortResult.Failed("$GETTER returned ${value?.javaClass?.name ?: "null"}.")
            }
        }
    }

    override fun stepTo(index: Int): PortResult<Unit> {
        val method = setter ?: return PortResult.Failed(lookupError ?: "Unavailable.")
        return guarded {
            method.invoke(audioManager, AudioManager.STREAM_MUSIC, index, 0)
            PortResult.Ok(Unit)
        }
    }

    private inline fun <T> guarded(block: () -> PortResult<T>): PortResult<T> = try {
        block()
    } catch (e: InvocationTargetException) {
        val cause = e.targetException ?: e
        PortResult.Failed("Method threw ${describe(cause)}")
    } catch (e: IllegalAccessException) {
        PortResult.Failed(describe(e))
    } catch (e: SecurityException) {
        PortResult.Failed(describe(e))
    } catch (e: IllegalArgumentException) {
        PortResult.Failed(describe(e))
    } catch (e: RuntimeException) {
        PortResult.Failed(describe(e))
    }

    companion object {
        const val GETTER = "semGetFineVolume"
        const val SETTER = "semSetFineVolume"
    }
}

private fun describe(t: Throwable): String = "${t.javaClass.simpleName}: ${t.message ?: "no message"}"

/** Raw AudioDeviceInfo type values, so no constant needs a newer API level. */
fun deviceTypeName(type: Int): String = when (type) {
    AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Built-in earpiece"
    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Built-in speaker"
    AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired headset"
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired headphones"
    AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth SCO"
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth A2DP"
    AudioDeviceInfo.TYPE_HDMI -> "HDMI"
    AudioDeviceInfo.TYPE_USB_DEVICE -> "USB device"
    AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB accessory"
    AudioDeviceInfo.TYPE_USB_HEADSET -> "USB headset"
    23 -> "Hearing aid"
    24 -> "Built-in speaker (safe)"
    26 -> "Bluetooth LE headset"
    27 -> "Bluetooth LE speaker"
    30 -> "Bluetooth LE broadcast"
    else -> "Output type $type"
}
