package com.sbz.ui.screens

import androidx.compose.foundation.background
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
    val scrollState = rememberScrollState()
    var selectedBandIndex by remember { mutableStateOf(0) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // MDRC Header & Master Switch
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SbzCardBg),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, if (config.mdrcEnabled) SbzCyan.copy(alpha = 0.4f) else SbzBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
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
                            text = "MBC de DynamicsProcessing nativo de 4 bandas",
                            fontSize = 11.sp,
                            color = SbzTextSecondary
                        )
                    }

                    Switch(
                        checked = config.mdrcEnabled,
                        onCheckedChange = { viewModel.setMdrcEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SbzCyan,
                            checkedTrackColor = SbzCyanDim
                        )
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Band Selection Tabs
                TabRow(
                    selectedTabIndex = selectedBandIndex,
                    containerColor = SbzSurface,
                    contentColor = SbzCyan
                ) {
                    config.mdrcBands.forEachIndexed { index, band ->
                        Tab(
                            selected = selectedBandIndex == index,
                            onClick = { selectedBandIndex = index },
                            text = { Text(band.name, fontSize = 11.sp, maxLines = 1) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Active Band Controls
                val activeBand = config.mdrcBands.getOrNull(selectedBandIndex)
                if (activeBand != null) {
                    BandParameters(
                        band = activeBand,
                        enabled = config.mdrcEnabled,
                        onBandChange = { updated ->
                            viewModel.updateMdrcBand(selectedBandIndex, updated)
                        }
                    )
                }
            }
        }

        // AutoGain / AGC Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SbzCardBg),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, if (config.autoGainEnabled) SbzCyan.copy(alpha = 0.4f) else SbzBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "CONTROL DE AUTOGANANCIA / AGC",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = SbzTextPrimary
                        )
                        Text(
                            text = "Evita variaciones bruscas de volumen y recortes",
                            fontSize = 11.sp,
                            color = SbzTextSecondary
                        )
                    }

                    Switch(
                        checked = config.autoGainEnabled,
                        onCheckedChange = { viewModel.setAutoGain(it, config.autoGainTargetDb) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SbzCyan,
                            checkedTrackColor = SbzCyanDim
                        )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Nivel de volumen objetivo",
                        fontSize = 12.sp,
                        color = SbzTextSecondary
                    )
                    Text(
                        text = "%.1f dB".format(config.autoGainTargetDb),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = SbzCyan
                    )
                }

                Slider(
                    value = config.autoGainTargetDb,
                    onValueChange = { viewModel.setAutoGain(config.autoGainEnabled, it) },
                    valueRange = -24f..-6f,
                    enabled = config.autoGainEnabled,
                    colors = SliderDefaults.colors(
                        thumbColor = SbzCyan,
                        activeTrackColor = SbzCyan
                    )
                )
            }
        }
    }
}

@Composable
private fun BandParameters(
    band: MdrcBandConfig,
    enabled: Boolean,
    onBandChange: (MdrcBandConfig) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Cutoff Frequency
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Frecuencia de corte", fontSize = 12.sp, color = SbzTextSecondary)
            Text("${band.cutoffFrequencyHz.toInt()} Hz", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = SbzCyan)
        }

        // Umbral
        ParamSlider(
            label = "Umbral",
            value = band.thresholdDb,
            range = -60f..0f,
            format = "%.1f dB",
            enabled = enabled,
            onValueChange = { onBandChange(band.copy(thresholdDb = it)) }
        )

        // Relación
        ParamSlider(
            label = "Relación",
            value = band.ratio,
            range = 1f..20f,
            format = "%.1f:1",
            enabled = enabled,
            onValueChange = { onBandChange(band.copy(ratio = it)) }
        )

        // Attack
        ParamSlider(
            label = "Tiempo de ataque",
            value = band.attackMs,
            range = 0.5f..100f,
            format = "%.1f ms",
            enabled = enabled,
            onValueChange = { onBandChange(band.copy(attackMs = it)) }
        )

        // Release
        ParamSlider(
            label = "Tiempo de liberación",
            value = band.releaseMs,
            range = 10f..800f,
            format = "%.0f ms",
            enabled = enabled,
            onValueChange = { onBandChange(band.copy(releaseMs = it)) }
        )

        // Ganancia de compensación
        ParamSlider(
            label = "Ganancia de compensación",
            value = band.makeupGainDb,
            range = 0f..12f,
            format = "+%.1f dB",
            enabled = enabled,
            onValueChange = { onBandChange(band.copy(makeupGainDb = it)) }
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
            Text(label, fontSize = 11.sp, color = SbzTextSecondary)
            Text(format.format(value), fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = SbzTextPrimary)
        }
        Slider(
            value = value,
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
