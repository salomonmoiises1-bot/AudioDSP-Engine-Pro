package com.sbz.dsp

import android.media.audiofx.AudioEffect
import android.media.audiofx.DynamicsProcessing
import android.util.Log
import com.sbz.dsp.model.DspConfig
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Controls Android DynamicsProcessing for one audio session.
 *
 * Processing order inside DynamicsProcessing:
 *
 * Input Gain
 *     ↓
 * Pre-EQ
 *     ↓
 * MBC / MDRC
 *     ↓
 * Post-EQ
 *     ↓
 * Limiter
 *
 * The graphic EQ is applied once, at full requested gain, in Post-EQ.
 * Tone and Bass Boost are kept separate in Pre-EQ.
 *
 * MDRC crossover frequencies come directly from DspConfig.mdrcBands
 * and are applied individually to every channel/band.
 */
class DynamicsProcessingManager(
    val audioSessionId: Int,
    private val onControlLostListener: (() -> Unit)? = null
) {

    companion object {
        private const val TAG = "DynamicsProcManager"

        private const val PRIORITY = 100

        private const val TARGET_EQ_BANDS = 32
        private const val FALLBACK_EQ_BANDS_16 = 16
        private const val FALLBACK_EQ_BANDS_8 = 8

        private const val MBC_BANDS = 4
        private const val CHANNELS = 2

        private const val MIN_MDRC_CUTOFF_HZ = 20.0f
        private const val MAX_MDRC_CUTOFF_HZ = 22000.0f

        /*
         * Android requires MBC cutoff frequencies to be ordered:
         *
         * band 0 <= band 1 <= band 2 <= band 3
         *
         * We enforce a small positive separation so that malformed
         * user input cannot collapse two adjacent bands into one.
         */
        private const val MIN_MDRC_SEPARATION_HZ = 1.0f
    }

    private var dp: DynamicsProcessing? = null

    private var isEffectEnabled = false

    /**
     * Number of EQ bands actually accepted by the current
     * DynamicsProcessing instance.
     */
    private var supportedEqBands = TARGET_EQ_BANDS

    private var hasControl = false

    init {
        initializeEffect()
    }

    /**
     * Returns the actual number of EQ bands accepted by Android
     * for this audio session.
     *
     * This is important because some HAL implementations may reject
     * a 32-band configuration and require a smaller configuration.
     */
    fun getSupportedEqBands(): Int = supportedEqBands

    private fun initializeEffect() {
        release()

        val bandAttempts = intArrayOf(
            TARGET_EQ_BANDS,
            FALLBACK_EQ_BANDS_16,
            FALLBACK_EQ_BANDS_8
        )

        for (eqBands in bandAttempts) {
            try {
                val config = buildDynamicsConfig(eqBands)

                val effect = DynamicsProcessing(
                    PRIORITY,
                    audioSessionId,
                    config
                )

                effect.setControlStatusListener { _, controlGranted ->
                    hasControl = controlGranted

                    Log.d(
                        TAG,
                        "AudioSession $audioSessionId control status changed: $controlGranted"
                    )

                    if (!controlGranted) {
                        onControlLostListener?.invoke()
                    }
                }

                dp = effect
                supportedEqBands = eqBands
                hasControl = effect.hasControl()

                Log.i(
                    TAG,
                    "DynamicsProcessing initialized: " +
                        "session=$audioSessionId, " +
                        "eqBands=$eqBands, " +
                        "control=$hasControl"
                )

                return

            } catch (e: Exception) {
                Log.w(
                    TAG,
                    "Failed initializing DynamicsProcessing with " +
                        "$eqBands EQ bands for session $audioSessionId: " +
                        e.message
                )
            }
        }

        /*
         * Last-resort platform configuration.
         *
         * This does not pretend to provide 32 bands. The actual
         * supportedEqBands value is reported as 8 because this is
         * only a compatibility fallback.
         */
        try {
            val fallbackEffect = DynamicsProcessing(audioSessionId)

            dp = fallbackEffect
            supportedEqBands = 8
            hasControl = fallbackEffect.hasControl()

            Log.i(
                TAG,
                "Initialized default DynamicsProcessing fallback " +
                    "for session $audioSessionId"
            )

        } catch (e: Exception) {
            Log.e(
                TAG,
                "FATAL: Could not initialize DynamicsProcessing " +
                    "for session $audioSessionId: ${e.message}",
                e
            )

            dp = null
            supportedEqBands = 0
        }
    }

    private fun buildDynamicsConfig(
        eqBands: Int
    ): DynamicsProcessing.Config {

        val builder = DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
            CHANNELS,

            true,
            eqBands,

            true,
            MBC_BANDS,

            true,
            eqBands,

            true
        )

        val freqs =
            if (eqBands == TARGET_EQ_BANDS) {
                DspConfig.FREQUENCIES
            } else {
                downsampleFrequencies(
                    DspConfig.FREQUENCIES,
                    eqBands
                )
            }

        val preEq = DynamicsProcessing.Eq(
            true,
            true,
            eqBands
        )

        val postEq = DynamicsProcessing.Eq(
            true,
            true,
            eqBands
        )

        for (i in 0 until eqBands) {
            val frequency = freqs
                .getOrElse(i) { 1000.0f }
                .coerceIn(20.0f, 22000.0f)

            val preBand = DynamicsProcessing.EqBand(
                true,
                frequency,
                0.0f
            )

            val postBand = DynamicsProcessing.EqBand(
                true,
                frequency,
                0.0f
            )

            preEq.setBand(i, preBand)
            postEq.setBand(i, postBand)
        }

        /*
         * MDRC factory configuration is used ONLY when creating
         * the DynamicsProcessing structure.
         *
         * User configuration is NOT overwritten here.
         *
         * Actual user values are applied later by applyMdrc().
         */
        val mbc = DynamicsProcessing.Mbc(
            true,
            true,
            MBC_BANDS
        )

        val defaultBands = DspConfig.defaultMdrcBands()

        val safeDefaultCutoffs = normalizeMdrcCutoffs(
            defaultBands.map {
                it.cutoffFrequencyHz
            }
        )

        for (i in 0 until MBC_BANDS) {
            val bandDef = defaultBands[i]

            val mbcBand = DynamicsProcessing.MbcBand(
                true,

                safeDefaultCutoffs[i],

                bandDef.attackMs.coerceIn(
                    0.1f,
                    200.0f
                ),

                bandDef.releaseMs.coerceIn(
                    10.0f,
                    2000.0f
                ),

                bandDef.ratio.coerceIn(
                    1.0f,
                    30.0f
                ),

                bandDef.thresholdDb.coerceIn(
                    -60.0f,
                    0.0f
                ),

                bandDef.kneeDb.coerceIn(
                    0.0f,
                    24.0f
                ),

                -70.0f,

                1.0f,

                0.0f,

                bandDef.makeupGainDb.coerceIn(
                    0.0f,
                    18.0f
                )
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

        for (channel in 0 until CHANNELS) {
            baseConfig.setPreEqByChannelIndex(
                channel,
                preEq
            )

            baseConfig.setMbcByChannelIndex(
                channel,
                mbc
            )

            baseConfig.setPostEqByChannelIndex(
                channel,
                postEq
            )

            baseConfig.setLimiterByChannelIndex(
                channel,
                limiter
            )
        }

        return baseConfig
    }

    /**
     * Applies the complete DSP configuration.
     */
    @Synchronized
    fun applyConfig(config: DspConfig) {

        var effect = dp

        if (effect == null) {
            initializeEffect()
            effect = dp
        }

        if (effect == null) {
            Log.e(
                TAG,
                "Cannot apply DSP configuration: " +
                    "DynamicsProcessing unavailable for session " +
                    audioSessionId
            )
            return
        }

        try {
            if (effect.enabled != config.isEnabled) {
                effect.enabled = config.isEnabled
                isEffectEnabled = config.isEnabled
            }

            if (!config.isEnabled) {
                return
            }

            if (!effect.hasControl()) {
                hasControl = false

                Log.w(
                    TAG,
                    "No DynamicsProcessing control for session " +
                        audioSessionId
                )

                return
            }

            hasControl = true

            applyInputGain(
                effect,
                config
            )

            applyToneAndPreEq(
                effect,
                config
            )

            apply32BandEq(
                effect,
                config
            )

            /*
             * IMPORTANT:
             *
             * MDRC is applied AFTER EQ configuration.
             * It receives the exact cutoffs stored in DspConfig.
             */
            applyMdrc(
                effect,
                config
            )

            applyLimiterAndMasterGain(
                effect,
                config
            )

        } catch (e: Exception) {
            Log.e(
                TAG,
                "Error applying DSP config on session " +
                    "$audioSessionId: ${e.message}",
                e
            )
        }
    }

    /**
     * Calculates and applies the input gain, including balance
     * and the automatic headroom safeguard.
     */
    private fun applyInputGain(
        effect: DynamicsProcessing,
        config: DspConfig
    ) {

        val balance = config.balance
            .coerceIn(-1.0f, 1.0f)

        val leftGainFactor =
            if (balance <= 0.0f) {
                1.0f
            } else {
                1.0f - balance
            }

        val rightGainFactor =
            if (balance >= 0.0f) {
                1.0f
            } else {
                1.0f + balance
            }

        val leftBalanceDb =
            if (leftGainFactor > 0.001f) {
                20.0f * log10(leftGainFactor)
            } else {
                -60.0f
            }

        val rightBalanceDb =
            if (rightGainFactor > 0.001f) {
                20.0f * log10(rightGainFactor)
            } else {
                -60.0f
            }

        val safeguardHeadroomDb =
            config.computeHeadroomSafeguard()

        val totalLeftInputGain =
            (
                config.preGainDb +
                    leftBalanceDb +
                    safeguardHeadroomDb
                ).coerceIn(
                    -60.0f,
                    24.0f
                )

        val totalRightInputGain =
            (
                config.preGainDb +
                    rightBalanceDb +
                    safeguardHeadroomDb
                ).coerceIn(
                    -60.0f,
                    24.0f
                )

        effect.setInputGainbyChannel(
            0,
            totalLeftInputGain
        )

        effect.setInputGainbyChannel(
            1,
            totalRightInputGain
        )
    }

    /**
     * Applies Tone and Bass Boost to Pre-EQ.
     *
     * Graphic EQ remains separate and is applied later in Post-EQ.
     */
    private fun applyToneAndPreEq(
        effect: DynamicsProcessing,
        config: DspConfig
    ) {

        val bands = supportedEqBands

        if (bands <= 0) {
            return
        }

        val freqs =
            if (bands == TARGET_EQ_BANDS) {
                DspConfig.FREQUENCIES
            } else {
                downsampleFrequencies(
                    DspConfig.FREQUENCIES,
                    bands
                )
            }

        for (i in 0 until bands) {

            val frequency = freqs[i]

            var toneOffsetDb = 0.0f

            when {
                frequency <= 250.0f -> {
                    val factor =
                        (
                            1.0f -
                                (frequency / 250.0f)
                            ).coerceIn(
                                0.0f,
                                1.0f
                            )

                    toneOffsetDb =
                        config.toneBassDb * factor
                }

                frequency <= 4000.0f -> {
                    val octaves =
                        abs(
                            log2(
                                frequency / 1000.0f
                            )
                        )

                    val factor =
                        (
                            1.0f -
                                (octaves / 2.0f)
                            ).coerceIn(
                                0.0f,
                                1.0f
                            )

                    toneOffsetDb =
                        config.toneMidDb * factor
                }

                else -> {
                    val factor =
                        (
                            (frequency - 4000.0f) /
                                16000.0f
                            ).coerceIn(
                                0.0f,
                                1.0f
                            )

                    toneOffsetDb =
                        config.toneTrebleDb * factor
                }
            }

            val bassBoostDb =
                if (
                    config.bassBoostEnabled &&
                    frequency <= 250.0f
                ) {

                    val strength =
                        (
                            config.bassBoostStrength
                                .toFloat() /
                                1000.0f
                            ).coerceIn(
                                0.0f,
                                1.0f
                            )

                    val shape =
                        (
                            1.0f -
                                (frequency / 250.0f)
                            ).coerceIn(
                                0.0f,
                                1.0f
                            )

                    strength * 6.0f * shape

                } else {
                    0.0f
                }

            val gain =
                (
                    toneOffsetDb +
                        bassBoostDb
                    ).coerceIn(
                        -15.0f,
                        15.0f
                    )

            val band =
                DynamicsProcessing.EqBand(
                    true,
                    frequency,
                    gain
                )

            effect.setPreEqBandByChannelIndex(
                0,
                i,
                band
            )

            effect.setPreEqBandByChannelIndex(
                1,
                i,
                band
            )
        }
    }

    /**
     * Applies the graphic EQ exactly once.
     *
     * IMPORTANT:
     *
     * There is no 50/50 split anymore.
     * The complete requested gain goes into Post-EQ.
     */
    private fun apply32BandEq(
        effect: DynamicsProcessing,
        config: DspConfig
    ) {

        val bands = supportedEqBands

        if (bands <= 0) {
            return
        }

        val mappedGains =
            if (bands == TARGET_EQ_BANDS) {
                config.eqGains
            } else {
                downsampleGains(
                    config.eqGains,
                    bands
                )
            }

        val freqs =
            if (bands == TARGET_EQ_BANDS) {
                DspConfig.FREQUENCIES
            } else {
                downsampleFrequencies(
                    DspConfig.FREQUENCIES,
                    bands
                )
            }

        for (i in 0 until bands) {

            val requestedGain =
                mappedGains
                    .getOrElse(i) { 0.0f }
                    .coerceIn(
                        -15.0f,
                        15.0f
                    )

            val frequency = freqs[i]

            val band =
                DynamicsProcessing.EqBand(
                    true,
                    frequency,
                    requestedGain
                )

            effect.setPostEqBandByChannelIndex(
                0,
                i,
                band
            )

            effect.setPostEqBandByChannelIndex(
                1,
                i,
                band
            )
        }
    }

    /**
     * Applies the four MDRC bands individually.
     *
     * This is the critical correction.
     *
     * The previous implementation rebuilt a complete Mbc object every
     * time. This implementation writes each band directly into the
     * already configured DynamicsProcessing engine.
     *
     * Android exposes:
     *
     * setMbcBandByChannelIndex(channel, band, MbcBand)
     *
     * and:
     *
     * getMbcBandByChannelIndex(channel, band)
     *
     * allowing the actual value accepted by the effect to be verified.
     */
    private fun applyMdrc(
        effect: DynamicsProcessing,
        config: DspConfig
    ) {

        val configuredBands =
            config.mdrcBands

        if (configuredBands.size < MBC_BANDS) {

            Log.w(
                TAG,
                "MDRC requires $MBC_BANDS bands, " +
                    "but configuration contains " +
                    "${configuredBands.size}. " +
                    "Keeping existing MDRC configuration."
            )

            return
        }

        val cutoffs =
            normalizeMdrcCutoffs(
                configuredBands
                    .take(MBC_BANDS)
                    .map {
                        it.cutoffFrequencyHz
                    }
            )

        for (bandIndex in 0 until MBC_BANDS) {

            val configBand =
                configuredBands[bandIndex]

            val mbcBand =
                DynamicsProcessing.MbcBand(
                    config.mdrcEnabled,

                    cutoffs[bandIndex],

                    configBand.attackMs.coerceIn(
                        0.1f,
                        200.0f
                    ),

                    configBand.releaseMs.coerceIn(
                        10.0f,
                        2000.0f
                    ),

                    configBand.ratio.coerceIn(
                        1.0f,
                        30.0f
                    ),

                    configBand.thresholdDb.coerceIn(
                        -60.0f,
                        0.0f
                    ),

                    configBand.kneeDb.coerceIn(
                        0.0f,
                        24.0f
                    ),

                    -70.0f,

                    1.0f,

                    0.0f,

                    configBand.makeupGainDb.coerceIn(
                        0.0f,
                        18.0f
                    )
                )

            /*
             * Write the exact band directly to both channels.
             */
            effect.setMbcBandByChannelIndex(
                0,
                bandIndex,
                mbcBand
            )

            effect.setMbcBandByChannelIndex(
                1,
                bandIndex,
                mbcBand
            )
        }

        /*
         * Verify what Android actually accepted.
         *
         * This is deliberately done after every MDRC update so that
         * Logcat can reveal a HAL that clamps or rejects a cutoff.
         */
        verifyMdrcCutoffs(
            effect,
            cutoffs
        )
    }

    /**
     * Reads the four MBC bands back from both channels and reports
     * any cutoff mismatch.
     */
    private fun verifyMdrcCutoffs(
        effect: DynamicsProcessing,
        requestedCutoffs: List<Float>
    ) {

        for (channel in 0 until CHANNELS) {

            for (band in 0 until MBC_BANDS) {

                try {

                    val actualBand =
                        effect.getMbcBandByChannelIndex(
                            channel,
                            band
                        )

                    val requested =
                        requestedCutoffs[band]

                    val actual =
                        actualBand.cutoffFrequency

                    val difference =
                        abs(actual - requested)

                    if (difference > 0.5f) {

                        Log.w(
                            TAG,
                            "MDRC cutoff mismatch: " +
                                "session=$audioSessionId " +
                                "channel=$channel " +
                                "band=$band " +
                                "requested=${requested}Hz " +
                                "actual=${actual}Hz"
                        )

                    } else {

                        Log.d(
                            TAG,
                            "MDRC cutoff verified: " +
                                "session=$audioSessionId " +
                                "channel=$channel " +
                                "band=$band " +
                                "cutoff=${actual}Hz"
                        )
                    }

                } catch (e: Exception) {

                    Log.w(
                        TAG,
                        "Unable to verify MDRC band " +
                            "$band on channel $channel: " +
                            e.message
                    )
                }
            }
        }
    }

    /**
     * Normalizes MDRC cutoff frequencies while preserving their
     * intended band order.
     *
     * The Android API specifies that cutoff frequencies are expected
     * to increase with band number.
     */
    private fun normalizeMdrcCutoffs(
        values: List<Float>
    ): List<Float> {

        if (values.isEmpty()) {
            return emptyList()
        }

        val result =
            MutableList(
                min(values.size, MBC_BANDS)
            ) { 0.0f }

        for (i in result.indices) {

            var value =
                values[i]
                    .takeIf { it.isFinite() }
                    ?: DspConfig
                        .defaultMdrcBands()
                        .getOrElse(i) {
                            DspConfig
                                .defaultMdrcBands()
                                .last()
                        }
                        .cutoffFrequencyHz

            value =
                value.coerceIn(
                    MIN_MDRC_CUTOFF_HZ,
                    MAX_MDRC_CUTOFF_HZ
                )

            if (i > 0) {

                val minimumAllowed =
                    result[i - 1] +
                        MIN_MDRC_SEPARATION_HZ

                value =
                    max(
                        value,
                        minimumAllowed
                    )
            }

            result[i] =
                value.coerceAtMost(
                    MAX_MDRC_CUTOFF_HZ
                )
        }

        /*
         * If the last values collide with the 22 kHz limit,
         * repair backwards while preserving order.
         */
        for (i in result.lastIndex downTo 1) {

            val maximumAllowed =
                result[i] -
                    MIN_MDRC_SEPARATION_HZ

            if (result[i - 1] > maximumAllowed) {
                result[i - 1] =
                    maximumAllowed
                        .coerceAtLeast(
                            MIN_MDRC_CUTOFF_HZ
                        )
            }
        }

        return result
    }

    /**
     * Attempts to regain effect control after another audio effect
     * controller releases it.
     */
    fun reclaimControl() {

        try {

            val effect = dp ?: return

            if (!effect.hasControl()) {

                Log.d(
                    TAG,
                    "Reclaiming DynamicsProcessing control " +
                        "for session $audioSessionId..."
                )

                hasControl = effect.hasControl()

                if (hasControl) {
                    effect.enabled = isEffectEnabled
                }
            }

        } catch (e: Exception) {

            Log.w(
                TAG,
                "Failed reclaiming control on session " +
                    "$audioSessionId: ${e.message}"
            )
        }
    }

    fun isAvailable(): Boolean =
        dp != null

    fun hasControl(): Boolean =
        hasControl

    /**
     * Releases the Android audio effect.
     */
    fun release() {

        try {

            dp?.enabled = false
            dp?.release()

        } catch (e: Exception) {

            Log.w(
                TAG,
                "Error releasing DynamicsProcessing on " +
                    "session $audioSessionId: ${e.message}"
            )

        } finally {

            dp = null
            isEffectEnabled = false
            hasControl = false
        }
    }

    private fun downsampleFrequencies(
        src: FloatArray,
        count: Int
    ): FloatArray {

        if (count <= 0) {
            return FloatArray(0)
        }

        if (src.isEmpty()) {
            return FloatArray(count)
        }

        if (src.size == count) {
            return src.copyOf()
        }

        if (count == 1) {
            return floatArrayOf(src.first())
        }

        val result =
            FloatArray(count)

        val step =
            (src.size - 1).toFloat() /
                (count - 1).toFloat()

        for (i in 0 until count) {

            val index =
                (
                    i * step
                    ).roundToInt().coerceIn(
                        0,
                        src.size - 1
                    )

            result[i] =
                src[index]
        }

        return result
    }

    private fun downsampleGains(
        src: List<Float>,
        count: Int
    ): List<Float> {

        if (count <= 0) {
            return emptyList()
        }

        if (src.isEmpty()) {
            return List(count) { 0.0f }
        }

        if (src.size == count) {
            return src.toList()
        }

        if (count == 1) {
            return listOf(src.first())
        }

        val result =
            ArrayList<Float>(
                count
            )

        val step =
            (src.size - 1).toFloat() /
                (count - 1).toFloat()

        for (i in 0 until count) {

            val index =
                (
                    i * step
                    ).roundToInt().coerceIn(
                        0,
                        src.size - 1
                    )

            result.add(
                src[index]
            )
        }

        return result
    }

    /**
     * Applies limiter and master/automatic gain.
     */
    private fun applyLimiterAndMasterGain(
        effect: DynamicsProcessing,
        config: DspConfig
    ) {

        val agcGainDb =
            if (config.autoGainEnabled) {

                val averageBoost =
                    config.eqGains
                        .average()
                        .toFloat()

                /*
                 * Keep the existing automatic gain behavior,
                 * but limit its correction to a safe range.
                 */
                (
                    -averageBoost * 0.5f
                    ).coerceIn(
                        -6.0f,
                        6.0f
                    )

            } else {
                0.0f
            }

        val combinedMasterPostGain =
            (
                config.masterGainDb +
                    agcGainDb +
                    config.limiterPostGainDb
                ).coerceIn(
                    -30.0f,
                    12.0f
                )

        val limiter =
            DynamicsProcessing.Limiter(
                true,

                config.limiterEnabled,

                0,

                config.limiterAttackMs.coerceIn(
                    0.1f,
                    20.0f
                ),

                config.limiterReleaseMs.coerceIn(
                    5.0f,
                    1000.0f
                ),

                config.limiterRatio.coerceIn(
                    5.0f,
                    100.0f
                ),

                config.limiterThresholdDb.coerceIn(
                    -24.0f,
                    0.0f
                ),

                combinedMasterPostGain
            )

        effect.setLimiterByChannelIndex(
            0,
            limiter
        )

        effect.setLimiterByChannelIndex(
            1,
            limiter
        )
    }
}
