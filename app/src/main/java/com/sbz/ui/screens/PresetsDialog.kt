package com.sbz.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbz.ui.MainViewModel
import com.sbz.ui.theme.SbzCyan

@Composable
fun PresetsDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val presets by viewModel.presets.collectAsState()
    val selectedPresetId by viewModel.selectedPresetId.collectAsState()

    var showSaveDialog by remember { mutableStateOf(false) }
    var presetName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,

        title = {
            Text(
                text = "Preajustes",
                fontSize = 20.sp
            )
        },

        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                if (presets.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No hay preajustes guardados")
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(
                            items = presets,
                            key = { it.id }
                        ) { preset ->

                            val selected = preset.id == selectedPresetId

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (selected) {
                                        SbzCyan.copy(alpha = 0.12f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    }
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            horizontal = 12.dp,
                                            vertical = 8.dp
                                        ),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = preset.name,
                                            fontSize = 15.sp
                                        )

                                        if (selected) {
                                            Text(
                                                text = "ACTIVO",
                                                color = SbzCyan,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }

                                    IconButton(
                                        onClick = {
                                            viewModel.applyPreset(preset)
                                            onDismiss()
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Aplicar"
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            viewModel.deletePreset(preset.id)
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Eliminar"
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = {
                        presetName = ""
                        showSaveDialog = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text("Guardar preajuste")
                }
            }
        },

        confirmButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text("Cerrar")
            }
        }
    )

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = {
                showSaveDialog = false
            },

            title = {
                Text("Guardar preajuste")
            },

            text = {
                OutlinedTextField(
                    value = presetName,
                    onValueChange = {
                        presetName = it
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text("Nombre")
                    },
                    singleLine = true
                )
            },

            confirmButton = {
                TextButton(
                    enabled = presetName.isNotBlank(),
                    onClick = {
                        viewModel.saveNewPreset(presetName.trim())
                        showSaveDialog = false
                    }
                ) {
                    Text("Guardar")
                }
            },

            dismissButton = {
                TextButton(
                    onClick = {
                        showSaveDialog = false
                    }
                ) {
                    Text("Cancelar")
                }
            }
        )
    }
}
