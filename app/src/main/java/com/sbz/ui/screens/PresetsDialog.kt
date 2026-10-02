package com.sbz.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.ui.window.Dialog
import com.sbz.dsp.model.Preset
import com.sbz.ui.MainViewModel
import com.sbz.ui.theme.*

@Composable
fun PresetsDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val presets by viewModel.presets.collectAsState()
    val selectedId by viewModel.selectedPresetId.collectAsState()
    var showSaveDialog by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss
    ) {

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.86f),
            shape = RoundedCornerShape(7.dp),
            colors = CardDefaults.cardColors(
                containerColor = SbzCardBg
            ),
            border = BorderStroke(
                1.dp,
                SbzCyan.copy(alpha = 0.45f)
            )
        ) {

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
            ) {

                // ====================================================
                // HEADER
                // ====================================================

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {

                        Text(
                            text = "sBz // PRESET BANK",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = SbzCyan
                        )

                        Spacer(Modifier.height(3.dp))

                        Text(
                            text = "DSP CONFIGURATION LIBRARY",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            color = SbzTextSecondary
                        )
                    }

                    IconButton(
                        onClick = onDismiss
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = SbzTextSecondary
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                HorizontalDivider(
                    color = SbzBorder,
                    thickness = 1.dp
                )

                Spacer(Modifier.height(10.dp))

                // ====================================================
                // SAVE BUTTON
                // ====================================================

                Button(
                    onClick = {
                        showSaveDialog = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SbzCyan,
                        contentColor = SbzBackground
                    )
                ) {

                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp)
                    )

                    Spacer(Modifier.width(7.dp))

                    Text(
                        text = "GUARDAR CONFIGURACIÓN ACTUAL",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                }

                Spacer(Modifier.height(10.dp))

                // ====================================================
                // PRESET COUNT
                // ====================================================

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {

                    Text(
                        text = "AVAILABLE PRESETS",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                        color = SbzCyan
                    )

                    Text(
                        text = "%02d".format(presets.size),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                        color = SbzTextSecondary
                    )
                }

                Spacer(Modifier.height(7.dp))

                // ====================================================
                // PRESET LIST
                // ====================================================

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {

                    items(
                        items = presets,
                        key = { it.id }
                    ) { preset ->

                        val isSelected = preset.id == selectedId

                        PresetItemRow(
                            preset = preset,
                            isSelected = isSelected,
                            onSelect = {
                                viewModel.applyPreset(preset)
                                onDismiss()
                            },
                            onDelete = {
                                viewModel.deletePreset(preset.id)
                            }
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "SELECT A PRESET TO APPLY ITS COMPLETE DSP CONFIGURATION",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.sp,
                    color = SbzTextSecondary
                )
            }
        }
    }

    if (showSaveDialog) {

        SavePresetDialog(
            onDismiss = {
                showSaveDialog = false
            },
            onSave = { name ->
                viewModel.saveNewPreset(name)
                showSaveDialog = false
            }
        )
    }
}

// ====================================================================
// PRESET ROW
// ====================================================================

@Composable
private fun PresetItemRow(
    preset: Preset,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(5.dp))
            .background(
                if (isSelected) {
                    SbzCyan.copy(alpha = 0.08f)
                } else {
                    SbzSurface
                }
            )
            .border(
                BorderStroke(
                    1.dp,
                    if (isSelected) {
                        SbzCyan.copy(alpha = 0.7f)
                    } else {
                        SbzBorder
                    }
                ),
                RoundedCornerShape(5.dp)
            )
            .clickable {
                onSelect()
            }
            .padding(
                horizontal = 10.dp,
                vertical = 9.dp
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {

        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {

            // ========================================================
            // STATUS INDICATOR
            // ========================================================

            Surface(
                modifier = Modifier.size(25.dp),
                shape = RoundedCornerShape(3.dp),
                color = if (isSelected) {
                    SbzCyan.copy(alpha = 0.14f)
                } else {
                    SbzSurfaceVariant
                },
                border = BorderStroke(
                    1.dp,
                    if (isSelected) {
                        SbzCyan.copy(alpha = 0.6f)
                    } else {
                        SbzBorder
                    }
                )
            ) {

                Box(
                    contentAlignment = Alignment.Center
                ) {

                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Activo",
                            tint = SbzCyan,
                            modifier = Modifier.size(14.dp)
                        )
                    } else {
                        Text(
                            text = "DSP",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 7.sp,
                            color = SbzTextSecondary
                        )
                    }
                }
            }

            // ========================================================
            // NAME
            // ========================================================

            Column {

                Text(
                    text = preset.name,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = if (isSelected) {
                        FontWeight.Bold
                    } else {
                        FontWeight.Medium
                    },
                    fontSize = 11.sp,
                    color = if (isSelected) {
                        SbzCyan
                    } else {
                        SbzTextPrimary
                    },
                    maxLines = 1
                )

                Spacer(Modifier.height(2.dp))

                Text(
                    text = if (preset.isSystem) {
                        "SYSTEM PRESET"
                    } else {
                        "CUSTOM PRESET"
                    },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.sp,
                    color = SbzTextSecondary
                )
            }
        }

        // ============================================================
        // DELETE
        // ============================================================

        if (!preset.isSystem) {

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(32.dp)
            ) {

                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Eliminar",
                    tint = SbzRed.copy(alpha = 0.85f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// ====================================================================
// SAVE PRESET DIALOG
// ====================================================================

@Composable
private fun SavePresetDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {

    var presetName by remember {
        mutableStateOf("")
    }

    AlertDialog(
        onDismissRequest = onDismiss,

        containerColor = SbzCardBg,

        shape = RoundedCornerShape(7.dp),

        title = {

            Column {

                Text(
                    text = "sBz // SAVE PRESET",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = SbzCyan
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = "CREATE CUSTOM DSP CONFIGURATION",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = SbzTextSecondary
                )
            }
        },

        text = {

            Column {

                Text(
                    text = "Nombre de la configuración:",
                    fontSize = 11.sp,
                    color = SbzTextSecondary
                )

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = presetName,
                    onValueChange = {
                        presetName = it
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = {
                        Text(
                            text = "ej. Mi EQ de estudio",
                            fontSize = 11.sp,
                            color = SbzTextSecondary
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SbzCyan,
                        unfocusedBorderColor = SbzBorder,
                        focusedTextColor = SbzTextPrimary,
                        unfocusedTextColor = SbzTextPrimary,
                        cursorColor = SbzCyan
                    ),
                    shape = RoundedCornerShape(4.dp)
                )
            }
        },

        confirmButton = {

            Button(
                onClick = {
                    if (presetName.isNotBlank()) {
                        onSave(presetName.trim())
                    }
                },
                enabled = presetName.isNotBlank(),
                shape = RoundedCornerShape(4.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SbzCyan,
                    contentColor = SbzBackground,
                    disabledContainerColor = SbzSurfaceVariant,
                    disabledContentColor = SbzTextSecondary
                )
            ) {

                Text(
                    text = "GUARDAR",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
            }
        },

        dismissButton = {

            TextButton(
                onClick = onDismiss
            ) {

                Text(
                    text = "CANCELAR",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = SbzTextSecondary
                )
            }
        }
    )
}
