package com.sbz.ui.screens

import androidx.compose.foundation.BorderStroke
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
import com.sbz.dsp.SbzDspEngine
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

    val currentPreset =
        presets.find { it.id == selectedPresetId }?.name ?: "Personalizado"

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SbzBackground)
            .verticalScroll(scrollState)
            .padding(horizontal = 9.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {

        // ============================================================
        // SBZ DSP STATUS
        // ============================================================

        MasterStatusCard(
            isEnabled = config.isEnabled,
            engineStatus = engineStatus,
            currentPresetName = currentPreset,
            onToggle = { viewModel.toggleDsp() },
            onReclaim = { viewModel.reclaimDspControl() },
            onOpenPresets = onOpenPresets
        )

        // ============================================================
        // MASTER OUTPUT
        // ============================================================

        MasterGainSection(
            masterGainDb = config.masterGainDb,
            balance = config.balance,
            headroomComp = config.computeHeadroomSafeguard(),
            onMasterGainChange = { viewModel.setMasterGain(it) },
            onBalanceChange = { viewModel.setBalance(it) }
        )

        // ============================================================
        // PRESETS
        // ============================================================

        QuickPresetsRow(
            presets = presets,
            selectedId = selectedPresetId,
            onSelect = { viewModel.applyPreset(it) },
            onManageAll = onOpenPresets
        )

        // ============================================================
        // TONO
        // ============================================================

        ToneQuickSection(
            bass = config.toneBassDb,
            mid = config.toneMidDb,
            treble = config.toneTrebleDb,
            onToneChange = { b, m, t ->
                viewModel.setTone(b, m, t)
            },
            onExpand = {
                onNavigateToTab(2)
            }
        )

        // ============================================================
        // DSP MODULES
        // ============================================================

        DspStagesGrid(
            config = config,
            onNavigateToTab = onNavigateToTab
        )
    }
}

