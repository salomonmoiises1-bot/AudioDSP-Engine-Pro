package com.sbz.dsp.model

import java.io.Serializable

/**
 * Complete immutable configuration state for the sBz DSP Pipeline.
 * Covers Pre-Gain, Bass Boost, Tone, 32-Band EQ, MDRC, AGC, Spatial Virtualizer,
 * Master Gain, Balance, and Output Limiter Protection.
 */
data class DspConfig(
    val isEnabled: Boolean = true,

    // Pre-Gain Stage (-12.0 dB to +12.0 dB)
    val preGainDb: Float = 0.0f,

    // Bass Boost Stage (0 to 1000 mB)
    val bassBoostEnabled: Boolean = false,
    val bassBoostStrength: Short = 0, // 0 to 1000

    // Tone Control Stage (-12.0 dB to +12.0 dB)
    val toneBassDb: Float = 0.0f,
    val toneMidDb: Float = 0.0f,
    val toneTrebleDb: Float = 0.0f,

    // 32-Band Graphic Equalizer (-15.0 dB to +15.0 dB, 0.5 dB steps)
    val eqGains: List<Float> = List(32) { 0.0f },

    // Multiband Dynamic Range Compression (MDRC)
    val mdrcEnabled: Boolean = true,
    val mdrcBands: List<MdrcBandConfig> = defaultMdrcBands(),

    // AutoGain / AGC Control
    val autoGainEnabled: Boolean = true,
    val autoGainTargetDb: Float = -14.0f, // Loudness target LUFS / RMS approx

    // Spatial / Virtualizer Stage
    val virtualizerEnabled: Boolean = false,
    val virtualizerStrength: Short = 0, // 0 to 1000

    // Master Output & Stereo Balance
    val masterGainDb: Float = 0.0f, // -24.0 dB to +12.0 dB
    val balance: Float = 0.0f,      // -1.0 (Full Left) to +1.0 (Full Right)

    // Output Limiter & Digital Protection
    val limiterEnabled: Boolean = true,
    val limiterThresholdDb: Float = -0.5f,  // -12.0 to 0.0 dB
    val limiterAttackMs: Float = 1.0f,      // 0.1 to 10.0 ms
    val limiterReleaseMs: Float = 50.0f,    // 10.0 to 500.0 ms
    val limiterRatio: Float = 20.0f,        // Brickwall ratio
    val limiterPostGainDb: Float = 0.0f
) : Serializable {

    companion object {
        /**
         * 32 ISO Standardized Center Frequencies (1/3 Octave Standard) in Hz.
         */
        val FREQUENCIES = floatArrayOf(
            20f, 25f, 31.5f, 40f, 50f, 63f, 80f, 100f, 125f, 160f,
            200f, 250f, 315f, 400f, 500f, 630f, 800f, 1000f, 1250f, 1600f,
            2000f, 2500f, 3150f, 4000f, 5000f, 6300f, 8000f, 10000f, 12500f, 16000f,
            18000f, 20000f
        )

        fun defaultMdrcBands(): List<MdrcBandConfig> = listOf(
            MdrcBandConfig(
                name = "Low Sub",
                cutoffFrequencyHz = 160f,
                thresholdDb = -18f,
                ratio = 2.5f,
                attackMs = 15f,
                releaseMs = 120f,
                makeupGainDb = 1.0f
            ),
            MdrcBandConfig(
                name = "Low Mid",
                cutoffFrequencyHz = 800f,
                thresholdDb = -16f,
                ratio = 2.0f,
                attackMs = 10f,
                releaseMs = 80f,
                makeupGainDb = 0.5f
            ),
            MdrcBandConfig(
                name = "High Mid",
                cutoffFrequencyHz = 4000f,
                thresholdDb = -14f,
                ratio = 2.2f,
                attackMs = 5f,
                releaseMs = 60f,
                makeupGainDb = 0.5f
            ),
            MdrcBandConfig(
                name = "High Air",
                cutoffFrequencyHz = 20000f,
                thresholdDb = -12f,
                ratio = 2.0f,
                attackMs = 2f,
                releaseMs = 40f,
                makeupGainDb = 0.0f
            )
        )
    }

    /**
     * Compute safe internal headroom attenuation to prevent digital clipping
     * when high positive EQ, Tone, and Bass Boost are combined.
     */
    fun computeHeadroomSafeguard(): Float {
        val maxEqBoost = eqGains.maxOrNull()?.coerceAtLeast(0f) ?: 0f
        val toneBoost = maxOf(0f, toneBassDb, toneMidDb, toneTrebleDb)
        val bassBoostComp = if (bassBoostEnabled) (bassBoostStrength / 1000f) * 4.0f else 0f
        val totalCumulativeBoost = maxEqBoost + toneBoost + bassBoostComp + preGainDb
        return if (totalCumulativeBoost > 6.0f) {
            -(totalCumulativeBoost - 6.0f) * 0.75f
        } else {
            0.0f
        }
    }
}

/**
 * Multiband Dynamic Range Compressor band specification.
 */
data class MdrcBandConfig(
    val name: String,
    val cutoffFrequencyHz: Float,
    val thresholdDb: Float, // -60 dB to 0 dB
    val ratio: Float,       // 1.0 to 20.0
    val attackMs: Float,    // 0.1 to 100 ms
    val releaseMs: Float,   // 10 to 1000 ms
    val makeupGainDb: Float // 0 to 18 dB
) : Serializable
