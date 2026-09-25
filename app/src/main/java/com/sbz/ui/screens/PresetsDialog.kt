package com.sbz.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.window.Dialog
import com.sbz.dsp.model.Preset
import com.sbz.ui.MainViewModel
import com.sbz.ui.theme.*

@Composable
fun PresetsDialog(viewModel: MainViewModel, onDismiss: () -> Unit) {
    val presets by viewModel.presets.collectAsState()
    val selectedId by viewModel.selectedPresetId.collectAsState()
    var showSaveDialog by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            Modifier.fillMaxWidth().fillMaxHeight(0.85f),
            RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SbzSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, SbzBorder)
        ) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    Arrangement.SpaceBetween,
                    Alignment.CenterVertically
                ) {
                    Column {
                        Text("BIBLIOTECA DE PREAJUSTES", 14.sp, FontFamily.Monospace, FontWeight.Bold, color = SbzCyan)
                        Text("Gestionar configuraciones del DSP", 11.sp, color = SbzTextSecondary)
                    }
                    IconButton(onDismiss) {
                        Icon(Icons.Default.Close, "Cerrar", tint = SbzTextSecondary)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    { showSaveDialog = true },
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SbzCyan, contentColor = SbzBackground)
                ) {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Guardar configuración actual", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                Spacer(Modifier.height(12.dp))
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(presets) { preset ->
                        PresetItemRow(
                            preset, preset.id == selectedId,
                            { viewModel.applyPreset(preset); onDismiss() },
                            { viewModel.deletePreset(preset.id) }
                        )
                    }
                }
            }
        }
    }

    if (showSaveDialog) {
        SavePresetDialog(
            { showSaveDialog = false },
            {
                viewModel.saveNewPreset(it)
                showSaveDialog = false
            }
        )
    }
}

@Composable
private fun PresetItemRow(
    preset: Preset,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) SbzCardBg else SbzSurfaceVariant)
            .border(1.dp, if (isSelected) SbzCyan else Color.Transparent, RoundedCornerShape(8.dp))
            .clickable { onSelect() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        Arrangement.SpaceBetween, Alignment.CenterVertically
    ) {
        Row(Alignment.CenterVertically, Arrangement.spacedBy(8.dp)) {
            if (isSelected) {
                Icon(Icons.Default.Check, "Activo", tint = SbzCyan, Modifier.size(16.dp))
            }
            Column {
                Text(preset.name, 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) SbzCyan else SbzTextPrimary)
                Text(if (preset.isSystem) "Preajuste de fábrica" else "Preajuste personalizado", 10.sp, color = SbzTextSecondary)
            }
        }
        if (!preset.isSystem) {
            IconButton(onDelete, Modifier.size(28.dp)) {
                Icon(Icons.Default.Delete, "Eliminar", tint = SbzRed.copy(alpha = 0.8f), Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun SavePresetDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var presetName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Guardar preajuste", 16.sp, FontWeight.Bold, color = SbzTextPrimary) },
        text = {
            Column {
                Text("Introducí un nombre para tu configuración DSP personalizada:", 12.sp, color = SbzTextSecondary)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    presetName,
                    { presetName = it },
                    singleLine = true,
                    placeholder = { Text("ej.: Mi EQ de estudio", 12.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SbzCyan, unfocusedBorderColor = SbzBorder
                    )
                )
            }
        },
        confirmButton = {
            Button(
                { if (presetName.isNotBlank()) onSave(presetName.trim()) },
                enabled = presetName.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = SbzCyan, contentColor = SbzBackground)
            ) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onDismiss) { Text("Cancelar", color = SbzTextSecondary) }
        },
        containerColor = SbzSurface,
        shape = RoundedCornerShape(12.dp)
    )
}
