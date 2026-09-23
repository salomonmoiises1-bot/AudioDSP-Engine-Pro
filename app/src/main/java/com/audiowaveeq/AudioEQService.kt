package com.audiowaveeq

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.audiofx.DynamicsProcessing
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

class AudioEQService : Service() {

    companion object {
        private const val TAG = "WaveEQ_Service"

        const val ACTION_START_SERVICE =
            "com.audiowaveeq.action.START_SERVICE"

        const val ACTION_BOOT_START =
            "com.audiowaveeq.action.BOOT_START"

        const val ACTION_ENSURE_ACTIVE =
            "com.audiowaveeq.action.ENSURE_ACTIVE"

        const val ACTION_OPEN_SESSION =
            "com.audiowaveeq.action.OPEN_SESSION"

        const val ACTION_CLOSE_SESSION =
            "com.audiowaveeq.action.CLOSE_SESSION"

        const val ACTION_SET_BAND_GAIN =
            "com.audiowaveeq.action.SET_BAND_GAIN"

        const val ACTION_SET_ALL_BANDS =
            "com.audiowaveeq.action.SET_ALL_BANDS"

        const val ACTION_SET_LIMITER_ENABLED =
            "com.audiowaveeq.action.SET_LIMITER_ENABLED"

        const val ACTION_TOGGLE_EQ =
            "com.audiowaveeq.action.TOGGLE_EQ"

        const val ACTION_SET_MDRC_THRESHOLD =
            "com.audiowaveeq.action.SET_MDRC_THRESHOLD"

        const val EXTRA_AUDIO_SESSION =
            "audio_session"

        const val EXTRA_PACKAGE_NAME =
            "package_name"

        const val EXTRA_BAND_INDEX =
            "band_index"

        const val EXTRA_BAND_GAIN =
            "band_gain"

        const val EXTRA_ALL_GAINS =
            "all_gains"

        const val EXTRA_ENABLED =
            "enabled"

        const val EXTRA_MDRC_THRESHOLD =
            "mdrc_threshold"

        const val TOTAL_PEQ_BANDS = 32

        val PEQ_FREQUENCIES = floatArrayOf(
            20f, 25f, 31.5f, 40f,
            50f, 63f, 80f, 100f,
            125f, 160f, 200f, 250f,
            315f, 400f, 500f, 630f,
            800f, 1000f, 1250f, 1600f,
            2000f, 2500f, 3150f, 4000f,
            5000f, 6300f, 8000f, 10000f,
            12500f, 16000f, 18000f, 20000f
        )

        private const val CHANNEL_COUNT = 2
        private const val NOTIFICATION_CHANNEL = "waveeq_audio"

        private const val MIN_GAIN = -15f
        private const val MAX_GAIN = 15f
    }

    /*
     * IMPORTANTE:
     *
     * Ya NO usamos session 0 como supuesto "audio global".
     *
     * Cada DynamicsProcessing se conecta a una sesión de audio REAL
     * recibida mediante ACTION_OPEN_SESSION.
     */
    private val activeEffects =
        ConcurrentHashMap<Int, DynamicsProcessing>()

    private val sessionPackages =
        ConcurrentHashMap<Int, String>()

    private val currentBandGains =
        FloatArray(TOTAL_PEQ_BANDS) { 0f }

    @Volatile
    private var eqEnabled = true

    @Volatile
    private var limiterEnabled = true

    @Volatile
    private var mdrcThreshold = -12f

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()

        Log.i(TAG, "WaveEQ AudioEQService iniciado")

        acquireWakeLock()
        createNotificationChannel()
        startForegroundService()

        /*
         * NO hacemos:
         *
         * DynamicsProcessing(0, 0, ...)
         *
         * porque session 0 no representa automáticamente
         * todo el audio del dispositivo.
         */
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        if (intent == null) {
            return START_STICKY
        }

