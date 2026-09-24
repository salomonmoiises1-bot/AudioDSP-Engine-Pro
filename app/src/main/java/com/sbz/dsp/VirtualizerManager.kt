package com.sbz.dsp

import android.media.audiofx.Virtualizer
import android.util.Log

/**
 * Handles Android's native Virtualizer AudioEffect for stereo spatialization.
 */
class VirtualizerManager(
    val audioSessionId: Int
) {
    companion object {
        private const val TAG = "VirtualizerManager"
        private const val PRIORITY = 100
    }

    private var virtualizer: Virtualizer? = null

    init {
        try {
            virtualizer = Virtualizer(PRIORITY, audioSessionId).apply {
                if (strengthSupported) {
                    Log.d(TAG, "Virtualizer initialized for session $audioSessionId")
                } else {
                    Log.w(TAG, "Virtualizer strength parameter not supported on this device/session")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not initialize Virtualizer on session $audioSessionId: ${e.message}")
            virtualizer = null
        }
    }

    fun apply(enabled: Boolean, strength: Short) {
        val v = virtualizer ?: return
        try {
            if (v.enabled != enabled) {
                v.enabled = enabled
            }
            if (enabled && v.strengthSupported) {
                v.setStrength(strength.coerceIn(0, 1000))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error applying Virtualizer on session $audioSessionId: ${e.message}")
        }
    }

    fun release() {
        try {
            virtualizer?.enabled = false
            virtualizer?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing Virtualizer: ${e.message}")
        } finally {
            virtualizer = null
        }
    }
}
