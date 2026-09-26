package com.sbz.dsp

import com.sbz.dsp.model.DspConfig
import kotlin.math.*

/** Real-time stereo DSP core. Interleaved Float PCM: L,R,L,R... */
class StereoDspProcessor(private val sampleRate: Float = 48_000f) {
    private val eq = Equalizer32Band(sampleRate)
    private val bass = Biquad(); private val mid = Biquad(); private val treble = Biquad()
    private var config = DspConfig()
    private var env = 0f
    private var delay = FloatArray(256)
    private var delayPos = 0

    fun updateConfig(c: DspConfig) {
        config=c.normalized()
        eq.setGains(config.eqGainsDb)
        bass.setPeaking(sampleRate, 90f, .7f, config.bassBoostDb + config.toneBassDb)
        mid.setPeaking(sampleRate, 1000f, .7f, config.toneMidDb)
        treble.setPeaking(sampleRate, 8000f, .7f, config.toneTrebleDb)
    }

    fun process(buffer: FloatArray) {
        if (!config.isEnabled) return
        var peak=0f
        var i=0
        while (i+1<buffer.size) {
            var l=buffer[i]; var r=buffer[i+1]
            val pg=10f.pow(config.preGainDb/20f); l*=pg; r*=pg
            l=bass.processL(l); r=bass.processR(r); l=mid.processL(l); r=mid.processR(r); l=treble.processL(l); r=treble.processR(r)
            val e=eq.process(l,r); l=e.first; r=e.second
            if (config.mdrcEnabled) { val g=mdrcGain(l,r); l*=g; r*=g }
            if (config.spatialEnabled) { val s=config.spatialStrength; val side=(l-r)*s*.35f; l+=side; r-=side }
            val bal=config.balance
            if (bal>0) l*=1f-bal else r*=1f+bal
            val mg=10f.pow(config.masterGainDb/20f); l*=mg; r*=mg
            peak=max(peak,max(abs(l),abs(r)))
            buffer[i]=l; buffer[i+1]=r; i+=2
        }
        if (config.autoGainEnabled && peak>1e-5f) {
            val target=10f.pow(config.autoGainTargetDb/20f)
            val correction=(target/peak).coerceIn(.65f,1.15f)
            val alpha=if(correction<1f) .08f else .015f
            env += (correction-env)*alpha
            val g=(1f+env).coerceIn(.65f,1.1f)
            for (n in buffer.indices) buffer[n]*=g
        }
        if (config.limiterEnabled) {
            val ceiling=10f.pow(config.limiterCeilingDb/20f)
            for (n in buffer.indices) buffer[n]=tanh(buffer[n]/ceiling)*ceiling
        }
    }

    private fun mdrcGain(l: Float, r: Float): Float {
        val level=20f*log10(max(1e-6f,max(abs(l),abs(r))))
        val band=config.mdrcBands.firstOrNull() ?: return 1f
        if(level<=band.thresholdDb) return 1f
        val compressed=band.thresholdDb+(level-band.thresholdDb)/band.ratio
        return 10f.pow((compressed-level+band.makeupDb)/20f).coerceIn(.2f,1f)
    }

    fun reset() { eq.reset(); bass.reset(); mid.reset(); treble.reset(); env=0f; delay.fill(0f); delayPos=0 }
}
