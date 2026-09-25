package com.sbz.ui.screens

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
fun DynamicsScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val config by viewModel.config.collectAsState()
    var selectedBandIndex by remember { mutableStateOf(0) }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = SbzCardBg),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, if (config.mdrcEnabled) SbzCyan.copy(alpha = 0.4f) else SbzBorder)) {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column {
                        Text("MDRC — COMPRESOR MULTIBANDA", 12.sp, FontFamily.Monospace, FontWeight.Bold, color = SbzCyan)
                        Text("MBC DynamicsProcessing nativo de 4 bandas", 11.sp, color = SbzTextSecondary)
                    }
                    Switch(config.mdrcEnabled, { viewModel.setMdrcEnabled(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = SbzCyan, checkedTrackColor = SbzCyanDim))
                }
                Spacer(Modifier.height(14.dp))
                TabRow(selectedBandIndex, containerColor = SbzSurface, contentColor = SbzCyan) {
                    config.mdrcBands.forEachIndexed { index, band ->
                        Tab(selectedBandIndex == index, { selectedBandIndex = index }) {
                            Text(band.name, 11.sp, maxLines = 1)
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                config.mdrcBands.getOrNull(selectedBandIndex)?.let { activeBand ->
                    BandParameters(activeBand, config.mdrcEnabled) {
                        viewModel.updateMdrcBand(selectedBandIndex, it)
                    }
                }
            }
        }

        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = SbzCardBg),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, if (config.autoGainEnabled) SbzCyan.copy(alpha = 0.4f) else SbzBorder)) {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column {
                        Text("CONTROL DE GANANCIA AUTOMÁTICA / AGC", 12.sp, FontFamily.Monospace, FontWeight.Bold, color = SbzTextPrimary)
                        Text("Evita variaciones bruscas de volumen y saturación", 11.sp, color = SbzTextSecondary)
                    }
                    Switch(config.autoGainEnabled, { viewModel.setAutoGain(it, config.autoGainTargetDb) },
                        colors = SwitchDefaults.colors(checkedThumbColor = SbzCyan, checkedTrackColor = SbzCyanDim))
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Text("Nivel de volumen objetivo", 12.sp, color = SbzTextSecondary)
                    Text("%.1f dB".format(config.autoGainTargetDb), 12.sp, FontFamily.Monospace, color = SbzCyan)
                }
                Slider(config.autoGainTargetDb, { viewModel.setAutoGain(config.autoGainEnabled, it) },
                    valueRange = -24f..-6f, enabled = config.autoGainEnabled,
                    colors = SliderDefaults.colors(thumbColor = SbzCyan, activeTrackColor = SbzCyan))
            }
        }
    }
}

@Composable
private fun BandParameters(band: MdrcBandConfig, enabled: Boolean, onBandChange: (MdrcBandConfig) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            Text("Frecuencia de corte del crossover", 12.sp, color = SbzTextSecondary)
            Text("${band.cutoffFrequencyHz.toInt()} Hz", 12.sp, FontFamily.Monospace, color = SbzCyan)
        }
        ParamSlider("Umbral", band.thresholdDb, -60f..0f, "%.1f dB", enabled) { onBandChange(band.copy(thresholdDb = it)) }
        ParamSlider("Relación", band.ratio, 1f..20f, "%.1f:1", enabled) { onBandChange(band.copy(ratio = it)) }
        ParamSlider("Tiempo de ataque", band.attackMs, 0.5f..100f, "%.1f ms", enabled) { onBandChange(band.copy(attackMs = it)) }
        ParamSlider("Tiempo de liberación", band.releaseMs, 10f..800f, "%.0f ms", enabled) { onBandChange(band.copy(releaseMs = it)) }
        ParamSlider("Ganancia de compensación", band.makeupGainDb, 0f..12f, "+%.1f dB", enabled) { onBandChange(band.copy(makeupGainDb = it)) }
    }
}

@Composable
private fun ParamSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>,
                        format: String, enabled: Boolean, onValueChange: (Float) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            Text(label, 11.sp, color = SbzTextSecondary)
            Text(format.format(value), 11.sp, FontFamily.Monospace, color = SbzTextPrimary)
        }
        Slider(value, onValueChange, valueRange = range, enabled = enabled,
            colors = SliderDefaults.colors(thumbColor = SbzCyan, activeTrackColor = SbzCyan))
    }
}
