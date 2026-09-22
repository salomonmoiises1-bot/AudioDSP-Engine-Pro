package com.audiowaveeq

import android.app.Application
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * MainApplication
 * Aplicación Android 100% Nativa sin dependencias de React Native ni WebViews.
 * Inicia el servicio indestructible AudioEQService en segundo plano.
 */
class MainApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.i("WaveEQ_App", "Iniciando WaveEQ aplicación nativa Android 14+")

        // Inicializar el Foreground Service del motor de ecualización nativo
        val serviceIntent = Intent(this, AudioEQService::class.java).apply {
            action = AudioEQService.ACTION_START_SERVICE
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e("WaveEQ_App", "Error al iniciar AudioEQService: ${e.message}", e)
        }
    }
}
