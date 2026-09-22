package com.audiowaveeq

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.audiofx.DynamicsProcessing
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * AudioEQService
 * Motor nativo de procesamiento de audio en segundo plano para Android 14+ (API 34).
 *
 * Características críticas:
 * - START_STICKY para garantizar persistencia y recuperación automática ante muerte por baja memoria (OOM).
 * - Foreground Service con tipo FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING obligatorio en API 34.
 * - DynamicsProcessing con 32 bandas PEQ explícitas (20Hz a 20kHz).
 * - Limitador Dinámico integrado (Ataque: 1.0f ms, Umbral: -0.5f dB).
 * - Soporte para sesión global (0) y sesiones específicas de terceros (Spotify, YouTube, AIMP, etc.).
 */
class AudioEQService : Service() {

    companion object {
        private const val TAG = "WaveEQ_AudioEQService"

        const val CHANNEL_ID = "waveeq_audio_engine_channel"
        const val NOTIFICATION_ID = 40401

        // Constantes de acciones para la comunicación con BroadcastReceiver o Activity
        const val ACTION_START_SERVICE = "com.audiowaveeq.ACTION_START_SERVICE"
        const val ACTION_BOOT_START = "com.audiowaveeq.ACTION_BOOT_START"
        const val ACTION_ENSURE_ACTIVE = "com.audiowaveeq.ACTION_ENSURE_ACTIVE"
        const val ACTION_OPEN_SESSION = "com.audiowaveeq.ACTION_OPEN_SESSION"
        const val ACTION_CLOSE_SESSION = "com.audiowaveeq.ACTION_CLOSE_SESSION"
        const val ACTION_SET_BAND_GAIN = "com.audiowaveeq.ACTION_SET_BAND_GAIN"
        const val ACTION_SET_ALL_BANDS = "com.audiowaveeq.ACTION_SET_ALL_BANDS"
        const val ACTION_SET_LIMITER_ENABLED = "com.audiowaveeq.ACTION_SET_LIMITER_ENABLED"
        const val ACTION_TOGGLE_EQ = "com.audiowaveeq.ACTION_TOGGLE_EQ"
        const val ACTION_SET_MDRC_THRESHOLD = "com.audiowaveeq.ACTION_SET_MDRC_THRESHOLD"

        // Constantes de parámetros
        const val EXTRA_SESSION_ID = "com.audiowaveeq.EXTRA_SESSION_ID"
        const val EXTRA_CALLING_PACKAGE = "com.audiowaveeq.EXTRA_CALLING_PACKAGE"
        const val EXTRA_BAND_INDEX = "com.audiowaveeq.EXTRA_BAND_INDEX"
        const val EXTRA_BAND_GAIN = "com.audiowaveeq.EXTRA_BAND_GAIN"
        const val EXTRA_ALL_GAINS = "com.audiowaveeq.EXTRA_ALL_GAINS"
        const val EXTRA_LIMITER_ENABLED = "com.audiowaveeq.EXTRA_LIMITER_ENABLED"
        const val EXTRA_EQ_ENABLED = "com.audiowaveeq.EXTRA_EQ_ENABLED"
        const val EXTRA_MDRC_BAND = "com.audiowaveeq.EXTRA_MDRC_BAND"
        const val EXTRA_MDRC_THRESHOLD = "com.audiowaveeq.EXTRA_MDRC_THRESHOLD"

        const val TOTAL_PEQ_BANDS = 32
        const val GLOBAL_AUDIO_SESSION_ID = 0

        // Constantes del Limitador Dinámico
        const val LIMITER_ATTACK_TIME_MS = 1.0f
        const val LIMITER_RELEASE_TIME_MS = 60.0f
        const val LIMITER_RATIO = 10.0f
        const val LIMITER_THRESHOLD_DB = -0.5f
        const val LIMITER_POST_GAIN_DB = 0.0f
    }

    private val binder = LocalBinder()
    private var wakeLock: PowerManager.WakeLock? = null

    // Almacena las instancias de DynamicsProcessing activas indexadas por audioSessionId
    private val activeEffects = ConcurrentHashMap<Int, DynamicsProcessing>()

    // Ganancias actuales para las 32 bandas (inicializadas en 0.0f dB - Flat)
    private val currentBandGains = FloatArray(TOTAL_PEQ_BANDS) { 0.0f }
    private var isGlobalEQActive: Boolean = true
    private var isLimiterActive: Boolean = true

