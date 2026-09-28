package com.sbz.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbz.dsp.model.MdrcBandConfig
import com.sbz.ui.MainViewModel
import com.sbz.ui.theme.*

@Composable
fun DynamicsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val config by viewModel.config.collectAsState()
    val engineStatus by viewModel.engineStatus.collectAsState()

    var selectedBandIndex by remember { mutableStateOf(0) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = SbzCardBg
            ),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(
                1.dp,
                if (config.mdrcEnabled) {
                    SbzCyan.copy(alpha = 0.4f)
                } else {
                    SbzBorder
                }
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "MDRC — COMPRESOR MULTIBANDA",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = SbzCyan
                        )

                        Text(
                            text = "MBC DynamicsProcessing nativo de 4 bandas",
                            fontSize = 11.sp,
                            color = SbzTextSecondary
                        )
                    }

                    Switch(
                        checked = config.mdrcEnabled,
                        onCheckedChange = {
                            viewModel.setMdrcEnabled(it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SbzCyan,
                            checkedTrackColor = SbzCyanDim
                        )
                    )
                }

                Spacer(
                    modifier = Modifier.height(14.dp)
                )

                TabRow(
                    selectedTabIndex = selectedBandIndex,
                    containerColor = SbzSurface,
                    contentColor = SbzCyan
                ) {
                    config.mdrcBands.forEachIndexed { index, band ->
                        Tab(
                            selected = selectedBandIndex == index,
                            onClick = {
                                selectedBandIndex = index
                            }
                        ) {
                            Text(
                                text = band.name,
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(
                    modifier = Modifier.height(14.dp)
                )

                config.mdrcBands
                    .getOrNull(selectedBandIndex)
                    ?.let { activeBand ->

                        BandParameters(
                            band = activeBand,
                            bandIndex = selectedBandIndex,
                            allBands = config.mdrcBands,
                            enabled = config.mdrcEnabled
                        ) {
                            viewModel.updateMdrcBand(
                                selectedBandIndex,
                                it
                            )
                        }
                    }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = SbzCardBg
            ),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(
                1.dp,
                if (config.hallEnabled) {
                    SbzCyan.copy(alpha = 0.4f)
                } else {
                    SbzBorder
                }
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "HALL / REVERB AMBIENTAL",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = SbzCyan
                        )

                        Text(
                            text = if (engineStatus.hallReverbAvailable) {
                                "EnvironmentalReverb nativo disponible"
                            } else {
                                "Efecto nativo no disponible en la sesión actual"
                            },
                            fontSize = 11.sp,
                            color = SbzTextSecondary
                        )
                    }

                    Switch(
                        checked = config.hallEnabled,
                        onCheckedChange = {
                            viewModel.setHallEnabled(it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SbzCyan,
                            checkedTrackColor = SbzCyanDim
                        )
                    )
                }

                Spacer(
                    modifier = Modifier.height(8.dp)
                )

                Text(
                    text = "Parámetros reales del motor de reverb de Android. Los cambios se aplican en tiempo real a las sesiones DSP activas.",
                    fontSize = 11.sp,
                    color = SbzTextSecondary
                )

                HallSlider(
                    label = "Mezcla de reverb",
                    value = config.hallMixPercent,
                    range = 0f..100f,
                    format = "%.0f %%",
                    enabled = config.hallEnabled
                ) {
                    viewModel.setHallMix(it)
                }

                HallSlider(
                    label = "Tiempo de decaimiento",
                    value = config.hallDecayTimeMs,
                    range = 100f..20000f,
                    format = "%.0f ms",
                    enabled = config.hallEnabled
                ) {
                    viewModel.setHallDecayTime(it)
                }

                HallSlider(
                    label = "Decaimiento de agudos",
                    value = config.hallDecayHfRatio,
                    range = 0.1f..2f,
                    format = "%.2fx",
                    enabled = config.hallEnabled
                ) {
                    viewModel.setHallDecayHfRatio(it)
                }

                HallSlider(
                    label = "Densidad",
                    value = config.hallDensityPercent,
                    range = 0f..100f,
                    format = "%.0f %%",
                    enabled = config.hallEnabled
                ) {
                    viewModel.setHallDensity(it)
                }

                HallSlider(
                    label = "Difusión",
                    value = config.hallDiffusionPercent,
                    range = 0f..100f,
                    format = "%.0f %%",
                    enabled = config.hallEnabled
                ) {
                    viewModel.setHallDiffusion(it)
                }

                HallSlider(
                    label = "Reflexiones tempranas — retardo",
                    value = config.hallReflectionsDelayMs,
                    range = 0f..300f,
                    format = "%.0f ms",
                    enabled = config.hallEnabled
                ) {
                    viewModel.setHallReflectionsDelay(it)
                }

                HallSlider(
                    label = "Reflexiones tempranas — nivel",
                    value = config.hallReflectionsLevelDb,
                    range = -90f..10f,
                    format = "%.1f dB",
                    enabled = config.hallEnabled
                ) {
                    viewModel.setHallReflectionsLevel(it)
                }

                HallSlider(
                    label = "Reverb — retardo",
                    value = config.hallReverbDelayMs,
                    range = 0f..100f,
                    format = "%.0f ms",
                    enabled = config.hallEnabled
                ) {
                    viewModel.setHallReverbDelay(it)
                }

                HallSlider(
                    label = "Sala — altas frecuencias",
                    value = config.hallRoomHfLevelDb,
                    range = -90f..0f,
                    format = "%.1f dB",
                    enabled = config.hallEnabled
                ) {
                    viewModel.setHallRoomHfLevel(it)
                }

                HallSlider(
                    label = "Nivel general de sala",
                    value = config.hallRoomLevelDb,
                    range = -90f..0f,
                    format = "%.1f dB",
                    enabled = config.hallEnabled
                ) {
                    viewModel.setHallRoomLevel(it)
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = SbzCardBg
            ),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(
                1.dp,
                if (config.autoGainEnabled) {
                    SbzCyan.copy(alpha = 0.4f)
                } else {
                    SbzBorder
                }
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "COMPENSACIÓN AUTOMÁTICA DE HEADROOM",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = SbzTextPrimary
                        )

                        Text(
                            text = "Reduce automáticamente la ganancia cuando los refuerzos del DSP consumen margen",
                            fontSize = 11.sp,
                            color = SbzTextSecondary
                        )
                    }

                    Switch(
                        checked = config.autoGainEnabled,
                        onCheckedChange = {
                            viewModel.setAutoGain(
                                it,
                                config.autoGainTargetDb
                            )
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SbzCyan,
                            checkedTrackColor = SbzCyanDim
                        )
                    )
                }

                Spacer(
                    modifier = Modifier.height(12.dp)
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = if (config.autoGainEnabled) {
                        "Activo: el motor reduce automáticamente la ganancia de entrada cuando los refuerzos de EQ, tono y graves consumen demasiado margen."
                    } else {
                        "Desactivado: la ganancia de entrada usa únicamente el Pre-Gain configurado."
                    },
                    fontSize = 11.sp,
                    color = SbzTextSecondary
                )
            }
        }
    }
}

