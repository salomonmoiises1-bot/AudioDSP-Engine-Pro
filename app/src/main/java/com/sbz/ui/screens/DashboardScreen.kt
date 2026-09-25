package com.sbz.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbz.dsp.model.DspConfig
import com.sbz.dsp.model.Preset
import com.sbz.ui.MainViewModel
import com.sbz.ui.components.KnobControl
import com.sbz.ui.theme.*

@Composable
fun DashboardScreen(viewModel: MainViewModel, onNavigateToTab: (Int) -> Unit, onOpenPresets: () -> Unit, modifier: Modifier = Modifier) {
    val config by viewModel.config.collectAsState()
    val engineStatus by viewModel.engineStatus.collectAsState()
    val presets by viewModel.presets.collectAsState()
    val selectedPresetId by viewModel.selectedPresetId.collectAsState()
    val currentPreset = presets.find { it.id == selectedPresetId }?.name ?: "Personalizado"

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        MasterStatusCard(config.isEnabled, engineStatus, currentPreset, { viewModel.toggleDsp() },
            { viewModel.reclaimDspControl() }, onOpenPresets)
        MasterGainSection(config.masterGainDb, config.balance, config.computeHeadroomSafeguard(),
            { viewModel.setMasterGain(it) }, { viewModel.setBalance(it) })
        QuickPresetsRow(presets, selectedPresetId, { viewModel.applyPreset(it) }, onOpenPresets)
        ToneQuickSection(config.toneBassDb, config.toneMidDb, config.toneTrebleDb,
            { b, m, t -> viewModel.setTone(b, m, t) }, { onNavigateToTab(2) })
        DspStagesGrid(config, onNavigateToTab)
    }
}

@Composable
private fun MasterStatusCard(
    isEnabled: Boolean,
    engineStatus: com.sbz.dsp.SbzDspEngine.EngineStatus,
    currentPresetName: String,
    onToggle: () -> Unit,
    onReclaim: () -> Unit,
    onOpenPresets: () -> Unit
) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = SbzCardBg),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isEnabled) SbzCyan.copy(alpha = 0.4f) else SbzBorder)) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column {
                    Text("SBZ DSP NATIVO", 12.sp, FontFamily.Monospace, FontWeight.Bold,
                        color = if (isEnabled) SbzCyan else SbzTextSecondary)
                    Text(if (isEnabled) "Motor activo" else "Desactivado", 20.sp, fontWeight = FontWeight.Bold, color = SbzTextPrimary)
                }
                FilledIconButton(
                    onClick = onToggle, modifier = Modifier.size(54.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (isEnabled) SbzCyan else SbzSurfaceVariant,
                        contentColor = if (isEnabled) SbzBackground else SbzTextDisabled)
                ) {
                    Icon(Icons.Default.PowerSettingsNew, "Activar/desactivar DSP", Modifier.size(28.dp))
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(SbzSurface)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                Arrangement.SpaceBetween, Alignment.CenterVertically
            ) {
                Row(Alignment.CenterVertically, Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (engineStatus.isRunning) SbzGreen else SbzRed))
                    Text("Sesión global 0: ${if (engineStatus.globalSessionAttached) "Vinculada" else "Independiente"}",
                        11.sp, FontFamily.Monospace, color = SbzTextSecondary)
                }
                Text("Sesiones: ${engineStatus.activeSessions.size}", 11.sp, FontFamily.Monospace, color = SbzCyan)
                Text("Recuperar control", 11.sp, FontWeight.Bold, color = SbzAmber, modifier = Modifier.clickable { onReclaim() })
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().clickable { onOpenPresets() },
                Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("Preajuste: $currentPresetName", 13.sp, fontWeight = FontWeight.Medium, color = SbzTextSecondary)
                Icon(Icons.Default.ChevronRight, null, tint = SbzTextSecondary, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun MasterGainSection(masterGainDb: Float, balance: Float, headroomComp: Float,
                              onMasterGainChange: (Float) -> Unit, onBalanceChange: (Float) -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = SbzCardBg),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, SbzBorder)) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("GANANCIA MAESTRA DE SALIDA", 12.sp, FontFamily.Monospace, FontWeight.Bold, color = SbzTextSecondary)
                Text(if (masterGainDb > 0f) "+%.1f dB".format(masterGainDb) else "%.1f dB".format(masterGainDb),
                    14.sp, FontFamily.Monospace, FontWeight.Bold, color = if (masterGainDb > 0f) SbzAmber else SbzCyan)
            }
            Slider(masterGainDb, onMasterGainChange, valueRange = -24f..12f, steps = 71,
                colors = SliderDefaults.colors(thumbColor = SbzCyan, activeTrackColor = SbzCyan, inactiveTrackColor = SbzSurfaceVariant))
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("Balance L/R: ${if (balance == 0f) "Centro" else if (balance < 0f) "I %.0f%%".format(-balance * 100f) else "D %.0f%%".format(balance * 100f)}",
                    11.sp, color = SbzTextSecondary)
                if (headroomComp < 0f) Text("Protección: %.1f dB".format(headroomComp), 11.sp, FontFamily.Monospace, color = SbzAmber)
            }
        }
    }
}

