package com.sbz.data

import android.content.Context
import android.content.SharedPreferences
import com.sbz.dsp.model.DspConfig
import com.sbz.dsp.model.MdrcBandConfig
import com.sbz.dsp.model.Preset
import org.json.JSONArray
import org.json.JSONObject

/**
 * Storage and management of built-in factory and user-defined DSP Presets.
 *
 * Persists:
 * - Complete DSP configuration
 * - 32-band EQ gains
 * - MDRC configuration, including editable crossover/cutoff frequencies
 * - Limiter
 * - AutoGain
 * - Bass Boost
 * - Tone controls
 * - Virtualizer
 * - Master Gain
 * - Balance
 */
class PresetRepository(context: Context) {

    companion object {
        private const val PREFS_NAME = "sbz_presets_store"
        private const val KEY_CUSTOM_PRESETS = "custom_presets"
        private const val KEY_ACTIVE_CONFIG = "active_dsp_config"
        private const val KEY_SELECTED_PRESET_ID = "selected_preset_id"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getAllPresets(): List<Preset> {
        val systemPresets = Preset.createDefaultPresets()
        val customPresets = loadCustomPresets()
        return systemPresets + customPresets
    }

    fun getSelectedPresetId(): String =
        prefs.getString(KEY_SELECTED_PRESET_ID, "system_flat") ?: "system_flat"

    fun setSelectedPresetId(id: String) {
        prefs.edit()
            .putString(KEY_SELECTED_PRESET_ID, id)
            .apply()
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
        val list = loadCustomPresets()
            .filter { it.id != id }

        saveCustomPresets(list)
    }

    fun duplicateCustomPreset(
        id: String,
        newName: String
    ): Preset? {
        val source = loadCustomPresets()
            .firstOrNull { it.id == id }
            ?: return null

        return saveCustomPreset(
            name = newName,
            config = source.config
        )
    }

    fun renameCustomPreset(
        id: String,
        newName: String
    ): Boolean {
        val trimmedName = newName.trim()

        if (trimmedName.isEmpty()) {
            return false
        }

        val list = loadCustomPresets().toMutableList()
        val index = list.indexOfFirst { it.id == id }

        if (index < 0) {
            return false
        }

        list[index] = list[index].copy(
            name = trimmedName
        )

        saveCustomPresets(list)
        return true
    }

    fun saveActiveConfig(config: DspConfig) {
        val json = configToJson(config)

        prefs.edit()
            .putString(KEY_ACTIVE_CONFIG, json.toString())
            .apply()
    }

    fun loadActiveConfig(): DspConfig {
        val raw = prefs.getString(KEY_ACTIVE_CONFIG, null)
            ?: return DspConfig()

        return try {
            jsonToConfig(JSONObject(raw))
        } catch (_: Exception) {
            DspConfig()
        }
    }

    private fun loadCustomPresets(): List<Preset> {
        val raw = prefs.getString(KEY_CUSTOM_PRESETS, null)
            ?: return emptyList()

        val result = mutableListOf<Preset>()

        try {
            val array = JSONArray(raw)

            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i)
                    ?: continue

                val id = obj.optString("id", "")
                val name = obj.optString("name", "")

                if (id.isBlank() || name.isBlank()) {
                    continue
                }

                val configObject = obj.optJSONObject("config")
                    ?: continue

                val config = jsonToConfig(configObject)

                result += Preset(
                    id = id,
                    name = name,
                    category = obj.optString(
                        "category",
                        "Personalizados"
                    ),
                    isSystem = false,
                    config = config
                )
            }
        } catch (_: Exception) {
            // A corrupted custom-preset store must not break the DSP.
            return emptyList()
        }

