package com.example.battery_alarm

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.battery_alarm.ui.MainScreen
import com.example.battery_alarm.ui.SettingsScreen
import com.example.battery_alarm.ui.theme.BatteryAlarmTheme

/**
 * MainActivity is the entry point of the Battery Alarm app.
 * 
 * ## Key Responsibilities:
 * 1. Set up the Compose UI with the app theme
 * 2. Handle notification permission requests (required for Android 13+)
 * 3. Manage navigation between the Main screen and the Settings screen
 * 
 * ## Navigation Approach
 * We use a simple boolean state (`showSettings`) to switch between the two screens.
 * This avoids adding the Jetpack Navigation Compose library as a dependency, which
 * would be overkill for an app with only two screens.
 * 
 * How it works:
 * - `showSettings = false` → show MainScreen
 * - `showSettings = true`  → show SettingsScreen
 * - MainScreen's Settings button sets `showSettings = true`
 * - SettingsScreen's back arrow sets `showSettings = false`
 * - We use `rememberSaveable` so the navigation state survives configuration changes
 *   (e.g., screen rotation).
 * 
 * ## Why Request Notification Permission Here?
 * Starting from Android 13 (API 33), apps must request the POST_NOTIFICATIONS permission
 * at runtime before showing any notifications. Without this permission:
 * - The foreground service notification won't be visible
 * - Battery alert notifications won't appear
 * 
 * We request this permission when the activity starts so that:
 * 1. The user sees the permission dialog early in their experience
 * 2. The service can show notifications immediately when enabled
 * 
 * ## How Permission Handling Works:
 * 1. On activity creation, we check if we already have the permission
 * 2. If not, we use ActivityResultContracts.RequestPermission() to show the system dialog
 * 3. The result (granted/denied) is stored in a state variable
 * 4. MainScreen can use this state to show appropriate UI or warnings
 */
class MainActivity : ComponentActivity() {
    
    /**
     * Tracks whether notification permission has been granted.
     * 
     * This state is observed by MainScreen to potentially show warnings
     * if the user tries to enable the service without notification permission.
     * 
     * Why mutableStateOf? This allows Compose to recompose when the value changes,
     * ensuring the UI stays in sync with the actual permission state.
     */
    private val hasNotificationPermission = mutableStateOf(false)
    
    /**
     * Permission request launcher using the modern Activity Result API.
     * 
     * ## Why ActivityResultContracts?
     * - It's the recommended way to handle permission requests since AndroidX Activity 1.2.0
     * - It's lifecycle-aware and handles configuration changes properly
     * - It avoids the deprecated onRequestPermissionsResult() callback
     * 
     * ## How it works:
     * 1. We register a contract that knows how to request a single permission
     * 2. When we call launcher.launch(permission), the system shows the permission dialog
     * 3. When the user responds, the lambda receives a Boolean (granted = true/false)
     * 4. We update our state variable so the UI can react accordingly
     */
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        // Update our state when the user responds to the permission dialog
        hasNotificationPermission.value = isGranted
        
        // Log the result for debugging purposes
        if (isGranted) {
            android.util.Log.d("MainActivity", "Notification permission GRANTED")
        } else {
            android.util.Log.d("MainActivity", "Notification permission DENIED")
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Check and request notification permission for Android 13+ (API 33+)
        // This must be done before setContent to ensure the state is correct on first render
        checkAndRequestNotificationPermission()
        
        setContent {
            BatteryAlarmTheme {
                // ── Navigation state ──────────────────────────
                // rememberSaveable keeps the value across configuration changes
                // (rotation, theme change, etc.) so the user doesn't lose their
                // place when the activity is recreated.
                var showSettings by rememberSaveable { mutableStateOf(false) }

                // ── Screen switching ──────────────────────────
                // Simple conditional composition: show one screen or the other.
                // This is the lightest-weight navigation pattern in Compose.
                if (showSettings) {
                    // Settings screen with its own Scaffold (has TopAppBar)
                    SettingsScreen(
                        onNavigateBack = {
                            // When user taps back arrow in Settings, return to MainScreen
                            showSettings = false
                        }
                    )
                } else {
                    // Main screen wrapped in a Scaffold for edge-to-edge padding
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        MainScreen(
                            modifier = Modifier.padding(innerPadding),
                            // Pass the permission state so MainScreen can react to it
                            hasNotificationPermission = hasNotificationPermission.value,
                            // Provide a callback to request permission if user tries to enable
                            // service without permission
                            onRequestNotificationPermission = {
                                requestNotificationPermission()
                            },
                            // Navigate to settings when the gear icon is tapped
                            onNavigateToSettings = {
                                showSettings = true
                            }
                        )
                    }
                }
            }
        }
    }
    
    /**
     * Checks if notification permission is already granted, and requests it if not.
     * 
     * ## Version Check Explanation:
     * - Android 13 (API 33) introduced POST_NOTIFICATIONS as a runtime permission
     * - On earlier versions, notification permission is granted automatically at install time
     * - We only need to request it on API 33+
     * 
     * ## Why Check First?
     * - If permission is already granted, we don't want to show the dialog again
     * - checkSelfPermission() tells us the current state without prompting the user
     */
    private fun checkAndRequestNotificationPermission() {
        // Only needed for Android 13 (API 33) and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Check if we already have the permission
            val permissionStatus = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            )
            
            if (permissionStatus == PackageManager.PERMISSION_GRANTED) {
                // Already have permission - update state and we're done
                hasNotificationPermission.value = true
            } else {
                // Don't have permission - request it from the user
                // This will show the system permission dialog
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            // On Android 12 and below, notification permission is granted at install time
            // So we can assume we have it
            hasNotificationPermission.value = true
        }
    }
    
    /**
     * Requests notification permission (can be called again if user previously denied).
     * 
     * This is exposed to MainScreen so it can trigger a permission request
     * if the user tries to enable the service without having granted permission.
     */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
