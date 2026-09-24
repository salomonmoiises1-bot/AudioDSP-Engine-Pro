package com.sbz.dsp.model

import java.io.Serializable
import java.util.UUID

/**
 * Representation of a saved DSP Preset.
 */
data class Preset(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val isSystem: Boolean = false,
    val config: DspConfig
) : Serializable {
    companion object {
        fun createDefaultPresets(): List<Preset> = listOf(
            Preset(
                id = "system_flat",
                name = "Studio Reference Flat",
                isSystem = true,
                config = DspConfig()
            ),
            Preset(
                id = "system_bass_focus",
                name = "Deep Bass Impact",
                isSystem = true,
                config = DspConfig(
                    bassBoostEnabled = true,
                    bassBoostStrength = 450,
                    toneBassDb = 3.5f,
                    toneTrebleDb = 1.0f,
                    eqGains = listOf(
                        6.0f, 5.5f, 5.0f, 4.5f, 4.0f, 3.5f, 2.5f, 1.5f, 0.5f, 0.0f,
                        0.0f, -0.5f, -0.5f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.5f, 0.5f,
                        1.0f, 1.0f, 1.5f, 2.0f, 2.0f, 2.0f, 1.5f, 1.0f, 0.5f, 0.0f, 0.0f, 0.0f
                    )
                )
            ),
            Preset(
                id = "system_vocal_clarity",
                name = "Vocal Clarity & Presence",
                isSystem = true,
                config = DspConfig(
                    toneBassDb = -1.5f,
                    toneMidDb = 2.5f,
                    toneTrebleDb = 2.0f,
                    eqGains = listOf(
                        -3.0f, -3.0f, -2.5f, -2.0f, -1.5f, -1.0f, -0.5f, 0.0f, 0.5f, 1.0f,
                        1.5f, 2.0f, 2.5f, 3.0f, 3.5f, 3.0f, 2.5f, 2.0f, 1.5f, 1.0f,
                        1.5f, 2.0f, 2.5f, 2.0f, 1.5f, 1.0f, 0.5f, 0.0f, -0.5f, -1.0f, -1.5f, -2.0f
                    )
                )
            ),
            Preset(
                id = "system_club_edm",
                name = "Club & Electronic EDM",
                isSystem = true,
                config = DspConfig(
                    bassBoostEnabled = true,
                    bassBoostStrength = 350,
                    virtualizerEnabled = true,
                    virtualizerStrength = 400,
                    toneBassDb = 4.0f,
                    toneTrebleDb = 3.0f,
                    eqGains = listOf(
                        5.0f, 5.5f, 6.0f, 5.0f, 4.0f, 2.5f, 1.0f, 0.0f, -1.0f, -1.5f,
                        -1.0f, -0.5f, 0.0f, 0.5f, 1.0f, 1.0f, 0.5f, 0.5f, 1.0f, 1.5f,
                        2.5f, 3.0f, 4.0f, 4.5f, 5.0f, 4.5f, 4.0f, 3.5f, 3.0f, 2.0f, 1.0f, 0.5f
                    )
                )
            ),
            Preset(
                id = "system_rock_metal",
                name = "Dynamic Rock & Metal",
                isSystem = true,
                config = DspConfig(
                    toneBassDb = 2.5f,
                    toneMidDb = -1.0f,
                    toneTrebleDb = 3.0f,
                    eqGains = listOf(
                        4.0f, 3.5f, 3.0f, 2.5f, 1.5f, 0.5f, 0.0f, -0.5f, -1.0f, -1.5f,
                        -2.0f, -1.5f, -1.0f, 0.0f, 0.5f, 1.0f, 1.5f, 2.0f, 2.5f, 3.0f,
                        3.5f, 4.0f, 4.0f, 4.5f, 4.0f, 3.5f, 3.0f, 2.5f, 2.0f, 1.0f, 0.5f, 0.0f
                    )
                )
            ),
            Preset(
                id = "system_mastering",
                name = "Audiophile Mastering",
                isSystem = true,
                config = DspConfig(
                    limiterEnabled = true,
                    limiterThresholdDb = -0.3f,
                    limiterAttackMs = 0.5f,
                    limiterReleaseMs = 40.0f,
                    mdrcEnabled = true,
                    autoGainEnabled = true,
                    eqGains = listOf(
                        1.0f, 1.0f, 0.8f, 0.5f, 0.3f, 0.0f, 0.0f, 0.0f, -0.3f, -0.5f,
                        -0.5f, -0.3f, 0.0f, 0.2f, 0.4f, 0.5f, 0.4f, 0.2f, 0.0f, 0.2f,
                        0.5f, 0.8f, 1.0f, 1.2f, 1.4f, 1.5f, 1.2f, 1.0f, 0.8f, 0.5f, 0.2f, 0.0f
                    )
                )
            )
        )
    }
}
