package com.example.battery_alarm.ui

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.battery_alarm.R
import com.example.battery_alarm.service.BatteryMonitorService
import com.example.battery_alarm.ui.theme.BatteryAlarmTheme

/**
 * Main screen composable for the Battery Alarm app.
 *
 * Why a separate file? This keeps MainActivity concise and focused on lifecycle and
 * composition setup, while this file focuses on UI logic. It also improves testability,
 * reusability, and readability as the project grows (e.g., KMP or multiple screens).
 *
 * ## How the Service Integration Works:
 * 1. The screen maintains a boolean state `isServiceEnabled` that tracks whether
 *    the battery monitoring service should be running.
 * 2. When the user taps the power button, we toggle this state.
 * 3. Based on the new state, we either start or stop the BatteryMonitorService.
 * 4. The state is saved using `rememberSaveable` so it survives configuration changes
 *    (like screen rotation), but note that the actual service state is persisted
 *    by the Android system (services survive activity recreation).
 *
 * ## Important Notes:
 * - The service runs independently of the activity. Even if the app is closed,
 *   the service continues monitoring battery levels.
 * - We use SharedPreferences to persist the service enabled state across app restarts.
 * - The UI state is synchronized with the ACTUAL service state on composition.
 * 
 * ## Reboot Handling:
 * - After a device reboot, the service might not be running even if SharedPreferences
 *   says "enabled = true" (because the BootCompletedReceiver might not have fired yet,
 *   or there was an error starting the service).
 * - We check `BatteryMonitorService.isRunning` to get the ACTUAL service state.
 * - If there's a mismatch, we sync SharedPreferences with the actual state.
 * - This prevents the UI from showing "ON" when the service isn't actually running.
 * 
 * ## Notification Permission (Android 13+):
 * - On Android 13 (API 33) and above, we need runtime permission to show notifications
 * - The `hasNotificationPermission` parameter tells us if the user granted permission
 * - If permission is not granted, we show a warning and request it when user tries to enable service
 * - The `onRequestNotificationPermission` callback triggers the permission request dialog
 * 
 * @param modifier Modifier for the root composable
 * @param hasNotificationPermission Whether the app has permission to show notifications (passed from MainActivity)
 * @param onRequestNotificationPermission Callback to request notification permission when needed
 * @param onNavigateToSettings Callback invoked when the user taps the Settings gear icon.
 *                              MainActivity handles the actual screen switch.
 */
@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    hasNotificationPermission: Boolean = true,
    onRequestNotificationPermission: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    // Get context for starting/stopping the service and accessing SharedPreferences
    val context = LocalContext.current

    // SharedPreferences key for persisting service state
    // This allows the app to remember if the service was enabled when the app restarts
    val prefs = context.getSharedPreferences("battery_alarm_prefs", Context.MODE_PRIVATE)

    // Get the saved preference value
    val savedServiceEnabled = prefs.getBoolean("service_enabled", false)
    
    // Get the ACTUAL service running state
    // This is important after a reboot - SharedPreferences might say "enabled"
    // but the service might not actually be running yet
    val actualServiceRunning = BatteryMonitorService.isRunning
    
    // Determine the initial state:
    // - If service is running, UI should show ON
    // - If service is NOT running but was enabled, we have two choices:
    //   1. Try to start it (but this might fail without notification permission)
    //   2. Show the actual state (OFF) and let user tap to enable
    // We choose option 2 to avoid confusing the user and to respect permission flow
    val initialState = actualServiceRunning
    
    // If there's a mismatch between saved state and actual state, sync SharedPreferences
    // This handles the case where:
    // - Service was enabled, device rebooted, but service didn't restart properly
    // - Now SharedPreferences says "enabled" but service isn't running
    // We update SharedPreferences to match reality so the UI is consistent
    if (savedServiceEnabled != actualServiceRunning) {
        // Update SharedPreferences to match the actual service state
        prefs.edit().putBoolean("service_enabled", actualServiceRunning).apply()
        android.util.Log.d("MainScreen", 
            "Synced SharedPreferences with actual service state: " +
            "saved=$savedServiceEnabled, actual=$actualServiceRunning")
    }

    // Initialize state from the ACTUAL service state (not just SharedPreferences)
    // rememberSaveable survives configuration changes
    var isServiceEnabled by rememberSaveable {
        mutableStateOf(initialState)
    }

    // Animated colors for a smoother, more delightful state transition
    val targetContainerColor = if (isServiceEnabled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val targetContentColor = if (isServiceEnabled) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val containerColor by animateColorAsState(targetValue = targetContainerColor, label = "fabContainerColor")
    val contentColor by animateColorAsState(targetValue = targetContentColor, label = "fabContentColor")

    // Subtle scale animation to emphasize the ON state without being distracting
    val scale by animateFloatAsState(targetValue = if (isServiceEnabled) 1.05f else 1f, label = "fabScale")

    Box(modifier = modifier.fillMaxSize()) {
        // Settings button at the top-end
        // When tapped, it triggers the navigation callback provided by MainActivity
        IconButton(
            onClick = onNavigateToSettings,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = stringResource(R.string.cd_settings),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        // Central toggle action - this is the main button to enable/disable battery monitoring
        FloatingActionButton(
            onClick = {
                // Toggle the service state
                val newState = !isServiceEnabled
                
                // If trying to enable the service, check notification permission first
                // Without notification permission, the service won't be able to show
                // the required foreground notification or battery alerts
                if (newState && !hasNotificationPermission) {
                    // Request permission first - the service will be started
                    // when the user grants permission and taps the button again
                    onRequestNotificationPermission()
                    // Don't proceed with enabling the service yet
                    return@FloatingActionButton
                }
                
                isServiceEnabled = newState

                // Persist the new state to SharedPreferences
                // This ensures the app remembers the setting even after being closed
                prefs.edit().putBoolean("service_enabled", newState).apply()

                // Start or stop the BatteryMonitorService based on the new state
                if (newState) {
                    // User wants to enable monitoring - start the foreground service
                    // The service will show a persistent notification and begin monitoring
                    BatteryMonitorService.start(context)
                } else {
                    // User wants to disable monitoring - stop the service
                    // This will also dismiss the persistent notification
                    BatteryMonitorService.stop(context)
                }
            },
            modifier = Modifier
                .align(Alignment.Center)
                .size(120.dp)
                .then(Modifier) // keeps chaining obvious
                .graphicsLayer(scaleX = scale, scaleY = scale),
            containerColor = containerColor,
            contentColor = contentColor,
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = if (isServiceEnabled) 8.dp else 6.dp,
                pressedElevation = 12.dp
            )
        ) {
            Icon(
                imageVector = Icons.Rounded.PowerSettingsNew,
                contentDescription = if (isServiceEnabled) {
                    stringResource(R.string.cd_disable_monitoring)
                } else {
                    stringResource(R.string.cd_enable_monitoring)
                },
                modifier = Modifier.size(48.dp)
            )
        }

        // Status text below
        Text(
            text = stringResource(
                R.string.battery_monitoring_status,
                stringResource(if (isServiceEnabled) R.string.on_label else R.string.off_label)
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(32.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MainScreenPreview() {
    BatteryAlarmTheme {
        Scaffold { inner ->
            MainScreen(Modifier.padding(inner))
        }
    }
}
