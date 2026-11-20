package com.checkpointalerts.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.checkpointalerts.app.data.Checkpoint
import com.checkpointalerts.app.data.SettingsStore
import com.checkpointalerts.app.ui.CheckpointViewModel
import com.checkpointalerts.app.ui.DetailScreen
import com.checkpointalerts.app.ui.HomeScreen
import com.checkpointalerts.app.ui.SettingsScreen

private sealed interface Screen {
    data object Home : Screen
    data object Settings : Screen
    data class Detail(val checkpoint: Checkpoint) : Screen
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CheckpointAlertsTheme {
                val viewModel: CheckpointViewModel = viewModel()
                val uiState by viewModel.uiState.collectAsState()
                var screen by remember { mutableStateOf<Screen>(Screen.Home) }

                val context = LocalContext.current
                val settings = remember { SettingsStore(context) }
                val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted -> viewModel.setNotificationGranted(granted) }

                // Notifications are the point of the app: ask once on first
                // launch. After that the in-app card handles re-asking.
                LaunchedEffect(Unit) {
                    val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        ContextCompat.checkSelfPermission(
                            context, Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
                    } else {
                        true
                    }
                    viewModel.setNotificationGranted(granted)
                    if (!granted && !settings.notificationPromptShown) {
                        settings.notificationPromptShown = true
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                BackHandler(enabled = screen != Screen.Home) {
                    screen = Screen.Home
                }

                when (val current = screen) {
                    Screen.Home -> HomeScreen(
                        uiState = uiState,
                        onRefresh = { viewModel.refresh() },
                        onOpenSettings = { screen = Screen.Settings },
                        onSelect = { screen = Screen.Detail(it) },
                        onToggleShowAll = { viewModel.setShowAll(!uiState.showAll) },
                        onRequestNotificationPermission = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                    )
                    Screen.Settings -> SettingsScreen(
                        uiState = uiState,
                        onBack = { screen = Screen.Home },
                        onScopeChange = { viewModel.setScope(it) },
                        onCountyChange = { viewModel.setHomeCounty(it) },
                        onNotificationsEnabledChange = { viewModel.setNotificationsEnabled(it) },
                        onRequestNotificationPermission = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        onRefresh = { viewModel.refresh() }
                    )
                    is Screen.Detail -> DetailScreen(
                        checkpoint = current.checkpoint,
                        onBack = { screen = Screen.Home }
                    )
                }
            }
        }
    }
}

@Composable
fun CheckpointAlertsTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dark ->
            dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colors, content = content)
}
