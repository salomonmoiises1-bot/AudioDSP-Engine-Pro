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

        private const val PRIORITY = Int.MAX_VALUE
        private const val CHANNEL_COUNT = 2

        private const val REQUESTED_EQ_BANDS = 128
        private const val FALLBACK_EQ_BANDS_127 = 127
        private const val FALLBACK_EQ_BANDS_32 = 32

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
        private const val DP_FRAME_DURATION_MS = 80f
        private const val MIN_EQ_WRITE_SPACING_MS = 50L
    }

    data class EqBackendInfo(
        val requestedBandCount: Int,
        val actualBandCount: Int,
        val frequenciesHz: List<Float>,
        val channelCount: Int,
        val isAvailable: Boolean
    )

    private var effect: DynamicsProcessing? = null
    private var eqBandCount: Int = 0
    private var initialized = false

    /** True when the live DP was created with both Pre-EQ and Post-EQ stages. */
    @Volatile
    private var interleaveEnabled = false

    /*
     * Equalizer314-style asynchronous DP writer.
     *
     * Graph changes are converted once, then the complete EQ stage is
     * written on a worker. This avoids dozens of Binder writes and avoids
     * rebuilding DynamicsProcessing during slider movement.
     */
    private val eqWorkerThread =
        android.os.HandlerThread("sBz-EqDpWorker").apply {
            start()
        }

    private val eqWorker =
        android.os.Handler(eqWorkerThread.looper)

    @Volatile
    private var pendingEqWrite: Runnable? = null

    @Volatile
    private var lastEqWriteMs: Long = 0L

    @Volatile
    private var eqBackendInfo =
        EqBackendInfo(
            requestedBandCount = REQUESTED_EQ_BANDS,
            actualBandCount = 0,
            frequenciesHz = emptyList(),
            channelCount = 0,
            isAvailable = false
        )

    fun getEqBackendInfo(): EqBackendInfo =
        eqBackendInfo

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            initialize()
        } else {
            Log.w(
                TAG,
                "DynamicsProcessing requires API 28+"
            )
        }
    }

    private fun initialize() {
        if (initialized) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return

        var lastError: Throwable? = null

        /*
         * The constructor is the real capability test. A Config.Builder can
         * succeed even when the vendor effect later rejects a stage/count.
         *
         * Try Equalizer314-style interleaving first and then fall back to
         * fewer bands or a single EQ stage.
         */
        for ((config, requestedInterleave) in createInitialConfigs()) {
            try {
                val dp =
                    DynamicsProcessing(
                        PRIORITY,
                        audioSessionId,
                        config
                    )

                dp.setControlStatusListener { _, hasControl ->
                    Log.i(
                        TAG,
                        "DynamicsProcessing control changed: " +
                            "hasControl=$hasControl " +
                            "session=$audioSessionId"
                    )

                    if (!hasControl) {
                        onControlLost?.invoke()
                    }
                }

                dp.setEnabled(true)

                effect = dp
                initialized = true

                refreshEqBackendInfo(dp)

                /*
                 * Use the actual live configuration, not only the requested
                 * configuration.
                 */
                interleaveEnabled =
                    requestedInterleave &&
                        try {
                            dp.getConfig().postEqBandCount ==
                                dp.getConfig().preEqBandCount &&
                                dp.getConfig().postEqBandCount > 0
                        } catch (_: Throwable) {
                            false
                        }

                Log.i(
                    TAG,
                    "DynamicsProcessing initialized: " +
                        "session=$audioSessionId " +
                        "preEq=$eqBandCount " +
                        "postEq=" +
                        try {
                            dp.getConfig().postEqBandCount
                        } catch (_: Throwable) {
                            0
                        } +
                        " interleave=$interleaveEnabled " +
                        "channels=${dp.channelCountSafe()}"
                )

                logEqBackendMap(dp)

                return
            } catch (t: Throwable) {
                lastError = t
                effect = null
                initialized = false
                interleaveEnabled = false

                Log.w(
                    TAG,
                    "DynamicsProcessing rejected config: " +
                        "pre=${config.preEqBandCount} " +
                        "post=${config.postEqBandCount} " +
                        "interleave=$requestedInterleave",
                    t
                )
            }
        }

        Log.e(
            TAG,
            "Unable to initialize DynamicsProcessing " +
                "for session=$audioSessionId",
            lastError
        )

        releaseInternal()
    }

    private fun createInitialConfigs():
        List<Pair<DynamicsProcessing.Config, Boolean>> {

        val result =
            mutableListOf<Pair<DynamicsProcessing.Config, Boolean>>()

        val bandCandidates =
            intArrayOf(
                REQUESTED_EQ_BANDS,
                FALLBACK_EQ_BANDS_127,
                FALLBACK_EQ_BANDS_32
            )

        for (interleaveCandidate in booleanArrayOf(true, false)) {
            for (bandCount in bandCandidates) {
                try {
                    val config =
                        DynamicsProcessing.Config.Builder(
                            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                            CHANNEL_COUNT,
                            true,
                            bandCount,
                            true,
                            MDRC_BAND_COUNT,
                            interleaveCandidate,
                            if (interleaveCandidate) bandCount else 0,
                            true
                        )
                            .setPreferredFrameDuration(
                                DP_FRAME_DURATION_MS
                            )
                            .build()

                    result +=
                        config to interleaveCandidate

                } catch (e: Throwable) {
                    Log.w(
                        TAG,
                        "Could not build DP config " +
                            "pre=$bandCount " +
                            "interleave=$interleaveCandidate",
                        e
                    )
                }
            }
        }

        return result
    }

    /**
     * Legacy vendor Equalizer path intentionally disabled.
     *
     * sBz now uses high-resolution DynamicsProcessing as the
     * single graphic-EQ backend.
     */
    private fun hasNativeGraphicEq(): Boolean =
        false

    /**
     * Reads back the configuration that the running effect actually exposes.
     *
     * This is intentionally separate from the requested logical bands:
     * the native backend may accept a different physical band count.
     */
    private fun refreshEqBackendInfo(
        dp: DynamicsProcessing
    ) {
        val actualCount =
            try {
                dp.getConfig().preEqBandCount
            } catch (_: Exception) {
                0
            }

        val frequencies =
            buildList {
                for (index in 0 until actualCount) {
                    try {
                        add(
                            dp
                                .getPreEqByChannelIndex(0)
                                .getBand(index)
                                .getCutoffFrequency()
                        )
                    } catch (_: Exception) {
                        break
                    }
                }
            }

        eqBandCount = actualCount

        eqBackendInfo =
            EqBackendInfo(
                requestedBandCount = REQUESTED_EQ_BANDS,
                actualBandCount = actualCount,
                frequenciesHz = frequencies,
                channelCount = dp.channelCountSafe(),
                isAvailable = actualCount > 0
            )
    }

    private fun logEqBackendMap(
        dp: DynamicsProcessing
    ) {
        val info = eqBackendInfo

        Log.i(
            TAG,
            "EQ backend map: " +
                "requested=${info.requestedBandCount}, " +
                "actual=${info.actualBandCount}, " +
                "channels=${info.channelCount}"
        )

        info.frequenciesHz.forEachIndexed { index, frequency ->
            Log.i(
                TAG,
                "EQ physical band[$index] cutoff=${frequency}Hz"
            )
        }

        if (info.actualBandCount != REQUESTED_EQ_BANDS) {
            Log.w(
                TAG,
                "EQ backend exposes " +
                    "${info.actualBandCount} physical bands; " +
                    "sBz retains $REQUESTED_EQ_BANDS logical bands. " +
                    "Logical-to-physical mapping is lossy until " +
                    "a PCM/software backend is available."
            )
        }
    }

    /**
     * Update one MDRC band without rebuilding the rest of the DSP chain.
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

        val mdrcCount =
            dp.getConfig().mbcBandCount

        if (
            mdrcCount <= 0 ||
            bandIndex !in 0 until mdrcCount
        ) {
            return
        }

        /*
         * Normalize the complete crossover set first.
         * This guarantees monotonically increasing cutoffs.
         */
        val bands =
            normalizeMdrcBands(
                config.mdrcBands
            )

        val source =
            bands.getOrNull(bandIndex)
                ?: defaultMdrcBand(bandIndex)

        val mdrcActive =
            config.isEnabled &&
                config.mdrcEnabled

        for (
            channel in 0 until dp.channelCountSafe()
        ) {
            try {
                val nativeBand =
                    createNativeMdrcBand(
                        source,
                        mdrcActive
                    )

                dp.setMbcBandByChannelIndex(
                    channel,
                    bandIndex,
                    nativeBand
                )

                val accepted =
                    dp
                        .getMbcBandByChannelIndex(
                            channel,
                            bandIndex
                        )
                        .getCutoffFrequency()

                if (
                    kotlin.math.abs(
                        accepted -
                            source.cutoffFrequencyHz
                    ) > 0.5f
                ) {
                    Log.w(
                        TAG,
                        "MDRC HAL adjusted cutoff: " +
                            "session=$audioSessionId " +
                            "channel=$channel " +
                            "band=$bandIndex " +
                            "requested=${source.cutoffFrequencyHz}Hz " +
                            "accepted=${accepted}Hz"
                    )
                }

            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "Unable to update MDRC " +
                        "band=$bandIndex " +
                        "on channel=$channel",
                    e
                )
            }
        }
    }

    private fun createNativeMdrcBand(
        source: MdrcBandConfig,
        enabled: Boolean
    ): DynamicsProcessing.MbcBand {

        return DynamicsProcessing.MbcBand(
            enabled,
            source.cutoffFrequencyHz.coerceIn(
                MIN_CUTOFF_HZ,
                MAX_CUTOFF_HZ
            ),
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
    }

    @Synchronized
    fun updateMasterGain(
        config: DspConfig
    ) {
        if (!initialized || effect == null) {
            initialize()
        }

        val dp = effect ?: return

        if (!config.isEnabled) return

        for (
            channel in 0 until dp.channelCountSafe()
        ) {
            try {
                val limiter =
                    dp.getLimiterByChannelIndex(
                        channel
                    )

                limiter.setPostGain(
                    (
                        config.limiterPostGainDb +
                            config.masterGainDb
                        ).coerceIn(
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
                    "Unable to update master gain " +
                        "on channel=$channel",
                    e
                )
            }
        }
    }

    @Synchronized
    fun updateBalance(
        config: DspConfig
    ) {
        if (!initialized || effect == null) {
            initialize()
        }

        val dp = effect ?: return

        val headroom =
            if (
                config.autoGainEnabled &&
                config.isEnabled
            ) {
                config.computeHeadroomSafeguard()
            } else {
                0f
            }

        for (
            channel in 0 until dp.channelCountSafe()
        ) {
            try {
                val gain =
                    (
                        config.preGainDb +
                            headroom +
                            balanceCompensationDb(
                                channel,
                                config.balance
                            )
                        ).coerceIn(
                            -12f,
                            12f
                        )

                dp.setInputGainbyChannel(
                    channel,
                    gain
                )

            } catch (e: Exception) {
                Log.w(
                    TAG,
                    "Unable to update balance " +
                        "on channel=$channel",
                    e
                )
            }
        }
    }

    @Synchronized
    fun updateTone(
        config: DspConfig
    ) {
        if (!initialized || effect == null) {
            initialize()
        }

        applyParametricEq(
            config,
            freezeLayout = true
        )
    }

    @Synchronized
    fun updateEqBand(
        config: DspConfig,
        sourceIndex: Int
    ) {
        if (
            sourceIndex !in
            config.eqGains.indices
        ) {
            return
        }

        if (!initialized || effect == null) {
            initialize()
        }

        /*
         * IMPORTANT:
         *
         * Individual logical EQ-band changes use the SAME
         * Equalizer314-style parametric path as the complete EQ.
         *
         * There is no separate legacy/native 5-band path here.
         */
        applyParametricEq(
            config,
            freezeLayout = true
        )
    }

    @Synchronized
    fun applyConfig(
        config: DspConfig
    ) {
        if (!initialized || effect == null) {
            initialize()
        }

        val dp = effect ?: return

        try {
            ParametricToDpConverter.layoutFrozen = false

            dp.setEnabled(
                config.isEnabled
            )

            val automaticHeadroom =
                if (
                    config.autoGainEnabled &&
                    config.isEnabled
                ) {
                    config.computeHeadroomSafeguard()
                } else {
                    0f
                }

            dp.setInputGainAllChannelsTo(
                (
                    config.preGainDb +
                        automaticHeadroom
                    ).coerceIn(
                        -12f,
                        12f
                    )
            )

            applyPreEq(
                dp,
                config
            )

            applyMdrc(
                dp,
                config
            )

            applyGraphicEq(
                dp,
                config
            )

            applyLimiter(
                dp,
                config
            )

            if (config.isEnabled) {
                applyMasterGain(
                    dp,
                    config
                )
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

    /**
     * Builds the 32-band sBz graphic curve using the Equalizer314 method:
     *
     * 32 logical bands
     * -> ParametricEqualizer
     * -> Biquad response
     * -> converter
     * -> PRE/POST DynamicsProcessing stages.
     */
    private fun applyPreEq(
        dp: DynamicsProcessing,
        config: DspConfig
    ) {
        applyParametricEq(
            config,
            freezeLayout = true
        )
    }

    /**
     * Equalizer314-style parametric EQ path.
     *
     * Logical source:
     *   DspConfig.FREQUENCIES / eqGains
     *
     * Processing:
     *   ParametricEqualizer
     *       ->
     *   BiquadFilter RBJ
     *       ->
     *   ParametricToDpConverter
     *       ->
     *   DynamicsProcessing PRE/POST EQ
     *
     * The asynchronous writer uses explicit immutable snapshots so
     * the worker never depends on ambiguous captured local references.
     */
    private fun applyParametricEq(
        config: DspConfig,
        freezeLayout: Boolean
    ) {
        val dp = effect ?: return

        val physicalCount =
            try {
                dp.getConfig().preEqBandCount
            } catch (_: Exception) {
                return
            }

        if (physicalCount <= 0) return

        ParametricToDpConverter.setNumBands(
            physicalCount
        )

        ParametricToDpConverter.deviceSampleRateHz =
            48000f

        /*
         * Equalizer314-style source:
         *
         * 32 logical sBz bands
         *        ↓
         * ParametricEqualizer
         *        ↓
         * RBJ Biquad filters
         *        ↓
         * ParametricToDpConverter
         *        ↓
         * DynamicsProcessing PRE/POST EQ
         */
        val eq =
            ParametricEqualizer(
                48000
            )

        eq.clearBands()

        val enabled =
            config.isEnabled

        /*
         * Keep the complete logical 32-band curve.
         */
        for (
            index in DspConfig.FREQUENCIES.indices
        ) {
            eq.addBand(
                frequency =
                    DspConfig.FREQUENCIES[index],

                gain =
                    if (enabled) {
                        config.eqGains
                            .getOrElse(index) {
                                0f
                            }
                            .coerceIn(
                                -15f,
                                15f
                            )
                    } else {
                        0f
                    },

                filterType =
                    BiquadFilter.FilterType.BELL,

                q = 4.318
            )
        }

        /*
         * Tone controls are folded into the SAME
         * parametric curve.
         *
         * No second graphic EQ path is created.
         */
        if (enabled) {

            if (config.toneBassDb != 0f) {
                eq.addBand(
                    120f,
                    config.toneBassDb.coerceIn(
                        -12f,
                        12f
                    ),
                    BiquadFilter.FilterType.BELL,
                    0.707
                )
            }

            if (config.toneMidDb != 0f) {
                eq.addBand(
                    1000f,
                    config.toneMidDb.coerceIn(
                        -12f,
                        12f
                    ),
                    BiquadFilter.FilterType.BELL,
                    0.707
                )
            }

            if (config.toneTrebleDb != 0f) {
                eq.addBand(
                    8000f,
                    config.toneTrebleDb.coerceIn(
                        -12f,
                        12f
                    ),
                    BiquadFilter.FilterType.BELL,
                    0.707
                )
            }

            if (
                config.bassBoostEnabled &&
                config.bassBoostStrength > 0
            ) {
                val boost =
                    (
                        config.bassBoostStrength
                            .coerceIn(0, 1000) /
                            1000f
                        ) * 6f

                eq.addBand(
                    100f,
                    boost,
                    BiquadFilter.FilterType.LOW_SHELF,
                    0.707
                )
            }
        }

        eq.isEnabled =
            enabled

        ParametricToDpConverter.layoutFrozen =
            freezeLayout

        val useInterleave =
            interleaveEnabled &&
                try {
                    dp.getConfig().postEqBandCount ==
                        physicalCount
                } catch (_: Exception) {
                    false
                }

        /*
         * Convert the COMPLETE parametric curve before
         * entering the asynchronous worker.
         */
        val converted =
            if (useInterleave) {
                ParametricToDpConverter
                    .convertInterleaved(eq)
            } else {
                ParametricToDpConverter
                    .convertFeatureAware(eq)
            }

        /*
         * Explicitly name the converter output.
         *
         * These are source arrays only. They are copied below
         * before crossing the asynchronous Handler boundary.
         */
        val sourcePreCutoffs =
            converted.cutoffs

        val sourcePreGains =
            converted.gains

        val sourcePostCutoffs =
            if (useInterleave) {
                converted.postCutoffs
            } else {
                null
            }

        val sourcePostGains =
            if (useInterleave) {
                converted.postGains
            } else {
                null
            }

        val n =
            minOf(
                physicalCount,
                sourcePreCutoffs.size,
                sourcePreGains.size
            )

        if (n <= 0) return

        /*
         * Explicit immutable snapshots.
         *
         * These are the ONLY arrays accessed by the Runnable.
         */
        val finalPreCutoffs =
            sourcePreCutoffs.copyOf(n)

        val finalPreGains =
            sourcePreGains.copyOf(n)

        val finalPost =
            if (
                sourcePostCutoffs != null &&
                sourcePostGains != null &&
                sourcePostCutoffs.size >= n &&
                sourcePostGains.size >= n
            ) {
                Pair(
                    sourcePostCutoffs.copyOf(n),
                    sourcePostGains.copyOf(n)
                )
            } else {
                null
            }

        val writeEnabled =
            enabled

        /*
         * Only the final native EQ write happens on the worker.
         *
         * The DynamicsProcessing effect is NOT rebuilt
         * for every slider movement.
         */
        val job =
            Runnable {
                try {
                    if (!dp.hasControl()) {
                        Log.w(
                            TAG,
                            "DP lost control; EQ write skipped " +
                                "for session=$audioSessionId"
                        )

                        return@Runnable
                    }

                    for (
                        channel in
                        0 until dp.channelCountSafe()
                    ) {

                        /*
                         * PRE-EQ
                         */
                        val preStage =
                            DynamicsProcessing.Eq(
                                true,
                                true,
                                n
                            )

                        for (i in 0 until n) {
                            preStage.setBand(
                                i,
                                DynamicsProcessing.EqBand(
                                    writeEnabled,

                                    finalPreCutoffs[i]
                                        .coerceIn(
                                            MIN_CUTOFF_HZ,
                                            MAX_CUTOFF_HZ
                                        ),

                                    finalPreGains[i]
                                        .coerceIn(
                                            -15f,
                                            15f
                                        )
                                )
                            )
                        }

                        dp.setPreEqByChannelIndex(
                            channel,
                            preStage
                        )

                        /*
                         * POST-EQ
                         *
                         * Used when the live DP supports
                         * Equalizer314-style interleaving.
                         */
                        finalPost?.let { postData ->

                            val postStage =
                                DynamicsProcessing.Eq(
                                    true,
                                    true,
                                    n
                                )

                            for (i in 0 until n) {
                                postStage.setBand(
                                    i,
                                    DynamicsProcessing.EqBand(
                                        writeEnabled,

                                        postData.first[i]
                                            .coerceIn(
                                                MIN_CUTOFF_HZ,
                                                MAX_CUTOFF_HZ
                                            ),

                                        postData.second[i]
                                            .coerceIn(
                                                -15f,
                                                15f
                                            )
                                    )
                                )
                            }

                            dp.setPostEqByChannelIndex(
                                channel,
                                postStage
                            )
                        }
                    }

                    lastEqWriteMs =
                        android.os.SystemClock
                            .uptimeMillis()

                } catch (e: Throwable) {
                    Log.e(
                        TAG,
                        "High-resolution EQ write failed",
                        e
                    )
                } finally {
                    pendingEqWrite =
                        null
                }
            }

        /*
         * Cancel the previous pending graph update.
         *
         * Rapid slider movements therefore do not generate
         * a Binder write for every intermediate position.
         */
        pendingEqWrite?.let {
            eqWorker.removeCallbacks(
                it
            )
        }

        pendingEqWrite =
            job

        val delay =
            (
                lastEqWriteMs +
                    MIN_EQ_WRITE_SPACING_MS -
                    android.os.SystemClock
                        .uptimeMillis()
                )
                .coerceIn(
                    0L,
                    MIN_EQ_WRITE_SPACING_MS
                )

        eqWorker.postDelayed(
            job,
            delay
        )
    }

    /**
     * The graphic EQ is already rendered by
     * applyParametricEq into PRE/POST.
     */
    private fun applyGraphicEq(
        dp: DynamicsProcessing,
        config: DspConfig
    ) {
        /*
         * Intentionally empty.
         *
         * This prevents a second graphic EQ curve from being
         * stacked on top of the Equalizer314-style curve.
         */
    }

    /**
     * Applies MDRC crossover frequencies and dynamics.
     *
     * Each MbcBand is written directly to the native effect.
     *
     * We intentionally DO NOT call setMbcByChannelIndex()
     * afterwards because doing so can overwrite individual
     * band values with a stale Mbc configuration.
     */
    private fun applyMdrc(
        dp: DynamicsProcessing,
        config: DspConfig
    ) {
        val mdrcCount =
            dp.getConfig().mbcBandCount

        if (mdrcCount <= 0) return

        val requestedBands =
            normalizeMdrcBands(
                config.mdrcBands
            )

        val mdrcActive =
            config.isEnabled &&
                config.mdrcEnabled

        for (
            channel in 0 until dp.channelCountSafe()
        ) {
            try {
                /*
                 * Build the complete native MBC off to the side,
                 * then submit the stage once.
                 */
                val nativeMbc =
                    DynamicsProcessing.Mbc(
                        config.isEnabled,
                        mdrcActive,
                        mdrcCount
                    )

                for (
                    bandIndex in 0 until mdrcCount
                ) {
                    val source =
                        requestedBands.getOrElse(
                            bandIndex
                        ) {
                            defaultMdrcBand(
                                bandIndex
                            )
                        }

                    nativeMbc.setBand(
                        bandIndex,
                        createNativeMdrcBand(
                            source,
                            mdrcActive
                        )
                    )
                }

                dp.setMbcByChannelIndex(
                    channel,
                    nativeMbc
                )

                /*
                 * Verify the actual crossover values
                 * after the HAL accepted them.
                 */
                for (
                    bandIndex in 0 until mdrcCount
                ) {
                    val requested =
                        requestedBands
                            .getOrElse(
                                bandIndex
                            ) {
                                defaultMdrcBand(
                                    bandIndex
                                )
                            }
                            .cutoffFrequencyHz

                    val accepted =
                        dp
                            .getMbcBandByChannelIndex(
                                channel,
                                bandIndex
                            )
                            .getCutoffFrequency()

                    if (
                        kotlin.math.abs(
                            accepted - requested
                        ) > 0.5f
                    ) {
                        Log.w(
                            TAG,
                            "MDRC HAL adjusted cutoff: " +
                                "session=$audioSessionId " +
                                "channel=$channel " +
                                "band=$bandIndex " +
                                "requested=${requested}Hz " +
                                "accepted=${accepted}Hz"
                        )
                    }
                }

            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "Unable to apply MDRC " +
                        "on channel=$channel",
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

        for (
            index in 0 until MDRC_BAND_COUNT
        ) {
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
                    .takeIf {
                        it.isFinite()
                    }
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
                    cutoffFrequencyHz =
                        cutoff,

                    thresholdDb =
                        input.thresholdDb
                            .takeIf {
                                it.isFinite()
                            }
                            ?.coerceIn(
                                -60f,
                                0f
                            )
                            ?: DEFAULT_THRESHOLD_DB,

                    ratio =
                        input.ratio
                            .takeIf {
                                it.isFinite()
                            }
                            ?.coerceIn(
                                1f,
                                20f
                            )
                            ?: DEFAULT_RATIO,

                    attackMs =
                        input.attackMs
                            .takeIf {
                                it.isFinite()
                            }
                            ?.coerceIn(
                                0.1f,
                                1000f
                            )
                            ?: DEFAULT_ATTACK_MS,

                    releaseMs =
                        input.releaseMs
                            .takeIf {
                                it.isFinite()
                            }
                            ?.coerceIn(
                                1f,
                                2000f
                            )
                            ?: DEFAULT_RELEASE_MS,

                    makeupGainDb =
                        input.makeupGainDb
                            .takeIf {
                                it.isFinite()
                            }
                            ?.coerceIn(
                                0f,
                                24f
                            )
                            ?: DEFAULT_MAKEUP_DB,

                    kneeDb =
                        input.kneeDb
                            .takeIf {
                                it.isFinite()
                            }
                            ?.coerceIn(
                                0f,
                                30f
                            )
                            ?: DEFAULT_KNEE_DB
                )

            result +=
                normalized

            previousCutoff =
                cutoff
        }

        return result
    }

    private fun defaultMdrcBand(
        index: Int
    ): MdrcBandConfig {
        return DspConfig
            .defaultMdrcBands()
            .getOrElse(index) {
                DspConfig
                    .defaultMdrcBands()
                    .last()
            }
    }

    private fun nativeBandFrequency(
        nativeIndex: Int,
        nativeCount: Int,
        band: DynamicsProcessing.EqBand
    ): Float {
        return try {
            band.getCutoffFrequency()
        } catch (_: Exception) {
            calculateStageFrequency(
                nativeIndex,
                nativeCount
            )
        }
    }

    private fun nearestSourceBandIndex(
        frequencyHz: Float,
        sourceCount: Int
    ): Int {
        if (sourceCount <= 1) return 0

        val last =
            minOf(
                sourceCount,
                DspConfig.FREQUENCIES.size
            ) - 1

        var bestIndex = 0
        var bestDistance =
            Float.POSITIVE_INFINITY

        for (index in 0..last) {
            val distance =
                kotlin.math.abs(
                    kotlin.math.ln(
                        (
                            DspConfig.FREQUENCIES[index] /
                                frequencyHz
                                    .coerceAtLeast(1f)
                            ).toDouble()
                    )
                )

            if (distance < bestDistance) {
                bestDistance =
                    distance.toFloat()

                bestIndex =
                    index
            }
        }

        return bestIndex
    }

    private fun mapSourceFrequencyToNativeIndex(
        frequencyHz: Float,
        nativeCount: Int,
        dp: DynamicsProcessing
    ): Int {
        if (nativeCount <= 1) return 0

        val postEq =
            try {
                dp.getPostEqByChannelIndex(0)
            } catch (_: Exception) {
                return 0
            }

        var bestIndex = 0
        var bestDistance =
            Float.POSITIVE_INFINITY

        for (index in 0 until nativeCount) {
            val nativeFrequency =
                try {
                    postEq
                        .getBand(index)
                        .getCutoffFrequency()
                } catch (_: Exception) {
                    calculateStageFrequency(
                        index,
                        nativeCount
                    )
                }

            val distance =
                kotlin.math.abs(
                    kotlin.math.ln(
                        (
                            nativeFrequency /
                                frequencyHz
                                    .coerceAtLeast(1f)
                            ).toDouble()
                    )
                )

            if (distance < bestDistance) {
                bestDistance =
                    distance.toFloat()

                bestIndex =
                    index
            }
        }

        return bestIndex
    }

    private fun balanceCompensationDb(
        channel: Int,
        balance: Float
    ): Float {
        val b =
            balance.coerceIn(
                -1f,
                1f
            )

        if (b == 0f) return 0f

        val attenuation =
            1f -
                kotlin.math.abs(b)

        val db =
            20f *
                kotlin.math.log10(
                    attenuation.coerceAtLeast(
                        0.001f
                    )
                )

        return when {
            b < 0f &&
                channel == 1 ->
                db

            b > 0f &&
                channel == 0 ->
                db

            else ->
                0f
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
                (
                    maxLog -
                        minLog
                    ) *
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
        for (
            channel in 0 until dp.channelCountSafe()
        ) {
            try {
                val limiter =
                    DynamicsProcessing.Limiter(
                        config.isEnabled,

                        config.isEnabled &&
                            (
                                config.limiterEnabled ||
                                    config.masterGainDb != 0f ||
                                    config.limiterPostGainDb != 0f
                                ),

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

        for (
            channel in 0 until dp.channelCountSafe()
        ) {
            try {
                val limiter =
                    dp.getLimiterByChannelIndex(
                        channel
                    )

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
        initialized &&
            effect != null

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
        interleaveEnabled = false

        pendingEqWrite?.let {
            eqWorker.removeCallbacks(it)
        }

        pendingEqWrite = null

        eqWorkerThread.quitSafely()
    }

    private fun DynamicsProcessing.channelCountSafe(): Int {
        return try {
            getChannelCount().coerceAtLeast(1)
        } catch (_: Exception) {
            CHANNEL_COUNT
        }
    }
}
