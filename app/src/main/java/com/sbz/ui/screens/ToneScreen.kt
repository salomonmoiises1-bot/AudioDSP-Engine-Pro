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
import com.sbz.ui.MainViewModel
import com.sbz.ui.components.SbzFader
import com.sbz.ui.theme.*

@Composable
fun ToneScreen(
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

        // ============================================================
        // HEADER
        // ============================================================

        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "sBz // TONE CONTROL",
                fontSize = 18.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = SbzCyan
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = "ANALOG TONE // INPUT CONTROL // HEADROOM",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = SbzTextSecondary
            )
        }

        // ============================================================
        // 3-BAND TONE
        // ============================================================

        RetroTonePanel(
            title = "TONE // 3-BAND ANALOG STAGE",
            subtitle = "LOW-SHELF // PEAK MID // HIGH-SHELF"
        ) {

            Text(
                text = "Ajuste tonal independiente por sección de frecuencia.",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = SbzTextSecondary
            )

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SbzFader(
                    gainDb = config.toneBassDb,
                    frequencyHz = 100f,
                    label = "GRAVES",
                    minGainDb = -12f,
                    maxGainDb = 12f,
                    stepDb = 0.5f,
                    onGainChanged = {
                        viewModel.setTone(
                            it,
                            config.toneMidDb,
                            config.toneTrebleDb
                        )
                    }
                )

                SbzFader(
                    gainDb = config.toneMidDb,
                    frequencyHz = 1000f,
                    label = "MEDIOS",
                    minGainDb = -12f,
                    maxGainDb = 12f,
                    stepDb = 0.5f,
                    onGainChanged = {
                        viewModel.setTone(
                            config.toneBassDb,
                            it,
                            config.toneTrebleDb
                        )
                    }
                )

                SbzFader(
                    gainDb = config.toneTrebleDb,
                    frequencyHz = 10000f,
                    label = "AGUDOS",
                    minGainDb = -12f,
                    maxGainDb = 12f,
                    stepDb = 0.5f,
                    onGainChanged = {
                        viewModel.setTone(
                            config.toneBassDb,
                            config.toneMidDb,
                            it
                        )
                    }
                )
            }

            Spacer(Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ToneFrequencyLabel("100 Hz")
                ToneFrequencyLabel("1 kHz")
                ToneFrequencyLabel("10 kHz")
            }
        }

        // ============================================================
        // BASS BOOST
        // ============================================================

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = SbzCardBg
            ),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(
                1.dp,
                if (config.bassBoostEnabled) {
                    SbzCyan.copy(alpha = 0.55f)
                } else {
                    SbzBorder
                }
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
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
                            text = "BASS BOOST // HARDWARE EFFECT",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = SbzCyan
                        )

                        Spacer(Modifier.height(3.dp))

                        Text(
                            text = "Realce armónico de subgraves",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = SbzTextSecondary
                        )
                    }

                    Switch(
                        checked = config.bassBoostEnabled,
                        onCheckedChange = {
                            viewModel.setBassBoost(
                                it,
                                config.bassBoostStrength
                            )
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SbzCyan,
                            checkedTrackColor = SbzCyanDim,
                            uncheckedThumbColor = SbzTextSecondary,
                            uncheckedTrackColor = SbzBorder
                        )
                    )
                }

                HorizontalDivider(
                    color = SbzBorder.copy(alpha = 0.7f),
                    thickness = 1.dp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "BOOST INTENSITY",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = SbzTextSecondary
                    )

                    Text(
                        text = "${config.bassBoostStrength / 10}%",
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = if (config.bassBoostEnabled) {
                            SbzCyan
                        } else {
                            SbzTextSecondary
                        }
                    )
                }

                Slider(
                    value = config.bassBoostStrength.toFloat(),
                    onValueChange = {
                        viewModel.setBassBoost(
                            config.bassBoostEnabled,
                            it.toInt().toShort()
                        )
                    },
                    valueRange = 0f..1000f,
                    enabled = config.bassBoostEnabled,
                    colors = SliderDefaults.colors(
                        thumbColor = SbzCyan,
                        activeTrackColor = SbzCyan,
                        inactiveTrackColor = SbzBorder,
                        disabledThumbColor = SbzTextSecondary,
                        disabledActiveTrackColor = SbzBorder,
                        disabledInactiveTrackColor = SbzBorder
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "0",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = SbzTextSecondary
                    )

                    Text(
                        text = "500",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = SbzTextSecondary
                    )

                    Text(
                        text = "1000",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = SbzTextSecondary
                    )
                }
            }
        }

        // ============================================================
        // PRE-GAIN
        // ============================================================

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = SbzCardBg
            ),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(
                1.dp,
                SbzBorder
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
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
                            text = "INPUT // PRE-GAIN",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = SbzCyan
                        )

                        Spacer(Modifier.height(3.dp))

                        Text(
                            text = "Nivel de entrada antes del procesamiento DSP",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = SbzTextSecondary
                        )
                    }

                    Text(
                        text = if (config.preGainDb > 0f) {
                            "+%.1f dB".format(config.preGainDb)
                        } else {
                            "%.1f dB".format(config.preGainDb)
                        },
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = SbzAmber
                    )
                }

                Slider(
                    value = config.preGainDb,
                    onValueChange = {
                        viewModel.setPreGain(it)
                    },
                    valueRange = -12f..12f,
                    steps = 47,
                    colors = SliderDefaults.colors(
                        thumbColor = SbzAmber,
                        activeTrackColor = SbzAmber,
                        inactiveTrackColor = SbzBorder
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "-12 dB",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = SbzTextSecondary
                    )

                    Text(
                        text = "0 dB",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = SbzTextSecondary
                    )

                    Text(
                        text = "+12 dB",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = SbzTextSecondary
                    )
                }
            }
        }

        // ============================================================
        // HEADROOM SAFEGUARD
        // ============================================================

        val safeguardDb = config.computeHeadroomSafeguard()

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = SbzSurface
            ),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(
                1.dp,
                if (safeguardDb < 0f) {
                    SbzAmber.copy(alpha = 0.55f)
                } else {
                    SbzBorder
                }
            )
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "HEADROOM // SAFEGUARD",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = if (safeguardDb < 0f) {
                            SbzAmber
                        } else {
                            SbzGreen
                        }
                    )

                    Text(
                        text = "%.1f dB".format(safeguardDb),
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = if (safeguardDb < 0f) {
                            SbzAmber
                        } else {
                            SbzGreen
                        }
                    )
                }

                HorizontalDivider(
                    color = SbzBorder.copy(alpha = 0.7f),
                    thickness = 1.dp
                )

                Text(
                    text = if (safeguardDb < 0f) {
                        "ATENUACIÓN DINÁMICA ACTIVA — se compensa el aumento acumulado de EQ y tono para reducir el riesgo de clipping."
                    } else {
                        "HEADROOM DISPONIBLE — los niveles de ganancia permanecen dentro de límites lineales seguros."
                    },
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = SbzTextSecondary,
                    lineHeight = 15.sp
                )

                Text(
                    text = if (safeguardDb < 0f) {
                        "STATUS: PROTECTED"
                    } else {
                        "STATUS: NOMINAL"
                    },
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = if (safeguardDb < 0f) {
                        SbzAmber
                    } else {
                        SbzGreen
                    }
                )
            }
        }
    }
}

// ====================================================================
// RETRO TONE PANEL
// ====================================================================

@Composable
private fun RetroTonePanel(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = SbzCardBg
        ),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
            1.dp,
            SbzBorder
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            Text(
                text = title,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = SbzCyan
            )

            Text(
                text = subtitle,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = SbzTextSecondary
            )

            HorizontalDivider(
                color = SbzBorder.copy(alpha = 0.7f),
                thickness = 1.dp
            )

            content()
        }
    }
}

// ====================================================================
// FREQUENCY LABEL
// ====================================================================

@Composable
private fun ToneFrequencyLabel(
    text: String
) {
    Text(
        text = text,
        fontSize = 9.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        color = SbzTextSecondary
    )
}
