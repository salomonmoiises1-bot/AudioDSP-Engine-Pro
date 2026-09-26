package com.sbz.dsp

import com.sbz.dsp.model.DspConfig

/** Deterministic 32-band stereo peaking EQ. */
class Equalizer32Band(private val sampleRate: Float = 48_000f) {
    private val filters = Array(32) { Biquad() }
    private var gains = FloatArray(32)

    fun setGains(values: List<Float>) {
        gains = FloatArray(32) { i -> values.getOrNull(i)?.coerceIn(-15f, 15f) ?: 0f }
        rebuild()
    }

    fun setBand(index: Int, gainDb: Float) {
        if (index !in 0 until 32) return
        gains[index] = gainDb.coerceIn(-15f, 15f)
        rebuild()
    }

    fun getGains(): List<Float> = gains.toList()

    private fun rebuild() {
        DspConfig.EQ_FREQUENCIES_HZ.forEachIndexed { i, f -> filters[i].setPeaking(sampleRate, f, 1.35f, gains[i]) }
    }

    fun process(left: Float, right: Float): Pair<Float, Float> {
        var l=left; var r=right
        for (f in filters) { l=f.processL(l); r=f.processR(r) }
        return l to r
    }

    fun reset() = filters.forEach { it.reset() }
}
