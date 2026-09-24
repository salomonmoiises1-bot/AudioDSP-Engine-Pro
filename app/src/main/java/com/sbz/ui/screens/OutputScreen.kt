package com.sbz.ui.screens

import androidx.compose.foundation.background
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
fun OutputScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val config by viewModel.config.collectAsState()
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Master Gain Stage
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SbzCardBg),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, SbzBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "MASTER OUTPUT GAIN",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = SbzCyan
                    )
                    Text(
                        text = if (config.masterGainDb > 0f) "+%.1f dB".format(config.masterGainDb) else "%.1f dB".format(config.masterGainDb),
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
                    steps = 71, // 0.5 dB
                    colors = SliderDefaults.colors(
                        thumbColor = SbzCyan,
                        activeTrackColor = SbzCyan
                    )
                )
            }
        }

        // Stereo Balance Stage
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SbzCardBg),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, SbzBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "STEREO BALANCE",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = SbzTextPrimary
                    )
                    val balText = when {
                        config.balance == 0f -> "CENTER"
                        config.balance < 0f -> "L %.0f%%".format(-config.balance * 100f)
                        else -> "R %.0f%%".format(config.balance * 100f)
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
                    Text("LEFT (100%)", fontSize = 10.sp, color = SbzTextSecondary)
                    Text("CENTER", fontSize = 10.sp, color = SbzTextSecondary)
                    Text("RIGHT (100%)", fontSize = 10.sp, color = SbzTextSecondary)
                }
            }
        }

        // Limiter & Protection Stage
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SbzCardBg),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, if (config.limiterEnabled) SbzCyan.copy(alpha = 0.4f) else SbzBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "LIMITER & DIGITAL PROTECTION",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = SbzCyan
                        )
                        Text(
                            text = "Native DynamicsProcessing final brickwall stage",
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

                Spacer(modifier = Modifier.height(14.dp))

                // Threshold
                LimiterSlider(
                    label = "Ceiling / Threshold",
                    value = config.limiterThresholdDb,
                    range = -12f..0f,
                    unit = "dB",
                    enabled = config.limiterEnabled,
                    onValueChange = {
                        viewModel.setLimiter(
                            config.limiterEnabled,
                            it,
                            config.limiterAttackMs,
                            config.limiterReleaseMs,
                            config.limiterRatio,
                            config.limiterPostGainDb
                        )
                    }
                )

                // Attack
                LimiterSlider(
                    label = "Attack Time",
                    value = config.limiterAttackMs,
                    range = 0.1f..10f,
                    unit = "ms",
                    enabled = config.limiterEnabled,
                    onValueChange = {
                        viewModel.setLimiter(
                            config.limiterEnabled,
                            config.limiterThresholdDb,
                            it,
                            config.limiterReleaseMs,
                            config.limiterRatio,
                            config.limiterPostGainDb
                        )
                    }
                )

                // Release
                LimiterSlider(
                    label = "Release Time",
                    value = config.limiterReleaseMs,
                    range = 10f..400f,
                    unit = "ms",
                    enabled = config.limiterEnabled,
                    onValueChange = {
                        viewModel.setLimiter(
                            config.limiterEnabled,
                            config.limiterThresholdDb,
                            config.limiterAttackMs,
                            it,
                            config.limiterRatio,
                            config.limiterPostGainDb
                        )
                    }
                )

                // Ratio
                LimiterSlider(
                    label = "Compression Ratio",
                    value = config.limiterRatio,
                    range = 10f..50f,
                    unit = ":1",
                    enabled = config.limiterEnabled,
                    onValueChange = {
                        viewModel.setLimiter(
                            config.limiterEnabled,
                            config.limiterThresholdDb,
                            config.limiterAttackMs,
                            config.limiterReleaseMs,
                            it,
                            config.limiterPostGainDb
                        )
                    }
                )
            }
        }

        // Digital Safety Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SbzSurface),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, SbzBorder)
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = SbzGreen,
                    modifier = Modifier.size(24.dp)
                )
                Column {
                    Text(
                        text = "DSP SANITIZATION & SAFEGUARD ACTIVE",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = SbzGreen
                    )
                    Text(
                        text = "Continuous NaN, Infinity, and digital overflow rejection active on native audio stream.",
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
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, fontSize = 11.sp, color = SbzTextSecondary)
            Text(
                "%.1f %s".format(value, unit),
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
