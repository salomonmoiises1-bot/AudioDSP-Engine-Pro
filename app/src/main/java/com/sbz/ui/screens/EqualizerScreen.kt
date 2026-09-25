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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SbzBackground)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // Real-Time Calculated Frequency Response Curve
        EqCurveVisualizer(
            eqGains = config.eqGains,
            toneBassDb = config.toneBassDb,
            toneMidDb = config.toneMidDb,
            toneTrebleDb = config.toneTrebleDb,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Toolbar: Band Count, Peak Info, Zero Reset Button
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
                    text = "Máximo: +%.1f dB / Mínimo: %.1f dB • Pasos de 0,5 dB".format(maxGain, minGain),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = SbzTextSecondary
                )
            }

            // Quick Actions
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Plano (0 dB)", fontSize = 11.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 32 Isolated Tactile Faders
        // Notice: The container is a LazyRow for horizontal panning across 32 bands.
        // Each SbzFader independently consumes vertical drag gestures with change.consume()
        // so touching or sliding a fader NEVER triggers unintended scrolling!
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            LazyRow(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                itemsIndexed(DspConfig.FREQUENCIES.toList()) { index, frequency ->
                    val gain = config.eqGains.getOrElse(index) { 0.0f }
                    SbzFader(
                        gainDb = gain,
                        frequencyHz = frequency,
                        onGainChanged = { newGain ->
                            viewModel.setBandGain(index, newGain)
                        }
                    )
                }
            }
        }
    }
}
