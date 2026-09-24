package com.sbz.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
fun DashboardScreen(
    viewModel: MainViewModel,
    onNavigateToTab: (Int) -> Unit,
    onOpenPresets: () -> Unit,
    modifier: Modifier = Modifier
) {
    val config by viewModel.config.collectAsState()
    val engineStatus by viewModel.engineStatus.collectAsState()
    val presets by viewModel.presets.collectAsState()
    val selectedPresetId by viewModel.selectedPresetId.collectAsState()

    val currentPreset = presets.find { it.id == selectedPresetId }?.name ?: "Custom"
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Master Power / Bypass Hero Card
        MasterStatusCard(
            isEnabled = config.isEnabled,
            engineStatus = engineStatus,
            currentPresetName = currentPreset,
            onToggle = { viewModel.toggleDsp() },
            onReclaim = { viewModel.reclaimDspControl() },
            onOpenPresets = onOpenPresets
        )

        // Master Output Level & Balance Section
        MasterGainSection(
            masterGainDb = config.masterGainDb,
            balance = config.balance,
            headroomComp = config.computeHeadroomSafeguard(),
            onMasterGainChange = { viewModel.setMasterGain(it) },
            onBalanceChange = { viewModel.setBalance(it) }
        )

        // Quick Preset Selector Row
        QuickPresetsRow(
            presets = presets,
            selectedId = selectedPresetId,
            onSelect = { viewModel.applyPreset(it) },
            onManageAll = onOpenPresets
        )

        // 3-Band Tone Quick Control Section
        ToneQuickSection(
            bass = config.toneBassDb,
            mid = config.toneMidDb,
            treble = config.toneTrebleDb,
            onToneChange = { b, m, t -> viewModel.setTone(b, m, t) },
            onExpand = { onNavigateToTab(2) }
        )

        // DSP Pipeline Stages Overview Grid
        DspStagesGrid(
            config = config,
            onNavigateToTab = onNavigateToTab
        )
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SbzCardBg),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isEnabled) SbzCyan.copy(alpha = 0.4f) else SbzBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "SBZ NATIVE DSP",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = if (isEnabled) SbzCyan else SbzTextSecondary
                    )
                    Text(
                        text = if (isEnabled) "Engine Active" else "Bypassed",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = SbzTextPrimary
                    )
                }

                // Power Toggle Button
                FilledIconButton(
                    onClick = onToggle,
                    modifier = Modifier.size(54.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (isEnabled) SbzCyan else SbzSurfaceVariant,
                        contentColor = if (isEnabled) SbzBackground else SbzTextDisabled
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.PowerSettingsNew,
                        contentDescription = "Master Bypass",
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Hardware Engine Status Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(SbzSurface)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (engineStatus.isRunning) SbzGreen else SbzRed)
                    )
                    Text(
                        text = "Global Session 0: ${if (engineStatus.globalSessionAttached) "Bound" else "Standalone"}",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = SbzTextSecondary
                    )
                }

                Text(
                    text = "Sessions: ${engineStatus.activeSessions.size}",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = SbzCyan
                )

                Text(
                    text = "Reclaim",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = SbzAmber,
                    modifier = Modifier.clickable { onReclaim() }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Active Preset indicator
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenPresets() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Preset: $currentPresetName",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = SbzTextSecondary
                )
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = SbzTextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun MasterGainSection(
    masterGainDb: Float,
    balance: Float,
    headroomComp: Float,
    onMasterGainChange: (Float) -> Unit,
    onBalanceChange: (Float) -> Unit
) {
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
                    text = "MASTER OUTPUT GAIN",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = SbzTextSecondary
                )
                Text(
                    text = if (masterGainDb > 0f) "+%.1f dB".format(masterGainDb) else "%.1f dB".format(masterGainDb),
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = if (masterGainDb > 0f) SbzAmber else SbzCyan
                )
            }

            Slider(
                value = masterGainDb,
                onValueChange = onMasterGainChange,
                valueRange = -24f..12f,
                steps = 71, // 0.5 dB steps
                colors = SliderDefaults.colors(
                    thumbColor = SbzCyan,
                    activeTrackColor = SbzCyan,
                    inactiveTrackColor = SbzSurfaceVariant
                )
            )

            // Balance & Headroom indicators
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "L/R Balance: ${if (balance == 0f) "Center" else if (balance < 0f) "L %.0f%%".format(-balance * 100f) else "R %.0f%%".format(balance * 100f)}",
                    fontSize = 11.sp,
                    color = SbzTextSecondary
                )

                if (headroomComp < 0f) {
                    Text(
                        text = "Safeguard: %.1f dB".format(headroomComp),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = SbzAmber
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickPresetsRow(
    presets: List<Preset>,
    selectedId: String,
    onSelect: (Preset) -> Unit,
    onManageAll: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "DSP PRESETS",
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = SbzTextSecondary
            )
            Text(
                text = "Library",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = SbzCyan,
                modifier = Modifier.clickable { onManageAll() }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(presets.take(6)) { preset ->
                val isSelected = preset.id == selectedId
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelect(preset) },
                    label = { Text(preset.name, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = SbzCyanDim,
                        selectedLabelColor = Color.White,
                        containerColor = SbzSurface,
                        labelColor = SbzTextSecondary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = if (isSelected) SbzCyan else SbzBorder,
                        enabled = true,
                        selected = isSelected
                    )
                )
            }
        }
    }
}

