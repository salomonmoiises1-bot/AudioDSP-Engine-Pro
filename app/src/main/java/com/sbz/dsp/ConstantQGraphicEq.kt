package com.sbz.dsp

import com.sbz.dsp.model.DspConfig
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.cos

/**
 * 32-band Constant-Q parallel graphic equalizer.
 *
 * Each band is a real band-pass biquad centered on one ISO 1/3-octave
 * frequency. The bands run in parallel from the same input. A band's gain is
 * applied as a delta around the dry signal:
 *
 *     y = x + sum((linearGain[i] - 1) * bandPass[i](x))
 *
 * Therefore a completely flat configuration is exactly the dry signal,
 * while changing one fader affects only the contribution of that band's
 * constant-Q filter. This is intentionally independent of Android
 * DynamicsProcessing's physical EQ band count.
 *
 * The class is a PCM processor. It does not itself capture or attach to an
 * Android global audio session; the caller must provide the PCM buffer.
 */
class ConstantQGraphicEq(
    private val sampleRate: Double = 48_000.0,
    private val q: Double = 4.318
) {
    companion object {
        const val BAND_COUNT = 32
        const val DEFAULT_Q = 4.318
        private const val MIN_GAIN_DB = -15.0
        private const val MAX_GAIN_DB = 15.0

        val CENTER_FREQUENCIES_HZ: DoubleArray = doubleArrayOf(
            20.0, 25.0, 31.5, 40.0, 50.0, 63.0, 80.0, 100.0,
            125.0, 160.0, 200.0, 250.0, 315.0, 400.0, 500.0, 630.0,
            800.0, 1000.0, 1250.0, 1600.0, 2000.0, 2500.0, 3150.0,
            4000.0, 5000.0, 6300.0, 8000.0, 10000.0, 12500.0, 16000.0,
            18000.0, 20000.0
        )
    }

    private val filters = Array(BAND_COUNT) { index ->
        BiquadFilter(sampleRate).also {
            it.configureBandPass(CENTER_FREQUENCIES_HZ[index], q)
        }
    }

    private val gainDbSnapshot = AtomicReference(FloatArray(BAND_COUNT))

    init {
        require(sampleRate > 0.0) { "Sample rate must be positive" }
        require(q > 0.0) { "Q must be positive" }
        require(sampleRate / 2.0 > CENTER_FREQUENCIES_HZ.last()) {
            "Sample rate is too low for the 20 kHz band"
        }
    }

    fun setBandGainDb(index: Int, gainDb: Float) {
        require(index in 0 until BAND_COUNT) { "Invalid EQ band index: $index" }
        val next = gainDbSnapshot.get().clone()
        next[index] = gainDb.coerceIn(MIN_GAIN_DB.toFloat(), MAX_GAIN_DB.toFloat())
        gainDbSnapshot.set(next)
    }

    fun setAllGainsDb(gains: List<Float>) {
        require(gains.size == BAND_COUNT) {
            "Expected $BAND_COUNT EQ gains, got ${gains.size}"
        }
        val next = FloatArray(BAND_COUNT) { index ->
            gains[index].coerceIn(MIN_GAIN_DB.toFloat(), MAX_GAIN_DB.toFloat())
        }
        gainDbSnapshot.set(next)
    }

    fun setAllGainsDb(gains: FloatArray) {
        require(gains.size == BAND_COUNT) {
            "Expected $BAND_COUNT EQ gains, got ${gains.size}"
        }
        val next = FloatArray(BAND_COUNT) { index ->
            gains[index].coerceIn(MIN_GAIN_DB.toFloat(), MAX_GAIN_DB.toFloat())
        }
        gainDbSnapshot.set(next)
    }

    fun setFromConfig(config: DspConfig) {
        setAllGainsDb(config.eqGains)
    }

    fun getGainsDb(): FloatArray = gainDbSnapshot.get().clone()

    /**
     * Process interleaved stereo Float PCM in-place.
     *
     * No heap allocation occurs in the audio loop after the gain snapshot is
     * acquired. Every band is advanced on every sample, including 0 dB bands,
     * so changing a band on a running stream does not revive stale filter
     * states.
     */
    fun processInterleavedStereo(
        buffer: FloatArray,
        offset: Int = 0,
        length: Int = buffer.size - offset
    ) {
        require(offset >= 0 && length >= 0 && offset + length <= buffer.size) {
            "Invalid PCM buffer range"
        }
        require(length % 2 == 0) { "Stereo interleaved buffer length must be even" }

        val gains = gainDbSnapshot.get()
        var i = offset
        val end = offset + length

        while (i < end) {
            val inputL = buffer[i].toDouble()
            val inputR = buffer[i + 1].toDouble()
            var outputL = inputL
            var outputR = inputR

            for (band in 0 until BAND_COUNT) {
                val filter = filters[band]
                filter.processStereo(inputL, inputR)
                val linearGain = 10.0.pow(gains[band].toDouble() / 20.0)
                val delta = linearGain - 1.0
                outputL += filter.lastOutputLeft * delta
                outputR += filter.lastOutputRight * delta
            }

            buffer[i] = outputL.toFloat()
            buffer[i + 1] = outputR.toFloat()
            i += 2
        }
    }

    fun reset() {
        filters.forEach(BiquadFilter::reset)
    }
}

/**
 * Constant-Q band-pass configuration using the RBJ cookbook equations.
 * The peak band-pass gain is unity at the center frequency.
 */
private fun BiquadFilter.configureBandPass(centerFreqHz: Double, q: Double) {
    val w0 = 2.0 * Math.PI * centerFreqHz / sampleRate
    val sinW0 = sin(w0)
    val cosW0 = cos(w0)
    val alpha = sinW0 / (2.0 * q)

    val rawB0 = alpha
    val rawB1 = 0.0
    val rawB2 = -alpha
    val rawA0 = 1.0 + alpha
    val rawA1 = -2.0 * cosW0
    val rawA2 = 1.0 - alpha

    b0 = rawB0 / rawA0
    b1 = rawB1 / rawA0
    b2 = rawB2 / rawA0
    a0 = 1.0
    a1 = rawA1 / rawA0
    a2 = rawA2 / rawA0
}
