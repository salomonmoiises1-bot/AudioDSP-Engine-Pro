package com.sbz.dsp.model

/** Complete user-facing DSP configuration. */
data class DspConfig(
    val isEnabled: Boolean = true,
    val preGainDb: Float = 0f,
    val bassBoostDb: Float = 0f,
    val toneBassDb: Float = 0f,
    val toneMidDb: Float = 0f,
    val toneTrebleDb: Float = 0f,
    val eqGainsDb: List<Float> = List(32) { 0f },
    val mdrcEnabled: Boolean = false,
    val mdrcBands: List<MdrcBandConfig> = defaultMdrcBands(),
    val autoGainEnabled: Boolean = true,
    val autoGainTargetDb: Float = -1.0f,
    val spatialEnabled: Boolean = false,
    val spatialStrength: Float = 0f,
    val masterGainDb: Float = 0f,
    val balance: Float = 0f,
    val limiterEnabled: Boolean = true,
    val limiterCeilingDb: Float = -0.2f
) {
    fun normalized(): DspConfig = copy(
        preGainDb = preGainDb.coerceIn(-12f, 12f),
        bassBoostDb = bassBoostDb.coerceIn(-12f, 12f),
        toneBassDb = toneBassDb.coerceIn(-12f, 12f),
        toneMidDb = toneMidDb.coerceIn(-12f, 12f),
        toneTrebleDb = toneTrebleDb.coerceIn(-12f, 12f),
        eqGainsDb = eqGainsDb.take(32).map { (kotlin.math.round(it * 2f) / 2f).coerceIn(-15f, 15f) }.let { it + List(32 - it.size) { 0f } },
        mdrcBands = mdrcBands.take(4),
        spatialStrength = spatialStrength.coerceIn(0f, 1f),
        masterGainDb = masterGainDb.coerceIn(-24f, 12f),
        balance = balance.coerceIn(-1f, 1f),
        limiterCeilingDb = limiterCeilingDb.coerceIn(-6f, 0f)
    )

    companion object {
        val EQ_FREQUENCIES_HZ = floatArrayOf(
            16f, 20f, 25f, 31.5f, 40f, 50f, 63f, 80f,
            100f, 125f, 160f, 200f, 250f, 315f, 400f, 500f,
            630f, 800f, 1000f, 1250f, 1600f, 2000f, 2500f, 3150f,
            4000f, 5000f, 6300f, 8000f, 10000f, 12500f, 16000f, 20000f
        )

        fun defaultMdrcBands() = listOf(
            MdrcBandConfig(20f, 160f),
            MdrcBandConfig(160f, 800f),
            MdrcBandConfig(800f, 4000f),
            MdrcBandConfig(4000f, 20000f)
        )
    }
}

data class MdrcBandConfig(
    val lowHz: Float,
    val highHz: Float,
    val thresholdDb: Float = -18f,
    val ratio: Float = 2.5f,
    val attackMs: Float = 12f,
    val releaseMs: Float = 140f,
    val makeupDb: Float = 0f
)
