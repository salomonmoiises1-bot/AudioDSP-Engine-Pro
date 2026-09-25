package com.sbz.ui.screens

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
import androidx.compose.material.icons.filled.Cerrar
import androidx.compose.material.icons.filled.Eliminar
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
    var showGuardarDialog by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SbzSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, SbzBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Dialog Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "BIBLIOTECA DE PREAJUSTES",
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = SbzCyan
                        )
                        Text(
                            text = "Gestionar configuraciones DSP",
                            fontSize = 11.sp,
                            color = SbzTextSecondary
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Cerrar, contentDescription = "Cerrar", tint = SbzTextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Guardar Current Config Button
                Button(
                    onClick = { showGuardarDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SbzCyan, contentColor = SbzBackground)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Guardar configuración actual", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(12.dp))

                // List of Presets
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(presets) { preset ->
                        val isSelected = preset.id == selectedId
                        PresetItemRow(
                            preset = preset,
                            isSelected = isSelected,
                            onSelect = {
                                viewModel.applyPreset(preset)
                                onDismiss()
                            },
                            onEliminar = {
                                viewModel.deletePreset(preset.id)
                            }
                        )
                    }
                }
            }
        }
    }

    if (showGuardarDialog) {
        GuardarPresetDialog(
            onDismiss = { showGuardarDialog = false },
            onGuardar = { name ->
                viewModel.saveNewPreset(name)
                showGuardarDialog = false
            }
        )
    }
}

@Composable
private fun PresetItemRow(
    preset: Preset,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onEliminar: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) SbzCardBg else SbzSurfaceVariant)
            .border(
                1.dp,
                if (isSelected) SbzCyan else Color.Transparent,
                RoundedCornerShape(8.dp)
            )
            .clickable { onSelect() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Activo",
                    tint = SbzCyan,
                    modifier = Modifier.size(16.dp)
                )
            }
            Column {
                Text(
                    text = preset.name,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) SbzCyan else SbzTextPrimary
                )
                Text(
                    text = if (preset.isSystem) "Preajuste de fábrica" else "Preajuste personalizado",
                    fontSize = 10.sp,
                    color = SbzTextSecondary
                )
            }
        }

        if (!preset.isSystem) {
            IconButton(
                onClick = onEliminar,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Eliminar,
                    contentDescription = "Eliminar",
                    tint = SbzRed.copy(alpha = 0.8f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun GuardarPresetDialog(
    onDismiss: () -> Unit,
    onGuardar: (String) -> Unit
) {
    var presetName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Guardar preajuste", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = SbzTextPrimary)
        },
        text = {
            Column {
                Text(
                    "Enter a name for your custom DSP configuration:",
                    fontSize = 12.sp,
                    color = SbzTextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = presetName,
                    onValueChange = { presetName = it },
                    singleLine = true,
                    placeholder = { Text("ej. Mi ecualización de estudio", fontSize = 12.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SbzCyan,
                        unfocusedBorderColor = SbzBorder
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (presetName.isNotBlank()) onGuardar(presetName.trim()) },
                enabled = presetName.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = SbzCyan, contentColor = SbzBackground)
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = SbzTextSecondary)
            }
        },
        containerColor = SbzSurface,
        shape = RoundedCornerShape(12.dp)
    )
}
