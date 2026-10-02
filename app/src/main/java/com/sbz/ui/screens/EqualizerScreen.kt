package com.sbz.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbz.dsp.model.DspConfig
import com.sbz.ui.MainViewModel
import com.sbz.ui.components.EqCurveVisualizer
import com.sbz.ui.theme.SbzBackground
import com.sbz.ui.theme.SbzBorder
import com.sbz.ui.theme.SbzCyan
import com.sbz.ui.theme.SbzTextSecondary
import kotlin.math.round

@Composable
fun EqualizerScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val config by viewModel.config.collectAsState()
    val frequencies = remember { DspConfig.FREQUENCIES.toList() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SbzBackground)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    BorderStroke(1.dp, SbzBorder),
                    RoundedCornerShape(6.dp)
                ),
            shape = RoundedCornerShape(6.dp),
            color = SbzBackground
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "sBz // GRAPHIC EQ",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = SbzCyan
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "32 BANDAS · ISO · DIGITAL SIGNAL PROCESSOR",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.sp,
                        color = SbzTextSecondary
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "EQ32",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = SbzCyan
                    )
                    Text(
                        text = "ACTIVE",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 7.sp,
                        color = SbzTextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(154.dp)
                .border(
                    BorderStroke(1.dp, SbzBorder),
                    RoundedCornerShape(6.dp)
                ),
            shape = RoundedCornerShape(6.dp),
            color = SbzBackground
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "RESPONSE CURVE",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = SbzCyan
                    )
                    Text(
                        text = "±12 dB",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.sp,
                        color = SbzTextSecondary
                    )
                }

                Spacer(modifier = Modifier.height(3.dp))

                EqCurveVisualizer(
                    eqGains = config.eqGains,
                    toneBassDb = config.toneBassDb,
                    toneMidDb = config.toneMidDb,
                    toneTrebleDb = config.toneTrebleDb,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(5.dp),
            color = SbzBackground
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val maxGain = config.eqGains.maxOrNull() ?: 0f
                val minGain = config.eqGains.minOrNull() ?: 0f

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "BAND CONTROL",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = SbzCyan
                    )
                    Text(
                        text = "MAX %+.1f dB   MIN %+.1f dB   STEP 0.5 dB"
                            .format(maxGain, minGain),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 7.sp,
                        color = SbzTextSecondary
                    )
                }

                OutlinedButton(
                    onClick = { viewModel.resetEq() },
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(
                        horizontal = 8.dp,
                        vertical = 3.dp
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = SbzCyan
                    ),
                    border = BorderStroke(1.dp, SbzBorder)
                ) {
                    Icon(
                        imageVector = Icons.Default.RestartAlt,
                        contentDescription = "Plano",
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "FLAT",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(5.dp))

        /*
         * EQ32 EN UNA SOLA LINEA
         *
         * Las 32 bandas permanecen en una única fila.
         * La fila tiene scroll horizontal para no sacrificar
         * legibilidad en pantallas pequeñas.
         */
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .border(
                    BorderStroke(1.dp, SbzBorder),
                    RoundedCornerShape(5.dp)
                ),
            shape = RoundedCornerShape(5.dp),
            color = SbzBackground
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 4.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (index in frequencies.indices) {
                    val frequency = frequencies[index]
                    val gain = config.eqGains.getOrElse(index) { 0f }

                    EqBandControl(
                        frequencyHz = frequency,
                        gainDb = gain,
                        onGainChanged = { value ->
                            viewModel.setBandGain(index, value)
                        },
                        modifier = Modifier
                            .width(34.dp)
                            .fillMaxHeight()
                    )
                }
            }
        }
    }
}

@Composable
private fun EqBandControl(
    frequencyHz: Float,
    gainDb: Float,
    onGainChanged: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "%+.1f".format(gainDb),
            fontFamily = FontFamily.Monospace,
            fontSize = 7.sp,
            fontWeight = FontWeight.Bold,
            color = if (gainDb != 0f) SbzCyan else SbzTextSecondary,
            maxLines = 1
        )

        Spacer(modifier = Modifier.height(2.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Slider(
                value = gainDb.coerceIn(-12f, 12f),
                onValueChange = { value ->
                    val stepped = (round(value * 2f) / 2f)
                        .coerceIn(-12f, 12f)

                    if (stepped != gainDb) {
                        onGainChanged(stepped)
                    }
                },
                valueRange = -12f..12f,
                steps = 47,
                modifier = Modifier
                    .width(145.dp)
                    .height(24.dp)
                    .rotate(-90f),
                colors = SliderDefaults.colors(
                    thumbColor = SbzCyan,
                    activeTrackColor = SbzCyan,
                    inactiveTrackColor = SbzBorder,
                    activeTickColor = SbzCyan,
                    inactiveTickColor = SbzBorder
                )
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = formatFrequency(frequencyHz),
            fontFamily = FontFamily.Monospace,
            fontSize = 6.sp,
            fontWeight = FontWeight.Bold,
            color = SbzTextSecondary,
            maxLines = 1
        )
    }
}

private fun formatFrequency(frequencyHz: Float): String {
    return when {
        frequencyHz >= 1000f -> {
            val khz = frequencyHz / 1000f
            if (khz >= 10f) {
                "%.0fk".format(khz)
            } else {
                "%.1fk".format(khz)
            }
        }

        else -> "%.0f".format(frequencyHz)
    }
}
