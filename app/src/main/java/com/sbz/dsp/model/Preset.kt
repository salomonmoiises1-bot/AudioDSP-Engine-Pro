package com.sbz.dsp.model

import java.io.Serializable
import java.util.UUID

/** Complete saved DSP configuration. */
data class Preset(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val category: String = "Personalizados",
    val isSystem: Boolean = false,
    val config: DspConfig
) : Serializable {
    companion object {
        private fun curve(vararg anchors: Pair<Int, Float>): List<Float> {
            val out = MutableList(32) { 0f }
            if (anchors.isEmpty()) return out
            for (i in 0 until 32) {
                val left = anchors.lastOrNull { it.first <= i }
                val right = anchors.firstOrNull { it.first >= i }
                out[i] = when {
                    left == null -> right!!.second
                    right == null -> left.second
                    left.first == right.first -> left.second
                    else -> {
                        val t = (i - left.first).toFloat() / (right.first - left.first).toFloat()
                        left.second + (right.second - left.second) * t
                    }
                }
            }
            return out.map { (kotlin.math.round(it * 2f) / 2f).coerceIn(-15f, 15f) }
        }

        private fun bands(vararg values: Float): List<Float> {
            require(values.size == 32) { "Un preset de EQ debe tener exactamente 32 bandas" }
            return values.map { (kotlin.math.round(it * 2f) / 2f).coerceIn(-15f, 15f) }
        }

        private fun preset(id: String, name: String, category: String, config: DspConfig): Preset {
            val effectiveConfig = if (config.mdrcBands == DspConfig.defaultMdrcBands()) {
                config.copy(mdrcBands = config.mdrcProfileForPresetForFactory(id))
            } else {
                config
            }
            return Preset(
                id = id,
                name = name,
                category = category,
                isSystem = true,
                config = effectiveConfig
            )
        }

        fun createDefaultPresets(): List<Preset> = listOf(
            preset("system_flat", "Plano de referencia de estudio", "Escucha", DspConfig()),
            preset("speaker_aiwa_aws544_mod45", "Aiwa AWS544BT modificado • 45 Hz", "Bocinas personalizadas", DspConfig(preGainDb=-3f, limiterThresholdDb=-1f, eqGains=bands(1f,1.5f,2.5f,3.5f,4f,3.5f,3f,2f,1f,0.5f,0f,-1f,-1.5f,-1f,-0.5f,0f,0f,0f,0.5f,0.5f,1f,1f,1.5f,2f,2f,2f,2.5f,2f,1.5f,1f,0.5f,0f))),
            preset("speaker_boombox_style", "Boombox • graves con pegada", "Bocinas personalizadas", DspConfig(preGainDb=-3f, limiterThresholdDb=-1f, eqGains=bands(0.5f,1.5f,2.5f,3.5f,4f,4f,3f,2f,1f,0f,-1f,-1.5f,-1f,-0.5f,0f,0f,0f,0f,0.5f,1f,1.5f,2f,2.5f,3f,3f,3f,3.5f,3f,2.5f,2f,1.5f,1f))),
            preset("speaker_harman_go_style", "Harman Go • cálido y definido", "Bocinas personalizadas", DspConfig(preGainDb=-2f, limiterThresholdDb=-1f, eqGains=bands(1f,1.5f,2f,2.5f,3f,2.5f,2f,1.5f,1f,0.5f,0f,-0.5f,-0.5f,0f,0f,0f,0f,0.5f,0.5f,1f,1f,1.5f,2f,2.5f,2.5f,2.5f,2f,2f,1.5f,1f,0.5f,0f))),
            preset("speaker_sonos_style", "Sonos • equilibrado", "Bocinas personalizadas", DspConfig(preGainDb=-2f, limiterThresholdDb=-1f, eqGains=bands(0.5f,1f,1.5f,2f,2.5f,2f,1.5f,1f,0.5f,0f,-0.5f,-0.5f,0f,0f,0f,0f,0f,0f,0.5f,0.5f,1f,1f,1.5f,2f,2f,2f,1.5f,1.5f,1f,0.5f,0f,0f))),
            preset("speaker_bose_style", "Bose • cálido y amplio", "Bocinas personalizadas", DspConfig(preGainDb=-2.5f, limiterThresholdDb=-1f, eqGains=bands(1.5f,2f,2.5f,3f,3f,2.5f,2f,1.5f,1f,0.5f,0f,-0.5f,-0.5f,0f,0f,0f,0f,0f,0.5f,0.5f,1f,1.5f,2f,2.5f,2.5f,2f,2f,1.5f,1f,0.5f,0f,-0.5f))),
            preset("speaker_devialet_style", "Devialet • graves profundos y aire", "Bocinas personalizadas", DspConfig(preGainDb=-4f, limiterThresholdDb=-1f, eqGains=bands(2f,2.5f,3f,4f,4.5f,4f,3f,2f,1f,0f,-1f,-1.5f,-1f,-0.5f,0f,0f,0f,0f,0.5f,1f,1.5f,2f,2.5f,3f,3.5f,3.5f,3f,2.5f,2f,1.5f,1f,0.5f))),
            preset("speaker_aiwa_reference_45", "Aiwa modificado • referencia 45 Hz", "Bocinas personalizadas", DspConfig(preGainDb=-2f, limiterThresholdDb=-1f, eqGains=bands(0f,0.5f,1f,1.5f,2f,1.5f,1f,0.5f,0f,0f,0f,-0.5f,-0.5f,0f,0f,0f,0f,0f,0f,0.5f,0.5f,1f,1f,1.5f,1.5f,1.5f,1f,1f,0.5f,0.5f,0f,0f))),
            preset("portable_harman", "Portátil equilibrado", "Carácter portátil", DspConfig(eqGains = curve(0 to 2f, 4 to 1f, 10 to 0f, 20 to 1f, 27 to 2f, 31 to 1f))),
            preset("portable_jbl", "Portátil con graves", "Carácter portátil", DspConfig(bassBoostEnabled = true, bassBoostStrength = 300, eqGains = curve(0 to 4f, 4 to 3f, 9 to 0f, 20 to 1f, 27 to 3f, 31 to 2f))),
            preset("portable_sony", "Portátil brillante", "Carácter portátil", DspConfig(bassBoostEnabled = true, bassBoostStrength = 250, toneBassDb = 2f, toneTrebleDb = 2f, eqGains = curve(0 to 3f, 5 to 2f, 11 to -1f, 20 to 1f, 31 to 3f))),
            preset("portable_bose", "Portátil cálido", "Carácter portátil", DspConfig(toneBassDb = 2f, toneMidDb = 0.5f, toneTrebleDb = 1f, eqGains = curve(0 to 2.5f, 6 to 1.5f, 14 to 0f, 24 to 1.5f, 31 to 2f))),
            preset("portable_sonos", "Portátil definido", "Carácter portátil", DspConfig(toneBassDb = 1.5f, toneTrebleDb = 1.5f, eqGains = curve(0 to 2f, 5 to 1f, 14 to 0f, 23 to 1f, 31 to 1.5f))),
            preset("genre_rock", "Rock", "Géneros", DspConfig(toneBassDb = 2f, toneTrebleDb = 2f, eqGains = curve(0 to 3f, 6 to 1f, 11 to -1.5f, 18 to 1f, 24 to 3f, 31 to 2f))),
            preset("genre_pop", "Pop", "Géneros", DspConfig(toneBassDb = 1.5f, toneTrebleDb = 2f, eqGains = curve(0 to 2f, 7 to 0.5f, 13 to 0f, 20 to 2f, 27 to 2.5f, 31 to 2f))),
            preset("genre_electronic", "Electrónica", "Géneros", DspConfig(bassBoostEnabled = true, bassBoostStrength = 250, eqGains = curve(0 to 4f, 5 to 3f, 12 to -1f, 21 to 2f, 28 to 4f, 31 to 3f))),
            preset("genre_edm", "EDM", "Géneros", DspConfig(bassBoostEnabled = true, bassBoostStrength = 350, virtualizerEnabled = true, virtualizerStrength = 300, eqGains = curve(0 to 5f, 5 to 4f, 12 to -1f, 21 to 2f, 28 to 5f, 31 to 3f))),
            preset("genre_hiphop", "Hip-Hop", "Géneros", DspConfig(bassBoostEnabled = true, bassBoostStrength = 400, toneBassDb = 3f, eqGains = curve(0 to 5f, 6 to 3f, 13 to -1f, 21 to 1f, 27 to 2f, 31 to 1f))),
            preset("genre_rnb", "R&B", "Géneros", DspConfig(toneBassDb = 2f, toneMidDb = 1f, toneTrebleDb = 1.5f, eqGains = curve(0 to 3f, 7 to 1f, 14 to -1f, 20 to 1.5f, 27 to 2f, 31 to 1f))),
            preset("genre_metal", "Metal", "Géneros", DspConfig(toneBassDb = 2f, toneMidDb = -1f, toneTrebleDb = 2.5f, eqGains = curve(0 to 3f, 6 to 1f, 12 to -2f, 18 to 1f, 24 to 3f, 31 to 2f))),
            preset("genre_latin", "Latino", "Géneros", DspConfig(toneBassDb = 2.5f, toneTrebleDb = 1.5f, eqGains = curve(0 to 3f, 5 to 2f, 12 to 0f, 19 to 1f, 26 to 2f, 31 to 1.5f))),
            preset("genre_reggae", "Reggae", "Géneros", DspConfig(bassBoostEnabled = true, bassBoostStrength = 300, toneBassDb = 2.5f, eqGains = curve(0 to 4f, 7 to 2f, 15 to 0f, 23 to 1f, 31 to 1.5f))),
            preset("genre_jazz", "Jazz", "Géneros", DspConfig(toneMidDb = 1f, toneTrebleDb = 1f, eqGains = curve(0 to 1f, 7 to 0f, 13 to 1f, 20 to 1.5f, 27 to 1f, 31 to 0.5f))),
            preset("genre_blues", "Blues", "Géneros", DspConfig(toneBassDb = 1.5f, toneMidDb = 1f, eqGains = curve(0 to 2f, 7 to 0.5f, 13 to 1.5f, 21 to 1f, 31 to 1f))),
            preset("genre_classical", "Clásico", "Géneros", DspConfig(toneTrebleDb = 1f, eqGains = curve(0 to 1f, 8 to 0f, 16 to 1f, 23 to 1.5f, 31 to 1f))),
            preset("genre_acoustic", "Acústico", "Géneros", DspConfig(toneMidDb = 1.5f, toneTrebleDb = 1f, eqGains = curve(0 to 1.5f, 8 to 0f, 15 to 1.5f, 24 to 2f, 31 to 1f))),
            preset("genre_country", "Country", "Géneros", DspConfig(toneBassDb = 1.5f, toneTrebleDb = 2f, eqGains = curve(0 to 2f, 7 to 0.5f, 14 to 0f, 21 to 1.5f, 31 to 2f))),
            preset("genre_vocal", "Vocal", "Géneros", DspConfig(toneBassDb = -1.5f, toneMidDb = 2.5f, toneTrebleDb = 2f, eqGains = curve(0 to -3f, 8 to -0.5f, 14 to 2.5f, 19 to 3f, 26 to 1f, 31 to -1f))),
            preset("goal_deep_bass", "Graves profundos", "Objetivos", DspConfig(bassBoostEnabled = true, bassBoostStrength = 500, toneBassDb = 4f, eqGains = curve(0 to 6f, 5 to 5f, 10 to 2f, 17 to 0f, 27 to 1f, 31 to 1f))),
            preset("goal_bass_punch", "Golpe de graves", "Objetivos", DspConfig(bassBoostEnabled = true, bassBoostStrength = 350, toneBassDb = 3f, eqGains = curve(0 to 4f, 6 to 4f, 12 to 0f, 20 to 1f, 31 to 1f))),
            preset("goal_natural", "Natural", "Objetivos", DspConfig(eqGains = curve(0 to 0.5f, 8 to 0f, 16 to 0f, 24 to 0.5f, 31 to 0f))),
            preset("goal_reference", "Referencia", "Objetivos", DspConfig()),
            preset("goal_warm", "Cálido", "Objetivos", DspConfig(toneBassDb = 2f, toneTrebleDb = -1f, eqGains = curve(0 to 2f, 8 to 1f, 16 to 0f, 24 to -1f, 31 to -1f))),
            preset("goal_bright", "Brillante", "Objetivos", DspConfig(toneTrebleDb = 3f, eqGains = curve(0 to -1f, 8 to 0f, 16 to 1f, 24 to 3f, 31 to 3f))),
            preset("goal_smooth", "Suave", "Objetivos", DspConfig(toneTrebleDb = -1f, eqGains = curve(0 to 1f, 8 to 0.5f, 16 to 0f, 24 to -1f, 31 to -1f))),
            preset("goal_punchy", "Dinámico", "Objetivos", DspConfig(bassBoostEnabled = true, bassBoostStrength = 250, toneBassDb = 2f, toneTrebleDb = 2f, eqGains = curve(0 to 3f, 6 to 3f, 13 to -1f, 20 to 2f, 28 to 2f, 31 to 1f))),
            preset("goal_wide", "Amplio", "Objetivos", DspConfig(virtualizerEnabled = true, virtualizerStrength = 450, eqGains = curve(0 to 1f, 8 to 0f, 16 to 0f, 24 to 1f, 31 to 1f))),
            preset("scenario_small_speaker", "Altavoz pequeño", "Escenarios", DspConfig(bassBoostEnabled = true, bassBoostStrength = 200, toneMidDb = 1.5f, toneTrebleDb = 1.5f, eqGains = curve(0 to -4f, 5 to 0f, 13 to 2f, 24 to 2f, 31 to 1f))),
            preset("scenario_headphones", "Auriculares", "Escenarios", DspConfig(eqGains = curve(0 to 1f, 6 to 0f, 14 to 0.5f, 21 to 1f, 28 to 1.5f, 31 to 1f))),
            preset("scenario_earbuds", "Intraauriculares", "Escenarios", DspConfig(bassBoostEnabled = true, bassBoostStrength = 150, toneBassDb = 1.5f, toneTrebleDb = 1.5f, eqGains = curve(0 to 2f, 6 to 1f, 14 to 0f, 24 to 1.5f, 31 to 1f))),
            preset("scenario_party", "Fiesta", "Escenarios", DspConfig(bassBoostEnabled = true, bassBoostStrength = 400, virtualizerEnabled = true, virtualizerStrength = 350, toneBassDb = 3f, toneTrebleDb = 2.5f, masterGainDb = -2f, eqGains = curve(0 to 5f, 6 to 3f, 14 to -1f, 21 to 2f, 28 to 4f, 31 to 3f))),
            preset("scenario_live", "Directo", "Escenarios", DspConfig(hallEnabled = true, hallMixPercent = 18f, hallDecayTimeMs = 1400f, hallDensityPercent = 72f, hallDiffusionPercent = 82f, hallReflectionsDelayMs = 18f, virtualizerEnabled = true, virtualizerStrength = 250, toneMidDb = 1f, eqGains = curve(0 to 2f, 8 to 0f, 15 to 1f, 24 to 2f, 31 to 1f))),
            preset("scenario_concert", "Concierto", "Escenarios", DspConfig(hallEnabled = true, hallMixPercent = 28f, hallDecayTimeMs = 2200f, hallDensityPercent = 80f, hallDiffusionPercent = 90f, hallReflectionsDelayMs = 28f, hallReverbDelayMs = 24f, virtualizerEnabled = true, virtualizerStrength = 400, bassBoostEnabled = true, bassBoostStrength = 200, toneTrebleDb = 1.5f, eqGains = curve(0 to 3f, 7 to 1f, 14 to 0f, 22 to 2f, 30 to 3f))),
            preset("scenario_gaming", "Juegos", "Escenarios", DspConfig(hallEnabled = true, hallMixPercent = 14f, hallDecayTimeMs = 1000f, hallDensityPercent = 65f, hallDiffusionPercent = 78f, hallReflectionsDelayMs = 12f, virtualizerEnabled = true, virtualizerStrength = 300, toneMidDb = 2f, toneTrebleDb = 2f, eqGains = curve(0 to 1f, 8 to 0f, 14 to 2f, 22 to 2.5f, 31 to 2f))),
            preset("scenario_speech", "Voz", "Escenarios", DspConfig(toneBassDb = -3f, toneMidDb = 3f, toneTrebleDb = 1.5f, eqGains = curve(0 to -4f, 8 to -1f, 13 to 2.5f, 18 to 3f, 31 to 1f))),
            preset("scenario_podcast", "Podcast", "Escenarios", DspConfig(toneBassDb = -2f, toneMidDb = 2.5f, toneTrebleDb = 1f, eqGains = curve(0 to -3f, 8 to -1f, 13 to 2f, 19 to 2.5f, 31 to 0.5f))),
            preset("scenario_radio", "Radio", "Escenarios", DspConfig(toneBassDb = -2f, toneMidDb = 2f, toneTrebleDb = 2f, eqGains = curve(0 to -3f, 8 to 0f, 13 to 2f, 22 to 2f, 31 to 1f))),
            preset("scenario_movie", "Cine", "Escenarios", DspConfig(hallEnabled = true, hallMixPercent = 22f, hallDecayTimeMs = 1800f, hallDensityPercent = 75f, hallDiffusionPercent = 86f, hallReflectionsDelayMs = 22f, hallReverbDelayMs = 20f, bassBoostEnabled = true, bassBoostStrength = 250, toneBassDb = 2f, toneMidDb = 0.5f, toneTrebleDb = 1.5f, virtualizerEnabled = true, virtualizerStrength = 250, eqGains = curve(0 to 3f, 6 to 1f, 14 to 0f, 23 to 2f, 31 to 2f))),
            preset("scenario_night", "Noche", "Escenarios", DspConfig(toneBassDb = -1f, toneMidDb = 1f, toneTrebleDb = -1f, masterGainDb = -3f, eqGains = curve(0 to -1f, 8 to 0f, 16 to 1f, 24 to -1f, 31 to -2f))),
            preset("scenario_loudness", "Sonoridad", "Escenarios", DspConfig(bassBoostEnabled = true, bassBoostStrength = 300, toneBassDb = 3f, toneTrebleDb = 3f, masterGainDb = -3f, eqGains = curve(0 to 4f, 6 to 2f, 14 to -1f, 23 to 2f, 31 to 4f)))
        )
    }
}
