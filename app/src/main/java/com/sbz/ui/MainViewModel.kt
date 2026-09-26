package com.sbz.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sbz.data.PresetRepository
import com.sbz.dsp.SbzDspEngine
import com.sbz.dsp.model.DspConfig
import com.sbz.dsp.model.MdrcBandConfig
import com.sbz.dsp.model.Preset
import com.sbz.service.SbzAudioService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Main application ViewModel connecting Compose UI to the background SbzAudioService.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val presetRepo = PresetRepository(application)

    private val _config = MutableStateFlow(presetRepo.loadActiveConfig())
    val config: StateFlow<DspConfig> = _config.asStateFlow()

    private val _engineStatus = MutableStateFlow(SbzDspEngine.EngineStatus())
    val engineStatus: StateFlow<SbzDspEngine.EngineStatus> = _engineStatus.asStateFlow()

    private val _presets = MutableStateFlow(presetRepo.getAllPresets())
    val presets: StateFlow<List<Preset>> = _presets.asStateFlow()

    private val _selectedPresetId = MutableStateFlow(presetRepo.getSelectedPresetId())
    val selectedPresetId: StateFlow<String> = _selectedPresetId.asStateFlow()

    private val _isServiceBound = MutableStateFlow(false)
    val isServiceBound: StateFlow<Boolean> = _isServiceBound.asStateFlow()

    private var audioService: SbzAudioService? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? SbzAudioService.LocalBinder
            audioService = binder?.getService()
            _isServiceBound.value = true

            audioService?.let { s ->
                _config.value = s.getCurrentConfig()
                viewModelScope.launch {
                    s.dspEngine.engineState.collect { status ->
                        _engineStatus.value = status
                    }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            audioService = null
            _isServiceBound.value = false
        }
    }

    init {
        bindToService()
    }

    private fun bindToService() {
        val app = getApplication<Application>()
        val intent = Intent(app, SbzAudioService::class.java)
        app.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    fun toggleDsp() {
        val current = _config.value
        updateConfig(current.copy(isEnabled = !current.isEnabled))
    }

    fun setMasterGain(gainDb: Float) {
        val current = _config.value
        updateConfig(current.copy(masterGainDb = gainDb.coerceIn(-24.0f, 12.0f)))
    }

    fun setBalance(balance: Float) {
        val current = _config.value
        updateConfig(current.copy(balance = balance.coerceIn(-1.0f, 1.0f)))
    }

    fun setPreGain(gainDb: Float) {
        val current = _config.value
        updateConfig(current.copy(preGainDb = gainDb.coerceIn(-12.0f, 12.0f)))
    }

    fun setBassBoost(enabled: Boolean, strength: Short) {
        val current = _config.value
        updateConfig(current.copy(bassBoostEnabled = enabled, bassBoostStrength = strength))
    }

    fun setTone(bassDb: Float, midDb: Float, trebleDb: Float) {
        val current = _config.value
        updateConfig(
            current.copy(
                toneBassDb = bassDb.coerceIn(-12.0f, 12.0f),
                toneMidDb = midDb.coerceIn(-12.0f, 12.0f),
                toneTrebleDb = trebleDb.coerceIn(-12.0f, 12.0f)
            )
        )
    }

    fun setBandGain(index: Int, gainDb: Float) {
        val current = _config.value
        if (index !in current.eqGains.indices) return
        val newGains = current.eqGains.toMutableList()
        newGains[index] = gainDb.coerceIn(-15.0f, 15.0f)
        updateConfig(current.copy(eqGains = newGains))
    }

    fun resetEq() {
        val current = _config.value
        updateConfig(current.copy(eqGains = List(32) { 0.0f }))
    }

    fun setMdrcEnabled(enabled: Boolean) {
        val current = _config.value
        updateConfig(current.copy(mdrcEnabled = enabled))
    }

    fun updateMdrcBand(index: Int, band: MdrcBandConfig) {
        val current = _config.value
        if (index !in current.mdrcBands.indices) return
        val newBands = current.mdrcBands.toMutableList()
        newBands[index] = band
        updateConfig(current.copy(mdrcBands = newBands))
    }

    fun setAutoGain(enabled: Boolean, targetDb: Float) {
        val current = _config.value
        updateConfig(current.copy(autoGainEnabled = enabled, autoGainTargetDb = targetDb))
    }

    fun setVirtualizer(enabled: Boolean, strength: Short) {
        val current = _config.value
        updateConfig(current.copy(virtualizerEnabled = enabled, virtualizerStrength = strength))
    }

    fun setLimiter(
        enabled: Boolean,
        thresholdDb: Float,
        attackMs: Float,
        releaseMs: Float,
        ratio: Float,
        postGainDb: Float
    ) {
        val current = _config.value
        updateConfig(
            current.copy(
                limiterEnabled = enabled,
                limiterThresholdDb = thresholdDb,
                limiterAttackMs = attackMs,
                limiterReleaseMs = releaseMs,
                limiterRatio = ratio,
                limiterPostGainDb = postGainDb
            )
        )
    }

    fun applyPreset(preset: Preset) {
        _selectedPresetId.value = preset.id
        presetRepo.setSelectedPresetId(preset.id)
        updateConfig(preset.config)
    }

    fun saveNewPreset(name: String) {
        val saved = presetRepo.saveCustomPreset(name, _config.value)
        _presets.value = presetRepo.getAllPresets()
        _selectedPresetId.value = saved.id
        presetRepo.setSelectedPresetId(saved.id)
        // Keep the active state synchronized even when the service is already bound.
        presetRepo.saveActiveConfig(_config.value)
    }

    fun deletePreset(id: String) {
        presetRepo.deleteCustomPreset(id)
        if (_selectedPresetId.value == id) {
            _selectedPresetId.value = "system_flat"
            presetRepo.setSelectedPresetId("system_flat")
        }
        _presets.value = presetRepo.getAllPresets()
    }

    fun duplicatePreset(id: String, name: String) {
        presetRepo.duplicateCustomPreset(id, name.trim())
        _presets.value = presetRepo.getAllPresets()
    }

    fun renamePreset(id: String, name: String) {
        if (presetRepo.renameCustomPreset(id, name.trim())) {
            _presets.value = presetRepo.getAllPresets()
        }
    }

    fun reclaimDspControl() {
        audioService?.let {
            val intent = Intent(getApplication(), SbzAudioService::class.java).apply {
                action = SbzAudioService.ACTION_RECLAIM_CONTROL
            }
            getApplication<Application>().startService(intent)
        }
    }

    private fun updateConfig(newConfig: DspConfig) {
        _config.value = newConfig
        // Persist immediately. The service also persists, but doing it here
        // guarantees changes survive UI/service lifecycle transitions.
        presetRepo.saveActiveConfig(newConfig)
        audioService?.updateConfig(newConfig)
    }

    override fun onCleared() {
        try {
            getApplication<Application>().unbindService(serviceConnection)
        } catch (e: Exception) {
            // Ignored
        }
        super.onCleared()
    }
}
