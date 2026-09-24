package com.sbz.dsp

import android.util.Log
import com.sbz.dsp.model.DspConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Core sBz DSP Engine coordinating Android Audio Framework sessions and hardware effects.
 * Manages Session 0 (Global output) and individual application audio sessions.
 */
class SbzDspEngine {

    companion object {
        private const val TAG = "SbzDspEngine"
        const val GLOBAL_SESSION_ID = 0
    }

    private data class SessionPipeline(
        val dynamicsProcessing: DynamicsProcessingManager,
        val virtualizer: VirtualizerManager,
        val bassBoost: BassBoostManager
    )

    private val pipelines = ConcurrentHashMap<Int, SessionPipeline>()
    private var currentConfig = DspConfig()

    private val _engineState = MutableStateFlow(EngineStatus())
    val engineState: StateFlow<EngineStatus> = _engineState.asStateFlow()

    data class EngineStatus(
        val isRunning: Boolean = false,
        val activeSessions: Set<Int> = emptySet(),
        val globalSessionAttached: Boolean = false,
        val dynamicsProcessingAvailable: Boolean = false,
        val lastErrorMessage: String? = null
    )

    /**
     * Start the DSP engine and bind to Global Session 0.
     */
    @Synchronized
    fun start() {
        Log.i(TAG, "Starting sBz DSP Engine...")
        // Try attaching to Global Session 0
        attachSession(GLOBAL_SESSION_ID)

        _engineState.value = _engineState.value.copy(
            isRunning = true,
            globalSessionAttached = pipelines.containsKey(GLOBAL_SESSION_ID),
            activeSessions = pipelines.keys.toSet()
        )
    }

    /**
     * Attach a new audio session ID (e.g. dispatched by ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION).
     */
    @Synchronized
    fun attachSession(sessionId: Int) {
        if (pipelines.containsKey(sessionId)) {
            Log.d(TAG, "Session $sessionId already attached, updating config...")
            applyConfigToSession(sessionId, currentConfig)
            return
        }

        Log.i(TAG, "Attaching DSP Pipeline to AudioSession: $sessionId")
        try {
            val dpManager = DynamicsProcessingManager(sessionId) {
                Log.w(TAG, "Control lost on session $sessionId, scheduling reclaim...")
                reclaimAllControl()
            }
            val virtManager = VirtualizerManager(sessionId)
            val bassManager = BassBoostManager(sessionId)

            val pipeline = SessionPipeline(dpManager, virtManager, bassManager)
            pipelines[sessionId] = pipeline

            // Apply current config to this newly attached session
            applyConfigToPipeline(pipeline, currentConfig)

            updateState()
        } catch (e: Exception) {
            Log.e(TAG, "Failed attaching audio session $sessionId: ${e.message}", e)
            _engineState.value = _engineState.value.copy(
                lastErrorMessage = "Error on session $sessionId: ${e.localizedMessage}"
            )
        }
    }

    /**
     * Detach an audio session ID when closed.
     */
    @Synchronized
    fun detachSession(sessionId: Int) {
        // Never detach global session unless stopping the whole engine
        if (sessionId == GLOBAL_SESSION_ID && _engineState.value.isRunning) {
            Log.d(TAG, "Ignoring detach on Global Session 0 while engine is running")
            return
        }

        Log.i(TAG, "Detaching DSP Pipeline from AudioSession: $sessionId")
        pipelines.remove(sessionId)?.let { pipeline ->
            try {
                pipeline.dynamicsProcessing.release()
                pipeline.virtualizer.release()
                pipeline.bassBoost.release()
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing session $sessionId pipeline: ${e.message}")
            }
        }
        updateState()
    }

    /**
     * Update the active DSP configuration across all running sessions in real-time.
     */
    @Synchronized
    fun updateConfig(config: DspConfig) {
        currentConfig = config
        for ((_, pipeline) in pipelines) {
            applyConfigToPipeline(pipeline, config)
        }
        updateState()
    }

    private fun applyConfigToSession(sessionId: Int, config: DspConfig) {
        pipelines[sessionId]?.let { pipeline ->
            applyConfigToPipeline(pipeline, config)
        }
    }

    private fun applyConfigToPipeline(pipeline: SessionPipeline, config: DspConfig) {
        try {
            // DynamicsProcessing carries EQ, MDRC, Tone, Limiter, AGC, Master Gain
            pipeline.dynamicsProcessing.applyConfig(config)

            // Bass Boost
            pipeline.bassBoost.apply(
                config.isEnabled && config.bassBoostEnabled,
                config.bassBoostStrength
            )

            // Virtualizer
            pipeline.virtualizer.apply(
                config.isEnabled && config.virtualizerEnabled,
                config.virtualizerStrength
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error applying config to pipeline: ${e.message}", e)
        }
    }

    /**
     * Attempt to reclaim effect control on all active sessions.
     */
    @Synchronized
    fun reclaimAllControl() {
        for ((sessionId, pipeline) in pipelines) {
            Log.d(TAG, "Reclaiming control on session $sessionId...")
            pipeline.dynamicsProcessing.reclaimControl()
        }
    }

    /**
     * Stop and release all audio effects completely.
     */
    @Synchronized
    fun stop() {
        Log.i(TAG, "Stopping sBz DSP Engine and releasing all effects...")
        for ((sessionId, pipeline) in pipelines) {
            try {
                pipeline.dynamicsProcessing.release()
                pipeline.virtualizer.release()
                pipeline.bassBoost.release()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping session $sessionId: ${e.message}")
            }
        }
        pipelines.clear()
        _engineState.value = EngineStatus(isRunning = false)
    }

    private fun updateState() {
        val dpAvail = pipelines.values.any { it.dynamicsProcessing.isAvailable() }
        _engineState.value = _engineState.value.copy(
            activeSessions = pipelines.keys.toSet(),
            globalSessionAttached = pipelines.containsKey(GLOBAL_SESSION_ID),
            dynamicsProcessingAvailable = dpAvail
        )
    }
}
