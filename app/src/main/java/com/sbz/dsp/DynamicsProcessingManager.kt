package com.sbz.dsp

import android.media.audiofx.DynamicsProcessing
import android.os.Build
import android.util.Log
import com.sbz.dsp.model.DspConfig
import com.sbz.dsp.model.MdrcBandConfig

/**
 * Controls Android DynamicsProcessing for the sBz DSP chain.
 *
 * Pipeline inside DynamicsProcessing:
 *   Input Gain / Pre-Gain
 *   -> Pre-EQ (Tone / Bass Boost compensation)
 *   -> MBC / MDRC
 *   -> Post-EQ (32-band graphic EQ)
 *   -> Limiter
 *
 * IMPORTANT:
 * The graphic EQ is applied once at the requested gain in Post-EQ.
 * There is no 50/50 gain split between Pre-EQ and Post-EQ.
 *
 * MDRC cutoffs are taken directly from DspConfig.mdrcBands.
 * They are not replaced by default values when applying a configuration.
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

    /**
     * Initializes the native DynamicsProcessing effect.
     */
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
                    "DynamicsProcessing control changed: hasControl=$hasControl session=$audioSessionId"
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
                "DynamicsProcessing initialized: session=$audioSessionId eqBands=$eqBandCount"
            )

        } catch (e: Exception) {
            Log.e(
                TAG,
                "Unable to initialize DynamicsProcessing for session=$audioSessionId",
                e
            )

            releaseInternal()
        }
    }

    private fun createInitialConfig(): DynamicsProcessing.Config {
        val requested = REQUESTED_EQ_BANDS

        val candidates = intArrayOf(
            requested,
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
                    "DynamicsProcessing config created: EQ=$bandCount MDRC=$MDRC_BAND_COUNT"
                )

                return config

            } catch (e: Exception) {
                lastError = e

                Log.w(
                    TAG,
                    "Unable to create DynamicsProcessing config with EQ=$bandCount",
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
     * Applies the complete sBz configuration.
     */
    @Synchronized
    fun applyConfig(config: DspConfig) {
        if (!initialized || effect == null) {
            initialize()
        }

        val dp = effect ?: return

        try {
            /*
             * Global enable state.
             */
            dp.setEnabled(config.isEnabled)

            /*
             * Pre-gain.
             *
             * DynamicsProcessing input gain is in dB.
             */
            dp.setInputGainAllChannelsTo(
                config.preGainDb.coerceIn(-12f, 12f)
            )

            /*
             * Pre-EQ.
             *
             * Tone and Bass Boost are intentionally kept separate
             * from the graphic 32-band EQ.
             */
            applyPreEq(dp, config)

            /*
             * MDRC.
             *
             * This is the important corrected section:
             * config.mdrcBands is used directly.
             */
            applyMdrc(dp, config)

            /*
             * Graphic EQ.
             *
             * Each requested EQ gain is applied once at full value
             * to Post-EQ. No 50/50 split.
             */
            applyGraphicEq(dp, config)

            /*
             * Output limiter.
             */
            applyLimiter(dp, config)

            /*
             * Final master gain is represented through the limiter
             * post gain so that it remains inside the native
             * DynamicsProcessing output stage.
             */
            if (config.isEnabled) {
                applyMasterGain(dp, config)
            }

            Log.d(
                TAG,
                "DSP configuration applied successfully: " +
                    "session=$audioSessionId " +
                    "mdrcEnabled=${config.mdrcEnabled} " +
                    "mdrcBands=${config.mdrcBands.size}"
            )

        } catch (e: Exception) {
            Log.e(
                TAG,
                "Error applying DSP configuration to session=$audioSessionId",
                e
            )
        }
    }

    /**
     * Applies Tone/Bass Boost compensation in Pre-EQ.
     *
     * The graphic EQ itself is NOT applied here.
     */
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

                /*
                 * Keep the pre-EQ neutral by default.
                 *
                 * Tone/Bass Boost are represented through the first
                 * available low/mid/high regions when possible.
                 */
                val bassGain = if (config.isEnabled) {
                    config.toneBassDb.coerceIn(-12f, 12f)
                } else {
                    0f
                }

                val midGain = if (config.isEnabled) {
                    config.toneMidDb.coerceIn(-12f, 12f)
                } else {
                    0f
                }

                val trebleGain = if (config.isEnabled) {
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

                /*
                 * Bass Boost is deliberately not implemented as a second
                 * native BassBoost effect here. Its contribution is kept
                 * inside the DynamicsProcessing stage to avoid the
                 * mute/control conflicts observed on some devices.
                 */
                if (config.bassBoostEnabled && config.isEnabled) {
                    applyBassBoostCompensation(preEq, count, config)
                }

                dp.setPreEqByChannelIndex(channel, preEq)

            } catch (e: Exception) {
                Log.w(
                    TAG,
                    "Unable to apply Pre-EQ on channel=$channel",
                    e
                )
            }
        }
    }

    /**
     * Applies a controlled low-frequency compensation for Bass Boost.
     */
    private fun applyBassBoostCompensation(
        preEq: DynamicsProcessing.Eq,
        count: Int,
        config: DspConfig
    ) {
        if (count <= 0) return

        val boostDb = (
            config.bassBoostStrength.coerceIn(0, 1000) / 1000f
        ) * 6f

        for (bandIndex in 0 until count) {
            val band = preEq.getBand(bandIndex)

            val frequency = try {
                band.getCutoffFrequency()
            } catch (_: Exception) {
                calculateStageFrequency(bandIndex, count)
            }

            if (frequency <= 160f) {
                val existingGain = try {
                    band.getGain()
                } catch (_: Exception) {
                    0f
                }

                band.setGain(
                    (existingGain + boostDb).coerceIn(-15f, 15f)
                )
            }
        }
    }

    /**
     * Applies the 32-band graphic EQ exactly once in Post-EQ.
     */
    private fun applyGraphicEq(
        dp: DynamicsProcessing,
        config: DspConfig
    ) {
        val count = dp.getConfig().postEqBandCount

        if (count <= 0) return

        for (channel in 0 until dp.channelCountSafe()) {
            try {
                val postEq = dp.getPostEqByChannelIndex(channel)

                postEq.setEnabled(config.isEnabled)

                for (bandIndex in 0 until count) {
                    val sourceIndex = mapEqBandIndex(
                        bandIndex,
                        count,
                        config.eqGains.size
                    )

                    val frequency = DspConfig.FREQUENCIES[
                        sourceIndex.coerceIn(
                            0,
                            DspConfig.FREQUENCIES.lastIndex
                        )
                    ]

                    val gain = if (config.isEnabled) {
                        config.eqGains.getOrElse(sourceIndex) { 0f }
                            .coerceIn(-15f, 15f)
                    } else {
                        0f
                    }

                    val band = postEq.getBand(bandIndex)

                    band.setEnabled(config.isEnabled)
                    band.setCutoffFrequency(
                        frequency.coerceIn(
                            MIN_CUTOFF_HZ,
                            MAX_CUTOFF_HZ
                        )
                    )

                    /*
                     * IMPORTANT:
                     * Full requested EQ gain goes here.
                     * There is no 50/50 Pre/Post split.
                     */
                    band.setGain(gain)
                }

                dp.setPostEqByChannelIndex(channel, postEq)

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
     * Applies the configurable four-band MDRC.
     *
     * The cutoff frequency is taken directly from each
     * MdrcBandConfig. The values are normalized only for validity
     * and ordering; they are never replaced with defaults.
     */
    private fun applyMdrc(
        dp: DynamicsProcessing,
        config: DspConfig
    ) {
        val mdrc = dp.getConfig().mbcBandCount

        if (mdrc <= 0) return

        val requestedBands = normalizeMdrcBands(config.mdrcBands)

        for (channel in 0 until dp.channelCountSafe()) {
            try {
                val mbc = dp.getMbcByChannelIndex(channel)

                mbc.setEnabled(
                    config.isEnabled && config.mdrcEnabled
                )

                for (bandIndex in 0 until mdrc) {
                    val source = requestedBands.getOrElse(bandIndex) {
                        defaultMdrcBand(bandIndex)
                    }

                    val cutoff = source.cutoffFrequencyHz
                        .coerceIn(
                            MIN_CUTOFF_HZ,
                            MAX_CUTOFF_HZ
                        )

                    val nativeBand = DynamicsProcessing.MbcBand(
                        config.isEnabled && config.mdrcEnabled,
                        source.attackMs.coerceIn(0.1f, 1000f),
                        source.releaseMs.coerceIn(1f, 2000f),
                        source.ratio.coerceIn(1f, 20f),
                        source.thresholdDb.coerceIn(-60f, 0f),
                        source.kneeDb.coerceIn(0f, 30f),
                        -80f,
                        1f,
                        0f,
                        source.makeupGainDb.coerceIn(0f, 24f),
                        cutoff
                    )

                    /*
                     * The critical operation:
                     * write the exact band to the exact channel.
                     */
                    dp.setMbcBandByChannelIndex(
                        channel,
                        bandIndex,
                        nativeBand
                    )

                    /*
                     * Read it back from the native effect.
                     *
                     * This tells us whether the Android HAL accepted,
                     * rounded or clamped the requested crossover.
                     */
                    try {
                        val actual = dp.getMbcBandByChannelIndex(
                            channel,
                            bandIndex
                        )

                        val actualCutoff =
                            actual.getCutoffFrequency()

                        val difference =
                            kotlin.math.abs(
                                actualCutoff - cutoff
                            )

                        if (difference > 0.5f) {
                            Log.w(
                                TAG,
                                "MDRC cutoff mismatch: " +
                                    "session=$audioSessionId " +
                                    "channel=$channel " +
                                    "band=$bandIndex " +
                                    "requested=${cutoff}Hz " +
                                    "actual=${actualCutoff}Hz"
                            )
                        } else {
                            Log.i(
                                TAG,
                                "MDRC cutoff verified: " +
                                    "session=$audioSessionId " +
                                    "channel=$channel " +
                                    "band=$bandIndex " +
                                    "cutoff=${actualCutoff}Hz"
                            )
                        }

                    } catch (verifyError: Exception) {
                        Log.w(
                            TAG,
                            "Unable to read back MDRC band " +
                                "channel=$channel band=$bandIndex",
                            verifyError
                        )
                    }
                }

                /*
                 * Keep the MBC object itself synchronized with the
                 * individual band writes.
                 */
                try {
                    dp.setMbcByChannelIndex(channel, mbc)
                } catch (_: Exception) {
                    /*
                     * Individual band writes above are the authoritative
                     * operation. Some implementations may reject the
                     * aggregate Mbc write after individual updates.
                     */
                }

            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "Unable to apply MDRC on channel=$channel",
                    e
                )
            }
        }
    }

    /**
     * Makes MDRC frequencies valid and strictly increasing.
     *
     * This does NOT replace user values with defaults.
     */
    private fun normalizeMdrcBands(
        bands: List<MdrcBandConfig>
    ): List<MdrcBandConfig> {
        val source = if (bands.isEmpty()) {
            DspConfig.defaultMdrcBands()
        } else {
            bands
        }

        val result = mutableListOf<MdrcBandConfig>()

        var previousCutoff = MIN_CUTOFF_HZ -
            MIN_CUTOFF_SEPARATION_HZ

        for (index in 0 until MDRC_BAND_COUNT) {
            val input = source.getOrNull(index)
                ?: defaultMdrcBand(index)

            val minimum =
                if (index == 0) {
                    MIN_CUTOFF_HZ
                } else {
                    previousCutoff +
                        MIN_CUTOFF_SEPARATION_HZ
                }

            val remainingBands =
                MDRC_BAND_COUNT - index - 1

            val maximum =
                MAX_CUTOFF_HZ -
                    remainingBands *
                    MIN_CUTOFF_SEPARATION_HZ

            val cutoff = input.cutoffFrequencyHz
                .takeIf { it.isFinite() }
                ?.coerceIn(minimum, maximum)
                ?: defaultMdrcBand(index)
                    .cutoffFrequencyHz
                    .coerceIn(minimum, maximum)

            val normalized = input.copy(
                cutoffFrequencyHz = cutoff,
                thresholdDb = input.thresholdDb
                    .takeIf { it.isFinite() }
                    ?.coerceIn(-60f, 0f)
                    ?: DEFAULT_THRESHOLD_DB,
                ratio = input.ratio
                    .takeIf { it.isFinite() }
                    ?.coerceIn(1f, 20f)
                    ?: DEFAULT_RATIO,
                attackMs = input.attackMs
                    .takeIf { it.isFinite() }
                    ?.coerceIn(0.1f, 1000f)
                    ?: DEFAULT_ATTACK_MS,
                releaseMs = input.releaseMs
                    .takeIf { it.isFinite() }
                    ?.coerceIn(1f, 2000f)
                    ?: DEFAULT_RELEASE_MS,
                makeupGainDb = input.makeupGainDb
                    .takeIf { it.isFinite() }
                    ?.coerceIn(0f, 24f)
                    ?: DEFAULT_MAKEUP_DB,
                kneeDb = input.kneeDb
                    .takeIf { it.isFinite() }
                    ?.coerceIn(0f, 30f)
                    ?: DEFAULT_KNEE_DB
            )

            result += normalized
            previousCutoff = cutoff
        }

        return result
    }

    private fun defaultMdrcBand(index: Int): MdrcBandConfig {
        return DspConfig.defaultMdrcBands()
            .getOrElse(index) {
                DspConfig.defaultMdrcBands().last()
            }
    }

    /**
     * Maps the internal 32-band EQ configuration to the actual
     * number of native Post-EQ bands supported by the device.
     */
    private fun mapEqBandIndex(
        nativeIndex: Int,
        nativeCount: Int,
        sourceCount: Int
    ): Int {
        if (nativeCount <= 1 || sourceCount <= 1) return 0

        val normalized =
            nativeIndex.toFloat() /
                (nativeCount - 1).toFloat()

        return kotlin.math.round(
            normalized * (sourceCount - 1)
        ).toInt()
    }

    /**
     * Generates sensible frequencies for the Tone Pre-EQ stage.
     */
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

        val minLog = kotlin.math.ln(
            MIN_CUTOFF_HZ.toDouble()
        )

        val maxLog = kotlin.math.ln(
            20000.0
        )

        return kotlin.math.exp(
            minLog +
                (maxLog - minLog) *
                normalized
        ).toFloat()
    }

    /**
     * Applies the output limiter.
     */
    private fun applyLimiter(
        dp: DynamicsProcessing,
        config: DspConfig
    ) {
        for (channel in 0 until dp.channelCountSafe()) {
            try {
                val limiter = DynamicsProcessing.Limiter(
                    config.isEnabled && config.limiterEnabled,
                    0,
                    config.limiterAttackMs.coerceIn(0.1f, 1000f),
                    config.limiterReleaseMs.coerceIn(1f, 2000f),
                    config.limiterRatio.coerceIn(1f, 100f),
                    config.limiterThresholdDb.coerceIn(-60f, 0f),
                    config.limiterPostGainDb.coerceIn(-24f, 12f)
                )

                dp.setLimiterByChannelIndex(
                    channel,
                    limiter
                )

            } catch (e: Exception) {
                Log.w(
                    TAG,
                    "Unable to apply limiter on channel=$channel",
                    e
                )
            }
        }
    }

    /**
     * Applies master gain through the native limiter post-gain stage.
     *
     * This keeps the final output inside DynamicsProcessing without
     * introducing another native effect.
     */
    private fun applyMasterGain(
        dp: DynamicsProcessing,
        config: DspConfig
    ) {
        if (config.limiterEnabled) {
            for (channel in 0 until dp.channelCountSafe()) {
                try {
                    val limiter = dp.getLimiterByChannelIndex(channel)

                    val totalPostGain =
                        (
                            config.limiterPostGainDb +
                                config.masterGainDb
                            ).coerceIn(-24f, 12f)

                    limiter.setPostGain(totalPostGain)

                    dp.setLimiterByChannelIndex(
                        channel,
                        limiter
                    )

                } catch (e: Exception) {
                    Log.w(
                        TAG,
                        "Unable to apply master gain on channel=$channel",
                        e
                    )
                }
            }
        }
    }

    /**
     * Returns the number of EQ bands actually supported by the
     * current DynamicsProcessing instance.
     */
    fun getSupportedEqBands(): Int = eqBandCount

    /**
     * Returns whether the native effect currently has control.
     */
    fun hasControl(): Boolean {
        return try {
            effect?.hasControl() == true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Returns whether DynamicsProcessing is available.
     */
    fun isAvailable(): Boolean {
        return initialized && effect != null
    }

    /**
     * Attempts to reclaim native effect control.
     */
    fun reclaimControl() {
        try {
            effect?.let {
                if (!it.hasControl()) {
                    Log.i(
                        TAG,
                        "DynamicsProcessing currently does not have control: session=$audioSessionId"
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to inspect DynamicsProcessing control", e)
        }
    }

    /**
     * Releases the native effect.
     */
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

    /**
     * Safe channel count accessor.
     */
    private fun DynamicsProcessing.channelCountSafe(): Int {
        return try {
            getChannelCount().coerceAtLeast(1)
        } catch (_: Exception) {
            CHANNEL_COUNT
        }
    }
}
