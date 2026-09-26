package com.sbz.session

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.media.audiofx.AudioEffect
import android.util.Log
import com.sbz.service.SbzAudioService

/**
 * BroadcastReceiver listening for system and media app audio effect session broadcasts:
 * - ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION
 * - ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION
 */
class AudioSessionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AudioSessionReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val sessionId = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, AudioEffect.ERROR_BAD_VALUE)
        val packageName = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME) ?: "unknown"

        Log.d(TAG, "Received AudioSession action: $action, sessionId: $sessionId, package: $packageName")

        if (sessionId == AudioEffect.ERROR_BAD_VALUE) {
            Log.w(TAG, "Invalid audio session id received, ignoring")
            return
        }

        when (action) {
            AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> {
                Log.i(TAG, "New audio session opened: $sessionId ($packageName)")
                val serviceIntent = Intent(context, SbzAudioService::class.java).apply {
                    this.action = SbzAudioService.ACTION_ATTACH_SESSION
                    putExtra(SbzAudioService.EXTRA_SESSION_ID, sessionId)
                    putExtra(SbzAudioService.EXTRA_PACKAGE_NAME, packageName)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
            AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> {
                Log.i(TAG, "Audio session closed: $sessionId ($packageName)")
                val serviceIntent = Intent(context, SbzAudioService::class.java).apply {
                    this.action = SbzAudioService.ACTION_DETACH_SESSION
                    putExtra(SbzAudioService.EXTRA_SESSION_ID, sessionId)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
        }
    }
}
