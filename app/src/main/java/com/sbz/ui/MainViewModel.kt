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
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    private var persistJob: Job? = null

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
        updateRealtimeConfig(current.copy(masterGainDb = gainDb.coerceIn(-24.0f, 12.0f)))
    }

    fun setBalance(balance: Float) {
        val current = _config.value
        updateRealtimeConfig(current.copy(balance = balance.coerceIn(-1.0f, 1.0f)))
    }

    fun setPreGain(gainDb: Float) {
        val current = _config.value
        updateRealtimeConfig(current.copy(preGainDb = gainDb.coerceIn(-12.0f, 12.0f)))
    }

    fun setBassBoost(enabled: Boolean, strength: Short) {
        val current = _config.value
        updateRealtimeConfig(
            current.copy(
                bassBoostEnabled = enabled,
                bassBoostStrength = strength.coerceIn(0, 1000)
            )
        )
    }

    fun setTone(bassDb: Float, midDb: Float, trebleDb: Float) {
        val current = _config.value
        updateRealtimeConfig(
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
        updateRealtimeConfig(current.copy(eqGains = newGains))
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
        val newConfig = current.copy(mdrcBands = newBands)

        // MDRC crossover faders use a surgical native update. This prevents
        // every drag event from rebuilding the complete DSP chain.
        _config.value = newConfig
        audioService?.updateMdrcBand(newConfig, index)

        // Keep persistence debounced so storage is not written per motion event.
        schedulePersist(newConfig)
    }

    fun setHallEnabled(enabled: Boolean) {
        val current = _config.value
        updateConfig(current.copy(hallEnabled = enabled))
    }

    fun setHallMix(percent: Float) {
        updateConfig(_config.value.copy(hallMixPercent = percent.coerceIn(0f, 100f)))
    }

    fun setHallDecayTime(ms: Float) {
        updateConfig(_config.value.copy(hallDecayTimeMs = ms.coerceIn(100f, 20_000f)))
    }

    fun setHallDecayHfRatio(ratio: Float) {
        updateConfig(_config.value.copy(hallDecayHfRatio = ratio.coerceIn(0.1f, 2.0f)))
    }

    fun setHallDensity(percent: Float) {
        updateConfig(_config.value.copy(hallDensityPercent = percent.coerceIn(0f, 100f)))
    }

    fun setHallDiffusion(percent: Float) {
        updateConfig(_config.value.copy(hallDiffusionPercent = percent.coerceIn(0f, 100f)))
    }

    fun setHallReflectionsDelay(ms: Float) {
        updateConfig(_config.value.copy(hallReflectionsDelayMs = ms.coerceIn(0f, 300f)))
    }

    fun setHallReflectionsLevel(db: Float) {
        updateConfig(_config.value.copy(hallReflectionsLevelDb = db.coerceIn(-90f, 10f)))
    }

    fun setHallReverbDelay(ms: Float) {
        updateConfig(_config.value.copy(hallReverbDelayMs = ms.coerceIn(0f, 100f)))
    }

    fun setHallRoomHfLevel(db: Float) {
        updateConfig(_config.value.copy(hallRoomHfLevelDb = db.coerceIn(-90f, 0f)))
    }

    fun setHallRoomLevel(db: Float) {
        updateConfig(_config.value.copy(hallRoomLevelDb = db.coerceIn(-90f, 0f)))
    }

    fun setAutoGain(enabled: Boolean, targetDb: Float) {
        val current = _config.value
        updateRealtimeConfig(
            current.copy(
                autoGainEnabled = enabled,
                autoGainTargetDb = targetDb.coerceIn(-60f, 0f)
            )
        )
    }

    fun setVirtualizer(enabled: Boolean, strength: Short) {
        val current = _config.value
        updateRealtimeConfig(
            current.copy(
                virtualizerEnabled = enabled,
                virtualizerStrength = strength.coerceIn(0, 1000)
            )
        )
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
        updateRealtimeConfig(
            current.copy(
                limiterEnabled = enabled,
                limiterThresholdDb = thresholdDb.coerceIn(-60f, 0f),
                limiterAttackMs = attackMs.coerceIn(0.1f, 1000f),
                limiterReleaseMs = releaseMs.coerceIn(1f, 2000f),
                limiterRatio = ratio.coerceIn(1f, 100f),
                limiterPostGainDb = postGainDb.coerceIn(-24f, 12f)
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
        schedulePersist(_config.value, immediate = true)
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

    private fun updateRealtimeConfig(newConfig: DspConfig) {
        _config.value = newConfig
        audioService?.updateRealtimeConfig(newConfig)
        schedulePersist(newConfig)
    }

    private fun updateConfig(newConfig: DspConfig) {
        _config.value = newConfig

        // DSP changes are applied immediately, but disk persistence is debounced.
        // This avoids SharedPreferences I/O on every fader movement.
        audioService?.updateConfig(newConfig, persist = false)
        schedulePersist(newConfig)
    }

    private fun schedulePersist(
        config: DspConfig,
        immediate: Boolean = false
    ) {
        persistJob?.cancel()
        persistJob = viewModelScope.launch(Dispatchers.IO) {
            if (!immediate) delay(250L)
            presetRepo.saveActiveConfig(config)
        }
    }

    override fun onCleared() {
        try {
            getApplication<Application>().unbindService(serviceConnection)
        } catch (e: Exception) {
            // Ignored
        }
        persistJob?.cancel()
        presetRepo.saveActiveConfig(_config.value)
        super.onCleared()
    }
}