@Composable
private fun QuickPresetsRow(presets: List<Preset>, selectedId: String, onSelect: (Preset) -> Unit, onManageAll: () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text("PREAJUSTES DSP", 12.sp, FontFamily.Monospace, FontWeight.Bold, color = SbzTextSecondary)
            Text("Biblioteca", 12.sp, fontWeight = FontWeight.Medium, color = SbzCyan, modifier = Modifier.clickable { onManageAll() })
        }
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(presets.take(6)) { preset ->
                val isSelected = preset.id == selectedId
                FilterChip(
                    selected = isSelected, onClick = { onSelect(preset) },
                    label = { Text(preset.name, 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = SbzCyanDim, selectedLabelColor = Color.White,
                        containerColor = SbzSurface, labelColor = SbzTextSecondary),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = if (isSelected) SbzCyan else SbzBorder, enabled = true, selected = isSelected)
                )
            }
        }
    }
}

@Composable
private fun ToneQuickSection(bass: Float, mid: Float, treble: Float,
                             onToneChange: (Float, Float, Float) -> Unit, onExpand: () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = SbzCardBg),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, SbzBorder)) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("TONO DE 3 BANDAS", 12.sp, FontFamily.Monospace, FontWeight.Bold, color = SbzTextSecondary)
                Text("Detalles", 12.sp, color = SbzCyan, modifier = Modifier.clickable { onExpand() })
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
                KnobControl(bass, -12f..12f, "Graves", "dB") { onToneChange(it, mid, treble) }
                KnobControl(mid, -12f..12f, "Medios", "dB") { onToneChange(bass, it, treble) }
                KnobControl(treble, -12f..12f, "Agudos", "dB") { onToneChange(bass, mid, it) }
            }
        }
    }
}

@Composable
private fun DspStagesGrid(config: DspConfig, onNavigateToTab: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("MÓDULOS DE PROCESAMIENTO", 12.sp, FontFamily.Monospace, FontWeight.Bold, color = SbzTextSecondary)
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
            ModuleCard("EQ de 32 bandas", "Filtros gráficos activos", config.isEnabled, Modifier.weight(1f)) { onNavigateToTab(1) }
            ModuleCard("MDRC", "Compresor dinámico de 4 bandas", config.isEnabled && config.mdrcEnabled, Modifier.weight(1f)) { onNavigateToTab(3) }
        }
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
            ModuleCard("Espacial",
                if (config.virtualizerEnabled) "${config.virtualizerStrength / 10}% de anchura" else "Desactivado",
                config.isEnabled && config.virtualizerEnabled, Modifier.weight(1f)) { onNavigateToTab(4) }
            ModuleCard("Limitador",
                if (config.limiterEnabled) "Techo: ${config.limiterThresholdDb} dB" else "Apagado",
                config.isEnabled && config.limiterEnabled, Modifier.weight(1f)) { onNavigateToTab(5) }
        }
    }
}

@Composable
private fun ModuleCard(title: String, subtitle: String, isActive: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(modifier.clickable { onClick() }, colors = CardDefaults.cardColors(containerColor = SbzCardBg),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isActive) SbzCyan.copy(alpha = 0.5f) else SbzBorder)) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text(title, 14.sp, fontWeight = FontWeight.Bold, color = SbzTextPrimary)
                Box(Modifier.size(8.dp).clip(CircleShape).background(if (isActive) SbzCyan else SbzTextDisabled))
            }
            Spacer(Modifier.height(4.dp))
            Text(subtitle, 11.sp, color = SbzTextSecondary)
        }
    }
}
