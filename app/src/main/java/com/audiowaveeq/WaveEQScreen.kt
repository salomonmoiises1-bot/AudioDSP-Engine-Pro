package com.audiowaveeq

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.sin

/**
 * WaveEQScreen - Interfaz Gráfica 100% Nativa en Jetpack Compose
 * Incluye:
 * - 32 Bandas PEQ ISO
 * - MDRC (Control de Rango Dinámico Multibanda en 3 bandas independientes)
 * - Limitador Dinámico Anti-Clipping
 * - DEQ (Dynamic EQ adaptativo en tiempo real)
 */

// Las 32 frecuencias estándar ISO para ecualización paramétrica de precisión
val ISO_FREQUENCIES = listOf(
    "20Hz", "25Hz", "31.5Hz", "40Hz", "50Hz", "63Hz", "80Hz", "100Hz",
    "125Hz", "160Hz", "200Hz", "250Hz", "315Hz", "400Hz", "500Hz", "630Hz",
    "800Hz", "1kHz", "1.25k", "1.6k", "2kHz", "2.5k", "3.15k", "4kHz",
    "5kHz", "6.3k", "8kHz", "10k", "12.5k", "16k", "18k", "20k"
)

// Presets de ecualización profesionales de fábrica
val FACTORY_PRESETS = mapOf(
    "Flat / Neutral" to FloatArray(32) { 0.0f },
    "Harman Target" to floatArrayOf(
        4.8f, 4.6f, 4.2f, 3.8f, 3.2f, 2.5f, 1.8f, 1.2f,
        0.5f, 0.0f, -0.2f, -0.4f, -0.5f, -0.2f, 0.0f, 0.5f,
        1.0f, 1.8f, 2.4f, 2.8f, 3.2f, 2.8f, 2.2f, 1.5f,
        1.0f, 0.8f, 0.5f, 0.2f, 0.0f, -0.5f, -1.0f, -1.5f
    ),
    "Bass Boost" to floatArrayOf(
        7.0f, 6.8f, 6.5f, 6.0f, 5.2f, 4.5f, 3.5f, 2.5f,
        1.5f, 0.8f, 0.2f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f,
        0.0f, 0.0f, 0.0f, 0.0f, 0.2f, 0.5f, 0.8f, 1.0f,
        1.2f, 1.5f, 1.8f, 2.0f, 1.5f, 1.0f, 0.5f, 0.0f
    ),
    "Vocal Clarity" to floatArrayOf(
        -3.0f, -2.5f, -2.0f, -1.5f, -1.0f, -0.5f, 0.0f, 0.0f,
        0.2f, 0.5f, 0.8f, 1.2f, 1.8f, 2.4f, 3.2f, 3.8f,
        4.2f, 4.0f, 3.8f, 3.5f, 3.0f, 2.5f, 2.0f, 1.5f,
        1.0f, 0.5f, 0.0f, 0.0f, -0.5f, -1.0f, -1.5f, -2.0f
    ),
    "Electronic" to floatArrayOf(
        6.0f, 5.8f, 5.5f, 4.8f, 4.0f, 3.0f, 2.0f, 1.0f,
        0.0f, -0.5f, -1.0f, -1.5f, -1.2f, -0.8f, -0.5f, 0.0f,
        0.5f, 1.0f, 1.8f, 2.5f, 3.0f, 3.5f, 4.0f, 4.2f,
        4.0f, 3.5f, 3.0f, 2.5f, 2.0f, 1.5f, 1.0f, 0.5f
    ),
    "Studio Ref" to floatArrayOf(
        0.2f, 0.2f, 0.1f, 0.0f, 0.0f, -0.1f, -0.1f, 0.0f,
        0.0f, 0.1f, 0.0f, 0.0f, -0.1f, 0.0f, 0.0f, 0.1f,
        0.0f, 0.0f, 0.0f, 0.1f, 0.0f, 0.0f, -0.1f, 0.0f,
        0.0f, 0.1f, 0.0f, 0.0f, 0.0f, -0.1f, 0.0f, 0.0f
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaveEQScreen(
    service: AudioEQService?,
    modifier: Modifier = Modifier
) {
    // Estados reactivos sincronizados con el servicio
    var isEQEnabled by remember { mutableStateOf(service?.isEQActive() ?: true) }
    var isLimiterEnabled by remember { mutableStateOf(service?.isLimiterActive() ?: true) }
    var bandGains by remember {
        mutableStateOf(service?.getAllBandGains() ?: FloatArray(32) { 0.0f })
    }
    var selectedPreset by remember { mutableStateOf("Flat / Neutral") }
    var selectedBandIndex by remember { mutableStateOf<Int?>(16) } // Default 1kHz

    // Estados para MDRC (Control de Rango Dinámico Multibanda)
    var isMDRCEnabled by remember { mutableStateOf(service?.isMDRCEnabled() ?: true) }
    var mdrcBandIndex by remember { mutableStateOf(0) } // 0: Graves, 1: Medios, 2: Agudos
    var mdrcThresholds by remember { mutableStateOf(floatArrayOf(-14.0f, -16.0f, -18.0f)) }
    var mdrcRatios by remember { mutableStateOf(floatArrayOf(2.5f, 2.0f, 2.2f)) }
    var mdrcGains by remember { mutableStateOf(floatArrayOf(1.0f, 0.0f, 0.5f)) }

    // Estados para DEQ (Dynamic EQ)
    var isDEQEnabled by remember { mutableStateOf(service?.isDEQEnabled() ?: true) }
    var deqSensitivity by remember { mutableStateOf(service?.getDEQSensitivity() ?: 1.0f) }

    val scrollState = rememberScrollState()

    // Sincronizar estado inicial al conectarse el servicio
    LaunchedEffect(service) {
        if (service != null) {
            isEQEnabled = service.isEQActive()
            isLimiterEnabled = service.isLimiterActive()
            bandGains = service.getAllBandGains().clone()
            isMDRCEnabled = service.isMDRCEnabled()
            isDEQEnabled = service.isDEQEnabled()
            deqSensitivity = service.getDEQSensitivity()
            for (b in 0..2) {
                mdrcThresholds[b] = service.getMDRCThreshold(b)
                mdrcRatios[b] = service.getMDRCRatio(b)
                mdrcGains[b] = service.getMDRCGain(b)
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color(0xFF030712), // Slate 950
        topBar = {
            WaveEQTopBar(
                isEQEnabled = isEQEnabled,
                onToggleEQ = {
                    val nextState = !isEQEnabled
                    isEQEnabled = nextState
                    service?.toggleEQ(nextState)
                },
                onResetBands = {
                    val flatGains = FloatArray(32) { 0.0f }
                    bandGains = flatGains
                    selectedPreset = "Flat / Neutral"
                    service?.setAllBandGains(flatGains)
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Curva SPL en Tiempo Real
            FrequencyCurveCanvas(
                gains = bandGains,
                isEQEnabled = isEQEnabled,
                selectedBand = selectedBandIndex
            )

            // 2. Visualizador de Espectro FFT a 60 FPS
            SpectralVisualizerCanvas(
                gains = bandGains,
                isEQEnabled = isEQEnabled
            )

            // 3. Selector de Presets de Fábrica
            PresetsSelector(
                selectedPreset = selectedPreset,
                presets = FACTORY_PRESETS.keys.toList(),
                onSelectPreset = { presetName ->
                    selectedPreset = presetName
                    val presetValues = FACTORY_PRESETS[presetName] ?: FloatArray(32) { 0.0f }
                    bandGains = presetValues.clone()
                    service?.setAllBandGains(presetValues)
                }
            )

            // 4. Rack de Faders Paramétricos de 32 Bandas ISO
            BandsSliderRack(
                bandGains = bandGains,
                selectedBandIndex = selectedBandIndex,
                isEQEnabled = isEQEnabled,
                onBandGainChange = { index, newGain ->
                    val updated = bandGains.clone()
                    updated[index] = newGain
                    bandGains = updated
                    selectedPreset = "Personalizado"
                    service?.setBandGain(index, newGain)
                },
                onSelectBand = { index ->
                    selectedBandIndex = index
                }
            )

            // 5. MDRC (Control de Rango Dinámico Multibanda en 3 Bandas Independientes)
            MDRCCard(
                isEnabled = isMDRCEnabled,
                onToggle = {
                    val next = !isMDRCEnabled
                    isMDRCEnabled = next
                    service?.setMDRCEnabled(next)
                },
                selectedBand = mdrcBandIndex,
                onSelectBand = { mdrcBandIndex = it },
                threshold = mdrcThresholds[mdrcBandIndex],
                ratio = mdrcRatios[mdrcBandIndex],
                gain = mdrcGains[mdrcBandIndex],
                onThresholdChange = { newT ->
                    val updated = mdrcThresholds.clone()
                    updated[mdrcBandIndex] = newT
                    mdrcThresholds = updated
                    service?.setMDRCParameters(mdrcBandIndex, newT, mdrcRatios[mdrcBandIndex], mdrcGains[mdrcBandIndex])
                },
                onRatioChange = { newR ->
                    val updated = mdrcRatios.clone()
                    updated[mdrcBandIndex] = newR
                    mdrcRatios = updated
                    service?.setMDRCParameters(mdrcBandIndex, mdrcThresholds[mdrcBandIndex], newR, mdrcGains[mdrcBandIndex])
                },
                onGainChange = { newG ->
                    val updated = mdrcGains.clone()
                    updated[mdrcBandIndex] = newG
                    mdrcGains = updated
                    service?.setMDRCParameters(mdrcBandIndex, mdrcThresholds[mdrcBandIndex], mdrcRatios[mdrcBandIndex], newG)
                }
            )

            // 6. Limitador Dinámico Anti-Clipping y DEQ (Dynamic EQ)
            DynamicEQAndLimiterCard(
                isLimiterEnabled = isLimiterEnabled,
                onToggleLimiter = {
                    val nextLimiter = !isLimiterEnabled
                    isLimiterEnabled = nextLimiter
                    service?.setLimiterEnabled(nextLimiter)
                },
                isDEQEnabled = isDEQEnabled,
                onToggleDEQ = {
                    val nextDEQ = !isDEQEnabled
                    isDEQEnabled = nextDEQ
                    service?.setDEQEnabled(nextDEQ)
                },
                deqSensitivity = deqSensitivity,
                onDEQSensitivityChange = { newSens ->
                    deqSensitivity = newSens
                    service?.setDEQSensitivity(newSens)
                },
                maxGain = bandGains.maxOrNull() ?: 0.0f
            )

            // Pie de página con información técnica del motor
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "WaveEQ Nativo • Android 14+ (API 34)",
                    fontSize = 11.sp,
                    color = Color(0xFF6B7280),
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "DynamicsProcessing • MDRC + DEQ",
                    fontSize = 11.sp,
                    color = Color(0xFF06B6D4),
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

/**
 * Barra superior con botones de Control Maestro y Estado
 */
@Composable
fun WaveEQTopBar(
    isEQEnabled: Boolean,
    onToggleEQ: () -> Unit,
    onResetBands: () -> Unit
) {
    Surface(
        color = Color(0xFF0B0F19),
        tonalElevation = 4.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1F2937))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF06B6D4), Color(0xFF6366F1), Color(0xFF8B5CF6))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "WaveEQ",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Text(
                        text = "WaveEQ",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = if (isEQEnabled) "MOTOR ACTIVO (32 BANDAS + MDRC + DEQ)" else "BYPASS GLOBAL DESACTIVADO",
                        color = if (isEQEnabled) Color(0xFF34D399) else Color(0xFF94A3B8),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onResetBands,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1E293B))
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Restablecer a Plano",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(18.dp)
                    )
                }

                Button(
                    onClick = onToggleEQ,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isEQEnabled) Color(0xFF059669) else Color(0xFF334155),
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PowerSettingsNew,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isEQEnabled) "ACTIVO" else "BYPASS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Selector horizontal de presets rápidos
 */
@Composable
fun PresetsSelector(
    selectedPreset: String,
    presets: List<String>,
    onSelectPreset: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        presets.forEach { preset ->
            val isSelected = selectedPreset == preset
            FilterChip(
                selected = isSelected,
                onClick = { onSelectPreset(preset) },
                label = { Text(text = preset, fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFF083344),
                    selectedLabelColor = Color(0xFF38BDF8),
                    containerColor = Color(0xFF0F172A),
                    labelColor = Color(0xFF94A3B8)
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isSelected,
                    selectedBorderColor = Color(0xFF0284C7),
                    borderColor = Color(0xFF1E293B)
                ),
                shape = RoundedCornerShape(8.dp)
            )
        }
    }
}

/**
 * Renderizador de Curva de Respuesta de Frecuencia SPL en Compose Canvas
 */
@Composable
fun FrequencyCurveCanvas(
    gains: FloatArray,
    isEQEnabled: Boolean,
    selectedBand: Int?,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp),
        color = Color(0xFF090D16),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val midY = h / 2f

                // Líneas de cuadrícula horizontal para decibelios (-12, -6, 0, +6, +12 dB)
                val gridDbs = listOf(-12f, -6f, 0f, 6f, 12f)
                gridDbs.forEach { db ->
                    val y = midY - (db / 15f) * (h * 0.45f)
                    drawLine(
                        color = if (db == 0f) Color(0xFF334155) else Color(0xFF1E293B),
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = if (db == 0f) 1.5f else 1f
                    )
                }

                // Trazado de la curva SPL suave
                val path = Path()
                val stepX = w / (gains.size - 1)

                val points = gains.mapIndexed { idx, gain ->
                    val effectiveGain = if (isEQEnabled) gain else 0f
                    val x = idx * stepX
                    val y = midY - (effectiveGain / 15f) * (h * 0.45f)
                    Offset(x, y)
                }

                if (points.isNotEmpty()) {
                    path.moveTo(points.first().x, points.first().y)
                    for (i in 0 until points.size - 1) {
                        val p0 = points[i]
                        val p1 = points[i + 1]
                        val cx = (p0.x + p1.x) / 2f
                        path.cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
                    }

                    // Relleno de degradado bajo la curva
                    val fillPath = Path().apply {
                        addPath(path)
                        lineTo(w, h)
                        lineTo(0f, h)
                        close()
                    }

                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                if (isEQEnabled) Color(0x3306B6D4) else Color(0x11EF4444),
                                Color.Transparent
                            )
                        )
                    )

                    // Línea curva principal
                    drawPath(
                        path = path,
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF06B6D4),
                                Color(0xFF6366F1),
                                Color(0xFFA855F7),
                                Color(0xFFEC4899)
                            )
                        ),
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Resaltar la banda seleccionada actualmente
                    if (selectedBand != null && selectedBand in points.indices) {
                        val selPt = points[selectedBand]
                        drawCircle(
                            color = Color(0xFF06B6D4),
                            radius = 6.dp.toPx(),
                            center = selPt
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 3.dp.toPx(),
                            center = selPt
                        )
                    }
                }
            }

            // Etiquetas dB en el lateral
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 8.dp)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "+15 dB", color = Color(0xFF64748B), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                Text(text = "0 dB", color = Color(0xFF94A3B8), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                Text(text = "-15 dB", color = Color(0xFF64748B), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

/**
 * Visualizador Espectral FFT a 60 FPS en Compose Canvas
 */
@Composable
fun SpectralVisualizerCanvas(
    gains: FloatArray,
    isEQEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    // Animación continua para simular reactividad espectral a 60 FPS
    val infiniteTransition = rememberInfiniteTransition(label = "fft_anim")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28318f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp),
        color = Color(0xFF090D16),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp)) {
            val barCount = gains.size
            val barSpacing = 2.dp.toPx()
            val totalSpacing = barSpacing * (barCount - 1)
            val barWidth = (size.width - totalSpacing) / barCount
            val maxH = size.height

            for (i in 0 until barCount) {
                val gain = if (isEQEnabled) gains[i] else 0f
                // Simulación suave de espectro musical modulado por las ganancias reales
                val wave1 = (sin(phase + i * 0.4f) + 1f) / 2f
                val wave2 = (sin(phase * 1.5f + i * 0.2f) + 1f) / 2f
                val baseLevel = (wave1 * 0.5f + wave2 * 0.5f)

                val gainMultiplier = ((gain + 15f) / 30f).coerceIn(0.2f, 1.2f)
                val barHeightFraction = (baseLevel * gainMultiplier).coerceIn(0.08f, 0.95f)
                val barHeight = maxH * barHeightFraction

                val left = i * (barWidth + barSpacing)
                val top = maxH - barHeight

                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFA855F7), // Morado Neón
                            Color(0xFF06B6D4), // Cyan
                            Color(0xFF3B82F6)  // Azul
                        ),
                        startY = top,
                        endY = maxH
                    ),
                    topLeft = Offset(left, top),
                    size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx())
                )
            }
        }
    }
}

