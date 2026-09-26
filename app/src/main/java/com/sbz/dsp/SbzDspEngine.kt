package com.sbz.dsp

import com.sbz.dsp.model.DspConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/** Coordinates the deterministic DSP core. Audio-session effects are kept optional and never assumed global. */
class SbzDspEngine {
    data class EngineStatus(val isRunning:Boolean=false, val activeSessions:Int=0, val lastError:String?=null)
    private val _engineState=MutableStateFlow(EngineStatus())
    val engineState: StateFlow<EngineStatus> = _engineState.asStateFlow()
    private val processors=ConcurrentHashMap<Int,StereoDspProcessor>()
    private var config=DspConfig()

    fun start() { _engineState.value=EngineStatus(true,processors.size,null) }
    fun stop() { processors.values.forEach { it.reset() }; processors.clear(); _engineState.value=EngineStatus(false,0,null) }
    fun updateConfig(newConfig:DspConfig) { config=newConfig.normalized(); processors.values.forEach { it.updateConfig(config) } }

    fun attachSession(sessionId:Int) {
        if(sessionId<0) return
        processors.computeIfAbsent(sessionId){ StereoDspProcessor().also{it.updateConfig(config)} }
        _engineState.value=_engineState.value.copy(activeSessions=processors.size)
    }
    fun detachSession(sessionId:Int) { processors.remove(sessionId)?.reset(); _engineState.value=_engineState.value.copy(activeSessions=processors.size) }
    fun reclaimAllControl() { _engineState.value=_engineState.value.copy(activeSessions=processors.size) }
    fun process(sessionId:Int, pcm:FloatArray) { processors[sessionId]?.process(pcm) }
}
