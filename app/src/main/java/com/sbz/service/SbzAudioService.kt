package com.sbz.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.sbz.MainActivity
import com.sbz.R
import com.sbz.data.PresetRepository
import com.sbz.dsp.SbzDspEngine
import com.sbz.dsp.model.DspConfig

/**
 * Android Foreground Service holding and lifecycle-managing the sBz DSP Engine.
 * Ensures uninterrupted audio processing across the system, responds to audio device switches,
 * and maintains audio session tracking.
 */
class SbzAudioService : Service() {

    companion object {
        private const val TAG = "SbzAudioService"
        private const val NOTIFICATION_CHANNEL_ID = "sbz_dsp_channel"
        private const val NOTIFICATION_ID = 31415

        const val ACTION_START = "com.sbz.action.START"
        const val ACTION_STOP = "com.sbz.action.STOP"
        const val ACTION_TOGGLE_DSP = "com.sbz.action.TOGGLE_DSP"
        const val ACTION_ATTACH_SESSION = "com.sbz.action.ATTACH_SESSION"
        const val ACTION_DETACH_SESSION = "com.sbz.action.DETACH_SESSION"
        const val ACTION_RECLAIM_CONTROL = "com.sbz.action.RECLAIM_CONTROL"

        const val EXTRA_SESSION_ID = "extra_session_id"
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
    }

    private val binder = LocalBinder()
    val dspEngine = SbzDspEngine()
    private lateinit var presetRepo: PresetRepository
    private lateinit var audioManager: AudioManager
    private var activeConfig = DspConfig()

    inner class LocalBinder : Binder() {
        fun getService(): SbzAudioService = this@SbzAudioService
    }

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            super.onAudioDevicesAdded(addedDevices)
            Log.d(TAG, "Audio device connected, re-verifying DSP pipelines...")
            dspEngine.reclaimAllControl()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            super.onAudioDevicesRemoved(removedDevices)
            Log.d(TAG, "Audio device disconnected, re-verifying DSP pipelines...")
            dspEngine.reclaimAllControl()
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Creating SbzAudioService...")
        presetRepo = PresetRepository(this)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        createNotificationChannel()
        activeConfig = presetRepo.loadActiveConfig()

        // Register hardware device change callback
        try {
            audioManager.registerAudioDeviceCallback(audioDeviceCallback, null)
        } catch (e: Exception) {
            Log.w(TAG, "Could not register audioDeviceCallback: ${e.message}")
        }

        // Start DSP Engine and apply restored configuration
        dspEngine.start()
        dspEngine.updateConfig(activeConfig)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        Log.d(TAG, "onStartCommand action: $action")

        startForeground(NOTIFICATION_ID, buildNotification())

        when (action) {
            ACTION_START -> {
                if (!dspEngine.engineState.value.isRunning) {
                    dspEngine.start()
                    dspEngine.updateConfig(activeConfig)
                }
            }
            ACTION_STOP -> {
                dspEngine.stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE_DSP -> {
                val newEnabled = !activeConfig.isEnabled
                updateConfig(activeConfig.copy(isEnabled = newEnabled))
            }
            ACTION_ATTACH_SESSION -> {
                val sessionId = intent.getIntExtra(EXTRA_SESSION_ID, -1)
                if (sessionId != -1) {
                    dspEngine.attachSession(sessionId)
                }
            }
            ACTION_DETACH_SESSION -> {
                val sessionId = intent.getIntExtra(EXTRA_SESSION_ID, -1)
                if (sessionId != -1) {
                    dspEngine.detachSession(sessionId)
                }
            }
            ACTION_RECLAIM_CONTROL -> {
                dspEngine.reclaimAllControl()
            }
        }

        updateNotification()
        return START_STICKY
    }

    fun updateConfig(newConfig: DspConfig) {
        activeConfig = newConfig
        presetRepo.saveActiveConfig(newConfig)
        dspEngine.updateConfig(newConfig)
        updateNotification()
    }

    fun getCurrentConfig(): DspConfig = activeConfig

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "sBz DSP Engine",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Status of the active audio DSP pipeline"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val mainIntent = Intent(this, MainActivity::class.java)
        val pMain = PendingIntent.getActivity(
            this, 0, mainIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val toggleIntent = Intent(this, SbzAudioService::class.java).apply { action = ACTION_TOGGLE_DSP }
        val pToggle = PendingIntent.getService(
            this, 1, toggleIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, SbzAudioService::class.java).apply { action = ACTION_STOP }
        val pStop = PendingIntent.getService(
            this, 2, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val statusText = if (activeConfig.isEnabled) "DSP Active • Processing" else "DSP Bypassed"

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("sBz Audio DSP")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_sbz_logo)
            .setContentIntent(pMain)
            .setOngoing(true)
            .addAction(
                0,
                if (activeConfig.isEnabled) "Bypass" else "Enable",
                pToggle
            )
            .addAction(0, "Stop Engine", pStop)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, buildNotification())
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        Log.i(TAG, "Destroying SbzAudioService...")
        try {
            audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering audioDeviceCallback: ${e.message}")
        }
        dspEngine.stop()
        super.onDestroy()
    }
}
