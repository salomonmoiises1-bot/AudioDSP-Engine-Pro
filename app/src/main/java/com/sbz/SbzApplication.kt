package com.sbz

import android.app.Application
import android.content.Intent
import android.os.Build
import android.util.Log
import com.sbz.service.SbzAudioService

/**
 * sBz Application Entry Point.
 */
class SbzApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.i("SbzApp", "Initializing sBz Native Professional DSP...")
        startDspService()
    }

    private fun startDspService() {
        val intent = Intent(this, SbzAudioService::class.java).apply {
            action = SbzAudioService.ACTION_START
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            Log.e("SbzApp", "Failed starting SbzAudioService in application onCreate: ${e.message}")
        }
    }
}
