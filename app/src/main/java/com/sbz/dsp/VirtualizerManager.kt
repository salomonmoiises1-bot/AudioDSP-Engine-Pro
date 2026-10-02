package com.sbz.dsp

import android.media.audiofx.Virtualizer
import android.util.Log

/**
 * Manages Android's native Virtualizer AudioEffect.
 *
 * The global audio session (0) is intentionally not supported here.
 * Applying a Virtualizer to session 0 can mute the complete output on
 * devices whose audio HAL does not expose a safe global Virtualizer path.
 * The engine therefore creates this manager only for real audio sessions.
 */
class VirtualizerManager(
    val audioSessionId: Int
) {
    companion object {
        private const val TAG = "VirtualizerManager"
        private const val PRIORITY = 100
        private const val GLOBAL_SESSION_ID = 0
    }

    private var virtualizer: Virtualizer? = null
    private var lastRequestedEnabled = false
    private var lastRequestedStrength: Short = 0

    init {
        if (audioSessionId == GLOBAL_SESSION_ID) {
            Log.i(TAG, "Virtualizer disabled for global session 0")
            return
        }

        try {
            virtualizer = Virtualizer(PRIORITY, audioSessionId).also { v ->
                if (v.strengthSupported) {
                    Log.d(TAG, "Virtualizer initialized for session $audioSessionId")
                } else {
                    Log.w(TAG, "Virtualizer strength parameter not supported on session $audioSessionId")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not initialize Virtualizer on session $audioSessionId: ${e.message}")
            virtualizer = null
        }
    }

    fun apply(enabled: Boolean, strength: Short) {
        lastRequestedEnabled = enabled
        lastRequestedStrength = strength.coerceIn(0, 1000).toShort()

        val v = virtualizer ?: return

        try {
            val safeStrength = lastRequestedStrength

            if (enabled && v.strengthSupported) {
                v.setStrength(safeStrength)
            }

            if (v.enabled != enabled) {
                v.enabled = enabled
            }

            Log.d(
                TAG,
                "Virtualizer session=$audioSessionId enabled=$enabled strength=$safeStrength"
            )
        } catch (e: Exception) {
            Log.w(
                TAG,
                "Error applying Virtualizer on session $audioSessionId: ${e.message}"
            )
            // Never propagate a native Virtualizer failure into the DSP engine.
            // If the HAL rejects the operation, force the effect off when possible.
            try {
                if (v.enabled) v.enabled = false
            } catch (_: Exception) {
                // Ignore secondary HAL errors.
            }
        }
    }

    fun release() {
        val v = virtualizer ?: return
        try {
            if (v.enabled) {
                v.enabled = false
            }
            v.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing Virtualizer on session $audioSessionId: ${e.message}")
        } finally {
            virtualizer = null
        }
    }
}
