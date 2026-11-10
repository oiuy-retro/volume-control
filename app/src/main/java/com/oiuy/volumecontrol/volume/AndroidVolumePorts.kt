package com.oiuy.volumecontrol.volume

import android.content.Context
import android.media.AudioManager
import android.os.Build
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

fun isSamsungManufacturer(manufacturer: String?): Boolean =
    manufacturer?.trim().equals("samsung", ignoreCase = true)

/** Samsung fine volume when available, otherwise Android's public steps. */
fun createVolumeEngine(context: Context): VolumeEngine {
    val app = context.applicationContext
    return VolumeEngine(
        isSamsung = isSamsungManufacturer(Build.MANUFACTURER),
        stream = AndroidStreamPort(app),
        fineFactory = { ReflectiveSamsungFineVolumePort(app) },
    )
}

class AndroidStreamPort(context: Context) : StreamPort {
    private val audioManager =
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    override fun max(): Int = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

    override fun index(): Int = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

    override fun setIndex(index: Int): PortResult<Unit> = try {
        if (index != index()) {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, index, 0)
        }
        PortResult.Ok(Unit)
    } catch (e: RuntimeException) {
        PortResult.Failed(describe(e))
    }

    override fun showUi() {
        try {
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_SAME,
                AudioManager.FLAG_SHOW_UI,
            )
        } catch (_: RuntimeException) {
            // Panel is cosmetic; ignore.
        }
    }
}

/** Public one-step adjust calls only (never a fine-mode setStreamVolume). */
class AndroidPlainVolumeKeyPort(context: Context) : PlainVolumeKeyPort {
    private val audioManager =
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    override fun step(direction: Int, stream: PlainStream): PortResult<Unit> {
        val adjust = when {
            direction > 0 -> AudioManager.ADJUST_RAISE
            direction < 0 -> AudioManager.ADJUST_LOWER
            else -> return PortResult.Ok(Unit)
        }
        return try {
            when (stream) {
                PlainStream.MEDIA -> audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, adjust, 0)
                PlainStream.SYSTEM_DEFAULT ->
                    audioManager.adjustSuggestedStreamVolume(adjust, AudioManager.USE_DEFAULT_STREAM_TYPE, 0)
            }
            PortResult.Ok(Unit)
        } catch (e: RuntimeException) {
            PortResult.Failed(describe(e))
        }
    }
}

/**
 * Looks up only two Samsung AudioManager methods with ordinary public
 * [Class.getMethod]: `semGetFineVolume(int)` and `semSetFineVolume(int, int, int)`.
 * No accessibility overrides, no other members, no Binder calls, no hidden-API
 * exemptions. Construct only on Samsung devices.
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

    override fun stepTo(index: Int, flags: Int): PortResult<Unit> {
        val method = setter ?: return PortResult.Failed(lookupError ?: "Unavailable.")
        return guarded {
            method.invoke(audioManager, AudioManager.STREAM_MUSIC, index, flags)
            PortResult.Ok(Unit)
        }
    }

    private inline fun <T> guarded(block: () -> PortResult<T>): PortResult<T> = try {
        block()
    } catch (e: InvocationTargetException) {
        PortResult.Failed("Method threw ${describe(e.targetException ?: e)}")
    } catch (e: IllegalAccessException) {
        PortResult.Failed(describe(e))
    } catch (e: RuntimeException) {
        // Includes SecurityException and IllegalArgumentException.
        PortResult.Failed(describe(e))
    }

    companion object {
        const val GETTER = "semGetFineVolume"
        const val SETTER = "semSetFineVolume"
    }
}

private fun describe(t: Throwable): String = "${t.javaClass.simpleName}: ${t.message ?: "no message"}"
