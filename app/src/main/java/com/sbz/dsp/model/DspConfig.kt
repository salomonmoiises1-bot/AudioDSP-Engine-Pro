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
     * MDRC profiles used by factory presets.
     * Loading a preset therefore changes the actual multiband compressor state.
     */
    fun mdrcProfile(profile: String): List<MdrcBandConfig> {
        val base = defaultMdrcBands()
        fun b(i: Int, cutoff: Float, threshold: Float, ratio: Float,
              attack: Float, release: Float, makeup: Float) =
            base[i].copy(
                cutoffFrequencyHz = cutoff,
                thresholdDb = threshold,
                ratio = ratio,
                attackMs = attack,
                releaseMs = release,
                makeupGainDb = makeup
            )

        return when (profile.lowercase()) {
            "bass" -> listOf(
                b(0, 140f, -20f, 3.0f, 20f, 150f, 1.5f),
                b(1, 700f, -18f, 2.5f, 14f, 100f, 1.0f),
                b(2, 4200f, -14f, 2.0f, 7f, 70f, 0.5f),
                b(3, 20000f, -12f, 1.8f, 3f, 45f, 0.0f)
            )
            "punch" -> listOf(
                b(0, 180f, -16f, 3.5f, 8f, 90f, 1.0f),
                b(1, 900f, -15f, 2.8f, 6f, 70f, 0.8f),
                b(2, 4500f, -13f, 2.4f, 4f, 55f, 0.5f),
                b(3, 20000f, -11f, 2.0f, 2f, 40f, 0.0f)
            )
            "bright" -> listOf(
                b(0, 180f, -18f, 2.0f, 18f, 130f, 0.5f),
                b(1, 850f, -17f, 2.0f, 12f, 90f, 0.5f),
                b(2, 5000f, -16f, 2.8f, 4f, 55f, 1.0f),
                b(3, 18000f, -14f, 2.5f, 2f, 35f, 0.5f)
            )
            "speech" -> listOf(
                b(0, 160f, -24f, 2.0f, 25f, 180f, 0.0f),
                b(1, 700f, -20f, 2.2f, 15f, 120f, 0.5f),
                b(2, 3500f, -16f, 3.0f, 5f, 70f, 1.0f),
                b(3, 20000f, -18f, 2.0f, 2f, 50f, 0.0f)
            )
            "wide" -> listOf(
                b(0, 160f, -19f, 2.2f, 15f, 120f, 0.5f),
                b(1, 800f, -17f, 2.0f, 10f, 85f, 0.5f),
                b(2, 4000f, -15f, 2.2f, 5f, 60f, 0.5f),
                b(3, 20000f, -13f, 2.0f, 2f, 40f, 0.0f)
            )
            else -> base
        }
    }

    internal fun mdrcProfileForPresetForFactory(id: String): List<MdrcBandConfig> =
        when {
            id.contains("deep_bass") || id.contains("bass_punch") ||
                id.contains("hiphop") || id.contains("electronic") ||
                id.contains("edm") || id.contains("reggae") -> mdrcProfile("bass")
            id.contains("party") || id.contains("concert") -> mdrcProfile("punch")
            id.contains("bright") || id.contains("acoustic") ||
                id.contains("country") -> mdrcProfile("bright")
            id.contains("speech") || id.contains("podcast") ||
                id.contains("radio") || id.contains("vocal") -> mdrcProfile("speech")
            id.contains("wide") || id.contains("live") ||
                id.contains("gaming") -> mdrcProfile("wide")
            else -> defaultMdrcBands()
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
    val makeupGainDb: Float, // 0 to 18 dB
    val kneeDb: Float = 2.0f // 0 to 24 dB
) : Serializable
