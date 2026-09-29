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
