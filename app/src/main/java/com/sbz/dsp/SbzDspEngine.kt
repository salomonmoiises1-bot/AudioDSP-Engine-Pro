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
        val hallReverb: HallReverbManager
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
        val hallReverbAvailable: Boolean = false,
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
        if (sessionId != GLOBAL_SESSION_ID && pipelines.containsKey(GLOBAL_SESSION_ID)) {
            Log.i(TAG, "Real audio session $sessionId received; replacing global fallback session 0")
            pipelines.remove(GLOBAL_SESSION_ID)?.let { releasePipeline(it, GLOBAL_SESSION_ID) }
        }

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
            val hallManager = HallReverbManager(sessionId)

            val pipeline = SessionPipeline(dpManager, virtManager, hallManager)
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
        if (sessionId == GLOBAL_SESSION_ID && _engineState.value.isRunning) {
            Log.d(TAG, "Ignoring detach on Global Session 0 while engine is running")
            return
        }
        Log.i(TAG, "Detaching DSP Pipeline from AudioSession: $sessionId")
        pipelines.remove(sessionId)?.let { releasePipeline(it, sessionId) }

        if (sessionId != GLOBAL_SESSION_ID && pipelines.isEmpty() && _engineState.value.isRunning) {
            Log.i(TAG, "No real audio sessions remain; restoring global fallback session 0")
            attachSession(GLOBAL_SESSION_ID)
        }
        updateState()
    }

    private fun releasePipeline(pipeline: SessionPipeline, sessionId: Int) {
        try {
            pipeline.dynamicsProcessing.release()
            pipeline.virtualizer.release()
            pipeline.hallReverb.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing session $sessionId pipeline: ${e.message}")
        }
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

    @Synchronized
    fun updateRealtimeConfig(config: DspConfig) {
        val previous = currentConfig
        currentConfig = config

        val eqChanged = previous.eqGains != config.eqGains
        val changedEqIndices = if (eqChanged) {
            config.eqGains.indices.filter { i -> previous.eqGains.getOrNull(i) != config.eqGains.getOrNull(i) }
        } else emptyList()

        val onlyFastChanges = previous.isEnabled == config.isEnabled &&
                previous.preGainDb == config.preGainDb &&
                previous.bassBoostEnabled == config.bassBoostEnabled &&
                previous.bassBoostStrength == config.bassBoostStrength &&
                previous.mdrcEnabled == config.mdrcEnabled &&
                previous.mdrcBands == config.mdrcBands &&
                previous.hallEnabled == config.hallEnabled &&
                previous.hallMixPercent == config.hallMixPercent &&
                previous.hallDecayTimeMs == config.hallDecayTimeMs &&
                previous.hallDecayHfRatio == config.hallDecayHfRatio &&
                previous.hallDensityPercent == config.hallDensityPercent &&
                previous.hallDiffusionPercent == config.hallDiffusionPercent &&
                previous.hallReflectionsDelayMs == config.hallReflectionsDelayMs &&
                previous.hallReflectionsLevelDb == config.hallReflectionsLevelDb &&
                previous.hallReverbDelayMs == config.hallReverbDelayMs &&
                previous.hallRoomHfLevelDb == config.hallRoomHfLevelDb &&
                previous.hallRoomLevelDb == config.hallRoomLevelDb &&
                previous.autoGainEnabled == config.autoGainEnabled &&
                previous.autoGainTargetDb == config.autoGainTargetDb &&
                previous.virtualizerEnabled == config.virtualizerEnabled &&
                previous.virtualizerStrength == config.virtualizerStrength &&
                previous.limiterEnabled == config.limiterEnabled &&
                previous.limiterThresholdDb == config.limiterThresholdDb &&
                previous.limiterAttackMs == config.limiterAttackMs &&
                previous.limiterReleaseMs == config.limiterReleaseMs &&
                previous.limiterRatio == config.limiterRatio &&
                previous.limiterPostGainDb == config.limiterPostGainDb

        if (!onlyFastChanges) {
            updateConfig(config)
            return
        }

        for (pipeline in pipelines.values) {
            if (previous.masterGainDb != config.masterGainDb) {
                pipeline.dynamicsProcessing.updateMasterGain(config)
            }
            if (previous.balance != config.balance) {
                pipeline.dynamicsProcessing.updateBalance(config)
            }
            if (previous.toneBassDb != config.toneBassDb || previous.toneMidDb != config.toneMidDb || previous.toneTrebleDb != config.toneTrebleDb) {
                pipeline.dynamicsProcessing.updateTone(config)
            }
            for (index in changedEqIndices) {
                pipeline.dynamicsProcessing.updateEqBand(config, index)
            }
        }
        updateState()
    }

    @Synchronized
    fun updateMdrcBand(config: DspConfig, bandIndex: Int) {
        currentConfig = config
        for ((_, pipeline) in pipelines) {
            pipeline.dynamicsProcessing.updateMdrcBand(config, bandIndex)
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
            pipeline.dynamicsProcessing.applyConfig(config)
            pipeline.virtualizer.apply(
                config.isEnabled && config.virtualizerEnabled,
                config.virtualizerStrength
            )
            pipeline.hallReverb.apply(config)
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
            pipeline.hallReverb.reclaimControl()
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
                pipeline.hallReverb.release()
                } catch (e: Exception) {
                Log.w(TAG, "Error stopping session $sessionId: ${e.message}")
            }
        }
        pipelines.clear()
        _engineState.value = EngineStatus(isRunning = false)
    }

    private fun updateState() {
        val dpAvail = pipelines.values.any { it.dynamicsProcessing.isAvailable() }
        val hallAvail = pipelines.values.any { it.hallReverb.isAvailable() }
        _engineState.value = _engineState.value.copy(
            activeSessions = pipelines.keys.toSet(),
            globalSessionAttached = pipelines.containsKey(GLOBAL_SESSION_ID),
            dynamicsProcessingAvailable = dpAvail,
            hallReverbAvailable = hallAvail
        )
    }
}
