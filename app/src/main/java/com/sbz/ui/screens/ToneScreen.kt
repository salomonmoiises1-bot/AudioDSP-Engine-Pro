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
import com.sbz.ui.MainViewModel
import com.sbz.ui.components.KnobControl
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
        // 3-Band Tone Stage
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SbzCardBg),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, SbzBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "ANALOG-STYLE TONE STAGE",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = SbzCyan
                )
                Text(
                    text = "Low-Shelf, Peaking Mid, and High-Shelf shelving filters",
                    fontSize = 11.sp,
                    color = SbzTextSecondary
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    KnobControl(
                        value = config.toneBassDb,
                        range = -12f..12f,
                        label = "Bass (Low)",
                        unit = "dB",
                        onValueChange = { viewModel.setTone(it, config.toneMidDb, config.toneTrebleDb) }
                    )
                    KnobControl(
                        value = config.toneMidDb,
                        range = -12f..12f,
                        label = "Mid (1 kHz)",
                        unit = "dB",
                        onValueChange = { viewModel.setTone(config.toneBassDb, it, config.toneTrebleDb) }
                    )
                    KnobControl(
                        value = config.toneTrebleDb,
                        range = -12f..12f,
                        label = "Treble (High)",
                        unit = "dB",
                        onValueChange = { viewModel.setTone(config.toneBassDb, config.toneMidDb, it) }
                    )
                }
            }
        }

        // Bass Boost Stage
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SbzCardBg),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, if (config.bassBoostEnabled) SbzCyan.copy(alpha = 0.4f) else SbzBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "BASS BOOST (HARDWARE EFFECT)",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = SbzTextPrimary
                        )
                        Text(
                            text = "Harmonic sub-bass enhancement",
                            fontSize = 11.sp,
                            color = SbzTextSecondary
                        )
                    }

                    Switch(
                        checked = config.bassBoostEnabled,
                        onCheckedChange = { viewModel.setBassBoost(it, config.bassBoostStrength) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SbzCyan,
                            checkedTrackColor = SbzCyanDim
                        )
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Strength",
                        fontSize = 12.sp,
                        color = SbzTextSecondary
                    )
                    Text(
                        text = "${config.bassBoostStrength / 10}%",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = SbzCyan
                    )
                }

                Slider(
                    value = config.bassBoostStrength.toFloat(),
                    onValueChange = { viewModel.setBassBoost(config.bassBoostEnabled, it.toInt().toShort()) },
                    valueRange = 0f..1000f,
                    enabled = config.bassBoostEnabled,
                    colors = SliderDefaults.colors(
                        thumbColor = SbzCyan,
                        activeTrackColor = SbzCyan
                    )
                )
            }
        }

        // Pre-Gain Stage
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
                        text = "INPUT PRE-GAIN",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = SbzTextPrimary
                    )
                    Text(
                        text = if (config.preGainDb > 0f) "+%.1f dB".format(config.preGainDb) else "%.1f dB".format(config.preGainDb),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = SbzAmber
                    )
                }

                Slider(
                    value = config.preGainDb,
                    onValueChange = { viewModel.setPreGain(it) },
                    valueRange = -12f..12f,
                    steps = 47, // 0.5 dB steps
                    colors = SliderDefaults.colors(
                        thumbColor = SbzAmber,
                        activeTrackColor = SbzAmber
                    )
                )
            }
        }

        // Headroom Safeguard Protection Status
        val safeguardDb = config.computeHeadroomSafeguard()
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SbzSurface),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, if (safeguardDb < 0f) SbzAmber.copy(alpha = 0.5f) else SbzBorder)
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "HEADROOM SAFEGUARD",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = if (safeguardDb < 0f) SbzAmber else SbzGreen
                    )
                    Text(
                        text = if (safeguardDb < 0f)
                            "Dynamic attenuation applied to prevent clipping from cumulative EQ/Tone boost"
                        else
                            "Output gain levels within safe non-clipping linear limits",
                        fontSize = 11.sp,
                        color = SbzTextSecondary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "%.1f dB".format(safeguardDb),
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = if (safeguardDb < 0f) SbzAmber else SbzGreen
                )
            }
        }
    }
}
