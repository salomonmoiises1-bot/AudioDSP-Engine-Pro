package com.sbz.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbz.ui.screens.*
import com.sbz.ui.theme.*

sealed class Screen(val title: String, val icon: ImageVector) {
    object Dashboard : Screen("Inicio", Icons.Default.Dashboard)
    object Equalizer : Screen("Ecualizador 32 bandas", Icons.Default.Equalizer)
    object Tone : Screen("Tono", Icons.Default.GraphicEq)
    object Dynamics : Screen("Dinámica", Icons.Default.Compress)
    object Spatial : Screen("Espacial", Icons.Default.SurroundSound)
    object Output : Screen("Salida", Icons.Default.Tune)
}

@Composable
fun SbzApp(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    var selectedTabIndex by remember { mutableStateOf(0) }
    var showPresetsDialog by remember { mutableStateOf(false) }
    val config by viewModel.config.collectAsState()
    val engineStatus by viewModel.engineStatus.collectAsState()

    val screens = listOf(
        Screen.Dashboard,
        Screen.Equalizer,
        Screen.Tone,
        Screen.Dynamics,
        Screen.Spatial,
        Screen.Output
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "sBz",
                            fontSize = 20.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Black,
                            color = SbzCyan
                        )
                        Text(
                            text = "AUDIO DSP",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            color = SbzTextSecondary
                        )
                        Box(
                            Modifier
                                .clip(CircleShape)
                                .background(
                                    if (config.isEnabled && engineStatus.isRunning) {
                                        SbzGreen.copy(alpha = 0.2f)
                                    } else {
                                        SbzRed.copy(alpha = 0.2f)
                                    }
                                )
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (config.isEnabled) "ACTIVO" else "DESACTIVADO",
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = if (config.isEnabled && engineStatus.isRunning) SbzGreen else SbzRed
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showPresetsDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Bookmark,
                            contentDescription = "Preajustes",
                            tint = SbzCyan
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SbzBackground,
                    titleContentColor = SbzTextPrimary
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = SbzSurface,
                tonalElevation = 0.dp
            ) {
                screens.forEachIndexed { index, screen ->
                    val isSelected = selectedTabIndex == index
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { selectedTabIndex = index },
                        icon = {
                            Icon(
                                imageVector = screen.icon,
                                contentDescription = screen.title,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        label = {
                            Text(
                                text = screen.title,
                                fontSize = 9.sp,
                                maxLines = 1,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = SbzCyan,
                            selectedTextColor = SbzCyan,
                            indicatorColor = SbzSurfaceVariant,
                            unselectedIconColor = SbzTextSecondary,
                            unselectedTextColor = SbzTextSecondary
                        )
                    )
                }
            }
        },
        containerColor = SbzBackground
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            when (selectedTabIndex) {
                0 -> DashboardScreen(
                    viewModel,
                    { selectedTabIndex = it },
                    { showPresetsDialog = true }
                )
                1 -> EqualizerScreen(viewModel)
                2 -> ToneScreen(viewModel)
                3 -> DynamicsScreen(viewModel)
                4 -> SpatialScreen(viewModel)
                5 -> OutputScreen(viewModel)
            }
        }
    }

    if (showPresetsDialog) {
        PresetsDialog(viewModel) { showPresetsDialog = false }
    }
}