        when (intent.action) {

            ACTION_START_SERVICE,
            ACTION_BOOT_START,
            ACTION_ENSURE_ACTIVE -> {
                Log.d(TAG, "Servicio activo; esperando sesiones de audio")
            }

            ACTION_OPEN_SESSION -> {
                val sessionId =
                    intent.getIntExtra(EXTRA_AUDIO_SESSION, -1)

                val packageName =
                    intent.getStringExtra(EXTRA_PACKAGE_NAME)
                        ?: "unknown"

                if (sessionId >= 0) {
                    attachSession(sessionId, packageName)
                }
            }

            ACTION_CLOSE_SESSION -> {
                val sessionId =
                    intent.getIntExtra(EXTRA_AUDIO_SESSION, -1)

                if (sessionId >= 0) {
                    detachSession(sessionId)
                }
            }

            ACTION_SET_BAND_GAIN -> {

                val index =
                    intent.getIntExtra(EXTRA_BAND_INDEX, -1)

                val gain =
                    intent.getFloatExtra(EXTRA_BAND_GAIN, 0f)

                if (index in 0 until TOTAL_PEQ_BANDS) {
                    setBandGain(index, gain)
                }
            }

            ACTION_SET_ALL_BANDS -> {

                val gains =
                    intent.getFloatArrayExtra(EXTRA_ALL_GAINS)

                if (gains != null) {
                    setAllBandGains(gains)
                }
            }

            ACTION_TOGGLE_EQ -> {

                eqEnabled =
                    intent.getBooleanExtra(
                        EXTRA_ENABLED,
                        true
                    )

                updateAllEffects()
            }

            ACTION_SET_LIMITER_ENABLED -> {

                limiterEnabled =
                    intent.getBooleanExtra(
                        EXTRA_ENABLED,
                        true
                    )

                updateAllEffects()
            }

            ACTION_SET_MDRC_THRESHOLD -> {

                mdrcThreshold =
                    intent.getFloatExtra(
                        EXTRA_MDRC_THRESHOLD,
                        -12f
                    )

                updateAllEffects()
            }
        }

