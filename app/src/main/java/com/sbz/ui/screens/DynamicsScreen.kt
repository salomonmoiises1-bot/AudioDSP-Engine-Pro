package com.sbz.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbz.dsp.model.MdrcBandConfig
import com.sbz.ui.MainViewModel
import com.sbz.ui.theme.*

@Composable
fun DynamicsScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val config by viewModel.config.collectAsState()
    var selectedBandIndex by remember { mutableStateOf(0) }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SbzCardBg),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (config.mdrcEnabled) SbzCyan.copy(alpha = 0.4f) else SbzBorder
            )
        ) {
            Column(Modifier.padding(16.dp)) {
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
                        onCheckedChange = { viewModel.setMdrcEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SbzCyan,
                            checkedTrackColor = SbzCyanDim
                        )
                    )
                }

                Spacer(Modifier.height(14.dp))

                TabRow(
                    selectedTabIndex = selectedBandIndex,
                    containerColor = SbzSurface,
                    contentColor = SbzCyan
                ) {
                    config.mdrcBands.forEachIndexed { index, band ->
                        Tab(
                            selected = selectedBandIndex == index,
                            onClick = { selectedBandIndex = index }
                        ) {
                            Text(
                                text = band.name,
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                config.mdrcBands.getOrNull(selectedBandIndex)?.let { activeBand ->
                    BandParameters(
                        band = activeBand,
                        bandIndex = selectedBandIndex,
                        allBands = config.mdrcBands,
                        enabled = config.mdrcEnabled
                    ) {
                        viewModel.updateMdrcBand(selectedBandIndex, it)
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SbzCardBg),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (config.autoGainEnabled) SbzCyan.copy(alpha = 0.4f) else SbzBorder
            )
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "CONTROL DE GANANCIA AUTOMÁTICA / AGC",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = SbzTextPrimary
                        )
                        Text(
                            text = "Evita variaciones bruscas de volumen y saturación",
                            fontSize = 11.sp,
                            color = SbzTextSecondary
                        )
                    }
                    Switch(
                        checked = config.autoGainEnabled,
                        onCheckedChange = {
                            viewModel.setAutoGain(it, config.autoGainTargetDb)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SbzCyan,
                            checkedTrackColor = SbzCyanDim
                        )
                    )
                }

                Spacer(Modifier.height(12.dp))

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
                    onValueChange = {
                        viewModel.setAutoGain(config.autoGainEnabled, it)
                    },
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
    bandIndex: Int,
    allBands: List<MdrcBandConfig>,
    enabled: Boolean,
    onBandChange: (MdrcBandConfig) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        var cutoffText by remember(band.cutoffFrequencyHz) {
            mutableStateOf(band.cutoffFrequencyHz.toInt().toString())
        }
        val minCutoff = if (bandIndex == 0) 20f
        else allBands[bandIndex - 1].cutoffFrequencyHz + 20f
        val maxCutoff = if (bandIndex == allBands.lastIndex) 22000f
        else allBands[bandIndex + 1].cutoffFrequencyHz - 20f

        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Frecuencia de corte del crossover",
                    fontSize = 12.sp,
                    color = SbzTextSecondary
                )
                Text(
                    text = "${band.cutoffFrequencyHz.toInt()} Hz",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = SbzCyan
                )
            }
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = cutoffText,
                onValueChange = { input ->
                    cutoffText = input.filter { it.isDigit() }.take(5)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { state ->
                        if (!state.isFocused) {
                            cutoffText.toFloatOrNull()?.let { raw ->
                                val value = raw.coerceIn(minCutoff, maxCutoff)
                                cutoffText = value.toInt().toString()
                                onBandChange(band.copy(cutoffFrequencyHz = value))
                            } ?: run {
                                cutoffText = band.cutoffFrequencyHz.toInt().toString()
                            }
                        }
                    },
                enabled = enabled,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                label = { Text("Hz") },
                supportingText = {
                    Text("${minCutoff.toInt()}–${maxCutoff.toInt()} Hz")
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SbzCyan,
                    unfocusedBorderColor = SbzBorder,
                    focusedLabelColor = SbzCyan,
                    cursorColor = SbzCyan
                )
            )
        }

        ParamSlider("Umbral", band.thresholdDb, -60f..0f, "%.1f dB", enabled) {
            onBandChange(band.copy(thresholdDb = it))
        }
        ParamSlider("Relación", band.ratio, 1f..20f, "%.1f:1", enabled) {
            onBandChange(band.copy(ratio = it))
        }
        ParamSlider("Tiempo de ataque", band.attackMs, 0.5f..100f, "%.1f ms", enabled) {
            onBandChange(band.copy(attackMs = it))
        }
        ParamSlider("Tiempo de liberación", band.releaseMs, 10f..800f, "%.0f ms", enabled) {
            onBandChange(band.copy(releaseMs = it))
        }
        ParamSlider("Ganancia de compensación", band.makeupGainDb, 0f..12f, "+%.1f dB", enabled) {
            onBandChange(band.copy(makeupGainDb = it))
        }
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