@Composable
private fun ToneQuickSection(
    bass: Float,
    mid: Float,
    treble: Float,
    onToneChange: (Float, Float, Float) -> Unit,
    onExpand: () -> Unit
) {
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
                    text = "3-BAND TONE",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = SbzTextSecondary
                )
                Text(
                    text = "Detail",
                    fontSize = 12.sp,
                    color = SbzCyan,
                    modifier = Modifier.clickable { onExpand() }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                KnobControl(
                    value = bass,
                    range = -12f..12f,
                    label = "Bass",
                    unit = "dB",
                    onValueChange = { onToneChange(it, mid, treble) }
                )
                KnobControl(
                    value = mid,
                    range = -12f..12f,
                    label = "Mid",
                    unit = "dB",
                    onValueChange = { onToneChange(bass, it, treble) }
                )
                KnobControl(
                    value = treble,
                    range = -12f..12f,
                    label = "Treble",
                    unit = "dB",
                    onValueChange = { onToneChange(bass, mid, it) }
                )
            }
        }
    }
}

@Composable
private fun DspStagesGrid(
    config: DspConfig,
    onNavigateToTab: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "PIPELINE MODULES",
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = SbzTextSecondary
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ModuleCard(
                title = "32-Band EQ",
                subtitle = "Active Graphic Filters",
                isActive = config.isEnabled,
                modifier = Modifier.weight(1f),
                onClick = { onNavigateToTab(1) }
            )
            ModuleCard(
                title = "MDRC",
                subtitle = "4-Band Dynamic Compressor",
                isActive = config.isEnabled && config.mdrcEnabled,
                modifier = Modifier.weight(1f),
                onClick = { onNavigateToTab(3) }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ModuleCard(
                title = "Spatial",
                subtitle = if (config.virtualizerEnabled) "${config.virtualizerStrength / 10}% Width" else "Bypassed",
                isActive = config.isEnabled && config.virtualizerEnabled,
                modifier = Modifier.weight(1f),
                onClick = { onNavigateToTab(4) }
            )
            ModuleCard(
                title = "Limiter",
                subtitle = if (config.limiterEnabled) "Ceiling: ${config.limiterThresholdDb} dB" else "Off",
                isActive = config.isEnabled && config.limiterEnabled,
                modifier = Modifier.weight(1f),
                onClick = { onNavigateToTab(5) }
            )
        }
    }
}

@Composable
private fun ModuleCard(
    title: String,
    subtitle: String,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = SbzCardBg),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isActive) SbzCyan.copy(alpha = 0.5f) else SbzBorder
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = SbzTextPrimary
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isActive) SbzCyan else SbzTextDisabled)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = SbzTextSecondary
            )
        }
    }
}
