package com.sbz.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbz.ui.MainViewModel
import com.sbz.ui.theme.*

@Composable
fun OutputScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val config by viewModel.config.collectAsState()

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = SbzCardBg),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, SbzBorder)) {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text("GANANCIA MAESTRA DE SALIDA", 12.sp, FontFamily.Monospace, FontWeight.Bold, color = SbzCyan)
                    Text(if (config.masterGainDb > 0f) "+%.1f dB".format(config.masterGainDb) else "%.1f dB".format(config.masterGainDb),
                        14.sp, FontFamily.Monospace, FontWeight.Bold,
                        color = if (config.masterGainDb > 0f) SbzAmber else SbzTextPrimary)
                }
                Slider(config.masterGainDb, { viewModel.setMasterGain(it) },
                    valueRange = -24f..12f, steps = 71,
                    colors = SliderDefaults.colors(thumbColor = SbzCyan, activeTrackColor = SbzCyan))
            }
        }

        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = SbzCardBg),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, SbzBorder)) {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text("BALANCE ESTÉREO", 12.sp, FontFamily.Monospace, FontWeight.Bold, color = SbzTextPrimary)
                    val balText = when {
                        config.balance == 0f -> "CENTRO"
                        config.balance < 0f -> "I %.0f%%".format(-config.balance * 100f)
                        else -> "D %.0f%%".format(config.balance * 100f)
                    }
                    Text(balText, 12.sp, FontFamily.Monospace, color = SbzCyan)
                }
                Slider(config.balance, { viewModel.setBalance(it) }, valueRange = -1f..1f,
                    colors = SliderDefaults.colors(thumbColor = SbzCyan, activeTrackColor = SbzCyan))
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Text("IZQUIERDA (100%)", 10.sp, color = SbzTextSecondary)
                    Text("CENTRO", 10.sp, color = SbzTextSecondary)
                    Text("DERECHA (100%)", 10.sp, color = SbzTextSecondary)
                }
            }
        }

        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = SbzCardBg),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, if (config.limiterEnabled) SbzCyan.copy(alpha = 0.4f) else SbzBorder)) {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column {
                        Text("LIMITADOR Y PROTECCIÓN DIGITAL", 12.sp, FontFamily.Monospace, FontWeight.Bold, color = SbzCyan)
                        Text("Etapa final brickwall nativa de DynamicsProcessing", 11.sp, color = SbzTextSecondary)
                    }
                    Switch(
                        config.limiterEnabled,
                        {
                            viewModel.setLimiter(it, config.limiterThresholdDb, config.limiterAttackMs,
                                config.limiterReleaseMs, config.limiterRatio, config.limiterPostGainDb)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = SbzCyan, checkedTrackColor = SbzCyanDim)
                    )
                }
                Spacer(Modifier.height(14.dp))
                LimiterSlider("Techo / Umbral", config.limiterThresholdDb, -12f..0f, "dB", config.limiterEnabled) {
                    viewModel.setLimiter(config.limiterEnabled, it, config.limiterAttackMs, config.limiterReleaseMs, config.limiterRatio, config.limiterPostGainDb)
                }
                LimiterSlider("Tiempo de ataque", config.limiterAttackMs, 0.1f..10f, "ms", config.limiterEnabled) {
                    viewModel.setLimiter(config.limiterEnabled, config.limiterThresholdDb, it, config.limiterReleaseMs, config.limiterRatio, config.limiterPostGainDb)
                }
                LimiterSlider("Tiempo de liberación", config.limiterReleaseMs, 10f..400f, "ms", config.limiterEnabled) {
                    viewModel.setLimiter(config.limiterEnabled, config.limiterThresholdDb, config.limiterAttackMs, it, config.limiterRatio, config.limiterPostGainDb)
                }
                LimiterSlider("Relación de compresión", config.limiterRatio, 10f..50f, ":1", config.limiterEnabled) {
                    viewModel.setLimiter(config.limiterEnabled, config.limiterThresholdDb, config.limiterAttackMs, config.limiterReleaseMs, it, config.limiterPostGainDb)
                }
            }
        }

        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = SbzSurface),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, SbzBorder)) {
            Row(Modifier.padding(14.dp), Alignment.CenterVertically, Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.Shield, null, tint = SbzGreen, modifier = Modifier.size(24.dp))
                Column {
                    Text("DEPURACIÓN Y PROTECCIÓN DEL DSP ACTIVAS", 11.sp, FontFamily.Monospace, FontWeight.Bold, color = SbzGreen)
                    Text("Rechazo continuo de NaN, Infinity y desbordamientos digitales activo en el flujo de audio nativo.",
                        11.sp, color = SbzTextSecondary)
                }
            }
        }
    }
}

@Composable
private fun LimiterSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>,
                          unit: String, enabled: Boolean, onValueChange: (Float) -> Unit) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            Text(label, 11.sp, color = SbzTextSecondary)
            Text("%.1f %s".format(value, unit), 11.sp, FontFamily.Monospace, color = SbzTextPrimary)
        }
        Slider(value, onValueChange, valueRange = range, enabled = enabled,
            colors = SliderDefaults.colors(thumbColor = SbzCyan, activeTrackColor = SbzCyan))
    }
}
