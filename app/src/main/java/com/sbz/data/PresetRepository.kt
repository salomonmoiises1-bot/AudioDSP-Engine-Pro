package com.sbz.data

import android.content.Context
import android.content.SharedPreferences
import com.sbz.dsp.model.DspConfig
import com.sbz.dsp.model.MdrcBandConfig
import com.sbz.dsp.model.Preset
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persistent storage for sBz factory presets, custom presets
 * and the currently active DSP configuration.
 *
 * MDRC cutoff frequencies are persisted individually for all
 * four bands.
 */
class PresetRepository(context: Context) {

    companion object {
        private const val PREFS_NAME = "sbz_presets_store"

        private const val KEY_CUSTOM_PRESETS = "custom_presets"
        private const val KEY_ACTIVE_CONFIG = "active_dsp_config"
        private const val KEY_SELECTED_PRESET_ID = "selected_preset_id"

        private const val DEFAULT_PRESET_ID = "system_flat"

        private const val MDRC_BAND_COUNT = 4
        private const val MIN_MDRC_CUTOFF_HZ = 20f
        private const val MAX_MDRC_CUTOFF_HZ = 22000f
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

    /**
     * Returns factory presets followed by user presets.
     */
    fun getAllPresets(): List<Preset> {
        val systemPresets = Preset.createDefaultPresets()
        val customPresets = loadCustomPresets()

        return systemPresets + customPresets
    }

    fun getSelectedPresetId(): String {
        return prefs.getString(
            KEY_SELECTED_PRESET_ID,
            DEFAULT_PRESET_ID
        ) ?: DEFAULT_PRESET_ID
    }

    fun setSelectedPresetId(id: String) {
        prefs.edit()
            .putString(KEY_SELECTED_PRESET_ID, id)
            .apply()
    }

    /**
     * Creates and stores a custom preset.
     *
     * The complete DSP configuration is stored, including all
     * MDRC cutoff frequencies.
     */
    fun saveCustomPreset(
        name: String,
        config: DspConfig
    ): Preset {
        val cleanName = name.trim().ifEmpty {
            "Preset personalizado"
        }

        val newPreset = Preset(
            name = cleanName,
            isSystem = false,
            config = sanitizeConfig(config)
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

    /**
     * Stores the active DSP configuration.
     */
    fun saveActiveConfig(config: DspConfig) {
        val safeConfig = sanitizeConfig(config)

        prefs.edit()
            .putString(
                KEY_ACTIVE_CONFIG,
                configToJson(safeConfig).toString()
            )
            .apply()
    }

    /**
     * Restores the active DSP configuration.
     *
     * If the stored JSON is invalid, a safe default configuration
     * is returned.
     */
    fun loadActiveConfig(): DspConfig {
        val raw = prefs.getString(
            KEY_ACTIVE_CONFIG,
            null
        ) ?: return DspConfig()

        return try {
            sanitizeConfig(
                jsonToConfig(
                    JSONObject(raw)
                )
            )
        } catch (_: Exception) {
            DspConfig()
        }
    }

    private fun loadCustomPresets(): List<Preset> {
        val raw = prefs.getString(
            KEY_CUSTOM_PRESETS,
            null
        ) ?: return emptyList()

        val result = mutableListOf<Preset>()

        try {
            val array = JSONArray(raw)

            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i)
                    ?: continue

                val id = obj.optString(
                    "id",
                    ""
                ).trim()

                val name = obj.optString(
                    "name",
                    "Preset personalizado"
                ).trim()

                if (id.isEmpty()) {
                    continue
                }

                val configObject =
                    obj.optJSONObject("config")
                        ?: JSONObject()

                val config = sanitizeConfig(
                    jsonToConfig(configObject)
                )

                result += Preset(
                    id = id,
                    name = if (name.isEmpty()) {
                        "Preset personalizado"
                    } else {
                        name
                    },
                    category = obj.optString(
                        "category",
                        "Personalizados"
                    ),
                    isSystem = false,
                    config = config
                )
            }

        } catch (_: Exception) {
            /*
             * A corrupted custom-preset store must not prevent
             * the DSP engine from starting.
             */
        }

        return result
    }

    /**
     * Reads all four MDRC bands.
     *
     * Existing valid values are preserved.
     * Missing or invalid bands are filled from the corresponding
     * factory default only.
     *
     * This prevents a partially corrupted JSON object from causing
     * all user MDRC crossover values to be replaced by defaults.
     */
    private fun jsonToMdrcBands(
        obj: JSONObject
    ): List<MdrcBandConfig> {

        val defaults = DspConfig.defaultMdrcBands()

        val array = obj.optJSONArray(
            "mdrcBands"
        )

        if (array == null) {
            return defaults
        }

        val result = mutableListOf<MdrcBandConfig>()

        for (i in 0 until MDRC_BAND_COUNT) {
            val defaultBand =
                defaults.getOrElse(i) {
                    defaults.last()
                }

            val jsonBand =
                array.optJSONObject(i)

            if (jsonBand == null) {
                result += defaultBand
                continue
            }

            val name = jsonBand.optString(
                "name",
                defaultBand.name
            )

            val cutoff = readFiniteFloat(
                jsonBand,
                "cutoffFrequencyHz",
                defaultBand.cutoffFrequencyHz
            ).coerceIn(
                MIN_MDRC_CUTOFF_HZ,
                MAX_MDRC_CUTOFF_HZ
            )

            val threshold = readFiniteFloat(
                jsonBand,
                "thresholdDb",
                defaultBand.thresholdDb
            ).coerceIn(
                -60f,
                0f
            )

            val ratio = readFiniteFloat(
                jsonBand,
                "ratio",
                defaultBand.ratio
            ).coerceIn(
                1f,
                20f
            )

            val attack = readFiniteFloat(
                jsonBand,
                "attackMs",
                defaultBand.attackMs
            ).coerceIn(
                0.1f,
                1000f
            )

            val release = readFiniteFloat(
                jsonBand,
                "releaseMs",
                defaultBand.releaseMs
            ).coerceIn(
                1f,
                2000f
            )

            val makeup = readFiniteFloat(
                jsonBand,
                "makeupGainDb",
                defaultBand.makeupGainDb
            ).coerceIn(
                0f,
                24f
            )

            val knee = readFiniteFloat(
                jsonBand,
                "kneeDb",
                defaultBand.kneeDb
            ).coerceIn(
                0f,
                30f
            )

            result += MdrcBandConfig(
                name = name.ifBlank {
                    defaultBand.name
                },
                cutoffFrequencyHz = cutoff,
                thresholdDb = threshold,
                ratio = ratio,
                attackMs = attack,
                releaseMs = release,
                makeupGainDb = makeup,
                kneeDb = knee
            )
        }

        /*
         * Preserve the stored values but ensure the crossover
         * sequence is valid and increasing.
         */
        return normalizeMdrcCutoffs(result)
    }

    private fun normalizeMdrcCutoffs(
        bands: List<MdrcBandConfig>
    ): List<MdrcBandConfig> {

        if (bands.size != MDRC_BAND_COUNT) {
            return DspConfig.defaultMdrcBands()
        }

        val result = mutableListOf<MdrcBandConfig>()

        var previous = MIN_MDRC_CUTOFF_HZ - 1f

        for (i in 0 until MDRC_BAND_COUNT) {
            val band = bands[i]

            val minimum =
                if (i == 0) {
                    MIN_MDRC_CUTOFF_HZ
                } else {
                    previous + 1f
                }

            val remaining =
                MDRC_BAND_COUNT - i - 1

            val maximum =
                MAX_MDRC_CUTOFF_HZ -
                    remaining

            val cutoff =
                band.cutoffFrequencyHz
                    .coerceIn(
                        minimum,
                        maximum
                    )

            val normalized = band.copy(
                cutoffFrequencyHz = cutoff
            )

            result += normalized
            previous = cutoff
        }

        return result
    }

    fun duplicateCustomPreset(
        id: String,
        newName: String
    ): Preset? {

        val source =
            loadCustomPresets()
                .firstOrNull { it.id == id }
                ?: return null

        return saveCustomPreset(
            newName,
            source.config
        )
    }

    fun renameCustomPreset(
        id: String,
        newName: String
    ): Boolean {

        val cleanName = newName.trim()

        if (cleanName.isEmpty()) {
            return false
        }

        val list =
            loadCustomPresets()
                .toMutableList()

        val index =
            list.indexOfFirst {
                it.id == id
            }

        if (index < 0) {
            return false
        }

        list[index] =
            list[index].copy(
                name = cleanName
            )

        saveCustomPresets(list)

        return true
    }

    private fun saveCustomPresets(
        list: List<Preset>
    ) {
        val array = JSONArray()

        for (preset in list) {
            val obj = JSONObject()

            obj.put(
                "id",
                preset.id
            )

            obj.put(
                "name",
                preset.name
            )

            obj.put(
                "category",
                preset.category
            )

            obj.put(
                "config",
                configToJson(
                    sanitizeConfig(
                        preset.config
                    )
                )
            )

            array.put(obj)
        }

        prefs.edit()
            .putString(
                KEY_CUSTOM_PRESETS,
                array.toString()
            )
            .apply()
    }

    /**
     * Serializes the complete DSP configuration.
     *
     * MDRC cutoffFrequencyHz is explicitly persisted here.
     */
    private fun configToJson(
        c: DspConfig
    ): JSONObject {

        val safe = sanitizeConfig(c)

        return JSONObject().apply {

            put(
                "isEnabled",
                safe.isEnabled
            )

            put(
                "preGainDb",
                safe.preGainDb.toDouble()
            )

            put(
                "bassBoostEnabled",
                safe.bassBoostEnabled
            )

            put(
                "bassBoostStrength",
                safe.bassBoostStrength.toInt()
            )

            put(
                "toneBassDb",
                safe.toneBassDb.toDouble()
            )

            put(
                "toneMidDb",
                safe.toneMidDb.toDouble()
            )

            put(
                "toneTrebleDb",
                safe.toneTrebleDb.toDouble()
            )

            val eqArray = JSONArray()

            safe.eqGains.forEach {
                eqArray.put(
                    it.toDouble()
                )
            }

            put(
                "eqGains",
                eqArray
            )

            put(
                "mdrcEnabled",
                safe.mdrcEnabled
            )

            val mdrcArray = JSONArray()

            safe.mdrcBands.forEach { band ->

                mdrcArray.put(
                    JSONObject().apply {

                        put(
                            "name",
                            band.name
                        )

                        /*
                         * The crossover value is stored explicitly.
                         */
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
                safe.autoGainEnabled
            )

            put(
                "autoGainTargetDb",
                safe.autoGainTargetDb.toDouble()
            )

            put(
                "virtualizerEnabled",
                safe.virtualizerEnabled
            )

            put(
                "virtualizerStrength",
                safe.virtualizerStrength.toInt()
            )

            put(
                "masterGainDb",
                safe.masterGainDb.toDouble()
            )

            put(
                "balance",
                safe.balance.toDouble()
            )

            put(
                "limiterEnabled",
                safe.limiterEnabled
            )

            put(
                "limiterThresholdDb",
                safe.limiterThresholdDb.toDouble()
            )

            put(
                "limiterAttackMs",
                safe.limiterAttackMs.toDouble()
            )

            put(
                "limiterReleaseMs",
                safe.limiterReleaseMs.toDouble()
            )

            put(
                "limiterRatio",
                safe.limiterRatio.toDouble()
            )

            put(
                "limiterPostGainDb",
                safe.limiterPostGainDb.toDouble()
            )
        }
    }

    /**
     * Deserializes the complete DSP configuration.
     */
    private fun jsonToConfig(
        obj: JSONObject
    ): DspConfig {

        val eqList = mutableListOf<Float>()

        val eqArray =
            obj.optJSONArray(
                "eqGains"
            )

        if (eqArray != null) {
            for (i in 0 until eqArray.length()) {
                val value =
                    eqArray.optDouble(
                        i,
                        0.0
                    ).toFloat()

                eqList += value
                    .takeIf { it.isFinite() }
                    ?.coerceIn(-15f, 15f)
                    ?: 0f
            }
        }

        val finalEqList =
            MutableList(32) { 0f }

        for (i in 0 until minOf(
            eqList.size,
            32
        )) {
            finalEqList[i] =
                eqList[i]
        }

        return DspConfig(

            isEnabled = obj.optBoolean(
                "isEnabled",
                true
            ),

            preGainDb =
                readFiniteFloat(
                    obj,
                    "preGainDb",
                    0f
                ).coerceIn(
                    -12f,
                    12f
                ),

            bassBoostEnabled =
                obj.optBoolean(
                    "bassBoostEnabled",
                    false
                ),

            bassBoostStrength =
                obj.optInt(
                    "bassBoostStrength",
                    0
                ).coerceIn(
                    0,
                    1000
                ).toShort(),

            toneBassDb =
                readFiniteFloat(
                    obj,
                    "toneBassDb",
                    0f
                ).coerceIn(
                    -12f,
                    12f
                ),

            toneMidDb =
                readFiniteFloat(
                    obj,
                    "toneMidDb",
                    0f
                ).coerceIn(
                    -12f,
                    12f
                ),

            toneTrebleDb =
                readFiniteFloat(
                    obj,
                    "toneTrebleDb",
                    0f
                ).coerceIn(
                    -12f,
                    12f
                ),

            eqGains = finalEqList,

            mdrcEnabled =
                obj.optBoolean(
                    "mdrcEnabled",
                    true
                ),

            mdrcBands =
                jsonToMdrcBands(obj),

            autoGainEnabled =
                obj.optBoolean(
                    "autoGainEnabled",
                    true
                ),

            autoGainTargetDb =
                readFiniteFloat(
                    obj,
                    "autoGainTargetDb",
                    -14f
                ).coerceIn(
                    -24f,
                    -6f
                ),

            virtualizerEnabled =
                obj.optBoolean(
                    "virtualizerEnabled",
                    false
                ),

            virtualizerStrength =
                obj.optInt(
                    "virtualizerStrength",
                    0
                ).coerceIn(
                    0,
                    1000
                ).toShort(),

            masterGainDb =
                readFiniteFloat(
                    obj,
                    "masterGainDb",
                    0f
                ).coerceIn(
                    -24f,
                    12f
                ),

            balance =
                readFiniteFloat(
                    obj,
                    "balance",
                    0f
                ).coerceIn(
                    -1f,
                    1f
                ),

            limiterEnabled =
                obj.optBoolean(
                    "limiterEnabled",
                    true
                ),

            limiterThresholdDb =
                readFiniteFloat(
                    obj,
                    "limiterThresholdDb",
                    -0.5f
                ).coerceIn(
                    -60f,
                    0f
                ),

            limiterAttackMs =
                readFiniteFloat(
                    obj,
                    "limiterAttackMs",
                    1f
                ).coerceIn(
                    0.1f,
                    1000f
                ),

            limiterReleaseMs =
                readFiniteFloat(
                    obj,
                    "limiterReleaseMs",
                    50f
                ).coerceIn(
                    1f,
                    2000f
                ),

            limiterRatio =
                readFiniteFloat(
                    obj,
                    "limiterRatio",
                    20f
                ).coerceIn(
                    1f,
                    100f
                ),

            limiterPostGainDb =
                readFiniteFloat(
                    obj,
                    "limiterPostGainDb",
                    0f
                ).coerceIn(
                    -24f,
                    12f
                )
        )
    }

    /**
     * Protects configurations before they enter persistence.
     */
    private fun sanitizeConfig(
        config: DspConfig
    ): DspConfig {

        val safeBands =
            normalizeMdrcCutoffs(
                config.mdrcBands
                    .map { band ->
                        band.copy(
                            cutoffFrequencyHz =
                                band.cutoffFrequencyHz
                                    .takeIf {
                                        it.isFinite()
                                    }
                                    ?.coerceIn(
                                        MIN_MDRC_CUTOFF_HZ,
                                        MAX_MDRC_CUTOFF_HZ
                                    )
                                    ?: 160f,

                            thresholdDb =
                                band.thresholdDb
                                    .takeIf {
                                        it.isFinite()
                                    }
                                    ?.coerceIn(
                                        -60f,
                                        0f
                                    )
                                    ?: -18f,

                            ratio =
                                band.ratio
                                    .takeIf {
                                        it.isFinite()
                                    }
                                    ?.coerceIn(
                                        1f,
                                        20f
                                    )
                                    ?: 2f,

                            attackMs =
                                band.attackMs
                                    .takeIf {
                                        it.isFinite()
                                    }
                                    ?.coerceIn(
                                        0.1f,
                                        1000f
                                    )
                                    ?: 10f,

                            releaseMs =
                                band.releaseMs
                                    .takeIf {
                                        it.isFinite()
                                    }
                                    ?.coerceIn(
                                        1f,
                                        2000f
                                    )
                                    ?: 80f,

                            makeupGainDb =
                                band.makeupGainDb
                                    .takeIf {
                                        it.isFinite()
                                    }
                                    ?.coerceIn(
                                        0f,
                                        24f
                                    )
                                    ?: 0f,

                            kneeDb =
                                band.kneeDb
                                    .takeIf {
                                        it.isFinite()
                                    }
                                    ?.coerceIn(
                                        0f,
                                        30f
                                    )
                                    ?: 2f
                        )
                    }
            )

        val safeEq =
            MutableList(32) { index ->
                config.eqGains
                    .getOrElse(index) { 0f }
                    .takeIf {
                        it.isFinite()
                    }
                    ?.coerceIn(
                        -15f,
                        15f
                    )
                    ?: 0f
            }

        return config.copy(
            preGainDb =
                config.preGainDb
                    .takeIf { it.isFinite() }
                    ?.coerceIn(
                        -12f,
                        12f
                    )
                    ?: 0f,

            toneBassDb =
                config.toneBassDb
                    .takeIf { it.isFinite() }
                    ?.coerceIn(
                        -12f,
                        12f
                    )
                    ?: 0f,

            toneMidDb =
                config.toneMidDb
                    .takeIf { it.isFinite() }
                    ?.coerceIn(
                        -12f,
                        12f
                    )
                    ?: 0f,

            toneTrebleDb =
                config.toneTrebleDb
                    .takeIf { it.isFinite() }
                    ?.coerceIn(
                        -12f,
                        12f
                    )
                    ?: 0f,

            eqGains = safeEq,

            mdrcBands = safeBands,

            autoGainTargetDb =
                config.autoGainTargetDb
                    .takeIf { it.isFinite() }
                    ?.coerceIn(
                        -24f,
                        -6f
                    )
                    ?: -14f,

            masterGainDb =
                config.masterGainDb
                    .takeIf { it.isFinite() }
                    ?.coerceIn(
                        -24f,
                        12f
                    )
                    ?: 0f,

            balance =
                config.balance
                    .takeIf { it.isFinite() }
                    ?.coerceIn(
                        -1f,
                        1f
                    )
                    ?: 0f,

            limiterThresholdDb =
                config.limiterThresholdDb
                    .takeIf { it.isFinite() }
                    ?.coerceIn(
                        -60f,
                        0f
                    )
                    ?: -0.5f,

            limiterAttackMs =
                config.limiterAttackMs
                    .takeIf { it.isFinite() }
                    ?.coerceIn(
                        0.1f,
                        1000f
                    )
                    ?: 1f,

            limiterReleaseMs =
                config.limiterReleaseMs
                    .takeIf { it.isFinite() }
                    ?.coerceIn(
                        1f,
                        2000f
                    )
                    ?: 50f,

            limiterRatio =
                config.limiterRatio
                    .takeIf { it.isFinite() }
                    ?.coerceIn(
                        1f,
                        100f
                    )
                    ?: 20f,

            limiterPostGainDb =
                config.limiterPostGainDb
                    .takeIf { it.isFinite() }
                    ?.coerceIn(
                        -24f,
                        12f
                    )
                    ?: 0f
        )
    }

    private fun readFiniteFloat(
        obj: JSONObject,
        key: String,
        default: Float
    ): Float {
        val value =
            obj.optDouble(
                key,
                default.toDouble()
            ).toFloat()

        return if (value.isFinite()) {
            value
        } else {
            default
        }
    }
}
