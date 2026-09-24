package com.sbz.data

import android.content.Context
import android.content.SharedPreferences
import com.sbz.dsp.model.DspConfig
import com.sbz.dsp.model.Preset
import org.json.JSONArray
import org.json.JSONObject

/**
 * Storage and management of built-in factory and user-defined DSP Presets.
 */
class PresetRepository(context: Context) {

    companion object {
        private const val PREFS_NAME = "sbz_presets_store"
        private const val KEY_CUSTOM_PRESETS = "custom_presets"
        private const val KEY_ACTIVE_CONFIG = "active_dsp_config"
        private const val KEY_SELECTED_PRESET_ID = "selected_preset_id"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getAllPresets(): List<Preset> {
        val systemPresets = Preset.createDefaultPresets()
        val customPresets = loadCustomPresets()
        return systemPresets + customPresets
    }

    fun getSelectedPresetId(): String {
        return prefs.getString(KEY_SELECTED_PRESET_ID, "system_flat") ?: "system_flat"
    }

    fun setSelectedPresetId(id: String) {
        prefs.edit().putString(KEY_SELECTED_PRESET_ID, id).apply()
    }

    fun saveCustomPreset(name: String, config: DspConfig): Preset {
        val newPreset = Preset(
            name = name,
            isSystem = false,
            config = config
        )
        val list = loadCustomPresets().toMutableList()
        list.add(newPreset)
        saveCustomPresets(list)
        return newPreset
    }

    fun deleteCustomPreset(id: String) {
        val list = loadCustomPresets().filter { it.id != id }
        saveCustomPresets(list)
    }

    fun saveActiveConfig(config: DspConfig) {
        val json = configToJson(config)
        prefs.edit().putString(KEY_ACTIVE_CONFIG, json.toString()).apply()
    }

    fun loadActiveConfig(): DspConfig {
        val raw = prefs.getString(KEY_ACTIVE_CONFIG, null) ?: return DspConfig()
        return try {
            jsonToConfig(JSONObject(raw))
        } catch (e: Exception) {
            DspConfig()
        }
    }

    private fun loadCustomPresets(): List<Preset> {
        val raw = prefs.getString(KEY_CUSTOM_PRESETS, null) ?: return emptyList()
        val result = mutableListOf<Preset>()
        try {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.getString("id")
                val name = obj.getString("name")
                val config = jsonToConfig(obj.getJSONObject("config"))
                result.add(Preset(id = id, name = name, isSystem = false, config = config))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result
    }

    private fun saveCustomPresets(list: List<Preset>) {
        val array = JSONArray()
        for (preset in list) {
            val obj = JSONObject().apply {
                put("id", preset.id)
                put("name", preset.name)
                put("config", configToJson(preset.config))
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_CUSTOM_PRESETS, array.toString()).apply()
    }

    private fun configToJson(c: DspConfig): JSONObject {
        return JSONObject().apply {
            put("isEnabled", c.isEnabled)
            put("preGainDb", c.preGainDb.toDouble())
            put("bassBoostEnabled", c.bassBoostEnabled)
            put("bassBoostStrength", c.bassBoostStrength.toInt())
            put("toneBassDb", c.toneBassDb.toDouble())
            put("toneMidDb", c.toneMidDb.toDouble())
            put("toneTrebleDb", c.toneTrebleDb.toDouble())

            val eqArray = JSONArray()
            c.eqGains.forEach { eqArray.put(it.toDouble()) }
            put("eqGains", eqArray)

            put("mdrcEnabled", c.mdrcEnabled)
            put("autoGainEnabled", c.autoGainEnabled)
            put("autoGainTargetDb", c.autoGainTargetDb.toDouble())

            put("virtualizerEnabled", c.virtualizerEnabled)
            put("virtualizerStrength", c.virtualizerStrength.toInt())

            put("masterGainDb", c.masterGainDb.toDouble())
            put("balance", c.balance.toDouble())

            put("limiterEnabled", c.limiterEnabled)
            put("limiterThresholdDb", c.limiterThresholdDb.toDouble())
            put("limiterAttackMs", c.limiterAttackMs.toDouble())
            put("limiterReleaseMs", c.limiterReleaseMs.toDouble())
            put("limiterRatio", c.limiterRatio.toDouble())
            put("limiterPostGainDb", c.limiterPostGainDb.toDouble())
        }
    }

    private fun jsonToConfig(obj: JSONObject): DspConfig {
        val eqList = mutableListOf<Float>()
        if (obj.has("eqGains")) {
            val arr = obj.getJSONArray("eqGains")
            for (i in 0 until arr.length()) {
                eqList.add(arr.getDouble(i).toFloat())
            }
        }
        val finalEqList = if (eqList.size == 32) eqList else List(32) { 0.0f }

        return DspConfig(
            isEnabled = obj.optBoolean("isEnabled", true),
            preGainDb = obj.optDouble("preGainDb", 0.0).toFloat(),
            bassBoostEnabled = obj.optBoolean("bassBoostEnabled", false),
            bassBoostStrength = obj.optInt("bassBoostStrength", 0).toShort(),
            toneBassDb = obj.optDouble("toneBassDb", 0.0).toFloat(),
            toneMidDb = obj.optDouble("toneMidDb", 0.0).toFloat(),
            toneTrebleDb = obj.optDouble("toneTrebleDb", 0.0).toFloat(),
            eqGains = finalEqList,
            mdrcEnabled = obj.optBoolean("mdrcEnabled", true),
            autoGainEnabled = obj.optBoolean("autoGainEnabled", true),
            autoGainTargetDb = obj.optDouble("autoGainTargetDb", -14.0).toFloat(),
            virtualizerEnabled = obj.optBoolean("virtualizerEnabled", false),
            virtualizerStrength = obj.optInt("virtualizerStrength", 0).toShort(),
            masterGainDb = obj.optDouble("masterGainDb", 0.0).toFloat(),
            balance = obj.optDouble("balance", 0.0).toFloat(),
            limiterEnabled = obj.optBoolean("limiterEnabled", true),
            limiterThresholdDb = obj.optDouble("limiterThresholdDb", -0.5).toFloat(),
            limiterAttackMs = obj.optDouble("limiterAttackMs", 1.0).toFloat(),
            limiterReleaseMs = obj.optDouble("limiterReleaseMs", 50.0).toFloat(),
            limiterRatio = obj.optDouble("limiterRatio", 20.0).toFloat(),
            limiterPostGainDb = obj.optDouble("limiterPostGainDb", 0.0).toFloat()
        )
    }
}