/**
 * Rack de Faders Paramétricos de 32 Bandas ISO
 */
@Composable
fun BandsSliderRack(
    bandGains: FloatArray,
    selectedBandIndex: Int?,
    isEQEnabled: Boolean,
    onBandGainChange: (Int, Float) -> Unit,
    onSelectBand: (Int) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF0B0F19),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "RACK DE 32 BANDAS ISO",
                    color = Color(0xFFE2E8F0),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                if (selectedBandIndex != null) {
                    val gain = bandGains[selectedBandIndex]
                    val freq = ISO_FREQUENCIES[selectedBandIndex]
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(text = freq, color = Color(0xFF22D3EE), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = if (gain > 0) "+${String.format("%.1f", gain)} dB" else "${String.format("%.1f", gain)} dB",
                            color = if (gain > 0) Color(0xFF34D399) else if (gain < 0) Color(0xFFFBBF24) else Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Deslizadores Horizontales
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (i in 0 until bandGains.size) {
                    BandFaderItem(
                        index = i,
                        label = ISO_FREQUENCIES[i],
                        gain = bandGains[i],
                        isSelected = selectedBandIndex == i,
                        isEQEnabled = isEQEnabled,
                        onGainChange = { newGain -> onBandGainChange(i, newGain) },
                        onSelect = { onSelectBand(i) }
                    )
                }
            }
        }
    }
}

