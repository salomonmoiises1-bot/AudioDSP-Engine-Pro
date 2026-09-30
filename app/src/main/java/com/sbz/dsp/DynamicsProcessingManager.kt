package com.sbz.dsp

import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
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
        private const val NATIVE_EQ_PRIORITY = 110
        private const val NATIVE_EQ_MIN_BANDS = 32
        private const val CHANNEL_COUNT = 2

        // The T509A exposes 5 PRE-EQ + 5 POST-EQ physical bands.
        // We therefore keep the 32-band logical preset/UI model but use
        // a stable 10-filter hardware backend on this device class.
        private const val REQUESTED_EQ_BANDS = 32
        private const val PHYSICAL_PRE_EQ_BANDS = 5
        private const val PHYSICAL_POST_EQ_BANDS = 5

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

        // Stable centers/cutoffs for the ten-filter hardware representation.
        // PRE-EQ carries the lower/middle portion; POST-EQ carries the upper
        // portion. The HAL may quantize these values, so the applied values
        // are always read back by diagnostics.
        private val PRE_EQ_FREQUENCIES = floatArrayOf(
            40f, 100f, 250f, 630f, 1600f
        )
        private val POST_EQ_FREQUENCIES = floatArrayOf(
            3150f, 5000f, 8000f, 12500f, 20000f
        )
    }

    data class EqBackendInfo(
        val requestedBandCount: Int,
        val actualBandCount: Int,
        val frequenciesHz: List<Float>,
        val channelCount: Int,
        val isAvailable: Boolean
    )

    private var effect: DynamicsProcessing? = null
    private var nativeEqualizer: Equalizer? = null
    private var nativeEqMap: IntArray = IntArray(0)
    private var eqBandCount: Int = 0
    private var initialized = false

    @Volatile
    private var eqBackendInfo = EqBackendInfo(
        requestedBandCount = REQUESTED_EQ_BANDS,
        actualBandCount = 0,
        frequenciesHz = emptyList(),
        channelCount = 0,
        isAvailable = false
    )

    fun getEqBackendInfo(): EqBackendInfo = eqBackendInfo

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
            refreshEqBackendInfo(dp)
            initializeNativeEqualizer()

            Log.i(
                TAG,
                "DynamicsProcessing initialized: " +
                    "session=$audioSessionId eqBands=$eqBandCount " +
                    "channels=${dp.channelCountSafe()}"
            )
            logEqBackendMap(dp)

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

    /**
     * Some vendor devices expose a richer legacy Equalizer effect than
     * DynamicsProcessing. If it exposes at least 32 bands, use it as the
     * graphic-EQ stage and leave DynamicsProcessing responsible for the
     * remaining DSP stages. This is particularly important on devices where
     * the vendor audio stack exposes 32+ physical EQ bands.
     */
    private fun initializeNativeEqualizer() {
        try {
            val eq = Equalizer(NATIVE_EQ_PRIORITY, audioSessionId)
            val bandCount = eq.numberOfBands.toInt()
            val range = eq.bandLevelRange

            if (bandCount >= NATIVE_EQ_MIN_BANDS && range.size >= 2) {
                nativeEqualizer = eq
                nativeEqMap = buildNativeEqMap(eq, DspConfig.FREQUENCIES)
                Log.i(
                    TAG,
                    "Native Equalizer available: session=$audioSessionId " +
                        "bands=$bandCount levelRange=${range[0]}..${range[1]}mB"
                )
                nativeEqMap.forEachIndexed { logical, physical ->
                    Log.i(
                        TAG,
                        "Native EQ map logical[$logical]=${DspConfig.FREQUENCIES[logical]}Hz -> physical[$physical]=" +
                            "${eq.getCenterFreq(physical.toShort()) / 1000f}Hz"
                    )
                }
            } else {
                Log.i(
                    TAG,
                    "Native Equalizer present but not selected: bands=$bandCount"
                )
                eq.release()
            }
        } catch (e: Exception) {
            Log.i(
                TAG,
                "Native Equalizer unavailable for session=$audioSessionId: ${e.message}"
            )
            nativeEqualizer = null
            nativeEqMap = IntArray(0)
        }
    }

    private fun buildNativeEqMap(
        eq: Equalizer,
        targetsHz: FloatArray
    ): IntArray {
        val count = eq.numberOfBands.toInt()
        val used = BooleanArray(count)
        return IntArray(targetsHz.size) { targetIndex ->
            var best = -1
            var bestDistance = Double.POSITIVE_INFINITY

            for (physical in 0 until count) {
                if (used[physical]) continue
                val centerHz = eq.getCenterFreq(physical.toShort()) / 1000.0
                val targetHz = targetsHz[targetIndex].toDouble()
                val distance = kotlin.math.abs(
                    kotlin.math.ln(
                        (centerHz / targetHz.coerceAtLeast(1.0))
                    )
                )
                if (distance < bestDistance) {
                    bestDistance = distance
                    best = physical
                }
            }

            if (best < 0) {
                best = 0
            }
            used[best] = true
            best
        }
    }

    private fun applyNativeGraphicEq(config: DspConfig) {
        val eq = nativeEqualizer ?: return
        if (nativeEqMap.size != DspConfig.FREQUENCIES.size) return

        try {
            val range = eq.bandLevelRange
            val minMb = range[0].toInt()
            val maxMb = range[1].toInt()

            for (logicalIndex in DspConfig.FREQUENCIES.indices) {
                val physicalIndex = nativeEqMap[logicalIndex]
                val gainDb = if (config.isEnabled) {
                    config.eqGains.getOrElse(logicalIndex) { 0f }
                } else {
                    0f
                }
                val millibels = (gainDb * 100f)
                    .toInt()
                    .coerceIn(minMb, maxMb)
                    .toShort()
                eq.setBandLevel(physicalIndex.toShort(), millibels)
            }

            if (eq.enabled != config.isEnabled) {
                eq.enabled = config.isEnabled
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to apply native graphic EQ on session=$audioSessionId", e)
        }
    }

    private fun hasNativeGraphicEq(): Boolean =
        nativeEqualizer != null && nativeEqMap.size == DspConfig.FREQUENCIES.size

    /**
     * Reads back the configuration that the running effect actually exposes.
     * This is intentionally separate from the requested 32 logical bands: the
     * native backend may accept a different physical band count.
     */
    private fun refreshEqBackendInfo(dp: DynamicsProcessing) {
        val preCount = try {
            dp.getConfig().preEqBandCount
        } catch (_: Exception) {
            0
        }
        val postCount = try {
            dp.getConfig().postEqBandCount
        } catch (_: Exception) {
            0
        }
        val actualCount = preCount + postCount

        val frequencies = buildList {
            for (index in 0 until preCount) {
                try {
                    add(dp.getPreEqByChannelIndex(0).getBand(index).getCutoffFrequency())
                } catch (_: Exception) {
                    break
                }
            }
            for (index in 0 until postCount) {
                try {
                    add(dp.getPostEqByChannelIndex(0).getBand(index).getCutoffFrequency())
                } catch (_: Exception) {
                    break
                }
            }
        }

        eqBandCount = actualCount
        eqBackendInfo = EqBackendInfo(
            requestedBandCount = REQUESTED_EQ_BANDS,
            actualBandCount = actualCount,
            frequenciesHz = frequencies,
            channelCount = dp.channelCountSafe(),
            isAvailable = actualCount > 0
        )
    }

    private fun logEqBackendMap(dp: DynamicsProcessing) {
        val info = eqBackendInfo
        Log.i(
            TAG,
            "EQ backend map: logical=${info.requestedBandCount}, " +
                "physical=${info.actualBandCount} (PRE+POST), " +
                "channels=${info.channelCount}"
        )

        info.frequenciesHz.forEachIndexed { index, frequency ->
            Log.i(TAG, "EQ physical band[$index] cutoff=${frequency}Hz")
        }

        if (info.actualBandCount != REQUESTED_EQ_BANDS) {
            Log.w(
                TAG,
                "EQ backend exposes ${info.actualBandCount} physical bands; " +
                    "sBz retains $REQUESTED_EQ_BANDS logical bands. " +
                    "Logical-to-physical mapping is lossy until a PCM/software backend is available."
            )
        }
    }

    private fun createInitialConfig(): DynamicsProcessing.Config {
        /*
         * Do not request 32/16/8 here anymore. The diagnostic on the target
         * T509A proved that the HAL reduces those requests to 5 PRE + 5 POST
         * bands anyway. Requesting 5 + 5 explicitly makes the actual backend
         * deterministic and lets sBz use both EQ stages as ten physical EQ
         * filters. The 32 logical bands remain in DspConfig/presets.
         */
        val builder = DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
            CHANNEL_COUNT,
            true,
            PHYSICAL_PRE_EQ_BANDS,
            true,
            MDRC_BAND_COUNT,
            true,
            PHYSICAL_POST_EQ_BANDS,
            true
        )

        val config = builder.build()

        eqBandCount = PHYSICAL_POST_EQ_BANDS

        Log.i(
            TAG,
            "DynamicsProcessing config created: " +
                "PRE=$PHYSICAL_PRE_EQ_BANDS POST=$PHYSICAL_POST_EQ_BANDS " +
                "MDRC=$MDRC_BAND_COUNT"
        )

        return config
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

        /*
         * Normalize the complete crossover set first. This guarantees that
         * the native MBC always receives monotonically increasing cutoffs.
         *
         * Android defines each MbcBand cutoff as the TOP of that band's
         * frequency range; band N therefore starts at the previous band's
         * cutoff. The HAL expects these cutoffs to increase with band index.
         */
        val bands = normalizeMdrcBands(config.mdrcBands)
        val source = bands.getOrNull(bandIndex) ?: defaultMdrcBand(bandIndex)
        val mdrcActive = config.isEnabled && config.mdrcEnabled

        for (channel in 0 until dp.channelCountSafe()) {
            try {
                val nativeBand = createNativeMdrcBand(source, mdrcActive)

                /*
                 * A single-band update is intentionally kept surgical for
                 * smooth fader response. No complete DSP rebuild is needed.
                 */
                dp.setMbcBandByChannelIndex(
                    channel,
                    bandIndex,
                    nativeBand
                )

                /*
                 * Read back the cutoff actually accepted by the HAL.
                 * Vendor implementations are allowed to quantize/clamp
                 * parameters, so the diagnostic log must reflect reality.
                 */
                val accepted = dp
                    .getMbcBandByChannelIndex(channel, bandIndex)
                    .getCutoffFrequency()

                if (kotlin.math.abs(accepted - source.cutoffFrequencyHz) > 0.5f) {
                    Log.w(
                        TAG,
                        "MDRC HAL adjusted cutoff: session=$audioSessionId " +
                            "channel=$channel band=$bandIndex " +
                            "requested=${source.cutoffFrequencyHz}Hz " +
                            "accepted=${accepted}Hz"
                    )
                }
            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "Unable to update MDRC band=$bandIndex on channel=$channel",
                    e
                )
            }
        }
    }

    private fun createNativeMdrcBand(
        source: MdrcBandConfig,
        enabled: Boolean
    ): DynamicsProcessing.MbcBand {
        /*
         * Android MbcBand constructor:
         * enabled, cutoff, attack, release, ratio, threshold, knee,
         * noiseGateThreshold, expanderRatio, preGain, postGain.
         *
         * Noise gate and expander are intentionally neutral here because
         * sBz's MDRC model does not expose them as user parameters yet.
         */
        return DynamicsProcessing.MbcBand(
            enabled,
            source.cutoffFrequencyHz.coerceIn(
                MIN_CUTOFF_HZ,
                MAX_CUTOFF_HZ
            ),
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
    }

    @Synchronized
    fun updateMasterGain(config: DspConfig) {
        if (!initialized || effect == null) initialize()
        val dp = effect ?: return
        if (!config.isEnabled) return

        for (channel in 0 until dp.channelCountSafe()) {
            try {
                val limiter = dp.getLimiterByChannelIndex(channel)
                limiter.setPostGain(
                    (config.limiterPostGainDb + config.masterGainDb)
                        .coerceIn(-24f, 12f)
                )
                dp.setLimiterByChannelIndex(channel, limiter)
            } catch (e: Exception) {
                Log.w(TAG, "Unable to update master gain on channel=$channel", e)
            }
        }
    }

    @Synchronized
    fun updateBalance(config: DspConfig) {
        if (!initialized || effect == null) initialize()
        val dp = effect ?: return
        val count = dp.getConfig().postEqBandCount
        if (count <= 0) return

        for (channel in 0 until dp.channelCountSafe()) {
            try {
                val postEq = dp.getPostEqByChannelIndex(channel)
                for (nativeIndex in 0 until count) {
                    val nativeFrequency = postEq.getBand(nativeIndex).getCutoffFrequency()
                    val sourceIndex = nearestSourceBandIndex(nativeFrequency, config.eqGains.size)
                    val eqGain = if (config.isEnabled) config.eqGains.getOrElse(sourceIndex) { 0f } else 0f
                    val gain = (eqGain + balanceCompensationDb(channel, config.balance))
                        .coerceIn(-60f, 15f)
                    postEq.getBand(nativeIndex).setGain(gain)
                }
                dp.setPostEqByChannelIndex(channel, postEq)
            } catch (e: Exception) {
                Log.w(TAG, "Unable to update balance on channel=$channel", e)
            }
        }
    }

    @Synchronized
    fun updateTone(config: DspConfig) {
        if (!initialized || effect == null) initialize()
        val dp = effect ?: return
        val count = dp.getConfig().preEqBandCount
        if (count <= 0) return

        for (channel in 0 until dp.channelCountSafe()) {
            try {
                val preEq = dp.getPreEqByChannelIndex(channel)
                for (bandIndex in 0 until count) {
                    val frequency = calculateStageFrequency(bandIndex, count)
                    val gain = when {
                        !config.isEnabled -> 0f
                        frequency <= 200f -> config.toneBassDb.coerceIn(-12f, 12f)
                        frequency <= 2000f -> config.toneMidDb.coerceIn(-12f, 12f)
                        else -> config.toneTrebleDb.coerceIn(-12f, 12f)
                    }
                    val band = preEq.getBand(bandIndex)
                    band.setGain(gain)
                    band.setEnabled(config.isEnabled)
                }
                if (config.bassBoostEnabled && config.isEnabled) {
                    applyBassBoostCompensation(preEq, count, config)
                }
                dp.setPreEqByChannelIndex(channel, preEq)
            } catch (e: Exception) {
                Log.w(TAG, "Unable to update tone on channel=$channel", e)
            }
        }
    }

    @Synchronized
    fun updateEqBand(config: DspConfig, sourceIndex: Int) {
        if (!initialized || effect == null) initialize()
        val dp = effect ?: return
        if (sourceIndex !in config.eqGains.indices) return

        if (hasNativeGraphicEq()) {
            applyNativeGraphicEq(config)
            return
        }

        /*
         * IMPORTANT: a logical 32-band fader must never move a physical
         * DynamicsProcessing cutoff. On the T509A multiple logical bands map
         * onto the same physical filter; moving its cutoff while dragging one
         * fader makes the mapping unstable and causes the grouped behaviour
         * observed on the low bands.
         *
         * Re-apply the complete stable 10-band map instead.
         */
        applyGraphicEq(config)
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

        val eqGains = stablePhysicalEqGains(config.eqGains, count, PRE_EQ_FREQUENCIES)

        for (channel in 0 until dp.channelCountSafe()) {
            try {
                val preEq = dp.getPreEqByChannelIndex(channel)
                preEq.setEnabled(config.isEnabled)

                for (bandIndex in 0 until count) {
                    val band = preEq.getBand(bandIndex)
                    val frequency = PRE_EQ_FREQUENCIES.getOrElse(bandIndex) {
                        calculateStageFrequency(bandIndex, count)
                    }
                    val toneGain = toneGainForFrequency(config, frequency)
                    val eqGain = if (config.isEnabled) eqGains[bandIndex] else 0f

                    band.setEnabled(config.isEnabled)
                    band.setCutoffFrequency(frequency.coerceIn(MIN_CUTOFF_HZ, MAX_CUTOFF_HZ))
                    band.setGain((toneGain + eqGain).coerceIn(-15f, 15f))
                }

                if (config.bassBoostEnabled && config.isEnabled) {
                    applyBassBoostCompensation(preEq, count, config)
                }

                dp.setPreEqByChannelIndex(channel, preEq)
            } catch (e: Exception) {
                Log.w(TAG, "Unable to apply Pre-EQ on channel=$channel", e)
            }
        }
    }

    private fun toneGainForFrequency(config: DspConfig, frequency: Float): Float {
        if (!config.isEnabled) return 0f
        return when {
            frequency <= 200f -> config.toneBassDb.coerceIn(-12f, 12f)
            frequency <= 2000f -> config.toneMidDb.coerceIn(-12f, 12f)
            else -> config.toneTrebleDb.coerceIn(-12f, 12f)
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
                calculateStageFrequency(bandIndex, count)
            }

            if (frequency <= 160f) {
                val existingGain = try {
                    band.getGain()
                } catch (_: Exception) {
                    0f
                }

                band.setGain((existingGain + boostDb).coerceIn(-15f, 15f))
            }
        }
    }

    private fun applyGraphicEq(
        dp: DynamicsProcessing,
        config: DspConfig
    ) {
        if (hasNativeGraphicEq()) {
            for (channel in 0 until dp.channelCountSafe()) {
                try {
                    val postEq = dp.getPostEqByChannelIndex(channel)
                    postEq.setEnabled(false)
                    for (bandIndex in 0 until postEq.bandCount) {
                        val band = postEq.getBand(bandIndex)
                        band.setEnabled(false)
                        band.setGain(0f)
                    }
                    dp.setPostEqByChannelIndex(channel, postEq)
                } catch (e: Exception) {
                    Log.w(TAG, "Unable to bypass DynamicsProcessing Post-EQ", e)
                }
            }
            applyNativeGraphicEq(config)
            return
        }

        val count = dp.getConfig().postEqBandCount
        if (count <= 0) return

        val eqGains = stablePhysicalEqGains(config.eqGains, count, POST_EQ_FREQUENCIES)

        for (channel in 0 until dp.channelCountSafe()) {
            try {
                val postEq = dp.getPostEqByChannelIndex(channel)
                postEq.setEnabled(config.isEnabled)

                for (bandIndex in 0 until count) {
                    val band = postEq.getBand(bandIndex)
                    val frequency = POST_EQ_FREQUENCIES.getOrElse(bandIndex) {
                        calculateStageFrequency(bandIndex, count)
                    }
                    val eqGain = if (config.isEnabled) eqGains[bandIndex] else 0f
                    val balanceGainDb = balanceCompensationDb(channel, config.balance)

                    band.setEnabled(config.isEnabled)
                    band.setCutoffFrequency(frequency.coerceIn(MIN_CUTOFF_HZ, MAX_CUTOFF_HZ))
                    band.setGain((eqGain + balanceGainDb).coerceIn(-60f, 15f))
                }

                dp.setPostEqByChannelIndex(channel, postEq)
            } catch (e: Exception) {
                Log.w(TAG, "Unable to apply Post-EQ on channel=$channel", e)
            }
        }
    }

    /**
     * Stable ten-filter backend for the target device. The 32 logical ISO
     * bands remain intact in DspConfig. Each physical filter receives a
     * logarithmic interpolation of the nearest logical bands, so changing a
     * low fader primarily changes the nearby physical filter instead of
     * affecting the entire EQ stage.
     */
    private fun stablePhysicalEqGains(
        logicalGains: List<Float>,
        physicalCount: Int,
        physicalFrequencies: FloatArray
    ): FloatArray {
        val result = FloatArray(physicalCount)
        if (logicalGains.isEmpty()) return result

        for (physicalIndex in 0 until physicalCount) {
            val target = physicalFrequencies.getOrElse(physicalIndex) {
                calculateStageFrequency(physicalIndex, physicalCount)
            }.coerceAtLeast(1f)

            result[physicalIndex] = interpolateLogicalGain(
                logicalGains,
                target
            )
        }

        return result
    }

    private fun interpolateLogicalGain(
        logicalGains: List<Float>,
        targetFrequencyHz: Float
    ): Float {
        val count = minOf(logicalGains.size, DspConfig.FREQUENCIES.size)
        if (count <= 0) return 0f
        if (count == 1) return logicalGains[0].coerceIn(-15f, 15f)

        if (targetFrequencyHz <= DspConfig.FREQUENCIES[0]) {
            return logicalGains[0].coerceIn(-15f, 15f)
        }
        if (targetFrequencyHz >= DspConfig.FREQUENCIES[count - 1]) {
            return logicalGains[count - 1].coerceIn(-15f, 15f)
        }

        for (index in 0 until count - 1) {
            val lowerHz = DspConfig.FREQUENCIES[index]
            val upperHz = DspConfig.FREQUENCIES[index + 1]

            if (targetFrequencyHz <= upperHz) {
                val lowerLog = kotlin.math.ln(lowerHz.toDouble())
                val upperLog = kotlin.math.ln(upperHz.toDouble())
                val targetLog = kotlin.math.ln(targetFrequencyHz.toDouble())
                val span = (upperLog - lowerLog).coerceAtLeast(1e-9)
                val t = ((targetLog - lowerLog) / span).toFloat().coerceIn(0f, 1f)

                val a = logicalGains[index].coerceIn(-15f, 15f)
                val b = logicalGains[index + 1].coerceIn(-15f, 15f)
                return a + (b - a) * t
            }
        }

        return logicalGains[count - 1].coerceIn(-15f, 15f)
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
        val mdrcCount = dp.getConfig().mbcBandCount
        if (mdrcCount <= 0) return

        val requestedBands = normalizeMdrcBands(config.mdrcBands)
        val mdrcActive = config.isEnabled && config.mdrcEnabled

        for (channel in 0 until dp.channelCountSafe()) {
            try {
                /*
                 * Build the complete native MBC off to the side, then submit
                 * the stage once. This is preferable to:
                 *   disable MBC -> write 4 bands -> enable MBC
                 *
                 * because the old sequence created an avoidable bypass window
                 * and could produce a transient/click on some vendor HALs.
                 *
                 * Android requires the replacement MBC to keep the same band
                 * count as the original stage.
                 */
                val nativeMbc = DynamicsProcessing.Mbc(
                    config.isEnabled,
                    mdrcActive,
                    mdrcCount
                )

                for (bandIndex in 0 until mdrcCount) {
                    val source = requestedBands.getOrElse(bandIndex) {
                        defaultMdrcBand(bandIndex)
                    }

                    nativeMbc.setBand(
                        bandIndex,
                        createNativeMdrcBand(source, mdrcActive)
                    )
                }

                dp.setMbcByChannelIndex(channel, nativeMbc)

                /*
                 * Verify the actual crossover values after the HAL accepted
                 * the configuration. This is especially important on vendor
                 * implementations that quantize or reject requested cutoffs.
                 */
                for (bandIndex in 0 until mdrcCount) {
                    val requested = requestedBands.getOrElse(bandIndex) {
                        defaultMdrcBand(bandIndex)
                    }.cutoffFrequencyHz

                    val accepted = dp
                        .getMbcBandByChannelIndex(channel, bandIndex)
                        .getCutoffFrequency()

                    if (kotlin.math.abs(accepted - requested) > 0.5f) {
                        Log.w(
                            TAG,
                            "MDRC HAL adjusted cutoff: session=$audioSessionId " +
                                "channel=$channel band=$bandIndex " +
                                "requested=${requested}Hz accepted=${accepted}Hz"
                        )
                    }
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

    private fun nativeBandFrequency(
        nativeIndex: Int,
        nativeCount: Int,
        band: DynamicsProcessing.EqBand
    ): Float {
        return try {
            band.getCutoffFrequency()
        } catch (_: Exception) {
            calculateStageFrequency(nativeIndex, nativeCount)
        }
    }

    private fun nearestSourceBandIndex(
        frequencyHz: Float,
        sourceCount: Int
    ): Int {
        if (sourceCount <= 1) return 0

        val last = minOf(sourceCount, DspConfig.FREQUENCIES.size) - 1
        var bestIndex = 0
        var bestDistance = Float.POSITIVE_INFINITY

        for (index in 0..last) {
            val distance = kotlin.math.abs(
                kotlin.math.ln(
                    (DspConfig.FREQUENCIES[index] / frequencyHz.coerceAtLeast(1f))
                        .toDouble()
                )
            )
            if (distance < bestDistance) {
                bestDistance = distance.toFloat()
                bestIndex = index
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

        val postEq = try {
            dp.getPostEqByChannelIndex(0)
        } catch (_: Exception) {
            return 0
        }

        var bestIndex = 0
        var bestDistance = Float.POSITIVE_INFINITY
        for (index in 0 until nativeCount) {
            val nativeFrequency = try {
                postEq.getBand(index).getCutoffFrequency()
            } catch (_: Exception) {
                calculateStageFrequency(index, nativeCount)
            }
            val distance = kotlin.math.abs(
                kotlin.math.ln(
                    (nativeFrequency / frequencyHz.coerceAtLeast(1f)).toDouble()
                )
            )
            if (distance < bestDistance) {
                bestDistance = distance.toFloat()
                bestIndex = index
            }
        }
        return bestIndex
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
            nativeEqualizer?.setEnabled(false)
        } catch (_: Exception) {
        }

        try {
            nativeEqualizer?.release()
        } catch (_: Exception) {
        }

        nativeEqualizer = null
        nativeEqMap = IntArray(0)

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
