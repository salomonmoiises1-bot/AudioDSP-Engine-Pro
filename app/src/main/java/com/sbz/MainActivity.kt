package com.sbz

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
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
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            SbzTheme {
                SbzApp(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-verify effect control upon resuming into view
        viewModel.reclaimDspControl()
    }
}