@Composable
private fun BandParameters(
    band: MdrcBandConfig,
    bandIndex: Int,
    allBands: List<MdrcBandConfig>,
    enabled: Boolean,
    onBandChange: (MdrcBandConfig) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val minimumCutoff = crossoverMinimumHz(
            bandIndex = bandIndex,
            allBands = allBands
        )
        val maximumCutoff = crossoverMaximumHz(
            bandIndex = bandIndex,
            allBands = allBands
        )
        val currentCutoff = band.cutoffFrequencyHz.coerceIn(
            minimumCutoff,
            maximumCutoff
        )

        Text(
            text = "CORTES DEL CROSSOVER MDRC",
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = SbzCyan
        )

        Text(
            text = "El corte de cada banda se aplica directamente al MBC nativo. Los cortes se mantienen ordenados para evitar solapamientos.",
            fontSize = 11.sp,
            color = SbzTextSecondary
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Frecuencia de corte",
                fontSize = 12.sp,
                color = SbzTextSecondary
            )

            Text(
                text = formatFrequency(currentCutoff),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = SbzCyan
            )
        }

        Text(
            text = "${formatFrequency(minimumCutoff)}  —  ${formatFrequency(maximumCutoff)}",
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            color = SbzTextSecondary
        )

        Slider(
            value = frequencyToSlider(
                currentCutoff,
                minimumCutoff,
                maximumCutoff
            ),
            onValueChange = {
                val frequency = sliderToFrequency(
                    it,
                    minimumCutoff,
                    maximumCutoff
                )

                onBandChange(
                    band.copy(
                        cutoffFrequencyHz = frequency
                    )
                )
            },
            valueRange = 0f..1f,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = SbzCyan,
                activeTrackColor = SbzCyan
            )
        )

        ParamSlider(
            label = "Umbral",
            value = band.thresholdDb,
            range = -60f..0f,
            format = "%.1f dB",
            enabled = enabled
        ) {
            onBandChange(
                band.copy(thresholdDb = it)
            )
        }

        ParamSlider(
            label = "Relación",
            value = band.ratio,
            range = 1f..20f,
            format = "%.1f:1",
            enabled = enabled
        ) {
            onBandChange(
                band.copy(ratio = it)
            )
        }

        ParamSlider(
            label = "Tiempo de ataque",
            value = band.attackMs,
            range = 0.5f..100f,
            format = "%.1f ms",
            enabled = enabled
        ) {
            onBandChange(
                band.copy(attackMs = it)
            )
        }

        ParamSlider(
            label = "Tiempo de liberación",
            value = band.releaseMs,
            range = 10f..800f,
            format = "%.0f ms",
            enabled = enabled
        ) {
            onBandChange(
                band.copy(releaseMs = it)
            )
        }

        ParamSlider(
            label = "Ganancia de compensación",
            value = band.makeupGainDb,
            range = 0f..12f,
            format = "+%.1f dB",
            enabled = enabled
        ) {
            onBandChange(
                band.copy(makeupGainDb = it)
            )
        }

        ParamSlider(
            label = "Knee",
            value = band.kneeDb,
            range = 0f..24f,
            format = "%.1f dB",
            enabled = enabled
        ) {
            onBandChange(
                band.copy(kneeDb = it)
            )
        }
    }
}

