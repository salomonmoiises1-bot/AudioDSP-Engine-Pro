package com.sbz.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
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

    val frequencies = remember {
        DspConfig.FREQUENCIES.toList()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SbzBackground)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {

        // ============================================================
        // CURVA DEL EQ
        // ============================================================
        EqCurveVisualizer(
            eqGains = config.eqGains,
            toneBassDb = config.toneBassDb,
            toneMidDb = config.toneMidDb,
            toneTrebleDb = config.toneTrebleDb,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(10.dp))

        // ============================================================
        // CABECERA
        // ============================================================
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
                contentPadding = PaddingValues(
                    horizontal = 10.dp,
                    vertical = 4.dp
                ),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = SbzCyan
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    SbzBorder
                )
            ) {
                Icon(
                    imageVector = Icons.Default.RestartAlt,
                    contentDescription = "Plano",
                    modifier = Modifier.size(14.dp)
                )

                Spacer(Modifier.width(4.dp))

                Text(
                    text = "Plano (0 dB)",
                    fontSize = 11.sp
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // ============================================================
        // ÁREA DEL EQ: DOS FILAS DE 16 BANDAS
        // ============================================================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {

            // ========================================================
            // FILA 1 — BANDAS 0..15
            // ========================================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Text(
                    text = "GRAVES Y MEDIOS",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = SbzTextSecondary,
                    modifier = Modifier.padding(
                        start = 4.dp,
                        bottom = 2.dp
                    )
                )

                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(
                        horizontal = 4.dp
                    )
                ) {
                    val topRowIndices = (0 until 16)
                        .filter { it < frequencies.size }

                    itemsIndexed(topRowIndices) { _, index ->

                        val frequency = frequencies[index]

                        SbzFader(
                            gainDb = config.eqGains
                                .getOrElse(index) { 0.0f },

                            frequencyHz = frequency,

                            onGainChanged = { gain ->
                                viewModel.setBandGain(
                                    index,
                                    gain
                                )
                            }
                        )
                    }
                }
            }

            // ========================================================
            // SEPARADOR ENTRE FILAS
            // ========================================================
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
            )

            // ========================================================
            // FILA 2 — BANDAS 16..31
            // ========================================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Text(
                    text = "MEDIOS-ALTOS Y AGUDOS",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = SbzTextSecondary,
                    modifier = Modifier.padding(
                        start = 4.dp,
                        bottom = 2.dp
                    )
                )

                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(
                        horizontal = 4.dp
                    )
                ) {
                    val bottomRowIndices =
                        (16 until frequencies.size).toList()

                    itemsIndexed(bottomRowIndices) { _, index ->

                        val frequency = frequencies[index]

                        SbzFader(
                            gainDb = config.eqGains
                                .getOrElse(index) { 0.0f },

                            frequencyHz = frequency,

                            onGainChanged = { gain ->
                                viewModel.setBandGain(
                                    index,
                                    gain
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}
