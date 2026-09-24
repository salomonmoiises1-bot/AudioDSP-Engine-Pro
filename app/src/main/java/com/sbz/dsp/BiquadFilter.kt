package com.sbz.dsp

import kotlin.math.*

/**
 * High-precision Robert Bristow-Johnson (RBJ) Biquad Filter implementation.
 * Provides exact filter coefficients for Peaking EQ, Low-Shelf, High-Shelf,
 * and complex transfer function magnitude evaluation: |H(e^jω)|.
 */
class BiquadFilter(
    val sampleRate: Double = 48000.0
) {
    var b0: Double = 1.0
    var b1: Double = 0.0
    var b2: Double = 0.0
    var a0: Double = 1.0
    var a1: Double = 0.0
    var a2: Double = 0.0

    // Stereo direct-form II transposed states
    private var x1L: Double = 0.0
    private var x2L: Double = 0.0
    private var y1L: Double = 0.0
    private var y2L: Double = 0.0

    private var x1R: Double = 0.0
    private var x2R: Double = 0.0
    private var y1R: Double = 0.0
    private var y2R: Double = 0.0

    /**
     * Configure as a Peaking EQ filter band with gain in dB, center frequency in Hz, and Q-factor.
     */
    fun configurePeaking(centerFreqHz: Double, gainDb: Double, q: Double = 4.318) {
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * Math.PI * centerFreqHz / sampleRate
        val alpha = sin(w0) / (2.0 * q)
        val cosW0 = cos(w0)

        val rawB0 = 1.0 + alpha * a
        val rawB1 = -2.0 * cosW0
        val rawB2 = 1.0 - alpha * a
        val rawA0 = 1.0 + alpha / a
        val rawA1 = -2.0 * cosW0
        val rawA2 = 1.0 - alpha / a

        // Normalize by a0
        b0 = rawB0 / rawA0
        b1 = rawB1 / rawA0
        b2 = rawB2 / rawA0
        a1 = rawA1 / rawA0
        a2 = rawA2 / rawA0
        a0 = 1.0
    }

    /**
     * Configure as Low-Shelf filter (Tone Bass).
     */
    fun configureLowShelf(cutoffFreqHz: Double, gainDb: Double, slope: Double = 1.0) {
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * Math.PI * cutoffFreqHz / sampleRate
        val cosW0 = cos(w0)
        val sinW0 = sin(w0)
        val alpha = sinW0 / 2.0 * sqrt((a + 1.0 / a) * (1.0 / slope - 1.0) + 2.0)
        val twoSqrtAAlpha = 2.0 * sqrt(a) * alpha

        val rawB0 = a * ((a + 1.0) - (a - 1.0) * cosW0 + twoSqrtAAlpha)
        val rawB1 = 2.0 * a * ((a - 1.0) - (a + 1.0) * cosW0)
        val rawB2 = a * ((a + 1.0) - (a - 1.0) * cosW0 - twoSqrtAAlpha)
        val rawA0 = (a + 1.0) + (a - 1.0) * cosW0 + twoSqrtAAlpha
        val rawA1 = -2.0 * ((a - 1.0) + (a + 1.0) * cosW0)
        val rawA2 = (a + 1.0) + (a - 1.0) * cosW0 - twoSqrtAAlpha

        b0 = rawB0 / rawA0
        b1 = rawB1 / rawA0
        b2 = rawB2 / rawA0
        a1 = rawA1 / rawA0
        a2 = rawA2 / rawA0
        a0 = 1.0
    }

    /**
     * Configure as High-Shelf filter (Tone Treble).
     */
    fun configureHighShelf(cutoffFreqHz: Double, gainDb: Double, slope: Double = 1.0) {
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * Math.PI * cutoffFreqHz / sampleRate
        val cosW0 = cos(w0)
        val sinW0 = sin(w0)
        val alpha = sinW0 / 2.0 * sqrt((a + 1.0 / a) * (1.0 / slope - 1.0) + 2.0)
        val twoSqrtAAlpha = 2.0 * sqrt(a) * alpha

        val rawB0 = a * ((a + 1.0) + (a - 1.0) * cosW0 + twoSqrtAAlpha)
        val rawB1 = -2.0 * a * ((a - 1.0) + (a + 1.0) * cosW0)
        val rawB2 = a * ((a + 1.0) + (a - 1.0) * cosW0 - twoSqrtAAlpha)
        val rawA0 = (a + 1.0) - (a - 1.0) * cosW0 + twoSqrtAAlpha
        val rawA1 = 2.0 * ((a - 1.0) - (a + 1.0) * cosW0)
        val rawA2 = (a + 1.0) - (a - 1.0) * cosW0 - twoSqrtAAlpha

        b0 = rawB0 / rawA0
        b1 = rawB1 / rawA0
        b2 = rawB2 / rawA0
        a1 = rawA1 / rawA0
        a2 = rawA2 / rawA0
        a0 = 1.0
    }

    /**
     * Reset filter internal states to avoid DC thumps or clicks.
     */
    fun reset() {
        x1L = 0.0; x2L = 0.0; y1L = 0.0; y2L = 0.0
        x1R = 0.0; x2R = 0.0; y1R = 0.0; y2R = 0.0
    }

    /**
     * Calculate frequency response magnitude in dB at target frequency:
     * H(e^jω) = (b0 + b1*e^-jω + b2*e^-2jω) / (1 + a1*e^-jω + a2*e^-2jω)
     */
    fun evaluateGainDbAt(frequencyHz: Double): Double {
        val w = 2.0 * Math.PI * frequencyHz / sampleRate
        val cosW = cos(w)
        val cos2W = cos(2.0 * w)
        val sinW = sin(w)
        val sin2W = sin(2.0 * w)

        val numReal = b0 + b1 * cosW + b2 * cos2W
        val numImag = -(b1 * sinW + b2 * sin2W)
        val denReal = 1.0 + a1 * cosW + a2 * cos2W
        val denImag = -(a1 * sinW + a2 * sin2W)

        val numMagSq = numReal * numReal + numImag * numImag
        val denMagSq = denReal * denReal + denImag * denImag

        val magSq = if (denMagSq > 1e-12) numMagSq / denMagSq else 1.0
        return 10.0 * log10(max(1e-10, magSq))
    }
}