        return result
    }

    /**
     * Reads MDRC bands individually.
     *
     * Unlike the previous implementation, one invalid/missing band does not
     * cause the entire MDRC configuration to be replaced by factory defaults.
     *
     * Existing valid custom values are preserved and only missing/invalid
     * entries are filled from the corresponding factory band.
     */
    private fun jsonToMdrcBands(
        obj: JSONObject
    ): List<MdrcBandConfig> {

        val defaults = DspConfig.defaultMdrcBands()
        val array = obj.optJSONArray("mdrcBands")

        if (array == null) {
            return defaults
        }

        val result = ArrayList<MdrcBandConfig>(defaults.size)

        for (i in defaults.indices) {
            val defaultBand = defaults[i]
            val jsonBand = array.optJSONObject(i)

            if (jsonBand == null) {
                result += defaultBand
                continue
            }

            result += MdrcBandConfig(
                name = jsonBand.optString(
                    "name",
                    defaultBand.name
                ),

                cutoffFrequencyHz = readFiniteFloat(
                    jsonBand,
                    "cutoffFrequencyHz",
                    defaultBand.cutoffFrequencyHz
                ).coerceIn(20.0f, 22000.0f),

                thresholdDb = readFiniteFloat(
                    jsonBand,
                    "thresholdDb",
                    defaultBand.thresholdDb
                ),

                ratio = readFiniteFloat(
                    jsonBand,
                    "ratio",
                    defaultBand.ratio
                ).coerceAtLeast(1.0f),

                attackMs = readFiniteFloat(
                    jsonBand,
                    "attackMs",
                    defaultBand.attackMs
                ).coerceAtLeast(0.0f),

                releaseMs = readFiniteFloat(
                    jsonBand,
                    "releaseMs",
                    defaultBand.releaseMs
                ).coerceAtLeast(0.0f),

                makeupGainDb = readFiniteFloat(
                    jsonBand,
                    "makeupGainDb",
                    defaultBand.makeupGainDb
                ),

                kneeDb = readFiniteFloat(
                    jsonBand,
                    "kneeDb",
                    defaultBand.kneeDb
                ).coerceAtLeast(0.0f)
            )
        }

        /*
         * Keep the four-band MDRC structure expected by DspConfig.
         *
         * Do not reorder or replace user values here. The actual cutoff
         * normalization/order handling is performed by the DSP manager
         * immediately before applying the configuration to DynamicsProcessing.
         */
        return result
    }

    /**
     * Safely reads a floating-point value from JSON.
     *
     * Invalid, missing, NaN or infinite values fall back to the supplied
     * default instead of corrupting the DSP configuration.
     */
    private fun readFiniteFloat(
        obj: JSONObject,
        key: String,
        defaultValue: Float
    ): Float {
        val value = obj.optDouble(
            key,
            defaultValue.toDouble()
        )

        return if (value.isFinite()) {
            value.toFloat()
        } else {
            defaultValue
        }
    }

    private fun saveCustomPresets(
        list: List<Preset>
    ) {
        val array = JSONArray()

        for (preset in list) {
            val obj = JSONObject().apply {
                put("id", preset.id)
                put("name", preset.name)
                put("category", preset.category)
                put("config", configToJson(preset.config))
            }

            array.put(obj)
        }

        prefs.edit()
            .putString(
                KEY_CUSTOM_PRESETS,
                array.toString()
            )
            .apply()
    }

    private fun configToJson(
        c: DspConfig
    ): JSONObject {
        return JSONObject().apply {

            put(
                "isEnabled",
                c.isEnabled
            )

            put(
                "preGainDb",
                c.preGainDb.toDouble()
            )

            put(
                "bassBoostEnabled",
                c.bassBoostEnabled
            )

            put(
                "bassBoostStrength",
                c.bassBoostStrength.toInt()
            )

            put(
                "toneBassDb",
                c.toneBassDb.toDouble()
            )

            put(
                "toneMidDb",
                c.toneMidDb.toDouble()
            )

            put(
                "toneTrebleDb",
                c.toneTrebleDb.toDouble()
            )

            /*
             * Full 32-band EQ configuration is always persisted.
             */
            val eqArray = JSONArray()

            c.eqGains.forEach { gain ->
                eqArray.put(gain.toDouble())
            }

            put(
                "eqGains",
                eqArray
            )

            /*
             * MDRC
             *
             * The editable crossover/cutoff frequency is persisted as
             * cutoffFrequencyHz together with all other band parameters.
             */
            put(
                "mdrcEnabled",
                c.mdrcEnabled
            )

            val mdrcArray = JSONArray()

            c.mdrcBands.forEach { band ->

                mdrcArray.put(
                    JSONObject().apply {

                        put(
                            "name",
                            band.name
                        )

                        put(
                            "cutoffFrequencyHz",
                            band.cutoffFrequencyHz.toDouble()
                        )

                        put(
                            "thresholdDb",
                            band.thresholdDb.toDouble()
                        )

                        put(
                            "ratio",
                            band.ratio.toDouble()
                        )

                        put(
                            "attackMs",
                            band.attackMs.toDouble()
                        )

                        put(
                            "releaseMs",
                            band.releaseMs.toDouble()
                        )

                        put(
                            "makeupGainDb",
                            band.makeupGainDb.toDouble()
                        )

                        put(
                            "kneeDb",
                            band.kneeDb.toDouble()
                        )
                    }
                )
            }

            put(
                "mdrcBands",
                mdrcArray
            )

            put(
                "autoGainEnabled",
                c.autoGainEnabled
            )

            put(
                "autoGainTargetDb",
                c.autoGainTargetDb.toDouble()
            )

            put(
                "virtualizerEnabled",
                c.virtualizerEnabled
            )

            put(
                "virtualizerStrength",
                c.virtualizerStrength.toInt()
            )

            put(
                "masterGainDb",
                c.masterGainDb.toDouble()
            )

            put(
                "balance",
                c.balance.toDouble()
            )

            put(
                "limiterEnabled",
                c.limiterEnabled
            )

            put(
                "limiterThresholdDb",
                c.limiterThresholdDb.toDouble()
            )

            put(
                "limiterAttackMs",
                c.limiterAttackMs.toDouble()
            )

            put(
                "limiterReleaseMs",
                c.limiterReleaseMs.toDouble()
            )

            put(
                "limiterRatio",
                c.limiterRatio.toDouble()
            )

            put(
                "limiterPostGainDb",
                c.limiterPostGainDb.toDouble()
            )
        }
    }

    private fun jsonToConfig(
        obj: JSONObject
    ): DspConfig {

        /*
         * EQ: always restore a complete 32-band array.
         * Older/incomplete presets fall back to flat for missing bands.
         */
        val eqList = MutableList(32) { 0.0f }

        obj.optJSONArray("eqGains")?.let { array ->

            val count = minOf(
                array.length(),
                eqList.size
            )

            for (i in 0 until count) {
                val value = array.optDouble(
                    i,
                    0.0
                )

                if (value.isFinite()) {
                    eqList[i] = value.toFloat()
                }
            }
        }

        return DspConfig(

            isEnabled = obj.optBoolean(
                "isEnabled",
                true
            ),

            preGainDb = readFiniteFloat(
                obj,
                "preGainDb",
                0.0f
            ),

            bassBoostEnabled = obj.optBoolean(
                "bassBoostEnabled",
                false
            ),

            bassBoostStrength = obj.optInt(
                "bassBoostStrength",
                0
            ).toShort(),

            toneBassDb = readFiniteFloat(
                obj,
                "toneBassDb",
                0.0f
            ),

            toneMidDb = readFiniteFloat(
                obj,
                "toneMidDb",
                0.0f
            ),

            toneTrebleDb = readFiniteFloat(
                obj,
                "toneTrebleDb",
                0.0f
            ),

            eqGains = eqList,

            mdrcEnabled = obj.optBoolean(
                "mdrcEnabled",
                true
            ),

            mdrcBands = jsonToMdrcBands(obj),

            autoGainEnabled = obj.optBoolean(
                "autoGainEnabled",
                true
            ),

            autoGainTargetDb = readFiniteFloat(
                obj,
                "autoGainTargetDb",
                -14.0f
            ),

            virtualizerEnabled = obj.optBoolean(
                "virtualizerEnabled",
                false
            ),

            virtualizerStrength = obj.optInt(
                "virtualizerStrength",
                0
            ).toShort(),

            masterGainDb = readFiniteFloat(
                obj,
                "masterGainDb",
                0.0f
            ),

            balance = readFiniteFloat(
                obj,
                "balance",
                0.0f
            ),

            limiterEnabled = obj.optBoolean(
                "limiterEnabled",
                true
            ),

            limiterThresholdDb = readFiniteFloat(
                obj,
                "limiterThresholdDb",
                -0.5f
            ),

            limiterAttackMs = readFiniteFloat(
                obj,
                "limiterAttackMs",
                1.0f
            ),

            limiterReleaseMs = readFiniteFloat(
                obj,
                "limiterReleaseMs",
                50.0f
            ),

            limiterRatio = readFiniteFloat(
                obj,
                "limiterRatio",
                20.0f
            ),

            limiterPostGainDb = readFiniteFloat(
                obj,
                "limiterPostGainDb",
                0.0f
            )
        )
    }
}
