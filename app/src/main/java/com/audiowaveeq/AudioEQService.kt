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

class AudioEQService : Service() {

    companion object {
        private const val TAG = "WaveEQ_AudioEQService"

        const val CHANNEL_ID = "waveeq_audio_engine_channel"
        const val NOTIFICATION_ID = 40401

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

        const val LIMITER_ATTACK_TIME_MS = 1.0f
        const val LIMITER_RELEASE_TIME_MS = 60.0f
        const val LIMITER_RATIO = 10.0f
        const val LIMITER_THRESHOLD_DB = -0.5f
        const val LIMITER_POST_GAIN_DB = 0.0f

        val EQ_FREQUENCIES = floatArrayOf(
            20.0f,
            25.0f,
            31.5f,
            40.0f,
            50.0f,
            63.0f,
            80.0f,
            100.0f,
            125.0f,
            160.0f,
            200.0f,
            250.0f,
            315.0f,
            400.0f,
            500.0f,
            630.0f,
            800.0f,
            1000.0f,
            1250.0f,
            1600.0f,
            2000.0f,
            2500.0f,
            3150.0f,
            4000.0f,
            5000.0f,
            6300.0f,
            8000.0f,
            10000.0f,
            12500.0f,
            16000.0f,
            18000.0f,
            20000.0f
        )
    }

    private val binder = LocalBinder()
    private var wakeLock: PowerManager.WakeLock? = null

    private val activeEffects =
        ConcurrentHashMap<Int, DynamicsProcessing>()

    private val currentBandGains =
        FloatArray(TOTAL_PEQ_BANDS) { 0.0f }

    private var isGlobalEQActive = true
    private var isLimiterActive = true

    private var isMDRCActive = true

    private val mdrcThresholds =
        floatArrayOf(-14.0f, -16.0f, -18.0f)

    private val mdrcRatios =
        floatArrayOf(2.5f, 2.0f, 2.2f)

    private val mdrcGains =
        floatArrayOf(1.0f, 0.0f, 0.5f)

    private var isDEQActive = true
    private var deqSensitivity = 1.0f

    inner class LocalBinder : Binder() {
        fun getService(): AudioEQService = this@AudioEQService
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()

        Log.i(
            TAG,
            "Iniciando AudioEQService Android ${Build.VERSION.RELEASE} " +
                    "(API ${Build.VERSION.SDK_INT})"
        )

        acquireWakeLock()
        createNotificationChannel()
        promoteToForegroundService()

        attachOrUpdateSession(
            GLOBAL_AUDIO_SESSION_ID,
            "android_global_mix"
        )
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        promoteToForegroundService()

        if (intent != null) {

            val action = intent.action

            val sessionId = intent.getIntExtra(
                EXTRA_SESSION_ID,
                GLOBAL_AUDIO_SESSION_ID
            )

            val callingPackage =
                intent.getStringExtra(EXTRA_CALLING_PACKAGE)
                    ?: "unknown"

            Log.d(
                TAG,
                "Comando recibido: $action " +
                        "sesión=$sessionId paquete=$callingPackage"
            )

            when (action) {

                ACTION_OPEN_SESSION,
                ACTION_ENSURE_ACTIVE,
                ACTION_START_SERVICE,
                ACTION_BOOT_START -> {
                    attachOrUpdateSession(
                        sessionId,
                        callingPackage
                    )
                }

                ACTION_CLOSE_SESSION -> {
                    detachSession(sessionId)
                }

                ACTION_SET_BAND_GAIN -> {

                    val bandIndex =
                        intent.getIntExtra(EXTRA_BAND_INDEX, -1)

                    val gain =
                        intent.getFloatExtra(EXTRA_BAND_GAIN, 0.0f)

                    if (bandIndex in 0 until TOTAL_PEQ_BANDS) {
                        setBandGain(
                            bandIndex,
                            gain
                        )
                    }
                }

                ACTION_SET_ALL_BANDS -> {

                    val gains =
                        intent.getFloatArrayExtra(EXTRA_ALL_GAINS)

                    if (
                        gains != null &&
                        gains.size == TOTAL_PEQ_BANDS
                    ) {
                        setAllBandGains(gains)
                    }
                }

                ACTION_SET_LIMITER_ENABLED -> {

                    val enabled =
                        intent.getBooleanExtra(
                            EXTRA_LIMITER_ENABLED,
                            true
                        )

                    setLimiterEnabled(enabled)
                }

                ACTION_TOGGLE_EQ -> {

                    val enabled =
                        intent.getBooleanExtra(
                            EXTRA_EQ_ENABLED,
                            true
                        )

                    toggleEQ(enabled)
                }

                ACTION_SET_MDRC_THRESHOLD -> {

                    val band =
                        intent.getIntExtra(
                            EXTRA_MDRC_BAND,
                            0
                        )

                    val threshold =
                        intent.getFloatExtra(
                            EXTRA_MDRC_THRESHOLD,
                            -12.0f
                        )

                    setMDRCThreshold(
                        band,
                        threshold
                    )
                }
            }

        } else {

            Log.d(
                TAG,
                "Servicio reiniciado por START_STICKY"
            )

            attachOrUpdateSession(
                GLOBAL_AUDIO_SESSION_ID,
                "system_rebound"
            )
        }

        return START_STICKY
    }