/**
 * Elemento Individual de Fader Vertical por Frecuencia
 */
@Composable
fun BandFaderItem(
    index: Int,
    label: String,
    gain: Float,
    isSelected: Boolean,
    isEQEnabled: Boolean,
    onGainChange: (Float) -> Unit,
    onSelect: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(42.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) Color(0xFF1E293B) else Color.Transparent)
            .clickable { onSelect() }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Valor de ganancia arriba
        Text(
            text = if (gain > 0) "+${gain.toInt()}" else "${gain.toInt()}",
            color = if (gain > 0) Color(0xFF34D399) else if (gain < 0) Color(0xFFFBBF24) else Color(0xFF64748B),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )

        // Fader deslizante vertical
        Box(
            modifier = Modifier
                .height(140.dp)
                .width(28.dp),
            contentAlignment = Alignment.Center
        ) {
            // Pista central
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF334155))
            )

            // Deslizador nativo
            Slider(
                value = gain,
                onValueChange = { onGainChange(it) },
                valueRange = -15f..15f,
                enabled = isEQEnabled,
                modifier = Modifier
                    .fillMaxHeight()
                    .width(140.dp)
                    .graphicsLayer {
                        rotationZ = 270f
                    },
                colors = SliderDefaults.colors(
                    thumbColor = if (isSelected) Color(0xFF22D3EE) else Color.White,
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent
                )
            )
        }

        // Etiqueta de frecuencia
        Text(
            text = label,
            color = if (isSelected) Color(0xFF22D3EE) else Color(0xFF94A3B8),
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

/**
 * MDRCCard: Control de Rango Dinámico Multibanda en 3 Bandas Independientes
 * - Graves (20 - 200 Hz)
 * - Medios (200 - 3000 Hz)
 * - Agudos (3000 - 20000 Hz)
 */
@Composable
fun MDRCCard(
    isEnabled: Boolean,
    onToggle: () -> Unit,
    selectedBand: Int,
    onSelectBand: (Int) -> Unit,
    threshold: Float,
    ratio: Float,
    gain: Float,
    onThresholdChange: (Float) -> Unit,
    onRatioChange: (Float) -> Unit,
    onGainChange: (Float) -> Unit
) {
    val bandNames = listOf("Graves (20-200Hz)", "Medios (200-3kHz)", "Agudos (3k-20kHz)")

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF0B0F19),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Encabezado MDRC
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Compress,
                        contentDescription = "MDRC",
                        tint = if (isEnabled) Color(0xFF6366F1) else Color(0xFF64748B),
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "MDRC (Compresión Multibanda)",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "3 Bandas de Frecuencia Independientes",
                            color = Color(0xFF94A3B8),
                            fontSize = 10.sp
                        )
                    }
                }

                Switch(
                    checked = isEnabled,
                    onCheckedChange = { onToggle() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF6366F1),
                        uncheckedThumbColor = Color(0xFF94A3B8),
                        uncheckedTrackColor = Color(0xFF1E293B)
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Selector de Banda (Graves / Medios / Agudos)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                bandNames.forEachIndexed { idx, name ->
                    val isSel = selectedBand == idx
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSel) Color(0xFF312E81) else Color(0xFF0F172A))
                            .border(1.dp, if (isSel) Color(0xFF6366F1) else Color(0xFF1E293B), RoundedCornerShape(6.dp))
                            .clickable { onSelectBand(idx) }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = name,
                            fontSize = 10.5.sp,
                            color = if (isSel) Color(0xFFA5B4FC) else Color(0xFF94A3B8),
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Sliders de Parámetros de la Banda Activa
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Slider Umbral (Threshold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Umbral (Threshold)", fontSize = 11.sp, color = Color(0xFF94A3B8))
                    Text(
                        text = "${String.format("%.1f", threshold)} dB",
                        fontSize = 11.sp,
                        color = Color(0xFF6366F1),
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Slider(
                    value = threshold,
                    onValueChange = onThresholdChange,
                    valueRange = -30f..0f,
                    enabled = isEnabled,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF818CF8),
                        activeTrackColor = Color(0xFF6366F1),
                        inactiveTrackColor = Color(0xFF1E293B)
                    )
                )

                // Slider Relación (Ratio)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Relación (Ratio)", fontSize = 11.sp, color = Color(0xFF94A3B8))
                    Text(
                        text = "${String.format("%.1f", ratio)}:1",
                        fontSize = 11.sp,
                        color = Color(0xFF6366F1),
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Slider(
                    value = ratio,
                    onValueChange = onRatioChange,
                    valueRange = 1f..8f,
                    enabled = isEnabled,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF818CF8),
                        activeTrackColor = Color(0xFF6366F1),
                        inactiveTrackColor = Color(0xFF1E293B)
                    )
                )

                // Slider Ganancia Compensatoria (PostGain)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Ganancia (PostGain)", fontSize = 11.sp, color = Color(0xFF94A3B8))
                    Text(
                        text = if (gain > 0) "+${String.format("%.1f", gain)} dB" else "${String.format("%.1f", gain)} dB",
                        fontSize = 11.sp,
                        color = Color(0xFF6366F1),
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Slider(
                    value = gain,
                    onValueChange = onGainChange,
                    valueRange = -6f..6f,
                    enabled = isEnabled,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF818CF8),
                        activeTrackColor = Color(0xFF6366F1),
                        inactiveTrackColor = Color(0xFF1E293B)
                    )
                )
            }
        }
    }
}

