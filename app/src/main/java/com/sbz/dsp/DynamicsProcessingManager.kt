package com.sbz.dsp

import android.media.audiofx.AudioEffect
import android.media.audiofx.DynamicsProcessing
import android.util.Log
import com.sbz.dsp.model.DspConfig
import com.sbz.dsp.model.MdrcBandConfig
import kotlin.math.*

/**
 * Gestor robusto para el efecto nativo DynamicsProcessing de Android en sBz.
 * Administra la cadena DSP completa: Pre-EQ, Ecualizador de 32 bandas, MDRC,
 * Limitador, AutoGain y sincronización de parámetros en tiempo real.
 */
class DynamicsProcessingManager(
    val audioSessionId: Int,
    private val onControlLostListener: (() -> Unit)? = null
) {
    companion object {
        private const val TAG = "DynamicsProcManager"
        private const val PRIORITY = 100 // Alta prioridad para retener el control del efecto

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

    private fun initializeEffect() {
        release()

        val bandAttempts = intArrayOf(TARGET_EQ_BANDS, FALLBACK_EQ_BANDS_16, FALLBACK_EQ_BANDS_8)
        for (eqBands in bandAttempts) {
            try {
                val config = buildDynamicsConfig(eqBands)
                val effect = DynamicsProcessing(PRIORITY, audioSessionId, config)
                effect.setControlStatusListener { _, controlGranted ->
                    hasControl = controlGranted
                    Log.d(TAG, "Estado de control en AudioSession $audioSessionId cambiado: $controlGranted")
                    if (!controlGranted) {
                        onControlLostListener?.invoke()
                    }
                }
                dp = effect
                supportedEqBands = eqBands
                hasControl = effect.hasControl()
                Log.i(TAG, "DynamicsProcessing inicializado con éxito para sesión $audioSessionId con $eqBands bandas EQ")
                return
            } catch (e: Exception) {
                Log.w(TAG, "Fallo al inicializar DynamicsProcessing con $eqBands bandas: ${e.message}")
            }
        }

        try {
            val fallbackEffect = DynamicsProcessing(audioSessionId)
            dp = fallbackEffect
            supportedEqBands = 8
            hasControl = fallbackEffect.hasControl()
            Log.i(TAG, "DynamicsProcessing inicializado con configuración de respaldo por defecto")
        } catch (e: Exception) {
            Log.e(TAG, "FATAL: No se pudo inicializar DynamicsProcessing: ${e.message}", e)
            dp = null
        }
    }

    private fun buildDynamicsConfig(eqBands: Int): DynamicsProcessing.Config {
        val builder = DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
            2,     // Canales estéreo
            true,  // PreEQ activo
            eqBands,
            true,  // MBC activo
            MBC_BANDS,
            true,  // PostEQ activo
            eqBands,
            true   // Limitador activo
        )

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
                2.0f,
                -70f,
                1.0f,
                0.0f,
                bandDef.makeupGainDb
            )
            mbc.setBand(i, mbcBand)
        }

        val limiter = DynamicsProcessing.Limiter(
            true,
            true,
            0,
            1.0f,
            50.0f,
            20.0f,
            -0.5f,
            0.0f
        )

        builder.setPreferredFrameDuration(10.0f)
        val baseConfig = builder.build()

        for (ch in 0 until 2) {
            baseConfig.setPreEqByChannelIndex(ch, preEq)
            baseConfig.setMbcByChannelIndex(ch, mbc)
            baseConfig.setPostEqByChannelIndex(ch, postEq)
            baseConfig.setLimiterByChannelIndex(ch, limiter)
        }

        return baseConfig
    }

    @Synchronized
    fun applyConfig(config: DspConfig) {
        val effect = dp ?: run {
            initializeEffect()
            dp ?: return
        }

        try {
            if (effect.enabled != config.isEnabled) {
                effect.enabled = config.isEnabled
                isEffectEnabled = config.isEnabled
            }

            if (!config.isEnabled) return

            val bal = config.balance.coerceIn(-1.0f, 1.0f)
            val leftGainFactor = if (bal <= 0f) 1.0f else (1.0f - bal)
            val rightGainFactor = if (bal >= 0f) 1.0f else (1.0f + bal)

            val leftBalanceDb = if (leftGainFactor > 0.001f) 20.0f * log10(leftGainFactor) else -60.0f
            val rightBalanceDb = if (rightGainFactor > 0.001f) 20.0f * log10(rightGainFactor) else -60.0f

            val safeguardHeadroomDb = config.computeHeadroomSafeguard()

            val totalLeftInputGain = (config.preGainDb + leftBalanceDb + safeguardHeadroomDb).coerceIn(-60.0f, 24.0f)
            val totalRightInputGain = (config.preGainDb + rightBalanceDb + safeguardHeadroomDb).coerceIn(-60.0f, 24.0f)

            effect.setInputGainbyChannel(0, totalLeftInputGain)
            effect.setInputGainbyChannel(1, totalRightInputGain)

            // Aplicación escalonada de la cadena DSP
            applyToneAndPreEq(effect, config)
            apply32BandEq(effect, config)
            applyMdrc(effect, config)
            applyLimiterAndMasterGain(effect, config)

        } catch (e: Exception) {
            Log.e(TAG, "Error aplicando configuración DSP en sesión $audioSessionId: ${e.message}", e)
        }
    }

    private fun applyToneAndPreEq(effect: DynamicsProcessing, config: DspConfig) {
        val bands = supportedEqBands
        val freqs = if (bands == TARGET_EQ_BANDS) DspConfig.FREQUENCIES else downsampleFrequencies(DspConfig.FREQUENCIES, bands)

        for (i in 0 until bands) {
            val freq = freqs[i]
            var toneOffsetDb = 0f

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

            val bassBoostDb = if (config.bassBoostEnabled && freq <= 250f) {
                val strength = (config.bassBoostStrength.toFloat() / 1000f).coerceIn(0f, 1f)
                val shape = (1f - (freq / 250f)).coerceIn(0f, 1f)
                strength * 6.0f * shape
            } else {
                0f
            }

            val preGain = (toneOffsetDb + bassBoostDb).coerceIn(-15.0f, 15.0f)
            val preEqBand = DynamicsProcessing.EqBand(true, freq, preGain)
            effect.setPreEqBandByChannelIndex(0, i, preEqBand)
            effect.setPreEqBandByChannelIndex(1, i, preEqBand)
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
            val requestedGain = mappedGains.getOrElse(i) { 0f }
            val freq = freqs[i]

            // CORRECCIÓN CLAVE: Entrega del 100% de ganancia en Post-EQ para todo el espectro,
            // permitiendo que las frecuencias graves (25Hz - 200Hz) respondan con potencia real.
            val gain = requestedGain

            val eqBand = DynamicsProcessing.EqBand(true, freq, gain.coerceIn(-15.0f, 15.0f))
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
                2.0f,
                -70f,
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
        val agcGainDb = if (config.autoGainEnabled) {
            val avgBoost = config.eqGains.average().toFloat()
            val correction = (-avgBoost * 0.5f).coerceIn(-6.0f, 6.0f)
            correction
        } else {
            0.0f
        }

        val combinedMasterPostGain = (config.masterGainDb + agcGainDb + config.limiterPostGainDb).coerceIn(-30.0f, 12.0f)

        val limiter = DynamicsProcessing.Limiter(
            true,
            config.limiterEnabled,
            0,
            config.limiterAttackMs.coerceIn(0.1f, 20f),
            config.limiterReleaseMs.coerceIn(5f, 1000f),
            config.limiterRatio.coerceIn(5f, 100f),
            config.limiterThresholdDb.coerceIn(-24f, 0f),
            combinedMasterPostGain
        )

        effect.setLimiterByChannelIndex(0, limiter)
        effect.setLimiterByChannelIndex(1, limiter)
    }

    fun reclaimControl() {
        try {
            dp?.let { effect ->
                if (!effect.hasControl()) {
                    effect.enabled = isEffectEnabled
                    hasControl = effect.hasControl()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Fallo al reclamar control en sesión $audioSessionId: ${e.message}")
        }
    }

    fun isAvailable(): Boolean = dp != null

    fun release() {
        try {
            dp?.enabled = false
            dp?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error liberando DynamicsProcessing: ${e.message}")
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
