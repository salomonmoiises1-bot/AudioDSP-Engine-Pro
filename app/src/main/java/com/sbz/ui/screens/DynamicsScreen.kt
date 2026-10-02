package com.sbz.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        // ============================================================
        // HEADER
        // ============================================================

        Text(
            text = "sBz // DYNAMICS PROCESSOR",
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = SbzCyan
        )

        Text(
            text = if (config.mdrcEnabled) {
                "MDRC ONLINE  •  4-BAND MULTIBAND COMPRESSION"
            } else {
                "MDRC BYPASSED  •  PROCESSOR STANDBY"
            },
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            color = SbzTextSecondary
        )

        // ============================================================
        // MDRC
        // ============================================================

        RetroPanel(
            title = "MDRC // MULTIBAND DYNAMICS",
            active = config.mdrcEnabled
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
                        text = "MBC / DYNAMIC RANGE CONTROL",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = SbzTextPrimary
                    )

                    Spacer(Modifier.height(3.dp))

                    Text(
                        text = "Procesador multibanda nativo de 4 bandas",
                        fontSize = 10.sp,
                        color = SbzTextSecondary
                    )
                }

                Switch(
                    checked = config.mdrcEnabled,
                    onCheckedChange = viewModel::setMdrcEnabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = SbzCyan,
                        checkedTrackColor = SbzCyanDim,
                        uncheckedThumbColor = SbzTextSecondary,
                        uncheckedTrackColor = SbzSurface
                    )
                )
            }

            Spacer(Modifier.height(12.dp))

            // ========================================================
            // BAND SELECTOR
            // ========================================================

            Text(
                text = "BAND SELECT",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                color = SbzCyan
            )

            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                config.mdrcBands.forEachIndexed { index, band ->

                    val selected = selectedBandIndex == index

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp),
                        onClick = {
                            selectedBandIndex = index
                        },
                        shape = RoundedCornerShape(4.dp),
                        color = if (selected) {
                            SbzCyan.copy(alpha = 0.16f)
                        } else {
                            SbzSurface
                        },
                        border = BorderStroke(
                            1.dp,
                            if (selected) {
                                SbzCyan.copy(alpha = 0.75f)
                            } else {
                                SbzBorder
                            }
                        )
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {

                            Text(
                                text = "B${index + 1}",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                color = if (selected) {
                                    SbzCyan
                                } else {
                                    SbzTextSecondary
                                }
                            )

                            Text(
                                text = band.name,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 8.sp,
                                color = if (selected) {
                                    SbzTextPrimary
                                } else {
                                    SbzTextSecondary
                                },
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

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

        // ============================================================
        // HALL / REVERB
        // ============================================================

        RetroPanel(
            title = "HALL // ENVIRONMENTAL REVERB",
            active = config.hallEnabled
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
                        text = "AMBIENT SPACE PROCESSOR",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = SbzTextPrimary
                    )

                    Spacer(Modifier.height(3.dp))

                    Text(
                        text = if (engineStatus.hallReverbAvailable) {
                            "EnvironmentalReverb disponible"
                        } else {
                            "EnvironmentalReverb no disponible"
                        },
                        fontSize = 10.sp,
                        color = SbzTextSecondary
                    )
                }

                Switch(
                    checked = config.hallEnabled,
                    onCheckedChange = viewModel::setHallEnabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = SbzCyan,
                        checkedTrackColor = SbzCyanDim,
                        uncheckedThumbColor = SbzTextSecondary,
                        uncheckedTrackColor = SbzSurface
                    )
                )
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = "PARÁMETROS DEL MOTOR NATIVO",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = SbzCyan
            )

            Spacer(Modifier.height(8.dp))

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

        // ============================================================
        // AUTOGAIN
        // ============================================================

        RetroPanel(
            title = "HEADROOM // AUTO GAIN",
            active = config.autoGainEnabled
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
                        text = "AUTOMATIC HEADROOM CONTROL",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = SbzTextPrimary
                    )

                    Spacer(Modifier.height(3.dp))

                    Text(
                        text = "Compensación automática de ganancia",
                        fontSize = 10.sp,
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
                        checkedTrackColor = SbzCyanDim,
                        uncheckedThumbColor = SbzTextSecondary,
                        uncheckedTrackColor = SbzSurface
                    )
                )
            }

            Spacer(Modifier.height(10.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(4.dp),
                color = SbzSurface,
                border = BorderStroke(
                    1.dp,
                    SbzBorder
                )
            ) {
                Text(
                    modifier = Modifier.padding(10.dp),
                    text = if (config.autoGainEnabled) {
                        "AUTO GAIN ACTIVE\nEl procesador reduce la ganancia de entrada cuando EQ, tono o graves consumen demasiado headroom."
                    } else {
                        "AUTO GAIN BYPASSED\nLa ganancia de entrada utiliza únicamente el Pre-Gain configurado."
                    },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    lineHeight = 14.sp,
                    color = if (config.autoGainEnabled) {
                        SbzCyan
                    } else {
                        SbzTextSecondary
                    }
                )
            }
        }

        Spacer(Modifier.height(4.dp))
    }
}

// ====================================================================
// RETRO PANEL
// ====================================================================

