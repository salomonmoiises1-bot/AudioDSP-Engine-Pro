package com.sbz.dsp

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.sin

/**
 * Equalizer314-style parametric response model. It owns the logical filters;
 * ParametricToDpConverter turns their summed response into DP staircase bands.
 */
class ParametricEqualizer(
    private val sampleRate: Float = 48000f,
    bandCount: Int = 32
) {
    data class Band(var frequency: Float, var gainDb: Float, var q: Float = 1.4142135f, var enabled: Boolean = true)
    private val bands = ArrayList<Band>(bandCount)
    private val filters = ArrayList<BiquadFilter>(bandCount)

    init { repeat(bandCount) { bands += Band(1000f, 0f); filters += BiquadFilter() } }

    fun getBandCount(): Int = bands.size
    fun getBand(index: Int): Band? = bands.getOrNull(index)

    fun setBand(index: Int, frequencyHz: Float, gainDb: Float, q: Float = 1.4142135f, enabled: Boolean = true) {
        if (index !in bands.indices) return
        val b=bands[index]; b.frequency=frequencyHz.coerceIn(10f, sampleRate*.49f); b.gainDb=gainDb.coerceIn(-24f,24f); b.q=q.coerceIn(.1f,20f); b.enabled=enabled
        val f=filters[index]; f.isEnabled=b.enabled && abs(b.gainDb)>0.0001f
        f.configure(BiquadFilter.Type.PEAKING,b.frequency,b.gainDb,b.q,sampleRate)
    }

    fun setEnabled(index:Int, enabled:Boolean) { bands.getOrNull(index)?.enabled=enabled; filters.getOrNull(index)?.isEnabled=enabled }

    /** Sum of all enabled RBJ filters at frequency f, in dB. */
    fun getFrequencyResponse(frequencyHz: Float): Float {
        var sum=0f
        for(i in bands.indices){ val b=bands[i]; if(!b.enabled || abs(b.gainDb)<1e-5f) continue; sum += filters[i].frequencyResponseDb(frequencyHz,sampleRate) }
        return sum
    }
}
