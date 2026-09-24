package com.sbz.dsp

import android.media.audiofx.AudioEffect
import android.media.audiofx.DynamicsProcessing
import android.util.Log
import com.sbz.dsp.model.DspConfig
import com.sbz.dsp.model.MdrcBandConfig
import kotlin.math.*

/**
 * Robust manager for Android's native DynamicsProcessing AudioEffect.
 * Manages creation, capability adaptation, stereo channels, Pre-EQ, MBC, Post-EQ,
 * Limiter, real-time parameter synchronization, and control loss/reclaim.
 */
class DynamicsProcessingManager(
    val audioSessionId: Int,
    private val onControlLostListener: (() -> Unit)? = null
) {
    companion object {
        private const val TAG = "DynamicsProcManager"
        private const val PRIORITY = 100 // High priority to hold effect control

        // Fallback band capabilities for devices with limited DSP memory
        private const val TARGET_EQ_BANDS = 32
        private const val FALLBACK_EQ_BANDS_16 = 16
        private const val FALLBACK_EQ_BANDS_8 = 8
        private const val MBC_BANDS = 4
    }

    private var dp: DynamicsProcessing? = null
    private var isEffectEnabled = false
    private var supportedEqBands = TARGET_EQ_BANDS
    private var hasControl = false

    init {
        initializeEffect()
    }

    /**
     * Attempts to create DynamicsProcessing with optimal configuration,
     * falling back gracefully if device vendor HAL has lower band limits.
     */
    private fun initializeEffect() {
        release()

        val bandAttempts = intArrayOf(TARGET_EQ_BANDS, FALLBACK_EQ_BANDS_16, FALLBACK_EQ_BANDS_8)
        for (eqBands in bandAttempts) {
            try {
                val config = buildDynamicsConfig(eqBands)
                val effect = DynamicsProcessing(PRIORITY, audioSessionId, config)
                effect.setControlStatusListener { _, controlGranted ->
                    hasControl = controlGranted
                    Log.d(TAG, "AudioSession $audioSessionId control status changed: $controlGranted")
                    if (!controlGranted) {
                        onControlLostListener?.invoke()
                    }
                }
                dp = effect
                supportedEqBands = eqBands
                hasControl = effect.hasControl()
                Log.i(TAG, "Successfully initialized DynamicsProcessing for session $audioSessionId with $eqBands EQ bands")
                return
            } catch (e: Exception) {
                Log.w(TAG, "Failed initializing DynamicsProcessing with $eqBands bands for session $audioSessionId: ${e.message}")
            }
        }

        // Final fallback: default single-parameter constructor
        try {
            val fallbackEffect = DynamicsProcessing(audioSessionId)
            dp = fallbackEffect
            supportedEqBands = 8
            hasControl = fallbackEffect.hasControl()
            Log.i(TAG, "Initialized default fallback DynamicsProcessing for session $audioSessionId")
        } catch (e: Exception) {
            Log.e(TAG, "FATAL: Could not initialize DynamicsProcessing for session $audioSessionId: ${e.message}", e)
            dp = null
        }
    }

    /**
     * Builds standard 2-channel (stereo) DynamicsProcessing configuration.
     */
    private fun buildDynamicsConfig(eqBands: Int): DynamicsProcessing.Config {
        val builder = DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
            2,     // 2 stereo channels
            true,  // PreEQ in use
            eqBands,
            true,  // MBC in use
            MBC_BANDS,
            true,  // PostEQ in use
            eqBands,
            true   // Limiter in use
        )

        // Set default frequency points for preEQ and postEQ
        val freqs = if (eqBands == TARGET_EQ_BANDS) {
            DspConfig.FREQUENCIES
        } else {
            downsampleFrequencies(DspConfig.FREQUENCIES, eqBands)
        }

        val preEq = DynamicsProcessing.Eq(true, true, eqBands)
        val postEq = DynamicsProcessing.Eq(true, true, eqBands)

        for (i in 0 until eqBands) {
            val cutoff = freqs.getOrElse(i) { 1000f }
            val eqBand = DynamicsProcessing.EqBand(true, cutoff, 0.0f)
            preEq.setBand(i, eqBand)
            postEq.setBand(i, eqBand)
        }

        // Default MBC bands
        val mbc = DynamicsProcessing.Mbc(true, true, MBC_BANDS)
        val defaultBands = DspConfig.defaultMdrcBands()
        for (i in 0 until MBC_BANDS) {
            val bandDef = defaultBands[i]
            val mbcBand = DynamicsProcessing.MbcBand(
                true,
                bandDef.cutoffFrequencyHz,
                bandDef.attackMs,
                bandDef.releaseMs,
                bandDef.ratio,
                bandDef.thresholdDb,
                0.0f, // knee width
                0.0f, // noise gate
                0.0f, // expander ratio
                0.0f, // preGain
                bandDef.makeupGainDb
            )
            mbc.setBand(i, mbcBand)
        }

        // Default Limiter
        val limiter = DynamicsProcessing.Limiter(
            true,
            true,
            0,     // linkGroup 0
            1.0f,  // attack
            50.0f, // release
            20.0f, // ratio
            -0.5f, // threshold
            0.0f   // postGain
        )

        builder.setPreferredFrameDuration(10.0f)
        val baseConfig = builder.build()

        // Set per-channel stages
        for (ch in 0 until 2) {
            baseConfig.setPreEqByChannelIndex(ch, preEq)
            baseConfig.setMbcByChannelIndex(ch, mbc)
            baseConfig.setPostEqByChannelIndex(ch, postEq)
            baseConfig.setLimiterByChannelIndex(ch, limiter)
        }

        return baseConfig
    }

    /**
     * Apply the entire DspConfig state in real time to the hardware DSP.
     */
    @Synchronized
    fun applyConfig(config: DspConfig) {
        val effect = dp ?: run {
            initializeEffect()
            dp ?: return
        }

        try {
            // Master toggle
            if (effect.enabled != config.isEnabled) {
                effect.enabled = config.isEnabled
                isEffectEnabled = config.isEnabled
            }

            if (!config.isEnabled) return

            // Compute Balance factors
            // balance: -1.0 (L only) to +1.0 (R only)
            val bal = config.balance.coerceIn(-1.0f, 1.0f)
            val leftGainFactor = if (bal <= 0f) 1.0f else (1.0f - bal)
            val rightGainFactor = if (bal >= 0f) 1.0f else (1.0f + bal)

            val leftBalanceDb = if (leftGainFactor > 0.001f) 20.0f * log10(leftGainFactor) else -60.0f
            val rightBalanceDb = if (rightGainFactor > 0.001f) 20.0f * log10(rightGainFactor) else -60.0f

            // Safeguard headroom calculation
            val safeguardHeadroomDb = config.computeHeadroomSafeguard()

            // 1. Pre-Gain & Balance
            val totalLeftInputGain = (config.preGainDb + leftBalanceDb + safeguardHeadroomDb).coerceIn(-60.0f, 24.0f)
            val totalRightInputGain = (config.preGainDb + rightBalanceDb + safeguardHeadroomDb).coerceIn(-60.0f, 24.0f)

            effect.setInputGainbyChannel(0, totalLeftInputGain)
            effect.setInputGainbyChannel(1, totalRightInputGain)

            // 2. Pre-EQ: Combined Tone (Bass, Mid, Treble) + Lower/Higher Spectrum Adjustment
            applyToneAndPreEq(effect, config)

            // 3. Post-EQ: 32-Band Equalizer
            apply32BandEq(effect, config)

            // 4. MDRC (Multiband Compressor)
            applyMdrc(effect, config)

            // 5. Limiter & Protection (with Master Gain)
            applyLimiterAndMasterGain(effect, config)

        } catch (e: Exception) {
            Log.e(TAG, "Error applying DSP config on session $audioSessionId: ${e.message}", e)
        }
    }

    private fun applyToneAndPreEq(effect: DynamicsProcessing, config: DspConfig) {
        val bands = supportedEqBands
        val freqs = if (bands == TARGET_EQ_BANDS) DspConfig.FREQUENCIES else downsampleFrequencies(DspConfig.FREQUENCIES, bands)

        for (i in 0 until bands) {
            val freq = freqs[i]
            var toneOffsetDb = 0f

            // Map Tone controls: Bass (<250Hz), Mid (250Hz-4000Hz), Treble (>4000Hz)
            if (freq <= 250f) {
                val factor = (1f - (freq / 250f)).coerceIn(0f, 1f)
                toneOffsetDb += config.toneBassDb * factor
            } else if (freq in 250f..4000f) {
                val midCenter = 1000f
                val octaves = abs(log2(freq / midCenter))
                val factor = (1f - (octaves / 2f)).coerceIn(0f, 1f)
                toneOffsetDb += config.toneMidDb * factor
            } else if (freq >= 4000f) {
                val factor = ((freq - 4000f) / 16000f).coerceIn(0f, 1f)
                toneOffsetDb += config.toneTrebleDb * factor
            }

            val gain = toneOffsetDb.coerceIn(-15.0f, 15.0f)

            val eqBand = DynamicsProcessing.EqBand(true, freq, gain)
            effect.setPreEqBandByChannelIndex(0, i, eqBand)
            effect.setPreEqBandByChannelIndex(1, i, eqBand)
        }
    }

    private fun apply32BandEq(effect: DynamicsProcessing, config: DspConfig) {
        val bands = supportedEqBands
        val mappedGains = if (bands == TARGET_EQ_BANDS) {
            config.eqGains
        } else {
            downsampleGains(config.eqGains, bands)
        }
        val freqs = if (bands == TARGET_EQ_BANDS) DspConfig.FREQUENCIES else downsampleFrequencies(DspConfig.FREQUENCIES, bands)

        for (i in 0 until bands) {
            val gain = mappedGains.getOrElse(i) { 0f }.coerceIn(-15.0f, 15.0f)
            val freq = freqs[i]
            val eqBand = DynamicsProcessing.EqBand(true, freq, gain)
            effect.setPostEqBandByChannelIndex(0, i, eqBand)
            effect.setPostEqBandByChannelIndex(1, i, eqBand)
        }
    }

    private fun applyMdrc(effect: DynamicsProcessing, config: DspConfig) {
        val enabled = config.mdrcEnabled
        val mbcStage = DynamicsProcessing.Mbc(true, enabled, MBC_BANDS)

        for (i in 0 until min(MBC_BANDS, config.mdrcBands.size)) {
            val band = config.mdrcBands[i]
            val mbcBand = DynamicsProcessing.MbcBand(
                enabled,
                band.cutoffFrequencyHz.coerceIn(20f, 22000f),
                band.attackMs.coerceIn(0.1f, 200f),
                band.releaseMs.coerceIn(10f, 2000f),
                band.ratio.coerceIn(1.0f, 30f),
                band.thresholdDb.coerceIn(-60f, 0f),
                2.0f, // smooth 2dB knee
                -70f, // noise gate
                1.0f,
                0.0f,
                band.makeupGainDb.coerceIn(0f, 18f)
            )
            mbcStage.setBand(i, mbcBand)
        }

        effect.setMbcByChannelIndex(0, mbcStage)
        effect.setMbcByChannelIndex(1, mbcStage)
    }

    private fun applyLimiterAndMasterGain(effect: DynamicsProcessing, config: DspConfig) {
        // AutoGain adjustment factor
        val agcGainDb = if (config.autoGainEnabled) {
            // AutoGain compensator based on target loudness vs current EQ sum
            val avgBoost = config.eqGains.average().toFloat()
            val correction = (-avgBoost * 0.5f).coerceIn(-6.0f, 6.0f)
            correction
        } else {
            0.0f
        }

        // Master gain combined with limiter post-gain
        val combinedMasterPostGain = (config.masterGainDb + agcGainDb + config.limiterPostGainDb).coerceIn(-30.0f, 12.0f)

        val limiter = DynamicsProcessing.Limiter(
            true,
            config.limiterEnabled,
            0, // link group
            config.limiterAttackMs.coerceIn(0.1f, 20f),
            config.limiterReleaseMs.coerceIn(5f, 1000f),
            config.limiterRatio.coerceIn(5f, 100f),
            config.limiterThresholdDb.coerceIn(-24f, 0f),
            combinedMasterPostGain
        )

        effect.setLimiterByChannelIndex(0, limiter)
        effect.setLimiterByChannelIndex(1, limiter)
    }

    /**
     * Reclaim control over the audio effect if stolen by another app or system event.
     */
    fun reclaimControl() {
        try {
            dp?.let { effect ->
                if (!effect.hasControl()) {
                    Log.d(TAG, "Reclaiming DynamicsProcessing control for session $audioSessionId...")
                    effect.enabled = isEffectEnabled
                    hasControl = effect.hasControl()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed reclaiming control on session $audioSessionId: ${e.message}")
        }
    }

    fun isAvailable(): Boolean = dp != null

    fun release() {
        try {
            dp?.enabled = false
            dp?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing DynamicsProcessing on session $audioSessionId: ${e.message}")
        } finally {
            dp = null
            isEffectEnabled = false
            hasControl = false
        }
    }

    private fun downsampleFrequencies(src: FloatArray, count: Int): FloatArray {
        if (src.size == count) return src
        val result = FloatArray(count)
        val step = (src.size - 1).toFloat() / (count - 1).toFloat()
        for (i in 0 until count) {
            val idx = (i * step).roundToInt().coerceIn(0, src.size - 1)
            result[i] = src[idx]
        }
        return result
    }

    private fun downsampleGains(src: List<Float>, count: Int): List<Float> {
        if (src.size == count) return src
        val result = mutableListOf<Float>()
        val step = (src.size - 1).toFloat() / (count - 1).toFloat()
        for (i in 0 until count) {
            val idx = (i * step).roundToInt().coerceIn(0, src.size - 1)
            result.add(src[idx])
        }
        return result
    }
}
