package com.sbz.dsp

import android.media.audiofx.DynamicsProcessing

class PureMultiBandEqEngine(audioSessionId: Int, private val totalBands: Int = 32) {

    private val dynamicsProcessing: DynamicsProcessing = DynamicsProcessing(0, audioSessionId, true).apply {
        isEnabled = true
    }

    fun setBandGain(bandIndex: Int, gainDb: Float) {
        if (bandIndex in 0 until totalBands) {
            try {
                val eqBand = dynamicsProcessing.getEqBand(bandIndex)
                eqBand.gain = gainDb
                eqBand.isEnabled = true
                dynamicsProcessing.setEqBand(bandIndex, eqBand)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        dynamicsProcessing.isEnabled = enabled
    }

    fun release() {
        dynamicsProcessing.release()
    }
}