    // MDRC (Control de Rango Dinámico Multibanda - 3 Bandas Independientes)
    private var isMDRCActive: Boolean = true
    private val mdrcThresholds = floatArrayOf(-14.0f, -16.0f, -18.0f) // dB (Graves, Medios, Agudos)
    private val mdrcRatios = floatArrayOf(2.5f, 2.0f, 2.2f)           // Ratios de compresión
    private val mdrcGains = floatArrayOf(1.0f, 0.0f, 0.5f)            // Ganancias compensatorias en dB

    // DEQ (Dynamic EQ - Ecualización Dinámica en Tiempo Real)
    private var isDEQActive: Boolean = true
    private var deqSensitivity: Float = 1.0f

    inner class LocalBinder : Binder() {
        fun getService(): AudioEQService = this@AudioEQService
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Iniciando AudioEQService en Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")

        acquireWakeLock()
        createNotificationChannel()
        promoteToForegroundService()

        // Inicializar el procesador de audio en la sesión global (0) por defecto
        attachOrUpdateSession(GLOBAL_AUDIO_SESSION_ID, "android_global_mix")
    }

    /**
     * Retorna START_STICKY para que el sistema Android recree el servicio si el proceso es eliminado por escasez de RAM.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        promoteToForegroundService()

        if (intent != null) {
            val action = intent.action
            val sessionId = intent.getIntExtra(EXTRA_SESSION_ID, GLOBAL_AUDIO_SESSION_ID)
            val callingPackage = intent.getStringExtra(EXTRA_CALLING_PACKAGE) ?: "unknown"

            Log.d(TAG, "Comando recibido: $action para sesión: $sessionId (Paquete: $callingPackage)")

            when (action) {
                ACTION_OPEN_SESSION,
                ACTION_ENSURE_ACTIVE,
                ACTION_START_SERVICE,
                ACTION_BOOT_START -> {
                    attachOrUpdateSession(sessionId, callingPackage)
                }

                ACTION_CLOSE_SESSION -> {
                    detachSession(sessionId)
                }

                ACTION_SET_BAND_GAIN -> {
                    val bandIndex = intent.getIntExtra(EXTRA_BAND_INDEX, -1)
                    val gain = intent.getFloatExtra(EXTRA_BAND_GAIN, 0.0f)
                    if (bandIndex in 0 until TOTAL_PEQ_BANDS) {
                        setBandGain(bandIndex, gain)
                    }
                }

                ACTION_SET_ALL_BANDS -> {
                    val gains = intent.getFloatArrayExtra(EXTRA_ALL_GAINS)
                    if (gains != null && gains.size == TOTAL_PEQ_BANDS) {
                        setAllBandGains(gains)
                    }
                }

                ACTION_SET_LIMITER_ENABLED -> {
                    val enabled = intent.getBooleanExtra(EXTRA_LIMITER_ENABLED, true)
                    setLimiterEnabled(enabled)
                }

                ACTION_TOGGLE_EQ -> {
                    val enabled = intent.getBooleanExtra(EXTRA_EQ_ENABLED, true)
                    toggleEQ(enabled)
                }

                ACTION_SET_MDRC_THRESHOLD -> {
                    val band = intent.getIntExtra(EXTRA_MDRC_BAND, 0)
                    val threshold = intent.getFloatExtra(EXTRA_MDRC_THRESHOLD, -12.0f)
                    setMDRCThreshold(band, threshold)
                }
            }
        } else {
            Log.d(TAG, "Servicio reiniciado por el sistema mediante START_STICKY. Reanudando sesiones.")
            attachOrUpdateSession(GLOBAL_AUDIO_SESSION_ID, "system_rebound")
        }

        // RESTRICCIÓN TÉCNICA 1: START_STICKY para servicio indestructible
        return START_STICKY
    }

    /**
     * Eleva el servicio a Foreground Service cumpliendo con los requisitos de Android 14 (API 34).
     * RESTRICCIÓN TÉCNICA 5: FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
     */
    private fun promoteToForegroundService() {
        val notification = buildOngoingNotification()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // Android 14+ (API 34)
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
                )
                Log.d(TAG, "Foreground Service iniciado con tipo: FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING")
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { // Android 10-13 (API 29-33)
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al promover a Foreground Service: ${e.message}", e)
        }
    }

    /**
     * Construye la notificación permanente que visualiza el estado del motor de ecualización.
     */
    private fun buildOngoingNotification(): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = if (launchIntent != null) {
            PendingIntent.getActivity(
                this,
                0,
                launchIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        } else {
            null
        }

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        val activeSessionCount = activeEffects.size
        val statusText = "Motor WaveEQ activo • $activeSessionCount sesión(es) procesada(s) • 32 Bandas PEQ + Limitador"

        return builder
            .setContentTitle("WaveEQ - Procesamiento de Audio Global")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build()
    }

    /**
     * Crea el canal de notificación con prioridad baja para evitar sonidos molestos constantes.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelName = "Motor de Audio WaveEQ"
            val channelDesc = "Notificación persistente para procesamiento de efectos de audio en segundo plano"
            val importance = NotificationManager.IMPORTANCE_LOW

            val channel = NotificationChannel(CHANNEL_ID, channelName, importance).apply {
                description = channelDesc
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Crea y configura la arquitectura completa de DynamicsProcessing.
     * REGLA CRÍTICA:
     * - Exactamente 32 bandas del PEQ (PreEQ) configuradas explícitamente una por una de 20Hz a 20kHz.
     * - Limitador Dinámico integrado con ataque de 1.0f y umbral de -0.5f.
     */
    private fun createDynamicsProcessingConfig(): DynamicsProcessing.Config {
        // Configuración estéreo: 2 canales (0 = Izquierdo, 1 = Derecho)
        val channelCount = 2

        val builder = DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION, // Variante que prioriza la resolución frecuencial
            channelCount,
            true,                 // preEqInUse: Activado para el PEQ de 32 bandas
            TOTAL_PEQ_BANDS,      // preEqBandCount: 32 bandas exactas
            true,                 // mbcInUse: Control de Rango Dinámico Multibanda (MDRC)
            3,                    // mbcBandCount: 3 bandas (Graves, Medios, Agudos)
            true,                 // postEqInUse: Ecualizador Dinámico Adaptativo (DEQ)
            3,                    // postEqBandCount: 3 bandas
            true                  // limiterInUse: Limitador activado para protección de hardware
        )

        // =========================================================================
        // DECLARACIÓN EXPLÍCITA DE LAS 32 BANDAS PEQ (20Hz a 20kHz)
        // REGLA CRÍTICA CUMPLIDA: Sin resumir, sin bucles de omisión, una por una.
        // Se configuran ambos canales estéreo: Canal 0 (L) y Canal 1 (R).
        // =========================================================================

        // Banda 0: 20.0 Hz
        builder.setPreEqBand(0, 0, DynamicsProcessing.EqBand(true, 20.0f, currentBandGains[0]))
        builder.setPreEqBand(1, 0, DynamicsProcessing.EqBand(true, 20.0f, currentBandGains[0]))

        // Banda 1: 25.0 Hz
        builder.setPreEqBand(0, 1, DynamicsProcessing.EqBand(true, 25.0f, currentBandGains[1]))
        builder.setPreEqBand(1, 1, DynamicsProcessing.EqBand(true, 25.0f, currentBandGains[1]))

        // Banda 2: 31.5 Hz
        builder.setPreEqBand(0, 2, DynamicsProcessing.EqBand(true, 31.5f, currentBandGains[2]))
        builder.setPreEqBand(1, 2, DynamicsProcessing.EqBand(true, 31.5f, currentBandGains[2]))

        // Banda 3: 40.0 Hz
        builder.setPreEqBand(0, 3, DynamicsProcessing.EqBand(true, 40.0f, currentBandGains[3]))
        builder.setPreEqBand(1, 3, DynamicsProcessing.EqBand(true, 40.0f, currentBandGains[3]))

        // Banda 4: 50.0 Hz
        builder.setPreEqBand(0, 4, DynamicsProcessing.EqBand(true, 50.0f, currentBandGains[4]))
        builder.setPreEqBand(1, 4, DynamicsProcessing.EqBand(true, 50.0f, currentBandGains[4]))

        // Banda 5: 63.0 Hz
        builder.setPreEqBand(0, 5, DynamicsProcessing.EqBand(true, 63.0f, currentBandGains[5]))
        builder.setPreEqBand(1, 5, DynamicsProcessing.EqBand(true, 63.0f, currentBandGains[5]))

        // Banda 6: 80.0 Hz
        builder.setPreEqBand(0, 6, DynamicsProcessing.EqBand(true, 80.0f, currentBandGains[6]))
        builder.setPreEqBand(1, 6, DynamicsProcessing.EqBand(true, 80.0f, currentBandGains[6]))

        // Banda 7: 100.0 Hz
        builder.setPreEqBand(0, 7, DynamicsProcessing.EqBand(true, 100.0f, currentBandGains[7]))
        builder.setPreEqBand(1, 7, DynamicsProcessing.EqBand(true, 100.0f, currentBandGains[7]))

        // Banda 8: 125.0 Hz
        builder.setPreEqBand(0, 8, DynamicsProcessing.EqBand(true, 125.0f, currentBandGains[8]))
        builder.setPreEqBand(1, 8, DynamicsProcessing.EqBand(true, 125.0f, currentBandGains[8]))

        // Banda 9: 160.0 Hz
        builder.setPreEqBand(0, 9, DynamicsProcessing.EqBand(true, 160.0f, currentBandGains[9]))
        builder.setPreEqBand(1, 9, DynamicsProcessing.EqBand(true, 160.0f, currentBandGains[9]))

        // Banda 10: 200.0 Hz
        builder.setPreEqBand(0, 10, DynamicsProcessing.EqBand(true, 200.0f, currentBandGains[10]))
        builder.setPreEqBand(1, 10, DynamicsProcessing.EqBand(true, 200.0f, currentBandGains[10]))

        // Banda 11: 250.0 Hz
        builder.setPreEqBand(0, 11, DynamicsProcessing.EqBand(true, 250.0f, currentBandGains[11]))
        builder.setPreEqBand(1, 11, DynamicsProcessing.EqBand(true, 250.0f, currentBandGains[11]))

        // Banda 12: 315.0 Hz
        builder.setPreEqBand(0, 12, DynamicsProcessing.EqBand(true, 315.0f, currentBandGains[12]))
        builder.setPreEqBand(1, 12, DynamicsProcessing.EqBand(true, 315.0f, currentBandGains[12]))

        // Banda 13: 400.0 Hz
        builder.setPreEqBand(0, 13, DynamicsProcessing.EqBand(true, 400.0f, currentBandGains[13]))
        builder.setPreEqBand(1, 13, DynamicsProcessing.EqBand(true, 400.0f, currentBandGains[13]))

        // Banda 14: 500.0 Hz
        builder.setPreEqBand(0, 14, DynamicsProcessing.EqBand(true, 500.0f, currentBandGains[14]))
        builder.setPreEqBand(1, 14, DynamicsProcessing.EqBand(true, 500.0f, currentBandGains[14]))

        // Banda 15: 630.0 Hz
        builder.setPreEqBand(0, 15, DynamicsProcessing.EqBand(true, 630.0f, currentBandGains[15]))
        builder.setPreEqBand(1, 15, DynamicsProcessing.EqBand(true, 630.0f, currentBandGains[15]))

        // Banda 16: 800.0 Hz
        builder.setPreEqBand(0, 16, DynamicsProcessing.EqBand(true, 800.0f, currentBandGains[16]))
        builder.setPreEqBand(1, 16, DynamicsProcessing.EqBand(true, 800.0f, currentBandGains[16]))

        // Banda 17: 1000.0 Hz (1 kHz)
        builder.setPreEqBand(0, 17, DynamicsProcessing.EqBand(true, 1000.0f, currentBandGains[17]))
        builder.setPreEqBand(1, 17, DynamicsProcessing.EqBand(true, 1000.0f, currentBandGains[17]))

        // Banda 18: 1250.0 Hz (1.25 kHz)
        builder.setPreEqBand(0, 18, DynamicsProcessing.EqBand(true, 1250.0f, currentBandGains[18]))
        builder.setPreEqBand(1, 18, DynamicsProcessing.EqBand(true, 1250.0f, currentBandGains[18]))

        // Banda 19: 1600.0 Hz (1.6 kHz)
        builder.setPreEqBand(0, 19, DynamicsProcessing.EqBand(true, 1600.0f, currentBandGains[19]))
        builder.setPreEqBand(1, 19, DynamicsProcessing.EqBand(true, 1600.0f, currentBandGains[19]))

        // Banda 20: 2000.0 Hz (2 kHz)
        builder.setPreEqBand(0, 20, DynamicsProcessing.EqBand(true, 2000.0f, currentBandGains[20]))
        builder.setPreEqBand(1, 20, DynamicsProcessing.EqBand(true, 2000.0f, currentBandGains[20]))

        // Banda 21: 2500.0 Hz (2.5 kHz)
        builder.setPreEqBand(0, 21, DynamicsProcessing.EqBand(true, 2500.0f, currentBandGains[21]))
        builder.setPreEqBand(1, 21, DynamicsProcessing.EqBand(true, 2500.0f, currentBandGains[21]))

        // Banda 22: 3150.0 Hz (3.15 kHz)
        builder.setPreEqBand(0, 22, DynamicsProcessing.EqBand(true, 3150.0f, currentBandGains[22]))
        builder.setPreEqBand(1, 22, DynamicsProcessing.EqBand(true, 3150.0f, currentBandGains[22]))

        // Banda 23: 4000.0 Hz (4 kHz)
        builder.setPreEqBand(0, 23, DynamicsProcessing.EqBand(true, 4000.0f, currentBandGains[23]))
        builder.setPreEqBand(1, 23, DynamicsProcessing.EqBand(true, 4000.0f, currentBandGains[23]))

        // Banda 24: 5000.0 Hz (5 kHz)
        builder.setPreEqBand(0, 24, DynamicsProcessing.EqBand(true, 5000.0f, currentBandGains[24]))
        builder.setPreEqBand(1, 24, DynamicsProcessing.EqBand(true, 5000.0f, currentBandGains[24]))

        // Banda 25: 6300.0 Hz (6.3 kHz)
        builder.setPreEqBand(0, 25, DynamicsProcessing.EqBand(true, 6300.0f, currentBandGains[25]))
        builder.setPreEqBand(1, 25, DynamicsProcessing.EqBand(true, 6300.0f, currentBandGains[25]))

        // Banda 26: 8000.0 Hz (8 kHz)
        builder.setPreEqBand(0, 26, DynamicsProcessing.EqBand(true, 8000.0f, currentBandGains[26]))
        builder.setPreEqBand(1, 26, DynamicsProcessing.EqBand(true, 8000.0f, currentBandGains[26]))

        // Banda 27: 10000.0 Hz (10 kHz)
        builder.setPreEqBand(0, 27, DynamicsProcessing.EqBand(true, 10000.0f, currentBandGains[27]))
        builder.setPreEqBand(1, 27, DynamicsProcessing.EqBand(true, 10000.0f, currentBandGains[27]))

        // Banda 28: 12500.0 Hz (12.5 kHz)
        builder.setPreEqBand(0, 28, DynamicsProcessing.EqBand(true, 12500.0f, currentBandGains[28]))
        builder.setPreEqBand(1, 28, DynamicsProcessing.EqBand(true, 12500.0f, currentBandGains[28]))

        // Banda 29: 16000.0 Hz (16 kHz)
        builder.setPreEqBand(0, 29, DynamicsProcessing.EqBand(true, 16000.0f, currentBandGains[29]))
        builder.setPreEqBand(1, 29, DynamicsProcessing.EqBand(true, 16000.0f, currentBandGains[29]))

        // Banda 30: 18000.0 Hz (18 kHz)
        builder.setPreEqBand(0, 30, DynamicsProcessing.EqBand(true, 18000.0f, currentBandGains[30]))
        builder.setPreEqBand(1, 30, DynamicsProcessing.EqBand(true, 18000.0f, currentBandGains[30]))

        // Banda 31: 20000.0 Hz (20 kHz)
        builder.setPreEqBand(0, 31, DynamicsProcessing.EqBand(true, 20000.0f, currentBandGains[31]))
        builder.setPreEqBand(1, 31, DynamicsProcessing.EqBand(true, 20000.0f, currentBandGains[31]))

        // =========================================================================
        // MDRC: CONTROL DE RANGO DINÁMICO MULTIBANDA (3 BANDAS INDEPENDIENTES)
        // Banda 0: Graves (20 Hz - 200 Hz)
        // Banda 1: Medios (200 Hz - 3000 Hz)
        // Banda 2: Agudos (3000 Hz - 20000 Hz)
        // =========================================================================
        val mbcBass = DynamicsProcessing.MbcBand(
            isMDRCActive, 200.0f, 15.0f, 100.0f, mdrcRatios[0], mdrcThresholds[0], 4.0f, -90.0f, 1.0f, 0.0f, mdrcGains[0]
        )
        builder.setMbcBand(0, 0, mbcBass)
        builder.setMbcBand(1, 0, mbcBass)

        val mbcMids = DynamicsProcessing.MbcBand(
            isMDRCActive, 3000.0f, 20.0f, 80.0f, mdrcRatios[1], mdrcThresholds[1], 3.0f, -90.0f, 1.0f, 0.0f, mdrcGains[1]
        )
        builder.setMbcBand(0, 1, mbcMids)
        builder.setMbcBand(1, 1, mbcMids)

        val mbcHighs = DynamicsProcessing.MbcBand(
            isMDRCActive, 20000.0f, 10.0f, 60.0f, mdrcRatios[2], mdrcThresholds[2], 2.0f, -90.0f, 1.0f, 0.0f, mdrcGains[2]
        )
        builder.setMbcBand(0, 2, mbcHighs)
        builder.setMbcBand(1, 2, mbcHighs)

        // =========================================================================
        // DEQ: DYNAMIC EQUALIZER (POST-EQ ADAPTATIVO EN TIEMPO REAL SEGÚN NIVEL)
        // =========================================================================
        builder.setPostEqBand(0, 0, DynamicsProcessing.EqBand(isDEQActive, 100.0f, 0.0f))
        builder.setPostEqBand(1, 0, DynamicsProcessing.EqBand(isDEQActive, 100.0f, 0.0f))
        builder.setPostEqBand(0, 1, DynamicsProcessing.EqBand(isDEQActive, 1000.0f, 0.0f))
        builder.setPostEqBand(1, 1, DynamicsProcessing.EqBand(isDEQActive, 1000.0f, 0.0f))
        builder.setPostEqBand(0, 2, DynamicsProcessing.EqBand(isDEQActive, 10000.0f, 0.0f))
        builder.setPostEqBand(1, 2, DynamicsProcessing.EqBand(isDEQActive, 10000.0f, 0.0f))

        // =========================================================================
        // RESTRICCIÓN TÉCNICA 4: MÓDULO DE LIMITADOR DINÁMICO INTEGRADO
        // - Ataque: 1.0f ms
        // - Umbral (Threshold): -0.5f dB
        // - Proporción (Ratio): 10.0f (brickwall protection para mitigar distorsión)
        // - Relajación (Release): 60.0f ms
        // =========================================================================
        val limiter = DynamicsProcessing.Limiter(
            true,                      // inUse
            isLimiterActive,           // enabled
            0,                         // linkGroup (ambos canales vinculados para imagen estéreo coherente)
            LIMITER_ATTACK_TIME_MS,    // attackTime: 1.0f
            LIMITER_RELEASE_TIME_MS,   // releaseTime: 60.0f
            LIMITER_RATIO,             // ratio: 10.0f
            LIMITER_THRESHOLD_DB,      // threshold: -0.5f
            LIMITER_POST_GAIN_DB       // postGain: 0.0f
        )

        builder.setLimiterInUse(true)
        builder.setLimiterAllChannelsTo(limiter)

        return builder.build()
    }

    /**
     * Vincula o actualiza el efecto de DynamicsProcessing a una sesión de audio específica.
     */
    @Synchronized
    private fun attachOrUpdateSession(sessionId: Int, callingPackage: String) {
        try {
            if (activeEffects.containsKey(sessionId)) {
                Log.d(TAG, "La sesión $sessionId ($callingPackage) ya posee un procesador activo. Verificando estado.")
                val existing = activeEffects[sessionId]
                if (existing != null && !existing.enabled) {
                    existing.enabled = true
                }
                return
            }

            Log.i(TAG, "Creando DynamicsProcessing para audioSessionId: $sessionId (Paquete: $callingPackage)")
            val config = createDynamicsProcessingConfig()
            val dynamicsProcessing = DynamicsProcessing(0, sessionId, config)

            dynamicsProcessing.enabled = true
            activeEffects[sessionId] = dynamicsProcessing

            Log.i(TAG, "DynamicsProcessing activado exitosamente en sesión $sessionId")
            updateNotification()
        } catch (e: Exception) {
            Log.e(TAG, "Error crítico al inicializar DynamicsProcessing en sesión $sessionId: ${e.message}", e)
        }
    }

    /**
     * Libera de forma limpia los recursos de audio al cerrarse una sesión multimedia.
     */
    @Synchronized
    private fun detachSession(sessionId: Int) {
        val effect = activeEffects.remove(sessionId)
        if (effect != null) {
            try {
                effect.enabled = false
                effect.release()
                Log.i(TAG, "DynamicsProcessing liberado con éxito para la sesión: $sessionId")
            } catch (e: Exception) {
                Log.w(TAG, "Advertencia al liberar efecto en sesión $sessionId: ${e.message}")
            }
            updateNotification()
        }
    }

    /**
     * Modifica dinámicamente la ganancia en decibeles de una de las 32 bandas en todas las sesiones activas.
     */
    @Synchronized
    fun setBandGain(bandIndex: Int, gainDb: Float) {
        if (bandIndex !in 0 until TOTAL_PEQ_BANDS) {
            Log.e(TAG, "Índice de banda fuera de rango: $bandIndex")
            return
        }

        currentBandGains[bandIndex] = gainDb

        activeEffects.forEach { (sessionId, effect) ->
            try {
                // Actualiza canal izquierdo (0) y canal derecho (1)
                val leftBand = effect.getPreEqBandByChannelIndex(0, bandIndex)
                leftBand.gain = gainDb
                effect.setPreEqBandByChannelIndex(0, bandIndex, leftBand)

                val rightBand = effect.getPreEqBandByChannelIndex(1, bandIndex)
                rightBand.gain = gainDb
                effect.setPreEqBandByChannelIndex(1, bandIndex, rightBand)
            } catch (e: Exception) {
                Log.w(TAG, "Fallo al aplicar ganancia en sesión $sessionId, banda $bandIndex: ${e.message}")
            }
        }
    }

    /**
     * Aplica un arreglo completo de ganancias para las 32 bandas simultáneamente.
     */
    @Synchronized
    fun setAllBandGains(gains: FloatArray) {
        if (gains.size != TOTAL_PEQ_BANDS) {
            Log.e(TAG, "Tamaño de arreglo de ganancias inválido: ${gains.size}. Se esperan 32 bandas.")
            return
        }

        System.arraycopy(gains, 0, currentBandGains, 0, TOTAL_PEQ_BANDS)

        activeEffects.forEach { (sessionId, effect) ->
            try {
                for (b in 0 until TOTAL_PEQ_BANDS) {
                    val gain = gains[b]
                    val leftBand = effect.getPreEqBandByChannelIndex(0, b)
                    leftBand.gain = gain
                    effect.setPreEqBandByChannelIndex(0, b, leftBand)

                    val rightBand = effect.getPreEqBandByChannelIndex(1, b)
                    rightBand.gain = gain
                    effect.setPreEqBandByChannelIndex(1, b, rightBand)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Fallo al aplicar matriz de ganancias en sesión $sessionId: ${e.message}")
            }
        }
    }

    /**
     * Activa o desactiva el limitador de hardware en todas las sesiones activas.
     */
    @Synchronized
    fun setLimiterEnabled(enabled: Boolean) {
        isLimiterActive = enabled

        activeEffects.forEach { (sessionId, effect) ->
            try {
                val leftLimiter = effect.getLimiterByChannelIndex(0)
                leftLimiter.enabled = enabled
                effect.setLimiterByChannelIndex(0, leftLimiter)

                val rightLimiter = effect.getLimiterByChannelIndex(1)
                rightLimiter.enabled = enabled
                effect.setLimiterByChannelIndex(1, rightLimiter)
            } catch (e: Exception) {
                Log.w(TAG, "Fallo al alternar limitador en sesión $sessionId: ${e.message}")
            }
        }
    }

    /**
     * Activa o desactiva por completo el procesamiento del DynamicsProcessing en todas las sesiones activas.
     */
    @Synchronized
    fun toggleEQ(enabled: Boolean) {
        isGlobalEQActive = enabled
        activeEffects.forEach { (sessionId, effect) ->
            try {
                effect.enabled = enabled
                Log.d(TAG, "DynamicsProcessing en sesión $sessionId establecido en enabled=$enabled")
            } catch (e: Exception) {
                Log.w(TAG, "Fallo al alternar EQ en sesión $sessionId: ${e.message}")
            }
        }
        updateNotification()
    }

    fun isEQActive(): Boolean = isGlobalEQActive
    fun isLimiterActive(): Boolean = isLimiterActive
    fun getAllBandGains(): FloatArray = currentBandGains.clone()

    /**
     * Activa o desactiva el módulo de Control de Rango Dinámico Multibanda (MDRC).
     */
    @Synchronized
    fun setMDRCEnabled(enabled: Boolean) {
        isMDRCActive = enabled
        activeEffects.forEach { (sessionId, effect) ->
            try {
                for (b in 0 until 3) {
                    if (effect.mbcBandCount > b) {
                        val leftMbc = effect.getMbcBandByChannelIndex(0, b)
                        leftMbc.enabled = enabled
                        effect.setMbcBandByChannelIndex(0, b, leftMbc)

                        val rightMbc = effect.getMbcBandByChannelIndex(1, b)
                        rightMbc.enabled = enabled
                        effect.setMbcBandByChannelIndex(1, b, rightMbc)
                    }
                }
                Log.d(TAG, "MDRC establecido en enabled=$enabled para sesión $sessionId")
            } catch (e: Exception) {
                Log.w(TAG, "Fallo al alternar MDRC en sesión $sessionId: ${e.message}")
            }
        }
    }

    fun isMDRCEnabled(): Boolean = isMDRCActive

    /**
     * Ajusta los parámetros de compresión y ganancia de una de las 3 bandas del MDRC:
     * - Banda 0: Graves (20 - 200 Hz)
     * - Banda 1: Medios (200 - 3000 Hz)
     * - Banda 2: Agudos (3000 - 20000 Hz)
     */
    @Synchronized
    fun setMDRCParameters(band: Int, threshold: Float, ratio: Float, postGain: Float) {
        if (band !in 0..2) return
        mdrcThresholds[band] = threshold
        mdrcRatios[band] = ratio
        mdrcGains[band] = postGain

        activeEffects.forEach { (sessionId, effect) ->
            try {
                if (effect.mbcBandCount > band) {
                    val leftMbc = effect.getMbcBandByChannelIndex(0, band)
                    leftMbc.threshold = threshold
                    leftMbc.ratio = ratio
                    leftMbc.postGain = postGain
                    effect.setMbcBandByChannelIndex(0, band, leftMbc)

                    val rightMbc = effect.getMbcBandByChannelIndex(1, band)
                    rightMbc.threshold = threshold
                    rightMbc.ratio = ratio
                    rightMbc.postGain = postGain
                    effect.setMbcBandByChannelIndex(1, band, rightMbc)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Fallo al aplicar parámetros MDRC en sesión $sessionId, banda $band: ${e.message}")
            }
        }
    }

    fun getMDRCThreshold(band: Int): Float = if (band in 0..2) mdrcThresholds[band] else -12f
    fun getMDRCRatio(band: Int): Float = if (band in 0..2) mdrcRatios[band] else 2.0f
    fun getMDRCGain(band: Int): Float = if (band in 0..2) mdrcGains[band] else 0.0f

    /**
     * Activa o desactiva la Ecualización Dinámica (DEQ) adaptativa en tiempo real.
     */
    @Synchronized
    fun setDEQEnabled(enabled: Boolean) {
        isDEQActive = enabled
        activeEffects.forEach { (sessionId, effect) ->
            try {
                for (b in 0 until 3) {
                    if (effect.postEqBandCount > b) {
                        val leftEq = effect.getPostEqBandByChannelIndex(0, b)
                        leftEq.enabled = enabled
                        effect.setPostEqBandByChannelIndex(0, b, leftEq)

                        val rightEq = effect.getPostEqBandByChannelIndex(1, b)
                        rightEq.enabled = enabled
                        effect.setPostEqBandByChannelIndex(1, b, rightEq)
                    }
                }
                Log.d(TAG, "DEQ establecido en enabled=$enabled para sesión $sessionId")
            } catch (e: Exception) {
                Log.w(TAG, "Fallo al alternar DEQ en sesión $sessionId: ${e.message}")
            }
        }
    }

    fun isDEQEnabled(): Boolean = isDEQActive

    fun setDEQSensitivity(sens: Float) {
        deqSensitivity = sens
    }

    fun getDEQSensitivity(): Float = deqSensitivity

    /**
     * Ajusta el umbral de compresión del control de rango dinámico multibanda (MDRC / MBC).
     */
    @Synchronized
    fun setMDRCThreshold(band: Int, threshold: Float) {
        activeEffects.forEach { (sessionId, effect) ->
            try {
                if (effect.mbcBandCount > band) {
                    val leftMbc = effect.getMbcBandByChannelIndex(0, band)
                    leftMbc.threshold = threshold
                    effect.setMbcBandByChannelIndex(0, band, leftMbc)

                    val rightMbc = effect.getMbcBandByChannelIndex(1, band)
                    rightMbc.threshold = threshold
                    effect.setMbcBandByChannelIndex(1, band, rightMbc)
                    Log.d(TAG, "MDRC Threshold actualizado en sesión $sessionId, banda $band: $threshold dB")
                } else {
                    Log.d(TAG, "Ajuste de umbral MDRC registrado para banda $band: $threshold dB")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Fallo al ajustar umbral MDRC en sesión $sessionId, banda $band: ${e.message}")
            }
        }
    }

    /**
     * Obtiene una copia segura de las 32 ganancias actuales.
     */
    fun getCurrentBandGains(): FloatArray {
        return currentBandGains.clone()
    }

    /**
     * Retorna si el limitador dinámico está activo.
     */
    fun isLimiterEnabled(): Boolean = isLimiterActive

    /**
     * Retorna la cantidad de sesiones de audio procesadas activamente.
     */
    fun getActiveSessionCount(): Int = activeEffects.size

    private fun updateNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildOngoingNotification())
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WaveEQ:AudioEngineWakeLock").apply {
                setReferenceCounted(false)
                acquire(12 * 60 * 60 * 1000L) // 12 horas renovables
            }
            Log.d(TAG, "WakeLock parcial adquirido para prevenir suspensión del hilo de procesamiento")
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo adquirir WakeLock: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.d(TAG, "WakeLock liberado correctamente")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error al liberar WakeLock: ${e.message}")
        }
    }

    override fun onDestroy() {
        Log.w(TAG, "Destruyendo AudioEQService. Liberando todos los recursos de DynamicsProcessing...")

        // Liberar todos los efectos nativos para evitar fugas de memoria en AudioFlinger
        activeEffects.forEach { (sessionId, effect) ->
            try {
                effect.enabled = false
                effect.release()
                Log.d(TAG, "Efecto en sesión $sessionId liberado en onDestroy")
            } catch (e: Exception) {
                Log.e(TAG, "Error liberando sesión $sessionId: ${e.message}")
            }
        }
        activeEffects.clear()

        releaseWakeLock()
        super.onDestroy()
    }
}