@Composable
private fun HallSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    format: String,
    enabled: Boolean,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                color = SbzTextSecondary
            )

            Text(
                text = format.format(value),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = SbzTextPrimary
            )
        }

        Slider(
            value = value.coerceIn(
                range.start,
                range.endInclusive
            ),
            onValueChange = onValueChange,
            valueRange = range,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = SbzCyan,
                activeTrackColor = SbzCyan
            )
        )
    }
}

@Composable
private fun ParamSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    format: String,
    enabled: Boolean,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                color = SbzTextSecondary
            )

            Text(
                text = format.format(value),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = SbzTextPrimary
            )
        }

        Slider(
            value = value.coerceIn(
                range.start,
                range.endInclusive
            ),
            onValueChange = onValueChange,
            valueRange = range,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = SbzCyan,
                activeTrackColor = SbzCyan
            )
        )
    }
}

/**
 * Converts 20 Hz .. 22 kHz to a logarithmic slider.
 *
 * Audio crossover frequencies must be represented
 * logarithmically rather than linearly.
 */
private const val MDRC_MIN_CUTOFF_HZ = 20f
private const val MDRC_MAX_CUTOFF_HZ = 22000f
private const val MDRC_CUTOFF_GAP_HZ = 1f

private fun crossoverMinimumHz(
    bandIndex: Int,
    allBands: List<MdrcBandConfig>
): Float {
    return if (bandIndex <= 0) {
        MDRC_MIN_CUTOFF_HZ
    } else {
        (
            allBands.getOrNull(bandIndex - 1)?.cutoffFrequencyHz
                ?: MDRC_MIN_CUTOFF_HZ
            ) + MDRC_CUTOFF_GAP_HZ
    }.coerceAtMost(MDRC_MAX_CUTOFF_HZ)
}

private fun crossoverMaximumHz(
    bandIndex: Int,
    allBands: List<MdrcBandConfig>
): Float {
    return if (bandIndex >= allBands.lastIndex) {
        MDRC_MAX_CUTOFF_HZ
    } else {
        (
            allBands.getOrNull(bandIndex + 1)?.cutoffFrequencyHz
                ?: MDRC_MAX_CUTOFF_HZ
            ) - MDRC_CUTOFF_GAP_HZ
    }.coerceAtLeast(MDRC_MIN_CUTOFF_HZ)
}

private fun frequencyToSlider(
    hz: Float,
    minHz: Float,
    maxHz: Float
): Float {
    val safeMin = minHz
        .coerceIn(MDRC_MIN_CUTOFF_HZ, MDRC_MAX_CUTOFF_HZ)
        .toDouble()
    val safeMax = maxHz
        .coerceIn(safeMin.toFloat(), MDRC_MAX_CUTOFF_HZ)
        .toDouble()
    val safeHz = hz
        .coerceIn(safeMin.toFloat(), safeMax.toFloat())
        .toDouble()

    if (safeMax <= safeMin) return 0f

    return (
        (
            kotlin.math.ln(safeHz) -
                kotlin.math.ln(safeMin)
            ) /
                (
                    kotlin.math.ln(safeMax) -
                        kotlin.math.ln(safeMin)
                    )
        ).toFloat()
}

private fun sliderToFrequency(
    value: Float,
    minHz: Float,
    maxHz: Float
): Float {
    val safeMin = minHz
        .coerceIn(MDRC_MIN_CUTOFF_HZ, MDRC_MAX_CUTOFF_HZ)
    val safeMax = maxHz
        .coerceIn(safeMin, MDRC_MAX_CUTOFF_HZ)

    if (safeMax <= safeMin) return safeMin

    return kotlin.math.exp(
        kotlin.math.ln(safeMin.toDouble()) +
            value.coerceIn(0f, 1f) *
            (
                kotlin.math.ln(safeMax.toDouble()) -
                    kotlin.math.ln(safeMin.toDouble())
                )
    ).toFloat()
}

private fun formatFrequency(
    hz: Float
): String {
    return when {
        hz >= 1000f ->
            "%.2f kHz".format(hz / 1000f)

        hz >= 100f ->
            "%.0f Hz".format(hz)

        else ->
            "%.1f Hz".format(hz)
    }
}
