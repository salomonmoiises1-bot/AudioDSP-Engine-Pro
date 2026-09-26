package com.sbz.dsp

import android.util.Log
import com.sbz.dsp.model.DspConfig

/**
 * Compatibility holder for Android DynamicsProcessing.
 * The deterministic sBz DSP is implemented by StereoDspProcessor instead of relying on
 * vendor-specific band counts or assuming session 0 is a universal output bus.
 */
class DynamicsProcessingManager(val audioSessionId:Int, private val onControlLost:(()->Unit)?=null) {
    companion object { private const val TAG="SbzDynamicsAdapter" }
    fun applyConfig(config:DspConfig) {
        Log.d(TAG,"Session $audioSessionId configured; deterministic DSP remains authoritative")
    }
    fun release() = Unit
}
