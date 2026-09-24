package com.sbz.dsp

import android.media.audiofx.BassBoost
import android.util.Log

/**
 * Handles Android's native BassBoost AudioEffect stage.
 */
class BassBoostManager(
    val audioSessionId: Int
) {
    companion object {
        private const val TAG = "BassBoostManager"
        private const val PRIORITY = 100
    }

    private var bassBoost: BassBoost? = null

    init {
        try {
            bassBoost = BassBoost(PRIORITY, audioSessionId).apply {
                if (strengthSupported) {
                    Log.d(TAG, "BassBoost initialized for session $audioSessionId")
                } else {
                    Log.w(TAG, "BassBoost strength not supported on session $audioSessionId")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not initialize BassBoost on session $audioSessionId: ${e.message}")
            bassBoost = null
        }
    }

    fun apply(enabled: Boolean, strength: Short) {
        val bb = bassBoost ?: return
        try {
            if (bb.enabled != enabled) {
                bb.enabled = enabled
            }
            if (enabled && bb.strengthSupported) {
                bb.setStrength(strength.coerceIn(0, 1000))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error applying BassBoost on session $audioSessionId: ${e.message}")
        }
    }

    fun release() {
        try {
            bassBoost?.enabled = false
            bassBoost?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing BassBoost: ${e.message}")
        } finally {
            bassBoost = null
        }
    }
}
