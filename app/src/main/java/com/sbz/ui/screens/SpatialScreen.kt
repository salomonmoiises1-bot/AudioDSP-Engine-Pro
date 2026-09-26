package com.sbz.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
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
fun SpatialScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val config by viewModel.config.collectAsState()

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = SbzCardBg),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, if (config.virtualizerEnabled) SbzCyan.copy(alpha = 0.4f) else SbzBorder)) {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column {
                        Text("VIRTUALIZADOR DE HARDWARE", 12.sp, FontFamily.Monospace, FontWeight.Bold, color = SbzCyan)
                        Text("Etapa espacial nativa de Android AudioEffect", 11.sp, color = SbzTextSecondary)
                    }
                    Switch(config.virtualizerEnabled,
                        { viewModel.setVirtualizer(it, config.virtualizerStrength) },
                        colors = SwitchDefaults.colors(checkedThumbColor = SbzCyan, checkedTrackColor = SbzCyanDim))
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Text("Intensidad de expansión espacial", 12.sp, color = SbzTextSecondary)
                    Text("${config.virtualizerStrength / 10}%", 14.sp, FontFamily.Monospace, FontWeight.Bold, color = SbzCyan)
                }
                Slider(config.virtualizerStrength.toFloat(),
                    { viewModel.setVirtualizer(config.virtualizerEnabled, it.toInt().toShort()) },
                    valueRange = 0f..1000f, enabled = config.virtualizerEnabled,
                    colors = SliderDefaults.colors(thumbColor = SbzCyan, activeTrackColor = SbzCyan))
                Spacer(Modifier.height(8.dp))
                Row(Alignment.CenterVertically, Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Headphones, null, tint = SbzCyan, modifier = Modifier.size(16.dp))
                    Text("La expansión acústica del virtualizador se disfruta mejor con auriculares o altavoces estéreo.",
                        11.sp, color = SbzTextSecondary)
                }
            }
        }
    }
}
