package com.sbz.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbz.dsp.model.DspConfig
import com.sbz.ui.MainViewModel
import com.sbz.ui.components.EqCurveVisualizer
import com.sbz.ui.components.SbzFader
import com.sbz.ui.theme.*

@Composable
fun EqualizerScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val config by viewModel.config.collectAsState()
    val topScrollState = rememberScrollState()
    val bottomScrollState = rememberScrollState()
    val frequencies = remember { DspConfig.FREQUENCIES.toList() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SbzBackground)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        EqCurveVisualizer(
            eqGains = config.eqGains,
            toneBassDb = config.toneBassDb,
            toneMidDb = config.toneMidDb,
            toneTrebleDb = config.toneTrebleDb,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "ECUALIZADOR GRÁFICO ISO DE 32 BANDAS",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = SbzCyan
                )

                val maxGain = config.eqGains.maxOrNull() ?: 0f
                val minGain = config.eqGains.minOrNull() ?: 0f

                Text(
                    text = "Máx.: +%.1f dB / Mín.: %.1f dB • Pasos de 0,5 dB"
                        .format(maxGain, minGain),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = SbzTextSecondary
                )
            }

            OutlinedButton(
                onClick = { viewModel.resetEq() },
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = SbzCyan),
                border = androidx.compose.foundation.BorderStroke(1.dp, SbzBorder)
            ) {
                Icon(
                    imageVector = Icons.Default.RestartAlt,
                    contentDescription = "Plano",
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(text = "Plano (0 dB)", fontSize = 11.sp)
            }
        }

        Spacer(Modifier.height(8.dp))

        // 32 bandas en dos filas de 16. Cada fila conserva su desplazamiento
        // horizontal independiente porque cada fader mide 52 dp de ancho.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            EqBandRow(
                frequencies = frequencies,
                gains = config.eqGains,
                startIndex = 0,
                endIndex = 16,
                scrollState = topScrollState,
                onGainChanged = viewModel::setBandGain,
                modifier = Modifier.weight(1f)
            )

            EqBandRow(
                frequencies = frequencies,
                gains = config.eqGains,
                startIndex = 16,
                endIndex = 32,
                scrollState = bottomScrollState,
                onGainChanged = viewModel::setBandGain,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun EqBandRow(
    frequencies: List<Float>,
    gains: List<Float>,
    startIndex: Int,
    endIndex: Int,
    scrollState: androidx.compose.foundation.ScrollState,
    onGainChanged: (Int, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (index in startIndex until endIndex) {
            val frequency = frequencies[index]
            SbzFader(
                gainDb = gains.getOrElse(index) { 0.0f },
                frequencyHz = frequency,
                onGainChanged = { gain -> onGainChanged(index, gain) }
            )
        }
    }
}
