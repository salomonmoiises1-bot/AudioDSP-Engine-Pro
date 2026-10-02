package com.sbz.ui.screens

import androidx.compose.foundation.BorderStroke
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // ============================================================
        // HEADER
        // ============================================================

        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "sBz // SPATIAL PROCESSOR",
                fontSize = 18.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = SbzCyan
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = "VIRTUALIZACIÓN ESPACIAL // NATIVE AUDIOEFFECT",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = SbzTextSecondary
            )
        }

        // ============================================================
        // MAIN VIRTUALIZER PANEL
        // ============================================================

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = SbzCardBg
            ),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(
                1.dp,
                if (config.virtualizerEnabled) {
                    SbzCyan.copy(alpha = 0.55f)
                } else {
                    SbzBorder
                }
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {

                // ----------------------------------------------------
                // PANEL HEADER
                // ----------------------------------------------------

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "SPATIAL // VIRTUALIZER",
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = SbzCyan
                        )

                        Spacer(Modifier.height(3.dp))

                        Text(
                            text = "Etapa espacial nativa de Android AudioEffect",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = SbzTextSecondary
                        )
                    }

                    Switch(
                        checked = config.virtualizerEnabled,
                        onCheckedChange = {
                            viewModel.setVirtualizer(
                                it,
                                config.virtualizerStrength
                            )
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SbzCyan,
                            checkedTrackColor = SbzCyanDim,
                            uncheckedThumbColor = SbzTextSecondary,
                            uncheckedTrackColor = SbzBorder
                        )
                    )
                }

                HorizontalDivider(
                    color = SbzBorder.copy(alpha = 0.7f),
                    thickness = 1.dp
                )

                // ----------------------------------------------------
                // STATUS
                // ----------------------------------------------------

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "PROCESSING STATUS",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = SbzTextSecondary
                    )

                    Text(
                        text = if (config.virtualizerEnabled) {
                            "ACTIVE"
                        } else {
                            "BYPASSED"
                        },
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = if (config.virtualizerEnabled) {
                            SbzCyan
                        } else {
                            SbzTextSecondary
                        }
                    )
                }

                // ----------------------------------------------------
                // STRENGTH
                // ----------------------------------------------------

                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SPATIAL INTENSITY",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = SbzTextSecondary
                        )

                        Text(
                            text = "${config.virtualizerStrength / 10}%",
                            fontSize = 15.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = if (config.virtualizerEnabled) {
                                SbzCyan
                            } else {
                                SbzTextSecondary
                            }
                        )
                    }

                    Spacer(Modifier.height(4.dp))

                    Slider(
                        value = config.virtualizerStrength.toFloat(),
                        onValueChange = {
                            viewModel.setVirtualizer(
                                config.virtualizerEnabled,
                                it.toInt().toShort()
                            )
                        },
                        valueRange = 0f..1000f,
                        enabled = config.virtualizerEnabled,
                        colors = SliderDefaults.colors(
                            thumbColor = SbzCyan,
                            activeTrackColor = SbzCyan,
                            inactiveTrackColor = SbzBorder,
                            disabledThumbColor = SbzTextSecondary,
                            disabledActiveTrackColor = SbzBorder,
                            disabledInactiveTrackColor = SbzBorder
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "0",
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            color = SbzTextSecondary
                        )

                        Text(
                            text = "500",
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            color = SbzTextSecondary
                        )

                        Text(
                            text = "1000",
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            color = SbzTextSecondary
                        )
                    }
                }
            }
        }

        // ============================================================
        // TECHNICAL INFORMATION PANEL
        // ============================================================

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = SbzCardBg
            ),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(
                1.dp,
                SbzBorder
            )
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {

                Text(
                    text = "SPATIAL // MONITOR",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = SbzCyan
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Headphones,
                        contentDescription = null,
                        tint = SbzCyan,
                        modifier = Modifier.size(18.dp)
                    )

                    Text(
                        text = "La expansión acústica del virtualizador " +
                                "se disfruta mejor con auriculares o " +
                                "altavoces estéreo.",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = SbzTextSecondary,
                        lineHeight = 15.sp
                    )
                }

                HorizontalDivider(
                    color = SbzBorder.copy(alpha = 0.7f),
                    thickness = 1.dp
                )

                Text(
                    text = "ENGINE: ANDROID AUDIOEFFECT",
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = SbzTextSecondary
                )

                Text(
                    text = "MODE: ${if (config.virtualizerEnabled) "ACTIVE" else "BYPASS"}",
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = if (config.virtualizerEnabled) {
                        SbzCyan
                    } else {
                        SbzTextSecondary
                    }
                )
            }
        }
    }
}
