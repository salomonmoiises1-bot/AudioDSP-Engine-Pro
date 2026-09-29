package com.sbz.diagnostics

import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.os.Build

/**
 * Diagnostic utility for inspecting the audio effects exposed by the
 * current Android device.
 *
 * This class DOES NOT modify the audio chain.
 * It only queries the platform and returns a textual report.
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

    private fun inspectAvailableEffects(report: StringBuilder) {
        report.appendLine("---- Available Audio Effects ----")

        try {
            val descriptors = AudioEffect.queryEffects()

            if (descriptors == null || descriptors.isEmpty()) {
                report.appendLine("No effect descriptors returned.")
                return
            }

            report.appendLine("Total descriptors: ${descriptors.size}")

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

    private fun inspectEqualizer(report: StringBuilder) {
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
            report.appendLine("Number of bands: $bands")
            report.appendLine(
                "Level range: ${levelRange[0]} .. ${levelRange[1]} mB"
            )

            for (band in 0 until bands) {
                try {
                    val range = equalizer.getBandFreqRange(band.toShort())

                    val center = try {
                        equalizer.getCenterFreq(band.toShort())
                    } catch (_: Exception) {
                        0
                    }

                    report.appendLine(
                        "Band $band: " +
                            "${range[0]}-${range[1]} mHz, " +
                            "center=${center} mHz"
                    )
                } catch (e: Exception) {
                    report.appendLine(
                        "Band $band: ERROR ${e.message}"
                    )
                }
            }
        } catch (e: Exception) {
            report.appendLine(
                "Available: false"
            )
            report.appendLine(
                "ERROR: ${e.javaClass.simpleName}: ${e.message}"
            )
        } finally {
            try {
                equalizer?.release()
            } catch (_: Exception) {
                // Ignore release errors during diagnostics.
            }
        }
    }

    private fun inspectDynamicsProcessing(report: StringBuilder) {
        report.appendLine("---- DynamicsProcessing ----")

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            report.appendLine("Available: false")
            report.appendLine("Requires Android 7.0 / API 24+.")
            return
        }

        var dynamics: DynamicsProcessing? = null

        try {
            /*
             * Create a diagnostic instance using the global audio session.
             *
             * We are only reading its configuration here.
             * No user DSP settings are changed by this diagnostic.
             */
            dynamics = DynamicsProcessing(
                0,
                0,
                DynamicsProcessing.Config.Builder(
                    0,
                    2,
                    true,
                    8,
                    false,
                    4,
                    false,
                    8,
                    false
                ).build()
            )

            val config = dynamics.config

            report.appendLine("Available: true")
            report.appendLine(
                "Input channels: ${config.inputChannelCount}"
            )

            report.appendLine(
                "Pre-EQ enabled: ${config.isPreEqInUse}"
            )

            if (config.isPreEqInUse) {
                val preEq = config.preEq

                report.appendLine(
                    "Pre-EQ bands: ${preEq.bandCount}"
                )

                for (i in 0 until preEq.bandCount) {
                    try {
                        val band = preEq.getBand(i)

                        report.appendLine(
                            "Pre-EQ band $i: " +
                                "frequency=${band.frequency} Hz, " +
                                "gain=${band.gain} dB"
                        )
                    } catch (e: Exception) {
                        report.appendLine(
                            "Pre-EQ band $i: ERROR ${e.message}"
                        )
                    }
                }
            }

            report.appendLine(
                "MBC enabled: ${config.isMbcInUse}"
            )

            if (config.isMbcInUse) {
                val mbc = config.mbc

                report.appendLine(
                    "MBC bands: ${mbc.bandCount}"
                )

                for (i in 0 until mbc.bandCount) {
                    try {
                        val band = mbc.getBand(i)

                        report.appendLine(
                            "MBC band $i: " +
                                "cutoff=${band.cutoffFrequency} Hz, " +
                                "threshold=${band.threshold} dB, " +
                                "ratio=${band.ratio}"
                        )
                    } catch (e: Exception) {
                        report.appendLine(
                            "MBC band $i: ERROR ${e.message}"
                        )
                    }
                }
            }

            report.appendLine(
                "Post-EQ enabled: ${config.isPostEqInUse}"
            )

            if (config.isPostEqInUse) {
                val postEq = config.postEq

                report.appendLine(
                    "Post-EQ bands: ${postEq.bandCount}"
                )

                for (i in 0 until postEq.bandCount) {
                    try {
                        val band = postEq.getBand(i)

                        report.appendLine(
                            "Post-EQ band $i: " +
                                "frequency=${band.frequency} Hz, " +
                                "gain=${band.gain} dB"
                        )
                    } catch (e: Exception) {
                        report.appendLine(
                            "Post-EQ band $i: ERROR ${e.message}"
                        )
                    }
                }
            }

            report.appendLine(
                "Limiter enabled: ${config.isLimiterInUse}"
            )
        } catch (e: Exception) {
            report.appendLine("Available: false")
            report.appendLine(
                "ERROR: ${e.javaClass.simpleName}: ${e.message}"
            )
        } finally {
            try {
                dynamics?.release()
            } catch (_: Exception) {
                // Ignore release errors during diagnostics.
            }
        }
    }
}

2. Reemplazar temporalmente "MainActivity.kt"

Mantengo todo lo que ya tenías y agrego solamente la ejecución del diagnóstico. El resultado aparecerá en Logcat con la etiqueta "sBz-AUDIO-DIAGNOSTICS"; no modifica tu interfaz ni el DSP.

:::writing{variant="document" id="74106" title="MainActivity.kt"}

package com.sbz

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.sbz.diagnostics.AudioEffectsDiagnostics
import com.sbz.ui.MainViewModel
import com.sbz.ui.SbzApp
import com.sbz.ui.theme.SbzTheme

/**
 * Main Activity hosting the Jetpack Compose sBz User Interface.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Notification permission handled
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request notification permission on Android 13 (API 33)+ for DSP Foreground Service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(
                    Manifest.permission.POST_NOTIFICATIONS
                )
            }
        }

        // Temporary device audio-effects diagnostic.
        // This only queries Android capabilities and does not alter DSP.
        runAudioEffectsDiagnostics()

        setContent {
            SbzTheme {
                SbzApp(viewModel = viewModel)
            }
        }
    }

    private fun runAudioEffectsDiagnostics() {
        Thread {
            try {
                val report = AudioEffectsDiagnostics.run()

                Log.i(
                    "sBz-AUDIO-DIAGNOSTICS",
                    "\n$report"
                )
            } catch (e: Exception) {
                Log.e(
                    "sBz-AUDIO-DIAGNOSTICS",
                    "Diagnostic failed",
                    e
                )
            }
        }.start()
    }

    override fun onResume() {
        super.onResume()

        // Re-verify effect control upon resuming into view
        viewModel.reclaimDspControl()
    }
}

Qué hacemos después de compilar

No vamos a interpretar nada todavía.

Instalás esta versión y necesitamos el resultado de Logcat correspondiente a:

sBz-AUDIO-DIAGNOSTICS

Con eso podremos saber qué expone tu dispositivo concreto y decidir la siguiente modificación del motor.

Importante: esta prueba no conecta todavía "ConstantQGraphicEq"; solamente obtiene el mapa real de efectos del dispositivo.
