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
            border = androidx.compose.foundation.BorderStroke(1.dp, SbzBorder)
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "GANANCIA MAESTRA DE SALIDA",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = SbzCyan
                    )
                    Text(
                        text = if (config.masterGainDb > 0f) {
                            "+%.1f dB".format(config.masterGainDb)
                        } else {
                            "%.1f dB".format(config.masterGainDb)
                        },
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = if (config.masterGainDb > 0f) SbzAmber else SbzTextPrimary
                    )
                }

                Slider(
                    value = config.masterGainDb,
                    onValueChange = { viewModel.setMasterGain(it) },
                    valueRange = -24f..12f,
                    steps = 71,
                    colors = SliderDefaults.colors(
                        thumbColor = SbzCyan,
                        activeTrackColor = SbzCyan
                    )
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SbzCardBg),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, SbzBorder)
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "BALANCE ESTÉREO",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = SbzTextPrimary
                    )

                    val balText = when {
                        config.balance == 0f -> "CENTRO"
                        config.balance < 0f -> "I %.0f%%".format(-config.balance * 100f)
                        else -> "D %.0f%%".format(config.balance * 100f)
                    }

                    Text(
                        text = balText,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = SbzCyan
                    )
                }

                Slider(
                    value = config.balance,
                    onValueChange = { viewModel.setBalance(it) },
                    valueRange = -1f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = SbzCyan,
                        activeTrackColor = SbzCyan
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("IZQUIERDA (100%)", fontSize = 10.sp, color = SbzTextSecondary)
                    Text("CENTRO", fontSize = 10.sp, color = SbzTextSecondary)
                    Text("DERECHA (100%)", fontSize = 10.sp, color = SbzTextSecondary)
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SbzCardBg),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (config.limiterEnabled) SbzCyan.copy(alpha = 0.4f) else SbzBorder
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
                            text = "LIMITADOR Y PROTECCIÓN DIGITAL",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = SbzCyan
                        )
                        Text(
                            text = "Etapa final brickwall nativa de DynamicsProcessing",
                            fontSize = 11.sp,
                            color = SbzTextSecondary
                        )
                    }

                    Switch(
                        checked = config.limiterEnabled,
                        onCheckedChange = {
                            viewModel.setLimiter(
                                it,
                                config.limiterThresholdDb,
                                config.limiterAttackMs,
                                config.limiterReleaseMs,
                                config.limiterRatio,
                                config.limiterPostGainDb
                            )
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SbzCyan,
                            checkedTrackColor = SbzCyanDim
                        )
                    )
                }

                Spacer(Modifier.height(14.dp))

                LimiterSlider(
                    "Techo / Umbral",
                    config.limiterThresholdDb,
                    -12f..0f,
                    "dB",
                    config.limiterEnabled
                ) {
                    viewModel.setLimiter(
                        config.limiterEnabled,
                        it,
                        config.limiterAttackMs,
                        config.limiterReleaseMs,
                        config.limiterRatio,
                        config.limiterPostGainDb
                    )
                }

                LimiterSlider(
                    "Tiempo de ataque",
                    config.limiterAttackMs,
                    0.1f..10f,
                    "ms",
                    config.limiterEnabled
                ) {
                    viewModel.setLimiter(
                        config.limiterEnabled,
                        config.limiterThresholdDb,
                        it,
                        config.limiterReleaseMs,
                        config.limiterRatio,
                        config.limiterPostGainDb
                    )
                }

                LimiterSlider(
                    "Tiempo de liberación",
                    config.limiterReleaseMs,
                    10f..400f,
                    "ms",
                    config.limiterEnabled
                ) {
                    viewModel.setLimiter(
                        config.limiterEnabled,
                        config.limiterThresholdDb,
                        config.limiterAttackMs,
                        it,
                        config.limiterRatio,
                        config.limiterPostGainDb
                    )
                }

                LimiterSlider(
                    "Relación de compresión",
                    config.limiterRatio,
                    10f..50f,
                    ":1",
                    config.limiterEnabled
                ) {
                    viewModel.setLimiter(
                        config.limiterEnabled,
                        config.limiterThresholdDb,
                        config.limiterAttackMs,
                        config.limiterReleaseMs,
                        it,
                        config.limiterPostGainDb
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SbzSurface),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, SbzBorder)
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = SbzGreen,
                    modifier = Modifier.size(24.dp)
                )
                Column {
                    Text(
                        text = "DEPURACIÓN Y PROTECCIÓN DEL DSP ACTIVAS",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = SbzGreen
                    )
                    Text(
                        text = "Rechazo continuo de NaN, Infinity y desbordamientos digitales activo en el flujo de audio nativo.",
                        fontSize = 11.sp,
                        color = SbzTextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun LimiterSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    unit: String,
    enabled: Boolean,
    onValueChange: (Float) -> Unit
) {
    Column(Modifier.padding(vertical = 4.dp)) {
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
                text = "%.1f %s".format(value, unit),
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
