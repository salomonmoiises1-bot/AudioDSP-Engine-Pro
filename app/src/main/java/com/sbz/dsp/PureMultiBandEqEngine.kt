package com.sbz.dsp

import android.media.audiofx.DynamicsProcessing
import android.util.Log

class PureMultiBandEqEngine(audioSessionId: Int, private val totalBands: Int = 32) {

    companion object {
        private const val TAG = "PureMultiBandEqEngine"
    }

    private val dynamicsProcessing: DynamicsProcessing? = try {
        val config = DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
            2, // Canales estéreo
            false, 0,
            false, 0,
            true, totalBands,
            false
        ).build()

        DynamicsProcessing(0, audioSessionId, config).apply {
            setEnabled(true)
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize DynamicsProcessing", e)
        null
    }

    fun setBandGain(bandIndex: Int, gainDb: Float) {
        val dp = dynamicsProcessing ?: return
        if (bandIndex in 0 until totalBands) {
            try {
                val channelCount = dp.channelCount.coerceAtLeast(1)
                for (channel in 0 until channelCount) {
                    val postEq = dp.getPostEqByChannelIndex(channel)
                    val band = postEq.getBand(bandIndex)
                    band.setGain(gainDb.coerceIn(-15f, 15f))
                    band.setEnabled(true)
                    postEq.setBand(bandIndex, band)
                    dp.setPostEqByChannelIndex(channel, postEq)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error setting band gain for index $bandIndex", e)
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        try {
            dynamicsProcessing?.setEnabled(enabled)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting enabled state", e)
        }
    }

    fun release() {
        try {
            dynamicsProcessing?.setEnabled(false)
            dynamicsProcessing?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing engine", e)
        }
    }
}
