package com.sbz.diagnostics

import android.media.audiofx.AudioEffect
import android.media.audiofx.Equalizer
import android.os.Build

/**
 * Diagnostic utility for inspecting audio effects exposed by the device.
 *
 * This class does not modify the sBz DSP chain.
 * It only queries Android audio-effect capabilities.
 */
object AudioEffectsDiagnostics {

    fun run(): String {
        val report = StringBuilder()

        report.appendLine("========== sBz AUDIO DIAGNOSTICS ==========")
        report.appendLine("Android API: ${Build.VERSION.SDK_INT}")
        report.appendLine("Android: ${Build.VERSION.RELEASE}")
        report.appendLine("Manufacturer: ${Build.MANUFACTURER}")
        report.appendLine("Model: ${Build.MODEL}")
        report.appendLine()

        inspectAvailableEffects(report)

        report.appendLine()

        inspectEqualizer(report)

        report.appendLine()

        inspectDynamicsProcessingAvailability(report)

        report.appendLine()
        report.appendLine("========== END DIAGNOSTICS ==========")

        return report.toString()
    }

    private fun inspectAvailableEffects(
        report: StringBuilder
    ) {
        report.appendLine("---- Available Audio Effects ----")

        try {
            val descriptors = AudioEffect.queryEffects()

            if (descriptors == null || descriptors.isEmpty()) {
                report.appendLine(
                    "No effect descriptors returned."
                )
                return
            }

            report.appendLine(
                "Total descriptors: ${descriptors.size}"
            )

            descriptors.forEachIndexed { index, descriptor ->
                report.appendLine(
                    "$index: " +
                        "name=${descriptor.name}, " +
                        "type=${descriptor.type}, " +
                        "uuid=${descriptor.uuid}, " +
                        "connect=${descriptor.connectMode}"
                )
            }
        } catch (e: Exception) {
            report.appendLine(
                "ERROR querying effects: " +
                    "${e.javaClass.simpleName}: ${e.message}"
            )
        }
    }

    private fun inspectEqualizer(
        report: StringBuilder
    ) {
        report.appendLine("---- Equalizer ----")

        var equalizer: Equalizer? = null

        try {
            equalizer = Equalizer(
                0,
                0
            )

            val bands = equalizer.numberOfBands.toInt()
            val levelRange = equalizer.bandLevelRange

            report.appendLine("Available: true")
            report.appendLine(
                "Number of bands: $bands"
            )

            report.appendLine(
                "Level range: " +
                    "${levelRange[0]} .. ${levelRange[1]} mB"
            )

            for (band in 0 until bands) {
                try {
                    val shortBand = band.toShort()

                    val frequencyRange =
                        equalizer.getBandFreqRange(shortBand)

                    val centerFrequency =
                        equalizer.getCenterFreq(shortBand)

                    report.appendLine(
                        "Band $band: " +
                            "${frequencyRange[0]}-" +
                            "${frequencyRange[1]} mHz, " +
                            "center=${centerFrequency} mHz"
                    )
                } catch (e: Exception) {
                    report.appendLine(
                        "Band $band: ERROR " +
                            "${e.javaClass.simpleName}: " +
                            "${e.message}"
                    )
                }
            }
        } catch (e: Exception) {
            report.appendLine("Available: false")
            report.appendLine(
                "ERROR: ${e.javaClass.simpleName}: ${e.message}"
            )
        } finally {
            try {
                equalizer?.release()
            } catch (_: Exception) {
                // Ignore release errors.
            }
        }
    }

    private fun inspectDynamicsProcessingAvailability(
        report: StringBuilder
    ) {
        report.appendLine("---- DynamicsProcessing ----")

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            report.appendLine("Available: false")
            report.appendLine(
                "Requires Android 7.0 / API 24+."
            )
            return
        }

        try {
            val descriptors = AudioEffect.queryEffects()

            val dynamicsType =
                AudioEffect.EFFECT_TYPE_DYNAMICS_PROCESSING

            val found = descriptors?.any {
                it.type == dynamicsType
            } == true

            report.appendLine(
                "Descriptor available: $found"
            )

            if (found) {
                descriptors
                    ?.filter {
                        it.type == dynamicsType
                    }
                    ?.forEach { descriptor ->
                        report.appendLine(
                            "DynamicsProcessing: " +
                                "name=${descriptor.name}, " +
                                "uuid=${descriptor.uuid}, " +
                                "connect=${descriptor.connectMode}"
                        )
                    }
            }
        } catch (e: Exception) {
            report.appendLine(
                "ERROR: ${e.javaClass.simpleName}: " +
                    "${e.message}"
            )
        }
    }
}