/**
 * Panel de Limitador Dinámico Anti-Clipping y DEQ (Dynamic EQ en Tiempo Real)
 */
@Composable
fun DynamicEQAndLimiterCard(
    isLimiterEnabled: Boolean,
    onToggleLimiter: () -> Unit,
    isDEQEnabled: Boolean,
    onToggleDEQ: () -> Unit,
    deqSensitivity: Float,
    onDEQSensitivityChange: (Float) -> Unit,
    maxGain: Float
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF0B0F19),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // SECCIÓN 1: LIMITADOR DINÁMICO
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Limiter",
                        tint = if (isLimiterEnabled) Color(0xFF34D399) else Color(0xFF64748B),
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "Limitador Dinámico Anti-Clipping",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Umbral: -0.5 dB • Ataque: 1.0 ms • Relación: 10:1",
                            color = Color(0xFF94A3B8),
                            fontSize = 10.sp
                        )
                    }
                }

                Switch(
                    checked = isLimiterEnabled,
                    onCheckedChange = { onToggleLimiter() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF059669),
                        uncheckedThumbColor = Color(0xFF94A3B8),
                        uncheckedTrackColor = Color(0xFF1E293B)
                    )
                )
            }

            // Medidor Dinámico de Reducción de Ganancia (Gain Reduction Meter)
            val simulatedGr = if (isLimiterEnabled && maxGain > 2.0f) {
                ((maxGain - 2.0f) * 0.7f).coerceIn(0f, 6f)
            } else {
                0f
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "GR: -${String.format("%.1f", simulatedGr)} dB",
                    fontSize = 11.sp,
                    color = if (simulatedGr > 0) Color(0xFFF87171) else Color(0xFF64748B),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF1E293B))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction = (simulatedGr / 6f).coerceIn(0f, 1f))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(Color(0xFF34D399), Color(0xFFFBBF24), Color(0xFFEF4444))
                                )
                            )
                    )
                }
            }

            Divider(color = Color(0xFF1E293B), thickness = 1.dp)

            // SECCIÓN 2: DEQ (DYNAMIC EQUALIZER EN TIEMPO REAL)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = "DEQ",
                        tint = if (isDEQEnabled) Color(0xFFEC4899) else Color(0xFF64748B),
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "DEQ (Dynamic Equalizer)",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Ajusta la respuesta frecuencial en tiempo real según el volumen",
                            color = Color(0xFF94A3B8),
                            fontSize = 10.sp
                        )
                    }
                }

                Switch(
                    checked = isDEQEnabled,
                    onCheckedChange = { onToggleDEQ() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFFEC4899),
                        uncheckedThumbColor = Color(0xFF94A3B8),
                        uncheckedTrackColor = Color(0xFF1E293B)
                    )
                )
            }

            // Sensibilidad DEQ
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Sensibilidad Dinámica", fontSize = 11.sp, color = Color(0xFF94A3B8))
                    Text(
                        text = "${String.format("%.1f", deqSensitivity)}x",
                        fontSize = 11.sp,
                        color = Color(0xFFEC4899),
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Slider(
                    value = deqSensitivity,
                    onValueChange = onDEQSensitivityChange,
                    valueRange = 0.2f..2.0f,
                    enabled = isDEQEnabled,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFF472B6),
                        activeTrackColor = Color(0xFFEC4899),
                        inactiveTrackColor = Color(0xFF1E293B)
                    )
                )
            }
        }
    }
}