@Composable
private fun MasterStatusCard(
    isEnabled: Boolean,
    engineStatus: SbzDspEngine.EngineStatus,
    currentPresetName: String,
    onToggle: () -> Unit,
    onReclaim: () -> Unit,
    onOpenPresets: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                BorderStroke(
                    1.dp,
                    if (isEnabled) {
                        SbzCyan.copy(alpha = 0.65f)
                    } else {
                        SbzBorder
                    }
                ),
                RoundedCornerShape(7.dp)
            ),
        shape = RoundedCornerShape(7.dp),
        color = SbzCardBg
    ) {

        Column(
            modifier = Modifier.padding(10.dp)
        ) {

            // --------------------------------------------------------
            // HEADER
            // --------------------------------------------------------

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Column(
                    modifier = Modifier.weight(1f)
                ) {

                    Text(
                        text = "sBz // NATIVE DSP",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isEnabled) {
                            SbzCyan
                        } else {
                            SbzTextSecondary
                        }
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = if (isEnabled) {
                            "PROCESSING ACTIVE"
                        } else {
                            "PROCESSING BYPASSED"
                        },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = SbzTextPrimary
                    )
                }

                FilledIconButton(
                    onClick = onToggle,
                    modifier = Modifier.size(48.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (isEnabled) {
                            SbzCyan
                        } else {
                            SbzSurfaceVariant
                        },
                        contentColor = if (isEnabled) {
                            SbzBackground
                        } else {
                            SbzTextDisabled
                        }
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.PowerSettingsNew,
                        contentDescription = "Activar o desactivar DSP",
                        modifier = Modifier.size(25.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // --------------------------------------------------------
            // ENGINE STATUS DISPLAY
            // --------------------------------------------------------

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(4.dp),
                color = SbzSurface
            ) {

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = 8.dp,
                            vertical = 7.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(
                                if (engineStatus.isRunning) {
                                    SbzGreen
                                } else {
                                    SbzRed
                                }
                            )
                    )

                    Spacer(modifier = Modifier.width(7.dp))

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {

                        Text(
                            text = "GLOBAL SESSION 0",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = SbzTextSecondary
                        )

                        Text(
                            text = if (
                                engineStatus.globalSessionAttached
                            ) {
                                "VINCULADA"
                            } else {
                                "INDEPENDIENTE"
                            },
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            color = SbzTextPrimary
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.End
                    ) {

                        Text(
                            text = "SESSIONS",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 7.sp,
                            color = SbzTextSecondary
                        )

                        Text(
                            text = engineStatus.activeSessions.size.toString(),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = SbzCyan
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = "RECLAIM",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = SbzAmber,
                        modifier = Modifier.clickable {
                            onReclaim()
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(7.dp))

            // --------------------------------------------------------
            // PRESET DISPLAY
            // --------------------------------------------------------

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .clickable {
                        onOpenPresets()
                    }
                    .padding(
                        horizontal = 5.dp,
                        vertical = 4.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Text(
                    text = "PRESET",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = SbzCyan
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = currentPresetName,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = SbzTextPrimary,
                    modifier = Modifier.weight(1f)
                )

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = SbzTextSecondary,
                    modifier = Modifier.size(16.dp)
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
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                BorderStroke(1.dp, SbzBorder),
                RoundedCornerShape(7.dp)
            ),
        shape = RoundedCornerShape(7.dp),
        color = SbzCardBg
    ) {

        Column(
            modifier = Modifier.padding(10.dp)
        ) {

            // --------------------------------------------------------
            // MASTER HEADER
            // --------------------------------------------------------

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Column(
                    modifier = Modifier.weight(1f)
                ) {

                    Text(
                        text = "MASTER OUTPUT",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = SbzCyan
                    )

                    Text(
                        text = "MAIN GAIN",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 7.sp,
                        color = SbzTextSecondary
                    )
                }

                Text(
                    text = if (masterGainDb >= 0f) {
                        "+%.1f dB".format(masterGainDb)
                    } else {
                        "%.1f dB".format(masterGainDb)
                    },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (masterGainDb > 0f) {
                        SbzAmber
                    } else {
                        SbzCyan
                    }
                )
            }

            Slider(
                value = masterGainDb.coerceIn(-24f, 12f),
                onValueChange = onMasterGainChange,
                valueRange = -24f..12f,
                steps = 71,
                colors = SliderDefaults.colors(
                    thumbColor = SbzCyan,
                    activeTrackColor = SbzCyan,
                    inactiveTrackColor = SbzSurfaceVariant
                )
            )

            // --------------------------------------------------------
            // OUTPUT INFO
            // --------------------------------------------------------

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Text(
                    text = when {
                        balance == 0f -> "BALANCE  CENTER"
                        balance < 0f ->
                            "BALANCE  L %.0f%%"
                                .format(-balance * 100f)

                        else ->
                            "BALANCE  R %.0f%%"
                                .format(balance * 100f)
                    },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.sp,
                    color = SbzTextSecondary,
                    modifier = Modifier.weight(1f)
                )

                if (headroomComp < 0f) {
                    Text(
                        text = "HEADROOM %.1f dB"
                            .format(headroomComp),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
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
            verticalAlignment = Alignment.CenterVertically
        ) {

            Column(
                modifier = Modifier.weight(1f)
            ) {

                Text(
                    text = "DSP PRESET BANK",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = SbzCyan
                )

                Text(
                    text = "MEMORY / QUICK ACCESS",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 7.sp,
                    color = SbzTextSecondary
                )
            }

            Text(
                text = "LIBRARY",
                fontFamily = FontFamily.Monospace,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = SbzCyan,
                modifier = Modifier.clickable {
                    onManageAll()
                }
            )
        }

        Spacer(modifier = Modifier.height(5.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {

            items(presets.take(6)) { preset ->

                val isSelected = preset.id == selectedId

                FilterChip(
                    selected = isSelected,
                    onClick = {
                        onSelect(preset)
                    },
                    label = {
                        Text(
                            text = preset.name,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            fontWeight = if (isSelected) {
                                FontWeight.Bold
                            } else {
                                FontWeight.Normal
                            }
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = SbzCyanDim,
                        selectedLabelColor = Color.White,
                        containerColor = SbzSurface,
                        labelColor = SbzTextSecondary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = if (isSelected) {
                            SbzCyan
                        } else {
                            SbzBorder
                        },
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
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                BorderStroke(1.dp, SbzBorder),
                RoundedCornerShape(7.dp)
            ),
        shape = RoundedCornerShape(7.dp),
        color = SbzCardBg
    ) {

        Column(
            modifier = Modifier.padding(10.dp)
        ) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Column(
                    modifier = Modifier.weight(1f)
                ) {

                    Text(
                        text = "TONE CONTROL",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = SbzCyan
                    )

                    Text(
                        text = "3-BAND QUICK ADJUST",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 7.sp,
                        color = SbzTextSecondary
                    )
                }

                Text(
                    text = "DETAIL",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = SbzCyan,
                    modifier = Modifier.clickable {
                        onExpand()
                    }
                )
            }

            Spacer(modifier = Modifier.height(7.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {

                KnobControl(
                    value = bass,
                    range = -12f..12f,
                    label = "GRAVES",
                    unit = "dB",
                    onValueChange = {
                        onToneChange(it, mid, treble)
                    }
                )

                KnobControl(
                    value = mid,
                    range = -12f..12f,
                    label = "MEDIOS",
                    unit = "dB",
                    onValueChange = {
                        onToneChange(bass, it, treble)
                    }
                )

                KnobControl(
                    value = treble,
                    range = -12f..12f,
                    label = "AGUDOS",
                    unit = "dB",
                    onValueChange = {
                        onToneChange(bass, mid, it)
                    }
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
    Column(
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {

        Text(
            text = "PROCESSING MODULES",
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = SbzCyan
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {

            ModuleCard(
                title = "EQ32",
                subtitle = "32 BAND GRAPHIC",
                isActive = config.isEnabled,
                modifier = Modifier.weight(1f),
                onClick = {
                    onNavigateToTab(1)
                }
            )

            ModuleCard(
                title = "MDRC",
                subtitle = "4 BAND DYNAMICS",
                isActive = config.isEnabled && config.mdrcEnabled,
                modifier = Modifier.weight(1f),
                onClick = {
                    onNavigateToTab(3)
                }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {

            ModuleCard(
                title = "SPATIAL",
                subtitle = if (config.virtualizerEnabled) {
                    "${config.virtualizerStrength / 10}% AMPLITUDE"
                } else {
                    "BYPASSED"
                },
                isActive = config.isEnabled &&
                    config.virtualizerEnabled,
                modifier = Modifier.weight(1f),
                onClick = {
                    onNavigateToTab(4)
                }
            )

            ModuleCard(
                title = "LIMITER",
                subtitle = if (config.limiterEnabled) {
                    "CEILING ${config.limiterThresholdDb} dB"
                } else {
                    "BYPASSED"
                },
                isActive = config.isEnabled &&
                    config.limiterEnabled,
                modifier = Modifier.weight(1f),
                onClick = {
                    onNavigateToTab(5)
                }
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
    Surface(
        modifier = modifier
            .clickable {
                onClick()
            }
            .border(
                BorderStroke(
                    1.dp,
                    if (isActive) {
                        SbzCyan.copy(alpha = 0.55f)
                    } else {
                        SbzBorder
                    }
                ),
                RoundedCornerShape(6.dp)
            ),
        shape = RoundedCornerShape(6.dp),
        color = SbzCardBg
    ) {

        Column(
            modifier = Modifier.padding(9.dp)
        ) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Text(
                    text = title,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isActive) {
                        SbzCyan
                    } else {
                        SbzTextPrimary
                    },
                    modifier = Modifier.weight(1f)
                )

                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(
                            if (isActive) {
                                SbzCyan
                            } else {
                                SbzTextDisabled
                            }
                        )
                )
            }

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = subtitle,
                fontFamily = FontFamily.Monospace,
                fontSize = 7.sp,
                color = SbzTextSecondary
            )
        }
    }
}
