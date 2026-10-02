package com.sbz.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
            text = "sBz // OUTPUT & PROTECTION",
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = SbzCyan
        )

        Text(
            text = if (config.limiterEnabled) {
                "OUTPUT STAGE ONLINE  •  LIMITER ACTIVE"
            } else {
                "OUTPUT STAGE ONLINE  •  LIMITER BYPASSED"
            },
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            color = SbzTextSecondary
        )

        // ============================================================
        // MASTER GAIN
        // ============================================================

        RetroOutputPanel(
            title = "MASTER // OUTPUT GAIN",
            active = true
        ) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {

                Text(
                    text = "MASTER LEVEL",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = SbzTextPrimary
                )

                Text(
                    text = if (config.masterGainDb > 0f) {
                        "+%.1f dB".format(config.masterGainDb)
                    } else {
                        "%.1f dB".format(config.masterGainDb)
                    },
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = if (config.masterGainDb > 0f) {
                        SbzAmber
                    } else {
                        SbzCyan
                    }
                )
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text = "GANANCIA MAESTRA DE SALIDA",
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                color = SbzTextSecondary
            )

            Slider(
                value = config.masterGainDb,
                onValueChange = viewModel::setMasterGain,
                valueRange = -24f..12f,
                steps = 71,
                colors = SliderDefaults.colors(
                    thumbColor = SbzCyan,
                    activeTrackColor = SbzCyan,
                    inactiveTrackColor = SbzBorder
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutputScaleLabel("-24 dB")
                OutputScaleLabel("0 dB")
                OutputScaleLabel("+12 dB")
            }
        }

        // ============================================================
        // BALANCE
        // ============================================================

        RetroOutputPanel(
            title = "STEREO // CHANNEL BALANCE",
            active = true
        ) {

            val balanceText = when {
                config.balance == 0f -> "CENTRO"
                config.balance < 0f ->
                    "IZQUIERDA %.0f%%".format(
                        -config.balance * 100f
                    )

                else ->
                    "DERECHA %.0f%%".format(
                        config.balance * 100f
                    )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {

                Text(
                    text = "BALANCE L / R",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = SbzTextPrimary
                )

                Text(
                    text = balanceText,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = SbzCyan
                )
            }

            Spacer(Modifier.height(4.dp))

            Slider(
                value = config.balance,
                onValueChange = viewModel::setBalance,
                valueRange = -1f..1f,
                colors = SliderDefaults.colors(
                    thumbColor = SbzCyan,
                    activeTrackColor = SbzCyan,
                    inactiveTrackColor = SbzBorder
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {

                Text(
                    text = "L 100%",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = SbzTextSecondary
                )

                Text(
                    text = "CENTER",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = if (config.balance == 0f) {
                        SbzCyan
                    } else {
                        SbzTextSecondary
                    }
                )

                Text(
                    text = "R 100%",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = SbzTextSecondary
                )
            }
        }

        // ============================================================
        // LIMITER
        // ============================================================

        RetroOutputPanel(
            title = "LIMITER // BRICKWALL PROTECTION",
            active = config.limiterEnabled
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
                        text = "DIGITAL OUTPUT PROTECTION",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = SbzTextPrimary
                    )

                    Spacer(Modifier.height(3.dp))

                    Text(
                        text = "Etapa final de DynamicsProcessing",
                        fontSize = 10.sp,
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
                        checkedTrackColor = SbzCyanDim,
                        uncheckedThumbColor = SbzTextSecondary,
                        uncheckedTrackColor = SbzSurface
                    )
                )
            }

            Spacer(Modifier.height(12.dp))

            OutputLimiterSlider(
                label = "Techo / Umbral",
                value = config.limiterThresholdDb,
                range = -12f..0f,
                unit = "dB",
                enabled = config.limiterEnabled
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

            OutputLimiterSlider(
                label = "Tiempo de ataque",
                value = config.limiterAttackMs,
                range = 0.1f..10f,
                unit = "ms",
                enabled = config.limiterEnabled
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

            OutputLimiterSlider(
                label = "Tiempo de liberación",
                value = config.limiterReleaseMs,
                range = 10f..400f,
                unit = "ms",
                enabled = config.limiterEnabled
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

            OutputLimiterSlider(
                label = "Relación de compresión",
                value = config.limiterRatio,
                range = 10f..50f,
                unit = ":1",
                enabled = config.limiterEnabled
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

        // ============================================================
        // PROTECTION STATUS
        // ============================================================

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = SbzSurface,
            shape = RoundedCornerShape(6.dp),
            border = BorderStroke(
                1.dp,
                SbzBorder
            )
        ) {

            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = SbzGreen,
                    modifier = Modifier.size(22.dp)
                )

                Column(
                    modifier = Modifier.weight(1f)
                ) {

                    Text(
                        text = "DSP SAFETY MONITOR",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = SbzGreen
                    )

                    Spacer(Modifier.height(3.dp))

                    Text(
                        text = "Protección continua contra NaN, Infinity y desbordamientos digitales en el flujo de audio.",
                        fontSize = 10.sp,
                        color = SbzTextSecondary
                    )
                }

                Text(
                    text = "● OK",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    color = SbzGreen
                )
            }
        }

        Spacer(Modifier.height(4.dp))
    }
}

// ====================================================================
// RETRO OUTPUT PANEL
// ====================================================================

@Composable
private fun RetroOutputPanel(
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
                    text = if (active) "● ACTIVE" else "○ BYPASS",
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
// SCALE LABEL
// ====================================================================

@Composable
private fun OutputScaleLabel(
    text: String
) {
    Text(
        text = text,
        fontFamily = FontFamily.Monospace,
        fontSize = 8.sp,
        color = SbzTextSecondary
    )
}

// ====================================================================
// LIMITER SLIDER
// ====================================================================

@Composable
private fun OutputLimiterSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    unit: String,
    enabled: Boolean,
    onValueChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier.padding(vertical = 3.dp)
    ) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {

            Text(
                text = label,
                fontSize = 10.sp,
                color = if (enabled) {
                    SbzTextSecondary
                } else {
                    SbzTextSecondary.copy(alpha = 0.55f)
                }
            )

            Text(
                text = "%.1f %s".format(value, unit),
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