    private fun promoteToForegroundService() {

        val notification =
            buildOngoingNotification()

        try {

            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.UPSIDE_DOWN_CAKE
            ) {

                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
                )

            } else if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.Q
            ) {

                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )

            } else {

                startForeground(
                    NOTIFICATION_ID,
                    notification
                )
            }

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Error al iniciar Foreground Service: ${e.message}",
                e
            )
        }
    }

    private fun buildOngoingNotification(): Notification {

        val launchIntent =
            packageManager.getLaunchIntentForPackage(packageName)

        val pendingIntent =
            if (launchIntent != null) {

                PendingIntent.getActivity(
                    this,
                    0,
                    launchIntent,
                    PendingIntent.FLAG_IMMUTABLE or
                            PendingIntent.FLAG_UPDATE_CURRENT
                )

            } else {
                null
            }

        val builder =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

                Notification.Builder(
                    this,
                    CHANNEL_ID
                )

            } else {

                @Suppress("DEPRECATION")
                Notification.Builder(this)
            }

        val activeSessionCount =
            activeEffects.size

        return builder
            .setContentTitle(
                "WaveEQ - Procesamiento de Audio Global"
            )
            .setContentText(
                "Motor activo • $activeSessionCount sesión(es) • 32 Bandas PEQ"
            )
            .setSmallIcon(
                android.R.drawable.ic_media_play
            )
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setCategory(
                Notification.CATEGORY_SERVICE
            )
            .setVisibility(
                Notification.VISIBILITY_PUBLIC
            )
            .build()
    }

    private fun createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    "Motor de Audio WaveEQ",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {

                    description =
                        "Procesamiento de audio WaveEQ"

                    setShowBadge(false)

                    lockscreenVisibility =
                        Notification.VISIBILITY_PUBLIC
                }

            val manager =
                getSystemService(
                    Context.NOTIFICATION_SERVICE
                ) as NotificationManager

            manager.createNotificationChannel(channel)
        }
    }

    private fun createDynamicsProcessingConfig():
            DynamicsProcessing.Config {

        val channelCount = 2

        val builder =
            DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                channelCount,
                true,
                TOTAL_PEQ_BANDS,
                true,
                3,
                true,
                3,
                true
            )

        /*
         * 32 bandas PEQ.
         *
         * Las frecuencias se mantienen explícitas y en el mismo
         * orden que utiliza la interfaz.
         */

        for (band in 0 until TOTAL_PEQ_BANDS) {

            val eqBand =
                DynamicsProcessing.EqBand(
                    true,
                    EQ_FREQUENCIES[band],
                    currentBandGains[band]
                )

            builder.setPreEqBand(
                0,
                band,
                eqBand
            )

            builder.setPreEqBand(
                1,
                band,
                DynamicsProcessing.EqBand(
                    true,
                    EQ_FREQUENCIES[band],
                    currentBandGains[band]
                )
            )
        }

        val mbcBass =
            DynamicsProcessing.MbcBand(
                isMDRCActive,
                200.0f,
                15.0f,
                100.0f,
                mdrcRatios[0],
                mdrcThresholds[0],
                4.0f,
                -90.0f,
                1.0f,
                0.0f,
                mdrcGains[0]
            )

        builder.setMbcBand(
            0,
            0,
            mbcBass
        )

        builder.setMbcBand(
            1,
            0,
            mbcBass
        )

        val mbcMids =
            DynamicsProcessing.MbcBand(
                isMDRCActive,
                3000.0f,
                20.0f,
                80.0f,
                mdrcRatios[1],
                mdrcThresholds[1],
                3.0f,
                -90.0f,
                1.0f,
                0.0f,
                mdrcGains[1]
            )

        builder.setMbcBand(
            0,
            1,
            mbcMids
        )

        builder.setMbcBand(
            1,
            1,
            mbcMids
        )

        val mbcHighs =
            DynamicsProcessing.MbcBand(
                isMDRCActive,
                20000.0f,
                10.0f,
                60.0f,
                mdrcRatios[2],
                mdrcThresholds[2],
                2.0f,
                -90.0f,
                1.0f,
                0.0f,
                mdrcGains[2]
            )

        builder.setMbcBand(
            0,
            2,
            mbcHighs
        )

        builder.setMbcBand(
            1,
            2,
            mbcHighs
        )

        builder.setPostEqBand(
            0,
            0,
            DynamicsProcessing.EqBand(
                isDEQActive,
                100.0f,
                0.0f
            )
        )

        builder.setPostEqBand(
            1,
            0,
            DynamicsProcessing.EqBand(
                isDEQActive,
                100.0f,
                0.0f
            )
        )

        builder.setPostEqBand(
            0,
            1,
            DynamicsProcessing.EqBand(
                isDEQActive,
                1000.0f,
                0.0f
            )
        )

        builder.setPostEqBand(
            1,
            1,
            DynamicsProcessing.EqBand(
                isDEQActive,
                1000.0f,
                0.0f
            )
        )

        builder.setPostEqBand(
            0,
            2,
            DynamicsProcessing.EqBand(
                isDEQActive,
                10000.0f,
                0.0f
            )
        )

        builder.setPostEqBand(
            1,
            2,
            DynamicsProcessing.EqBand(
                isDEQActive,
                10000.0f,
                0.0f
            )
        )

        val limiter =
            DynamicsProcessing.Limiter(
                true,
                isLimiterActive,
                0,
                LIMITER_ATTACK_TIME_MS,
                LIMITER_RELEASE_TIME_MS,
                LIMITER_RATIO,
                LIMITER_THRESHOLD_DB,
                LIMITER_POST_GAIN_DB
            )

        builder.setLimiterInUse(true)
        builder.setLimiterAllChannelsTo(limiter)

        return builder.build()
    }

    @Synchronized
    private fun attachOrUpdateSession(
        sessionId: Int,
        callingPackage: String
    ) {

        try {

            if (activeEffects.containsKey(sessionId)) {

                val existing =
                    activeEffects[sessionId]

                if (
                    existing != null &&
                    !existing.enabled
                ) {
                    existing.enabled = true
                }

                return
            }

            Log.i(
                TAG,
                "Creando DynamicsProcessing para sesión " +
                        "$sessionId ($callingPackage)"
            )

            val config =
                createDynamicsProcessingConfig()

            val effect =
                DynamicsProcessing(
                    0,
                    sessionId,
                    config
                )

            effect.enabled = true

            activeEffects[sessionId] =
                effect

            updateNotification()

            Log.i(
                TAG,
                "DynamicsProcessing activo en sesión $sessionId"
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Error al inicializar sesión $sessionId: ${e.message}",
                e
            )
        }
    }

    @Synchronized
    private fun detachSession(
        sessionId: Int
    ) {

        val effect =
            activeEffects.remove(sessionId)

        if (effect != null) {

            try {

                effect.enabled = false
                effect.release()

            } catch (e: Exception) {

                Log.w(
                    TAG,
                    "Error liberando sesión $sessionId: ${e.message}"
                )
            }

            updateNotification()
        }
    }

    /*
     * ============================================================
     * 32 BANDAS — CAMBIO PRINCIPAL
     * ============================================================
     *
     * En lugar de modificar L y R por separado mediante:
     *
     * get -> modificar -> set
     *
     * usamos directamente la operación oficial:
     *
     * setPreEqBandAllChannelsTo()
     *
     * Así la misma EqBand se aplica a todos los canales.
     */

    @Synchronized
    fun setBandGain(
        bandIndex: Int,
        gainDb: Float
    ) {

        if (
            bandIndex !in
            0 until TOTAL_PEQ_BANDS
        ) {
            Log.e(
                TAG,
                "Índice de banda fuera de rango: $bandIndex"
            )
            return
        }

        val safeGain =
            gainDb.coerceIn(
                -15.0f,
                15.0f
            )

        currentBandGains[bandIndex] =
            safeGain

        activeEffects.forEach { (sessionId, effect) ->

            try {

                val band =
                    DynamicsProcessing.EqBand(
                        true,
                        EQ_FREQUENCIES[bandIndex],
                        safeGain
                    )

                effect.setPreEqBandAllChannelsTo(
                    bandIndex,
                    band
                )

                Log.d(
                    TAG,
                    "EQ ${EQ_FREQUENCIES[bandIndex]} Hz = " +
                            "$safeGain dB / sesión $sessionId"
                )

            } catch (e: Exception) {

                Log.w(
                    TAG,
                    "Fallo banda $bandIndex " +
                            "en sesión $sessionId: ${e.message}",
                    e
                )
            }
        }
    }

    @Synchronized
    fun setAllBandGains(
        gains: FloatArray
    ) {

        if (
            gains.size != TOTAL_PEQ_BANDS
        ) {
            Log.e(
                TAG,
                "Se esperaban 32 ganancias, recibidas ${gains.size}"
            )
            return
        }

        for (i in 0 until TOTAL_PEQ_BANDS) {

            currentBandGains[i] =
                gains[i].coerceIn(
                    -15.0f,
                    15.0f
                )
        }

        activeEffects.forEach { (sessionId, effect) ->

            try {

                for (
                    bandIndex
                    in 0 until TOTAL_PEQ_BANDS
                ) {

                    val band =
                        DynamicsProcessing.EqBand(
                            true,
                            EQ_FREQUENCIES[bandIndex],
                            currentBandGains[bandIndex]
                        )

                    effect.setPreEqBandAllChannelsTo(
                        bandIndex,
                        band
                    )
                }

                Log.d(
                    TAG,
                    "32 bandas aplicadas a todos los canales " +
                            "en sesión $sessionId"
                )

            } catch (e: Exception) {

                Log.w(
                    TAG,
                    "Fallo aplicando 32 bandas " +
                            "en sesión $sessionId: ${e.message}",
                    e
                )
            }
        }
    }

    @Synchronized
    fun setLimiterEnabled(
        enabled: Boolean
    ) {

        isLimiterActive =
            enabled

        activeEffects.forEach { (sessionId, effect) ->

            try {

                val left =
                    effect.getLimiterByChannelIndex(0)

                left.enabled = enabled

                effect.setLimiterByChannelIndex(
                    0,
                    left
                )

                val right =
                    effect.getLimiterByChannelIndex(1)

                right.enabled = enabled

                effect.setLimiterByChannelIndex(
                    1,
                    right
                )

            } catch (e: Exception) {

                Log.w(
                    TAG,
                    "Error limitador sesión $sessionId: ${e.message}"
                )
            }
        }
    }

    @Synchronized
    fun toggleEQ(
        enabled: Boolean
    ) {

        isGlobalEQActive =
            enabled

        activeEffects.forEach { (_, effect) ->

            try {
                effect.enabled = enabled
            } catch (e: Exception) {
                Log.w(
                    TAG,
                    "Error cambiando estado EQ: ${e.message}"
                )
            }
        }

        updateNotification()
    }

    fun isEQActive(): Boolean =
        isGlobalEQActive

    fun isLimiterActive(): Boolean =
        isLimiterActive

    fun getAllBandGains(): FloatArray =
        currentBandGains.clone()

    @Synchronized
    fun setMDRCEnabled(
        enabled: Boolean
    ) {

        isMDRCActive =
            enabled

        activeEffects.forEach { (sessionId, effect) ->

            try {

                for (b in 0 until 3) {

                    if (
                        effect.mbcBandCount > b
                    ) {

                        val left =
                            effect.getMbcBandByChannelIndex(
                                0,
                                b
                            )

                        left.enabled =
                            enabled

                        effect.setMbcBandByChannelIndex(
                            0,
                            b,
                            left
                        )

                        val right =
                            effect.getMbcBandByChannelIndex(
                                1,
                                b
                            )

                        right.enabled =
                            enabled

                        effect.setMbcBandByChannelIndex(
                            1,
                            b,
                            right
                        )
                    }
                }

            } catch (e: Exception) {

                Log.w(
                    TAG,
                    "Error MDRC sesión $sessionId: ${e.message}"
                )
            }
        }
    }

    fun isMDRCEnabled(): Boolean =
        isMDRCActive

    @Synchronized
    fun setMDRCParameters(
        band: Int,
        threshold: Float,
        ratio: Float,
        postGain: Float
    ) {

        if (band !in 0..2) return

        mdrcThresholds[band] =
            threshold

        mdrcRatios[band] =
            ratio

        mdrcGains[band] =
            postGain

        activeEffects.forEach { (_, effect) ->

            try {

                if (
                    effect.mbcBandCount > band
                ) {

                    val left =
                        effect.getMbcBandByChannelIndex(
                            0,
                            band
                        )

                    left.threshold =
                        threshold

                    left.ratio =
                        ratio

                    left.postGain =
                        postGain

                    effect.setMbcBandByChannelIndex(
                        0,
                        band,
                        left
                    )

                    val right =
                        effect.getMbcBandByChannelIndex(
                            1,
                            band
                        )

                    right.threshold =
                        threshold

                    right.ratio =
                        ratio

                    right.postGain =
                        postGain

                    effect.setMbcBandByChannelIndex(
                        1,
                        band,
                        right
                    )
                }

            } catch (e: Exception) {

                Log.w(
                    TAG,
                    "Error parámetros MDRC: ${e.message}"
                )
            }
        }
    }

    fun getMDRCThreshold(
        band: Int
    ): Float =
        if (band in 0..2)
            mdrcThresholds[band]
        else
            -12.0f

    fun getMDRCRatio(
        band: Int
    ): Float =
        if (band in 0..2)
            mdrcRatios[band]
        else
            2.0f

    fun getMDRCGain(
        band: Int
    ): Float =
        if (band in 0..2)
            mdrcGains[band]
        else
            0.0f

    @Synchronized
    fun setDEQEnabled(
        enabled: Boolean
    ) {

        isDEQActive =
            enabled

        activeEffects.forEach { (_, effect) ->

            try {

                for (b in 0 until 3) {

                    if (
                        effect.postEqBandCount > b
                    ) {

                        val left =
                            effect.getPostEqBandByChannelIndex(
                                0,
                                b
                            )

                        left.enabled =
                            enabled

                        effect.setPostEqBandByChannelIndex(
                            0,
                            b,
                            left
                        )

                        val right =
                            effect.getPostEqBandByChannelIndex(
                                1,
                                b
                            )

                        right.enabled =
                            enabled

                        effect.setPostEqBandByChannelIndex(
                            1,
                            b,
                            right
                        )
                    }
                }

            } catch (e: Exception) {

                Log.w(
                    TAG,
                    "Error DEQ: ${e.message}"
                )
            }
        }
    }

    fun isDEQEnabled(): Boolean =
        isDEQActive

    fun setDEQSensitivity(
        sens: Float
    ) {
        deqSensitivity =
            sens
    }

    fun getDEQSensitivity(): Float =
        deqSensitivity

    @Synchronized
    fun setMDRCThreshold(
        band: Int,
        threshold: Float
    ) {

        if (band !in 0..2) return

        mdrcThresholds[band] =
            threshold

        activeEffects.forEach { (sessionId, effect) ->

            try {

                if (
                    effect.mbcBandCount > band
                ) {

                    val left =
                        effect.getMbcBandByChannelIndex(
                            0,
                            band
                        )

                    left.threshold =
                        threshold

                    effect.setMbcBandByChannelIndex(
                        0,
                        band,
                        left
                    )

                    val right =
                        effect.getMbcBandByChannelIndex(
                            1,
                            band
                        )

                    right.threshold =
                        threshold

                    effect.setMbcBandByChannelIndex(
                        1,
                        band,
                        right
                    )

                    Log.d(
                        TAG,
                        "MDRC $band = $threshold dB"
                    )
                }

            } catch (e: Exception) {

                Log.w(
                    TAG,
                    "Error MDRC: ${e.message}"
                )
            }
        }
    }

    fun getCurrentBandGains(): FloatArray =
        currentBandGains.clone()

    fun isLimiterEnabled(): Boolean =
        isLimiterActive

    fun getActiveSessionCount(): Int =
        activeEffects.size

    private fun updateNotification() {

        val manager =
            getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager

        manager.notify(
            NOTIFICATION_ID,
            buildOngoingNotification()
        )
    }

    private fun acquireWakeLock() {

        try {

            val powerManager =
                getSystemService(
                    Context.POWER_SERVICE
                ) as PowerManager

            wakeLock =
                powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "WaveEQ:AudioEngineWakeLock"
                ).apply {

                    setReferenceCounted(false)

                    acquire(
                        12 * 60 * 60 * 1000L
                    )
                }

        } catch (e: Exception) {

            Log.w(
                TAG,
                "No se pudo adquirir WakeLock: ${e.message}"
            )
        }
    }

    private fun releaseWakeLock() {

        try {

            wakeLock?.let {

                if (it.isHeld) {
                    it.release()
                }
            }

        } catch (e: Exception) {

            Log.w(
                TAG,
                "Error liberando WakeLock: ${e.message}"
            )
        }
    }

    override fun onDestroy() {

        Log.w(
            TAG,
            "Destruyendo AudioEQService"
        )

        activeEffects.forEach { (_, effect) ->

            try {

                effect.enabled = false
                effect.release()

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "Error liberando DynamicsProcessing: ${e.message}"
                )
            }
        }

        activeEffects.clear()

        releaseWakeLock()

        super.onDestroy()
    }
}
