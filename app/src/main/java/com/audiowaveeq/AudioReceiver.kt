package com.audiowaveeq

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.os.Build
import android.util.Log

/**
 * AudioReceiver
 * Receptor de difusiones para interceptar identificadores de sesión de audio (audioSessionId)
 * generados por aplicaciones reproductoras multimedia (Spotify, YouTube, AIMP, Poweramp, etc.).
 *
 * En Android 14+ (API 34), intercepta las intenciones globales de AudioManager y AudioEffect
 * para dirigir el enrutamiento dinámico hacia AudioEQService.
 */
class AudioReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "WaveEQ_AudioReceiver"

        // Intenciones de compatibilidad para reproductores populares
        const val ACTION_SPOTIFY_PLAYBACK = "com.spotify.music.playbackstatechanged"
        const val ACTION_SPOTIFY_META = "com.spotify.music.metachanged"
        const val ACTION_AIMP_META = "com.aimp.player.metachanged"
        const val ACTION_AIMP_PLAYSTATE = "com.aimp.player.playstatechanged"
        const val ACTION_ANDROID_MUSIC_META = "com.android.music.metachanged"
        const val ACTION_ANDROID_MUSIC_PLAYSTATE = "com.android.music.playstatechanged"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.i(TAG, "Difusión interceptada: $action")

        val serviceIntent = Intent(context, AudioEQService::class.java)

        when (action) {
            AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> {
                val audioSessionId = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, AudioEffect.ERROR_BAD_VALUE)
                val callingPackage = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME) ?: "unknown_package"

                Log.d(TAG, "Solicitud de apertura de sesión detectada - ID: $audioSessionId, Paquete: $callingPackage")

                if (audioSessionId != AudioEffect.ERROR_BAD_VALUE) {
                    serviceIntent.action = AudioEQService.ACTION_OPEN_SESSION
                    serviceIntent.putExtra(AudioEQService.EXTRA_SESSION_ID, audioSessionId)
                    serviceIntent.putExtra(AudioEQService.EXTRA_CALLING_PACKAGE, callingPackage)
                    startTargetService(context, serviceIntent)
                } else {
                    Log.w(TAG, "AudioSession ID inválido recibido en OPEN_AUDIO_EFFECT_CONTROL_SESSION")
                }
            }

            AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> {
                val audioSessionId = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, AudioEffect.ERROR_BAD_VALUE)
                val callingPackage = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME) ?: "unknown_package"

                Log.d(TAG, "Solicitud de cierre de sesión detectada - ID: $audioSessionId, Paquete: $callingPackage")

                if (audioSessionId != AudioEffect.ERROR_BAD_VALUE) {
                    serviceIntent.action = AudioEQService.ACTION_CLOSE_SESSION
                    serviceIntent.putExtra(AudioEQService.EXTRA_SESSION_ID, audioSessionId)
                    serviceIntent.putExtra(AudioEQService.EXTRA_CALLING_PACKAGE, callingPackage)
                    startTargetService(context, serviceIntent)
                }
            }

            ACTION_SPOTIFY_PLAYBACK,
            ACTION_SPOTIFY_META,
            ACTION_AIMP_META,
            ACTION_AIMP_PLAYSTATE,
            ACTION_ANDROID_MUSIC_META,
            ACTION_ANDROID_MUSIC_PLAYSTATE -> {
                Log.d(TAG, "Evento de reproducción recibido desde reproductor externo: $action")
                // Se asegura de que el motor de procesamiento esté activo en modo de sesión global (0)
                serviceIntent.action = AudioEQService.ACTION_ENSURE_ACTIVE
                serviceIntent.putExtra(AudioEQService.EXTRA_CALLING_PACKAGE, intent.`package` ?: "media_player")
                startTargetService(context, serviceIntent)
            }

            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                Log.i(TAG, "Dispositivo iniciado o app actualizada. Despertando motor WaveEQ...")
                serviceIntent.action = AudioEQService.ACTION_BOOT_START
                startTargetService(context, serviceIntent)
            }

            else -> {
                Log.d(TAG, "Acción no manejada directamente: $action")
            }
        }
    }

    /**
     * Inicia el Foreground Service cumpliendo con los estándares de Android 8.0+ y Android 14 (API 34).
     */
    private fun startTargetService(context: Context, intent: Intent) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fallo al iniciar AudioEQService desde BroadcastReceiver: ${e.message}", e)
        }
    }
}
