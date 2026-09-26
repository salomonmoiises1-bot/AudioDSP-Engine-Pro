package com.sbz.dsp

import android.media.audiofx.EnvironmentalReverb
import android.util.Log
import com.sbz.dsp.model.DspConfig

/**
 * Native Android environmental reverb controller.
 *
 * This is a real AudioEffect. It does not synthesize or fake reverb in the UI.
 * The effect is created for the same audio session handled by sBz. Session 0
 * can target the global output mix on devices that expose the effect there.
 */
class HallReverbManager(private val audioSessionId: Int) {

    companion object {
        private const val TAG = "HallReverbManager"
        private const val PRIORITY = 100
    }

    private var effect: EnvironmentalReverb? = null

    init {
        try {
            effect = EnvironmentalReverb(PRIORITY, audioSessionId)
            Log.i(TAG, "EnvironmentalReverb created for session $audioSessionId")
        } catch (t: Throwable) {
            Log.w(TAG, "EnvironmentalReverb unavailable for session $audioSessionId", t)
            effect = null
        }
    }

    fun isAvailable(): Boolean = effect != null

    fun hasControl(): Boolean = try {
        effect?.hasControl() == true
    } catch (_: Throwable) {
        false
    }

    fun apply(config: DspConfig) {
        val reverb = effect ?: return

        try {
            val enabled = config.isEnabled && config.hallEnabled
            if (!enabled) {
                reverb.enabled = false
                return
            }

            val settings = EnvironmentalReverb.Settings().apply {
                decayTime = config.hallDecayTimeMs.coerceIn(100f, 20_000f).toInt()
                decayHFRatio = (config.hallDecayHfRatio.coerceIn(0.1f, 2.0f) * 1000f).toInt().toShort()
                density = (config.hallDensityPercent.coerceIn(0f, 100f) * 10f).toInt().toShort()
                diffusion = (config.hallDiffusionPercent.coerceIn(0f, 100f) * 10f).toInt().toShort()
                reflectionsDelay = config.hallReflectionsDelayMs.coerceIn(0f, 300f).toInt()
                reflectionsLevel = dbToMb(config.hallReflectionsLevelDb, -9000, 1000)
                reverbDelay = config.hallReverbDelayMs.coerceIn(0f, 100f).toInt()
                reverbLevel = mixToReverbMb(config.hallMixPercent)
                roomHFLevel = dbToMb(config.hallRoomHfLevelDb, -9000, 0)
                roomLevel = dbToMb(config.hallRoomLevelDb, -9000, 0)
            }

            reverb.setProperties(settings)
            reverb.enabled = true

            val readBack = reverb.properties
            Log.d(
                TAG,
                "Hall applied session=$audioSessionId " +
                    "decay=${readBack.decayTime}ms " +
                    "mix=${readBack.reverbLevel}mB " +
                    "density=${readBack.density} diffusion=${readBack.diffusion}"
            )
        } catch (t: Throwable) {
            Log.w(TAG, "Failed applying EnvironmentalReverb to session $audioSessionId", t)
        }
    }

    fun reclaimControl() {
        try {
            effect?.let {
                if (!it.hasControl()) {
                    Log.d(TAG, "Reverb control not owned on session $audioSessionId")
                }
            }
        } catch (_: Throwable) {
        }
    }

    fun release() {
        try {
            effect?.release()
        } catch (_: Throwable) {
        } finally {
            effect = null
        }
    }

    private fun dbToMb(db: Float, min: Int, max: Int): Short {
        return (db.coerceIn(min / 100f, max / 100f) * 100f)
            .toInt()
            .coerceIn(min, max)
            .toShort()
    }

    /** Maps a 0..100% UI mix to Android's late-reverb level range [-9000, 0] mB. */
    private fun mixToReverbMb(percent: Float): Short {
        val p = percent.coerceIn(0f, 100f) / 100f
        return (-9000f + p * 9000f).toInt().toShort()
    }
}
