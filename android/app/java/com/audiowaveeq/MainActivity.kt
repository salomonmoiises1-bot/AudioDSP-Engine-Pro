package com.audiowaveeq

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * MainActivity
 * Actividad Principal 100% Nativa en Kotlin y Jetpack Compose.
 * Sin WebViews, sin React Native. Conexión directa mediante ServiceConnection a AudioEQService.
 */
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "WaveEQ_MainActivity"
    }

    private var audioService by mutableStateOf<AudioEQService?>(null)
    private var isServiceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as AudioEQService.LocalBinder
            audioService = binder.getService()
            isServiceBound = true
            Log.i(TAG, "AudioEQService vinculado exitosamente a la interfaz Compose")
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            audioService = null
            isServiceBound = false
            Log.w(TAG, "AudioEQService desvinculado de la interfaz Compose")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Asegurar que el Foreground Service del motor de ecualización esté activo
        val startServiceIntent = Intent(this, AudioEQService::class.java).apply {
            action = AudioEQService.ACTION_START_SERVICE
        }
        startService(startServiceIntent)

        // Renderizar la interfaz nativa en Jetpack Compose
        setContent {
            val darkScheme = darkColorScheme(
                primary = Color(0xFF06B6D4),
                secondary = Color(0xFF6366F1),
                background = Color(0xFF030712),
                surface = Color(0xFF0F172A),
                onBackground = Color(0xFFF8FAFC),
                onSurface = Color(0xFFF8FAFC)
            )

            MaterialTheme(colorScheme = darkScheme) {
                WaveEQScreen(service = audioService)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Vincular el servicio para comunicación directa en tiempo real
        val bindIntent = Intent(this, AudioEQService::class.java)
        bindService(bindIntent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onStop() {
        super.onStop()
        if (isServiceBound) {
            unbindService(serviceConnection)
            isServiceBound = false
        }
    }
}