        return START_STICKY
    }

    private fun attachSession(
        sessionId: Int,
        packageName: String
    ) {

        if (sessionId < 0) return

        /*
         * Si ya existe un efecto para esta sesión,
         * simplemente actualizamos su configuración.
         */
        if (activeEffects.containsKey(sessionId)) {
            sessionPackages[sessionId] = packageName
            updateEffect(activeEffects[sessionId]!!)
            return
        }

        try {

            val config =
                createDynamicsProcessingConfig()

            val effect =
                DynamicsProcessing(
                    0,
                    sessionId,
                    config
                )

            effect.enabled = true

            activeEffects[sessionId] = effect
            sessionPackages[sessionId] = packageName

            updateEffect(effect)

            Log.i(
                TAG,
                "DSP conectado a sesión $sessionId ($packageName)"
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "No se pudo conectar DSP a sesión $sessionId",
                e
            )
        }
    }

    private fun detachSession(sessionId: Int) {

        val effect =
            activeEffects.remove(sessionId)

        sessionPackages.remove(sessionId)

        try {
            effect?.release()
        } catch (_: Exception) {
        }

        Log.i(
            TAG,
            "DSP desconectado de sesión $sessionId"
        )
    }

    private fun createDynamicsProcessingConfig():
            DynamicsProcessing.Config {

        val preEq =
            DynamicsProcessing.Eq(
                true,
                TOTAL_PEQ_BANDS
            )

        val mbc =
            DynamicsProcessing.Mbc(
                true,
                3
            )

        val postEq =
            DynamicsProcessing.Eq(
                true,
                3
            )

        return DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
            CHANNEL_COUNT,
            preEq,
            mbc,
            postEq,
            limiterEnabled
        ).build()
    }

    private fun updateAllEffects() {

        for (effect in activeEffects.values) {
            updateEffect(effect)
        }
    }

    private fun updateEffect(
        effect: DynamicsProcessing
    ) {

        try {

            /*
             * 32 bandas PEQ reales.
             */
            for (channel in 0 until CHANNEL_COUNT) {

                for (band in 0 until TOTAL_PEQ_BANDS) {

                    val gain =
                        if (eqEnabled) {
                            currentBandGains[band]
                        } else {
                            0f
                        }

                    val parameters =
                        effect.getPreEqBand(
                            channel,
                            band
                        )

                    parameters.gain =
                        gain.coerceIn(
                            MIN_GAIN,
                            MAX_GAIN
                        )

                    effect.setPreEqBand(
                        channel,
                        band,
                        parameters
                    )
                }
            }

            /*
             * MDRC básico.
             *
             * No dejamos que el threshold extremo
             * silencie accidentalmente la señal.
             */
            val safeThreshold =
                mdrcThreshold.coerceIn(
                    -60f,
                    0f
                )

            for (channel in 0 until CHANNEL_COUNT) {

                val mbc =
                    effect.getMbcBand(
                        channel,
                        0
                    )

                mbc.threshold =
                    safeThreshold

                effect.setMbcBand(
                    channel,
                    0,
                    mbc
                )
            }

            effect.limiterEnabled =
                limiterEnabled

            effect.enabled = true

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Error actualizando DynamicsProcessing",
                e
            )
        }
    }

    private fun setBandGain(
        index: Int,
        gain: Float
    ) {

        if (index !in 0 until TOTAL_PEQ_BANDS) {
            return
        }

        currentBandGains[index] =
            gain.coerceIn(
                MIN_GAIN,
                MAX_GAIN
            )

        for (effect in activeEffects.values) {

            try {

                for (channel in 0 until CHANNEL_COUNT) {

                    val band =
                        effect.getPreEqBand(
                            channel,
                            index
                        )

                    band.gain =
                        currentBandGains[index]

                    effect.setPreEqBand(
                        channel,
                        index,
                        band
                    )
                }

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "Error aplicando banda $index",
                    e
                )
            }
        }
    }

    private fun setAllBandGains(
        gains: FloatArray
    ) {

        val count =
            minOf(
                gains.size,
                TOTAL_PEQ_BANDS
            )

        for (i in 0 until count) {

            currentBandGains[i] =
                gains[i].coerceIn(
                    MIN_GAIN,
                    MAX_GAIN
                )
        }

        updateAllEffects()
    }

    private fun acquireWakeLock() {

        try {

            val powerManager =
                getSystemService(
                    POWER_SERVICE
                ) as PowerManager

            wakeLock =
                powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "$packageName:WaveEQ"
                )

            wakeLock?.acquire()

        } catch (e: Exception) {

            Log.e(
                TAG,
                "No se pudo adquirir WakeLock",
                e
            )
        }
    }

    private fun createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val manager =
                getSystemService(
                    NotificationManager::class.java
                )

            val channel =
                NotificationChannel(
                    NOTIFICATION_CHANNEL,
                    "WaveEQ Audio DSP",
                    NotificationManager.IMPORTANCE_LOW
                )

            manager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundService() {

        val notification =
            Notification.Builder(
                this,
                NOTIFICATION_CHANNEL
            )
                .setContentTitle("WaveEQ")
                .setContentText(
                    "Procesador DSP de audio activo"
                )
                .setSmallIcon(
                    android.R.drawable.ic_media_play
                )
                .setOngoing(true)
                .build()

        if (Build.VERSION.SDK_INT >= 29) {

            startForeground(
                1001,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )

        } else {

            startForeground(
                1001,
                notification
            )
        }
    }

    override fun onDestroy() {

        for (effect in activeEffects.values) {

            try {
                effect.release()
            } catch (_: Exception) {
            }
        }

        activeEffects.clear()
        sessionPackages.clear()

        try {
            wakeLock?.release()
        } catch (_: Exception) {
        }

        wakeLock = null

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
