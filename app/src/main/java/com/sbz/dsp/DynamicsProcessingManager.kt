package com.sbz.dsp

import android.media.audiofx.DynamicsProcessing
import android.os.Build
import android.util.Log
import com.sbz.dsp.model.DspConfig
import com.sbz.dsp.model.MdrcBandConfig

/**
 * Controls Android DynamicsProcessing for the sBz DSP chain.
 *
 * Pipeline:
 *   Input Gain / Pre-Gain
 *   -> Pre-EQ
 *   -> MBC / MDRC
 *   -> Post-EQ
 *   -> Limiter
 *
 * Graphic EQ is applied once at full requested gain in Post-EQ.
 *
 * MDRC cutoff frequencies come directly from DspConfig.mdrcBands.
 */
class DynamicsProcessingManager(
    private val audioSessionId: Int,
    private val onControlLost: (() -> Unit)? = null
) {

    companion object {
        private const val TAG = "DynamicsProcessingMgr"

        private const val PRIORITY = 100
        private const val CHANNEL_COUNT = 2

        private const val REQUESTED_EQ_BANDS = 32
        private const val FALLBACK_EQ_BANDS_16 = 16
        private const val FALLBACK_EQ_BANDS_8 = 8

        private const val MDRC_BAND_COUNT = 4

        private const val MIN_CUTOFF_HZ = 20f
        private const val MAX_CUTOFF_HZ = 22000f
        private const val MIN_CUTOFF_SEPARATION_HZ = 1f

        private const val DEFAULT_THRESHOLD_DB = -18f
        private const val DEFAULT_RATIO = 2f
        private const val DEFAULT_ATTACK_MS = 10f
        private const val DEFAULT_RELEASE_MS = 80f
        private const val DEFAULT_MAKEUP_DB = 0f
        private const val DEFAULT_KNEE_DB = 2f
    }

    private var effect: DynamicsProcessing? = null
    private var eqBandCount: Int = 0
    private var initialized = false

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            initialize()
        } else {
            Log.w(TAG, "DynamicsProcessing requires API 28+")
        }
    }

    private fun initialize() {
        if (initialized) return

        try {
            val config = createInitialConfig()

            val dp = DynamicsProcessing(
                PRIORITY,
                audioSessionId,
                config
            )

            dp.setControlStatusListener { _, hasControl ->
                Log.i(
                    TAG,
                    "DynamicsProcessing control changed: " +
                        "hasControl=$hasControl session=$audioSessionId"
                )

                if (!hasControl) {
                    onControlLost?.invoke()
                }
            }

            dp.setEnabled(true)

            effect = dp
            initialized = true

            Log.i(
                TAG,
                "DynamicsProcessing initialized: " +
                    "session=$audioSessionId eqBands=$eqBandCount"
            )

        } catch (e: Exception) {
            Log.e(
                TAG,
                "Unable to initialize DynamicsProcessing " +
                    "for session=$audioSessionId",
                e
            )

            releaseInternal()
        }
    }

    private fun createInitialConfig(): DynamicsProcessing.Config {
        val candidates = intArrayOf(
            REQUESTED_EQ_BANDS,
            FALLBACK_EQ_BANDS_16,
            FALLBACK_EQ_BANDS_8
        )

        var lastError: Exception? = null

        for (bandCount in candidates) {
            try {
                val builder = DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                    CHANNEL_COUNT,
                    true,
                    bandCount,
                    true,
                    MDRC_BAND_COUNT,
                    true,
                    bandCount,
                    true
                )

                val config = builder.build()

                eqBandCount = bandCount

                Log.i(
                    TAG,
                    "DynamicsProcessing config created: " +
                        "EQ=$bandCount MDRC=$MDRC_BAND_COUNT"
                )

                return config

            } catch (e: Exception) {
                lastError = e

                Log.w(
                    TAG,
                    "Unable to create DynamicsProcessing " +
                        "config with EQ=$bandCount",
                    e
                )
            }
        }

        throw IllegalStateException(
            "Unable to create DynamicsProcessing configuration",
            lastError
        )
    }

    /**
     * Update one MDRC band without rebuilding the rest of the DSP chain.
     *
     * The cutoff of a band is also the lower boundary of the following band,
     * so changing one native MbcBand is sufficient for the crossover boundary
     * to move. The complete list is only normalized for validation; only the
     * requested native band is written to the effect.
     */
    @Synchronized
    fun updateMdrcBand(
        config: DspConfig,
        bandIndex: Int
    ) {
        if (!initialized || effect == null) {
            initialize()
        }

        val dp = effect ?: return
        val mdrcCount = dp.getConfig().mbcBandCount

        if (mdrcCount <= 0 || bandIndex !in 0 until mdrcCount) return

        val requestedBands = normalizeMdrcBands(config.mdrcBands)
        val source = requestedBands.getOrNull(bandIndex)
            ?: defaultMdrcBand(bandIndex)

        val mdrcActive = config.isEnabled && config.mdrcEnabled
        val cutoff = source.cutoffFrequencyHz
            .coerceIn(MIN_CUTOFF_HZ, MAX_CUTOFF_HZ)

        for (channel in 0 until dp.channelCountSafe()) {
            try {
                val nativeBand = DynamicsProcessing.MbcBand(
                    mdrcActive,
                    cutoff,
                    source.attackMs.coerceIn(0.1f, 1000f),
                    source.releaseMs.coerceIn(1f, 2000f),
                    source.ratio.coerceIn(1f, 20f),
                    source.thresholdDb.coerceIn(-60f, 0f),
                    source.kneeDb.coerceIn(0f, 30f),
                    -80f,
                    1f,
                    0f,
                    source.makeupGainDb.coerceIn(0f, 24f)
                )

                // Surgical update: no MBC disable/enable cycle and no rewrite
                // of the other three bands while the fader is moving.
                dp.setMbcBandByChannelIndex(
                    channel,
                    bandIndex,
                    nativeBand
                )
            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "Unable to update MDRC band=$bandIndex on channel=$channel",
                    e
                )
            }
        }
    }

    @Synchronized
    fun applyConfig(config: DspConfig) {
        if (!initialized || effect == null) {
            initialize()
        }

        val dp = effect ?: return

        try {
            dp.setEnabled(config.isEnabled)

            val automaticHeadroom =
                if (config.autoGainEnabled && config.isEnabled) {
                    config.computeHeadroomSafeguard()
                } else {
                    0f
                }

            dp.setInputGainAllChannelsTo(
                (config.preGainDb + automaticHeadroom)
                    .coerceIn(-12f, 12f)
            )

            applyPreEq(dp, config)
            applyMdrc(dp, config)
            applyGraphicEq(dp, config)
            applyLimiter(dp, config)

            if (config.isEnabled) {
                applyMasterGain(dp, config)
            }

        } catch (e: Exception) {
            Log.e(
                TAG,
                "Error applying DSP configuration " +
                    "to session=$audioSessionId",
                e
            )
        }
    }

    private fun applyPreEq(
        dp: DynamicsProcessing,
        config: DspConfig
    ) {
        val count = dp.getConfig().preEqBandCount

        if (count <= 0) return

        for (channel in 0 until dp.channelCountSafe()) {
            try {
                val preEq = dp.getPreEqByChannelIndex(channel)

                preEq.setEnabled(config.isEnabled)

                val bassGain =
                    if (config.isEnabled) {
                        config.toneBassDb.coerceIn(-12f, 12f)
                    } else {
                        0f
                    }

                val midGain =
                    if (config.isEnabled) {
                        config.toneMidDb.coerceIn(-12f, 12f)
                    } else {
                        0f
                    }

                val trebleGain =
                    if (config.isEnabled) {
                        config.toneTrebleDb.coerceIn(-12f, 12f)
                    } else {
                        0f
                    }

                for (bandIndex in 0 until count) {
                    val frequency = calculateStageFrequency(
                        bandIndex,
                        count
                    )

                    val gain = when {
                        frequency <= 200f -> bassGain
                        frequency <= 2000f -> midGain
                        else -> trebleGain
                    }

                    val band = preEq.getBand(bandIndex)

                    band.setEnabled(config.isEnabled)
                    band.setGain(gain)
                    band.setCutoffFrequency(
                        frequency.coerceIn(
                            MIN_CUTOFF_HZ,
                            MAX_CUTOFF_HZ
                        )
                    )
                }

                if (
                    config.bassBoostEnabled &&
                    config.isEnabled
                ) {
                    applyBassBoostCompensation(
                        preEq,
                        count,
                        config
                    )
                }

                dp.setPreEqByChannelIndex(
                    channel,
                    preEq
                )

            } catch (e: Exception) {
                Log.w(
                    TAG,
                    "Unable to apply Pre-EQ on channel=$channel",
                    e
                )
            }
        }
    }

    private fun applyBassBoostCompensation(
        preEq: DynamicsProcessing.Eq,
        count: Int,
        config: DspConfig
    ) {
        if (count <= 0) return

        val boostDb =
            (config.bassBoostStrength.coerceIn(0, 1000) / 1000f) * 6f

        for (bandIndex in 0 until count) {
            val band = preEq.getBand(bandIndex)

            val frequency = try {
                band.getCutoffFrequency()
            } catch (_: Exception) {
                calculateStageFrequency(
                    bandIndex,
                    count
                )
            }

            if (frequency <= 160f) {
                val existingGain = try {
                    band.getGain()
                } catch (_: Exception) {
                    0f
                }

                band.setGain(
                    (existingGain + boostDb)
                        .coerceIn(-15f, 15f)
                )
            }
        }
    }

    private fun applyGraphicEq(
        dp: DynamicsProcessing,
        config: DspConfig
    ) {
        val count = dp.getConfig().postEqBandCount

        if (count <= 0) return

        for (channel in 0 until dp.channelCountSafe()) {
            try {
                val postEq =
                    dp.getPostEqByChannelIndex(channel)

                postEq.setEnabled(config.isEnabled)

                for (bandIndex in 0 until count) {
                    val sourceIndex = mapEqBandIndex(
                        bandIndex,
                        count,
                        config.eqGains.size
                    )

                    val frequency =
                        DspConfig.FREQUENCIES[
                            sourceIndex.coerceIn(
                                0,
                                DspConfig.FREQUENCIES.lastIndex
                            )
                        ]

                    val eqGain =
                        if (config.isEnabled) {
                            config.eqGains
                                .getOrElse(sourceIndex) { 0f }
                                .coerceIn(-15f, 15f)
                        } else {
                            0f
                        }

                    // Balance is implemented as a per-channel gain at the final EQ stage.
                    // This avoids a second native effect and keeps the DSP chain compact.
                    val balanceGainDb = balanceCompensationDb(channel, config.balance)
                    val gain = (eqGain + balanceGainDb).coerceIn(-60f, 15f)

                    val band =
                        postEq.getBand(bandIndex)

                    band.setEnabled(config.isEnabled)

                    band.setCutoffFrequency(
                        frequency.coerceIn(
                            MIN_CUTOFF_HZ,
                            MAX_CUTOFF_HZ
                        )
                    )

                    /*
                     * Full graphic EQ gain is applied here.
                     * No 50/50 Pre-EQ/Post-EQ split.
                     */
                    band.setGain(gain)
                }

                dp.setPostEqByChannelIndex(
                    channel,
                    postEq
                )

            } catch (e: Exception) {
                Log.w(
                    TAG,
                    "Unable to apply Post-EQ on channel=$channel",
                    e
                )
            }
        }
    }

    /**
     * Applies MDRC crossover frequencies and dynamics.
     *
     * IMPORTANT:
     * Each MbcBand is written directly to the native effect.
     *
     * We intentionally DO NOT call setMbcByChannelIndex()
     * afterwards because doing so can overwrite the individual
     * band values with a stale Mbc configuration.
     */
    private fun applyMdrc(
        dp: DynamicsProcessing,
        config: DspConfig
    ) {
        val mdrc = dp.getConfig().mbcBandCount

        if (mdrc <= 0) return

        val requestedBands =
            normalizeMdrcBands(config.mdrcBands)

        for (channel in 0 until dp.channelCountSafe()) {
            try {
                /*
                 * Keep the MBC stage disabled while its bands are being
                 * replaced. This prevents a partially-updated multiband
                 * configuration from reaching the audio path and causing
                 * clicks/noise when MDRC is toggled.
                 */
                val mdrcActive =
                    config.isEnabled && config.mdrcEnabled

                val mbc = dp.getMbcByChannelIndex(channel)
                mbc.setEnabled(false)
                dp.setMbcByChannelIndex(channel, mbc)

                /*
                 * Write every band while the MBC stage is bypassed.
                 *
                 * Android's MbcBand constructor order is:
                 * enabled, cutoffFrequency, attackTime, releaseTime,
                 * ratio, threshold, kneeWidth, noiseGateThreshold,
                 * expanderRatio, preGain, postGain.
                 */
                for (bandIndex in 0 until mdrc) {
                    val source =
                        requestedBands.getOrElse(
                            bandIndex
                        ) {
                            defaultMdrcBand(bandIndex)
                        }

                    val cutoff =
                        source.cutoffFrequencyHz
                            .coerceIn(
                                MIN_CUTOFF_HZ,
                                MAX_CUTOFF_HZ
                            )

                    val nativeBand =
                        DynamicsProcessing.MbcBand(
                            mdrcActive,
                            cutoff,
                            source.attackMs.coerceIn(
                                0.1f,
                                1000f
                            ),
                            source.releaseMs.coerceIn(
                                1f,
                                2000f
                            ),
                            source.ratio.coerceIn(
                                1f,
                                20f
                            ),
                            source.thresholdDb.coerceIn(
                                -60f,
                                0f
                            ),
                            source.kneeDb.coerceIn(
                                0f,
                                30f
                            ),
                            -80f,
                            1f,
                            0f,
                            source.makeupGainDb.coerceIn(
                                0f,
                                24f
                            )
                        )

                    dp.setMbcBandByChannelIndex(
                        channel,
                        bandIndex,
                        nativeBand
                    )
                }

                /*
                 * Enable the fully-configured MBC only after all bands
                 * have been written.
                 */
                val finalMbc = dp.getMbcByChannelIndex(channel)
                finalMbc.setEnabled(mdrcActive)
                dp.setMbcByChannelIndex(channel, finalMbc)


            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "Unable to apply MDRC on channel=$channel",
                    e
                )
            }
        }
    }

    private fun normalizeMdrcBands(
        bands: List<MdrcBandConfig>
    ): List<MdrcBandConfig> {
        val source =
            if (bands.isEmpty()) {
                DspConfig.defaultMdrcBands()
            } else {
                bands
            }

        val result =
            mutableListOf<MdrcBandConfig>()

        var previousCutoff =
            MIN_CUTOFF_HZ -
                MIN_CUTOFF_SEPARATION_HZ

        for (index in 0 until MDRC_BAND_COUNT) {
            val input =
                source.getOrNull(index)
                    ?: defaultMdrcBand(index)

            val minimum =
                if (index == 0) {
                    MIN_CUTOFF_HZ
                } else {
                    previousCutoff +
                        MIN_CUTOFF_SEPARATION_HZ
                }

            val remainingBands =
                MDRC_BAND_COUNT -
                    index -
                    1

            val maximum =
                MAX_CUTOFF_HZ -
                    remainingBands *
                    MIN_CUTOFF_SEPARATION_HZ

            val cutoff =
                input.cutoffFrequencyHz
                    .takeIf { it.isFinite() }
                    ?.coerceIn(
                        minimum,
                        maximum
                    )
                    ?: defaultMdrcBand(index)
                        .cutoffFrequencyHz
                        .coerceIn(
                            minimum,
                            maximum
                        )

            val normalized =
                input.copy(
                    cutoffFrequencyHz = cutoff,

                    thresholdDb =
                        input.thresholdDb
                            .takeIf { it.isFinite() }
                            ?.coerceIn(-60f, 0f)
                            ?: DEFAULT_THRESHOLD_DB,

                    ratio =
                        input.ratio
                            .takeIf { it.isFinite() }
                            ?.coerceIn(1f, 20f)
                            ?: DEFAULT_RATIO,

                    attackMs =
                        input.attackMs
                            .takeIf { it.isFinite() }
                            ?.coerceIn(0.1f, 1000f)
                            ?: DEFAULT_ATTACK_MS,

                    releaseMs =
                        input.releaseMs
                            .takeIf { it.isFinite() }
                            ?.coerceIn(1f, 2000f)
                            ?: DEFAULT_RELEASE_MS,

                    makeupGainDb =
                        input.makeupGainDb
                            .takeIf { it.isFinite() }
                            ?.coerceIn(0f, 24f)
                            ?: DEFAULT_MAKEUP_DB,

                    kneeDb =
                        input.kneeDb
                            .takeIf { it.isFinite() }
                            ?.coerceIn(0f, 30f)
                            ?: DEFAULT_KNEE_DB
                )

            result += normalized
            previousCutoff = cutoff
        }

        return result
    }

    private fun defaultMdrcBand(
        index: Int
    ): MdrcBandConfig {
        return DspConfig
            .defaultMdrcBands()
            .getOrElse(index) {
                DspConfig.defaultMdrcBands().last()
            }
    }

    private fun mapEqBandIndex(
        nativeIndex: Int,
        nativeCount: Int,
        sourceCount: Int
    ): Int {
        if (
            nativeCount <= 1 ||
            sourceCount <= 1
        ) {
            return 0
        }

        val normalized =
            nativeIndex.toFloat() /
                (nativeCount - 1).toFloat()

        return kotlin.math.round(
            normalized *
                (sourceCount - 1)
        ).toInt()
    }

    private fun balanceCompensationDb(channel: Int, balance: Float): Float {
        val b = balance.coerceIn(-1f, 1f)
        if (b == 0f) return 0f

        val attenuation = 1f - kotlin.math.abs(b)
        val db = 20f * kotlin.math.log10(attenuation.coerceAtLeast(0.001f))

        return when {
            b < 0f && channel == 1 -> db
            b > 0f && channel == 0 -> db
            else -> 0f
        }
    }

    private fun calculateStageFrequency(
        index: Int,
        count: Int
    ): Float {
        if (count <= 1) {
            return 1000f
        }

        val normalized =
            index.toFloat() /
                (count - 1).toFloat()

        val minLog =
            kotlin.math.ln(
                MIN_CUTOFF_HZ.toDouble()
            )

        val maxLog =
            kotlin.math.ln(
                20000.0
            )

        return kotlin.math.exp(
            minLog +
                (maxLog - minLog) *
                normalized
        ).toFloat()
    }

    /**
     * Applies the native output limiter.
     */
    private fun applyLimiter(
        dp: DynamicsProcessing,
        config: DspConfig
    ) {
        for (channel in 0 until dp.channelCountSafe()) {
            try {
                /*
                 * Android API 28+ constructor:
                 *
                 * Limiter(
                 *     inUse,
                 *     enabled,
                 *     linkGroup,
                 *     attackTime,
                 *     releaseTime,
                 *     ratio,
                 *     threshold,
                 *     postGain
                 * )
                 */
                val limiter =
                    DynamicsProcessing.Limiter(
                        config.isEnabled,
                        config.isEnabled &&
                            (config.limiterEnabled ||
                                config.masterGainDb != 0f ||
                                config.limiterPostGainDb != 0f),
                        0,
                        config.limiterAttackMs
                            .coerceIn(
                                0.1f,
                                1000f
                            ),
                        config.limiterReleaseMs
                            .coerceIn(
                                1f,
                                2000f
                            ),
                        config.limiterRatio
                            .coerceIn(
                                1f,
                                100f
                            ),
                        config.limiterThresholdDb
                            .coerceIn(
                                -60f,
                                0f
                            ),
                        config.limiterPostGainDb
                            .coerceIn(
                                -24f,
                                12f
                            )
                    )

                dp.setLimiterByChannelIndex(
                    channel,
                    limiter
                )

            } catch (e: Exception) {
                Log.w(
                    TAG,
                    "Unable to apply limiter " +
                        "on channel=$channel",
                    e
                )
            }
        }
    }

    private fun applyMasterGain(
        dp: DynamicsProcessing,
        config: DspConfig
    ) {
        if (!config.isEnabled) return

        for (channel in 0 until dp.channelCountSafe()) {
            try {
                val limiter =
                    dp.getLimiterByChannelIndex(channel)

                val totalPostGain =
                    (
                        config.limiterPostGainDb +
                            config.masterGainDb
                        ).coerceIn(
                            -24f,
                            12f
                        )

                limiter.setPostGain(
                    totalPostGain
                )

                dp.setLimiterByChannelIndex(
                    channel,
                    limiter
                )

            } catch (e: Exception) {
                Log.w(
                    TAG,
                    "Unable to apply master gain " +
                        "on channel=$channel",
                    e
                )
            }
        }
    }

    fun getSupportedEqBands(): Int =
        eqBandCount

    fun hasControl(): Boolean {
        return try {
            effect?.hasControl() == true
        } catch (_: Exception) {
            false
        }
    }

    fun isAvailable(): Boolean =
        initialized && effect != null

    fun reclaimControl() {
        try {
            effect?.let {
                if (!it.hasControl()) {
                    Log.i(
                        TAG,
                        "DynamicsProcessing currently " +
                            "does not have control: " +
                            "session=$audioSessionId"
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(
                TAG,
                "Unable to inspect " +
                    "DynamicsProcessing control",
                e
            )
        }
    }

    fun release() {
        releaseInternal()
    }

    private fun releaseInternal() {
        try {
            effect?.setEnabled(false)
        } catch (_: Exception) {
        }

        try {
            effect?.release()
        } catch (_: Exception) {
        }

        effect = null
        initialized = false
        eqBandCount = 0
    }

    private fun DynamicsProcessing.channelCountSafe(): Int {
        return try {
            getChannelCount().coerceAtLeast(1)
        } catch (_: Exception) {
            CHANNEL_COUNT
        }
    }
}