@Composable
private fun RetroPanel(
    title: String,
    active: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = SbzCardBg
        ),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(
            1.dp,
            if (active) {
                SbzCyan.copy(alpha = 0.45f)
            } else {
                SbzBorder
            }
        )
    ) {

        Column(
            modifier = Modifier.padding(12.dp)
        ) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = if (active) {
                        SbzCyan
                    } else {
                        SbzTextSecondary
                    }
                )

                Text(
                    text = if (active) "● ON" else "○ OFF",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = if (active) {
                        SbzCyan
                    } else {
                        SbzTextSecondary
                    }
                )
            }

            Spacer(Modifier.height(10.dp))

            HorizontalDivider(
                color = SbzBorder,
                thickness = 1.dp
            )

            Spacer(Modifier.height(12.dp))

            content()
        }
    }
}

// ====================================================================
// MDRC BAND PARAMETERS
// ====================================================================

@Composable
private fun BandParameters(
    band: MdrcBandConfig,
    bandIndex: Int,
    allBands: List<MdrcBandConfig>,
    enabled: Boolean,
    onBandChange: (MdrcBandConfig) -> Unit
) {

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.alpha(
            if (enabled) 1f else 0.55f
        )
    ) {

        Text(
            text = "CROSSOVER / BAND ${bandIndex + 1}",
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            color = SbzCyan
        )

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

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = SbzSurface,
            shape = RoundedCornerShape(4.dp),
            border = BorderStroke(
                1.dp,
                SbzBorder
            )
        ) {

            Column(
                modifier = Modifier.padding(10.dp)
            ) {

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {

                    Text(
                        text = "FRECUENCIA DE CORTE",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        color = SbzTextSecondary
                    )

                    Text(
                        text = formatFrequency(currentCutoff),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = SbzCyan
                    )
                }

                Spacer(Modifier.height(3.dp))

                Text(
                    text = "${formatFrequency(minimumCutoff)}  →  ${formatFrequency(maximumCutoff)}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
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
                        activeTrackColor = SbzCyan,
                        inactiveTrackColor = SbzBorder
                    )
                )
            }
        }

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
            label = "Ataque",
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
            label = "Liberación",
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
            label = "Makeup Gain",
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

// ====================================================================
// HALL SLIDER
// ====================================================================

@Composable
private fun HallSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    format: String,
    enabled: Boolean,
    onValueChange: (Float) -> Unit
) {

    Column(
        modifier = Modifier.alpha(
            if (enabled) 1f else 0.55f
        )
    ) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {

            Text(
                text = label,
                fontSize = 10.sp,
                color = SbzTextSecondary
            )

            Text(
                text = format.format(value),
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = if (enabled) {
                    SbzCyan
                } else {
                    SbzTextSecondary
                }
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
                activeTrackColor = SbzCyan,
                inactiveTrackColor = SbzBorder
            )
        )
    }
}

// ====================================================================
// PARAM SLIDER
// ====================================================================

@Composable
private fun ParamSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    format: String,
    enabled: Boolean,
    onValueChange: (Float) -> Unit
) {

    Column(
        modifier = Modifier.alpha(
            if (enabled) 1f else 0.55f
        )
    ) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {

            Text(
                text = label,
                fontSize = 10.sp,
                color = SbzTextSecondary
            )

            Text(
                text = format.format(value),
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = if (enabled) {
                    SbzCyan
                } else {
                    SbzTextSecondary
                }
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
                activeTrackColor = SbzCyan,
                inactiveTrackColor = SbzBorder
            )
        )
    }
}

// ====================================================================
// MDRC CROSSOVER
// ====================================================================

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

// ====================================================================
// LOGARITHMIC FREQUENCY MAPPING
// ====================================================================

private fun frequencyToSlider(
    hz: Float,
    minHz: Float,
    maxHz: Float
): Float {

    val safeMin = minHz
        .coerceIn(
            MDRC_MIN_CUTOFF_HZ,
            MDRC_MAX_CUTOFF_HZ
        )
        .toDouble()

    val safeMax = maxHz
        .coerceIn(
            safeMin.toFloat(),
            MDRC_MAX_CUTOFF_HZ
        )
        .toDouble()

    val safeHz = hz
        .coerceIn(
            safeMin.toFloat(),
            safeMax.toFloat()
        )
        .toDouble()

    if (safeMax <= safeMin) {
        return 0f
    }

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

    val safeMin = minHz.coerceIn(
        MDRC_MIN_CUTOFF_HZ,
        MDRC_MAX_CUTOFF_HZ
    )

    val safeMax = maxHz.coerceIn(
        safeMin,
        MDRC_MAX_CUTOFF_HZ
    )

    if (safeMax <= safeMin) {
        return safeMin
    }

    return kotlin.math.exp(
        kotlin.math.ln(
            safeMin.toDouble()
        ) +
            value.coerceIn(0f, 1f) *
            (
                kotlin.math.ln(
                    safeMax.toDouble()
                ) -
                    kotlin.math.ln(
                        safeMin.toDouble()
                    )
                )
    ).toFloat()
}

// ====================================================================
// FREQUENCY FORMAT
// ====================================================================

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
