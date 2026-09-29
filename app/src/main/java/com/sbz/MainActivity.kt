package com.sbz

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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

    private var diagnosticReport by mutableStateOf<String?>(null)

    private val notificationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request notification permission on Android 13+.
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

        runAudioEffectsDiagnostics()

        setContent {
            SbzTheme {

                Box(
                    modifier = Modifier.fillMaxSize()
                ) {

                    SbzApp(viewModel = viewModel)

                    diagnosticReport?.let { report ->

                        AlertDialog(
                            onDismissRequest = {
                                diagnosticReport = null
                            },
                            title = {
                                Text("Diagnóstico de audio")
                            },
                            text = {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .verticalScroll(
                                            rememberScrollState()
                                        )
                                ) {
                                    Text(
                                        text = report
                                    )
                                }
                            },
                            confirmButton = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement =
                                        Arrangement.SpaceBetween
                                ) {

                                    TextButton(
                                        onClick = {
                                            copyDiagnosticReport(report)
                                        }
                                    ) {
                                        Text("Copiar")
                                    }

                                    Button(
                                        onClick = {
                                            diagnosticReport = null
                                        }
                                    ) {
                                        Text("Cerrar")
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    private fun runAudioEffectsDiagnostics() {

        Thread {

            try {

                val report =
                    AudioEffectsDiagnostics.run()

                Log.i(
                    "sBz-AUDIO-DIAGNOSTICS",
                    "\n$report"
                )

                runOnUiThread {
                    diagnosticReport = report
                }

            } catch (e: Exception) {

                val errorReport =
                    """
                    ========== sBz AUDIO DIAGNOSTICS ==========

                    ERROR ejecutando diagnóstico:

                    ${e.javaClass.simpleName}
                    ${e.message ?: "Sin mensaje de error"}

                    ===========================================
                    """.trimIndent()

                Log.e(
                    "sBz-AUDIO-DIAGNOSTICS",
                    "Diagnostic failed",
                    e
                )

                runOnUiThread {
                    diagnosticReport = errorReport
                }
            }
        }.start()
    }

    private fun copyDiagnosticReport(report: String) {

        val clipboard =
            getSystemService(Context.CLIPBOARD_SERVICE)
                    as ClipboardManager

        clipboard.setPrimaryClip(
            ClipData.newPlainText(
                "sBz Audio Diagnostics",
                report
            )
        )
    }

    override fun onResume() {
        super.onResume()

        // Re-verify effect control upon resuming into view.
        viewModel.reclaimDspControl()
    }
}
