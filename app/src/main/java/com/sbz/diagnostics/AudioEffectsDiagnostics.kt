package com.sbz.diagnostics

import android.media.audiofx.AudioEffect
import android.media.audiofx.DynamicsProcessing
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

        inspectDynamicsProcessing(report)

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
                report.appendLine("No effect descriptors returned.")
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

    private fun inspectDynamicsProcessing(
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

        var dynamicsProcessing: DynamicsProcessing? = null

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

            if (!found) {
                return
            }

            descriptors
                ?.filter {
                    it.type == dynamicsType
                }
                ?.forEach { descriptor ->
                    report.appendLine(
                        "Descriptor: " +
                            "name=${descriptor.name}, " +
                            "uuid=${descriptor.uuid}, " +
                            "connect=${descriptor.connectMode}"
                    )
                }

            /*
             * Temporary diagnostic configuration.
             *
             * This is NOT connected to the sBz audio chain.
             * It exists only so Android can expose the actual
             * DynamicsProcessing configuration accepted by the
             * device/effect implementation.
             */
            val config =
                DynamicsProcessing.Config.Builder(
                    2,
                    true,
                    5,
                    true,
                    4,
                    true,
                    5,
                    true
                ).build()

            dynamicsProcessing =
                DynamicsProcessing(
                    100,
                    0,
                    config
                )

            val actualConfig =
                dynamicsProcessing.config

            report.appendLine()
            report.appendLine("### DynamicsProcessing CONFIG ###")

            report.appendLine(
                "Channel count: " +
                    actualConfig.channelCount
            )

            report.appendLine(
                "Pre-EQ in use: " +
                    actualConfig.isPreEqInUse
            )

            report.appendLine(
                "Pre-EQ band count: " +
                    actualConfig.preEqBandCount
            )

            report.appendLine(
                "MBC in use: " +
                    actualConfig.isMbcInUse
            )

            report.appendLine(
                "MBC band count: " +
                    actualConfig.mbcBandCount
            )

            report.appendLine(
                "Post-EQ in use: " +
                    actualConfig.isPostEqInUse
            )

            report.appendLine(
                "Post-EQ band count: " +
                    actualConfig.postEqBandCount
            )

            report.appendLine(
                "Limiter in use: " +
                    actualConfig.isLimiterInUse
            )

            /*
             * Inspect every channel.
             */
            for (
                channelIndex in 0 until actualConfig.channelCount
            ) {
                report.appendLine()
                report.appendLine(
                    "### CHANNEL $channelIndex ###"
                )

                try {
                    val channel =
                        actualConfig.getChannelByChannelIndex(
                            channelIndex
                        )

                    inspectPreEq(
                        report,
                        channel
                    )

                    inspectMbc(
                        report,
                        channel
                    )

                    inspectPostEq(
                        report,
                        channel
                    )

                    inspectLimiter(
                        report,
                        channel
                    )
                } catch (e: Exception) {
                    report.appendLine(
                        "Channel ERROR: " +
                            "${e.javaClass.simpleName}: " +
                            "${e.message}"
                    )
                }
            }
        } catch (e: Exception) {
            report.appendLine(
                "DynamicsProcessing ERROR: " +
                    "${e.javaClass.simpleName}: " +
                    "${e.message}"
            )
        } finally {
            try {
                dynamicsProcessing?.release()
            } catch (_: Exception) {
                // Ignore release errors.
            }
        }
    }

    private fun inspectPreEq(
        report: StringBuilder,
        channel: DynamicsProcessing.Channel
    ) {
        report.appendLine()
        report.appendLine("--- PRE-EQ ---")

        try {
            val eq = channel.preEq

            report.appendLine(
                "Bands: ${eq.bandCount}"
            )

            for (bandIndex in 0 until eq.bandCount) {
                try {
                    val band =
                        eq.getBand(bandIndex)

                    report.appendLine(
                        "Band $bandIndex: " +
                            "enabled=${band.isEnabled}, " +
                            "cutoff=${band.cutoffFrequency} Hz, " +
                            "gain=${band.gain} dB"
                    )
                } catch (e: Exception) {
                    report.appendLine(
                        "Band $bandIndex ERROR: " +
                            "${e.javaClass.simpleName}: " +
                            "${e.message}"
                    )
                }
            }
        } catch (e: Exception) {
            report.appendLine(
                "PRE-EQ ERROR: " +
                    "${e.javaClass.simpleName}: " +
                    "${e.message}"
            )
        }
    }

    private fun inspectMbc(
        report: StringBuilder,
        channel: DynamicsProcessing.Channel
    ) {
        report.appendLine()
        report.appendLine("--- MBC ---")

        try {
            val mbc = channel.mbc

            report.appendLine(
                "Bands: ${mbc.bandCount}"
            )

            for (bandIndex in 0 until mbc.bandCount) {
                try {
                    val band =
                        mbc.getBand(bandIndex)

                    report.appendLine(
                        "Band $bandIndex: " +
                            "enabled=${band.isEnabled}, " +
                            "cutoff=${band.cutoffFrequency} Hz, " +
                            "attack=${band.attackTime} ms, " +
                            "release=${band.releaseTime} ms, " +
                            "ratio=${band.ratio}, " +
                            "threshold=${band.threshold} dB, " +
                            "knee=${band.kneeWidth} dB, " +
                            "noiseGate=${band.noiseGateThreshold} dB, " +
                            "expanderRatio=${band.expanderRatio}, " +
                            "preGain=${band.preGain} dB, " +
                            "postGain=${band.postGain} dB"
                    )
                } catch (e: Exception) {
                    report.appendLine(
                        "Band $bandIndex ERROR: " +
                            "${e.javaClass.simpleName}: " +
                            "${e.message}"
                    )
                }
            }
        } catch (e: Exception) {
            report.appendLine(
                "MBC ERROR: " +
                    "${e.javaClass.simpleName}: " +
                    "${e.message}"
            )
        }
    }

    private fun inspectPostEq(
        report: StringBuilder,
        channel: DynamicsProcessing.Channel
    ) {
        report.appendLine()
        report.appendLine("--- POST-EQ ---")

        try {
            val eq = channel.postEq

            report.appendLine(
                "Bands: ${eq.bandCount}"
            )

            for (bandIndex in 0 until eq.bandCount) {
                try {
                    val band =
                        eq.getBand(bandIndex)

                    report.appendLine(
                        "Band $bandIndex: " +
                            "enabled=${band.isEnabled}, " +
                            "cutoff=${band.cutoffFrequency} Hz, " +
                            "gain=${band.gain} dB"
                    )
                } catch (e: Exception) {
                    report.appendLine(
                        "Band $bandIndex ERROR: " +
                            "${e.javaClass.simpleName}: " +
                            "${e.message}"
                    )
                }
            }
        } catch (e: Exception) {
            report.appendLine(
                "POST-EQ ERROR: " +
                    "${e.javaClass.simpleName}: " +
                    "${e.message}"
            )
        }
    }

    private fun inspectLimiter(
        report: StringBuilder,
        channel: DynamicsProcessing.Channel
    ) {
        report.appendLine()
        report.appendLine("--- LIMITER ---")

        try {
            val limiter = channel.limiter

            report.appendLine(
                "enabled=${limiter.isEnabled}, " +
                    "attack=${limiter.attackTime} ms, " +
                    "release=${limiter.releaseTime} ms, " +
                    "ratio=${limiter.ratio}, " +
                    "threshold=${limiter.threshold} dB, " +
                    "postGain=${limiter.postGain} dB"
            )
        } catch (e: Exception) {
            report.appendLine(
                "LIMITER ERROR: " +
                    "${e.javaClass.simpleName}: " +
                    "${e.message}"
            )
        }
    }
}
