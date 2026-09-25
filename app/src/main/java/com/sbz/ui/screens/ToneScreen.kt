package com.sbz.ui.screens

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
fun ToneScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val config by viewModel.config.collectAsState()
    val scrollState = rememberScrollState()

    Column(
        modifier.fillMaxSize().verticalScroll(scrollState).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SbzCardBg),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, SbzBorder)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("ETAPA DE TONO ESTILO ANALÓGICO", 12.sp, FontFamily.Monospace, FontWeight.Bold, color = SbzCyan)
                Text("Filtros Low-Shelf, Peaking de medios y High-Shelf", 11.sp, color = SbzTextSecondary)
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
                    KnobControl(config.toneBassDb, -12f..12f, "Graves (Bajos)", "dB") {
                        viewModel.setTone(it, config.toneMidDb, config.toneTrebleDb)
                    }
                    KnobControl(config.toneMidDb, -12f..12f, "Medios (1 kHz)", "dB") {
                        viewModel.setTone(config.toneBassDb, it, config.toneTrebleDb)
                    }
                    KnobControl(config.toneTrebleDb, -12f..12f, "Agudos (Altos)", "dB") {
                        viewModel.setTone(config.toneBassDb, config.toneMidDb, it)
                    }
                }
            }
        }

        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SbzCardBg),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp,
                if (config.bassBoostEnabled) SbzCyan.copy(alpha = 0.4f) else SbzBorder)
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column {
                        Text("REFUERZO DE GRAVES (EFECTO DE HARDWARE)", 12.sp, FontFamily.Monospace, FontWeight.Bold, color = SbzTextPrimary)
                        Text("Realce armónico de subgraves", 11.sp, color = SbzTextSecondary)
                    }
                    Switch(
                        config.bassBoostEnabled,
                        { viewModel.setBassBoost(it, config.bassBoostStrength) },
                        colors = SwitchDefaults.colors(checkedThumbColor = SbzCyan, checkedTrackColor = SbzCyanDim)
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Text("Intensidad", 12.sp, color = SbzTextSecondary)
                    Text("${config.bassBoostStrength / 10}%", 12.sp, FontFamily.Monospace, color = SbzCyan)
                }
                Slider(
                    config.bassBoostStrength.toFloat(),
                    { viewModel.setBassBoost(config.bassBoostEnabled, it.toInt().toShort()) },
                    valueRange = 0f..1000f,
                    enabled = config.bassBoostEnabled,
                    colors = SliderDefaults.colors(thumbColor = SbzCyan, activeTrackColor = SbzCyan)
                )
            }
        }

        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SbzCardBg),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, SbzBorder)
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text("PRE-GANANCIA DE ENTRADA", 12.sp, FontFamily.Monospace, FontWeight.Bold, color = SbzTextPrimary)
                    Text(
                        if (config.preGainDb > 0f) "+%.1f dB".format(config.preGainDb) else "%.1f dB".format(config.preGainDb),
                        12.sp, FontFamily.Monospace, color = SbzAmber
                    )
                }
                Slider(
                    config.preGainDb,
                    { viewModel.setPreGain(it) },
                    valueRange = -12f..12f,
                    steps = 47,
                    colors = SliderDefaults.colors(thumbColor = SbzAmber, activeTrackColor = SbzAmber)
                )
            }
        }

        val safeguardDb = config.computeHeadroomSafeguard()
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SbzSurface),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp,
                if (safeguardDb < 0f) SbzAmber.copy(alpha = 0.5f) else SbzBorder)
        ) {
            Row(Modifier.padding(14.dp), Alignment.CenterVertically, Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("PROTECCIÓN DE HEADROOM", 11.sp, FontFamily.Monospace, FontWeight.Bold,
                        color = if (safeguardDb < 0f) SbzAmber else SbzGreen)
                    Text(
                        if (safeguardDb < 0f)
                            "Atenuación dinámica aplicada para evitar saturación por el realce acumulado del EQ/Tono"
                        else
                            "Niveles de ganancia de salida dentro de límites lineales seguros, sin saturación",
                        11.sp, color = SbzTextSecondary
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text("%.1f dB".format(safeguardDb), 14.sp, FontFamily.Monospace, FontWeight.Bold,
                    color = if (safeguardDb < 0f) SbzAmber else SbzGreen)
            }
        }
    }
}
