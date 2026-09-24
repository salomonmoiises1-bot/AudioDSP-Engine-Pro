package com.sbz.ui.screens

import androidx.compose.foundation.background
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
fun SpatialScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val config by viewModel.config.collectAsState()
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SbzCardBg),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, if (config.virtualizerEnabled) SbzCyan.copy(alpha = 0.4f) else SbzBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "HARDWARE VIRTUALIZER",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = SbzCyan
                        )
                        Text(
                            text = "Native Android AudioEffect Spatial Stage",
                            fontSize = 11.sp,
                            color = SbzTextSecondary
                        )
                    }

                    Switch(
                        checked = config.virtualizerEnabled,
                        onCheckedChange = { viewModel.setVirtualizer(it, config.virtualizerStrength) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SbzCyan,
                            checkedTrackColor = SbzCyanDim
                        )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Spatial Widening Intensity",
                        fontSize = 12.sp,
                        color = SbzTextSecondary
                    )
                    Text(
                        text = "${config.virtualizerStrength / 10}%",
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = SbzCyan
                    )
                }

                Slider(
                    value = config.virtualizerStrength.toFloat(),
                    onValueChange = { viewModel.setVirtualizer(config.virtualizerEnabled, it.toInt().toShort()) },
                    valueRange = 0f..1000f,
                    enabled = config.virtualizerEnabled,
                    colors = SliderDefaults.colors(
                        thumbColor = SbzCyan,
                        activeTrackColor = SbzCyan
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Headphones,
                        contentDescription = null,
                        tint = SbzCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Virtualizer acoustic widening is best experienced with headphones or stereo speakers.",
                        fontSize = 11.sp,
                        color = SbzTextSecondary
                    )
                }
            }
        }
    }
}
