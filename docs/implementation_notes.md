# Implementation Notes and Q&A

This document explains key implementation choices and answers your questions from the previous conversation.

## 1) Splitting MainScreen into a separate file
- Why: Keeping `MainActivity` focused on Android lifecycle and `setContent` setup improves readability and maintainability. UI-related composables live in a dedicated file (`ui/MainScreen.kt`), making it easier to expand the UI, add previews, and test composables in isolation.
- Scalability: As the app grows (settings screen, KMP modules), this separation prevents `MainActivity` from becoming a “god class”.
- Compose best practice: Feature- or screen-level composables in their own files/packages is a common pattern in Compose apps.

## 2) Power icon instead of PlayArrow
- We switched to `Icons.Rounded.PowerSettingsNew` (from `material-icons-extended`). This icon semantically matches a power toggle and is widely recognized.
- We chose the Rounded style for a friendlier look aligned with Material You. The Filled/Outlined variants are also possible based on taste.

## 3) Visual ON/OFF emphasis and subtle animation
- Colors: The FAB uses animated state-based colors:
  - ON: `colorScheme.primary` with `onPrimary` content
  - OFF: `surfaceVariant` with `onSurfaceVariant` content
- Animation: We added small, tasteful animations using Compose Animation APIs:
  - `animateColorAsState` for smooth color transitions
  - `animateFloatAsState` + `graphicsLayer` for a subtle scale (1.0 -> 1.05) when ON
- Shape: FAB is inherently circular, matching your “round button” suggestion. Elevation is slightly higher when ON for extra emphasis.

## 4) Avoiding string duplication vs. concatenation
- Instead of concatenating strings in code (e.g., "Battery monitoring is " + "ON"), we use `stringResource` with formatting: `R.string.battery_monitoring_status` ("Battery monitoring is %1$s") and pass `R.string.on_label` or `R.string.off_label`.
- Why: This is best practice for localization. Different languages may need to reorder words or change spacing. String resources with placeholders handle these cases cleanly.

## 5) Accessibility
- Content descriptions are added for the Settings and Power buttons, with different descriptions for enabling/disabling. This improves TalkBack experience and overall accessibility.

## 6) KMP readiness
- While this is an Android app today, structuring UI and logic cleanly is a good first step for future KMP reuse. State and business logic can later move into shared modules, while platform-specific UI remains cleanly separated.

## 7) What changed in the codebase
- `MainActivity.kt`: Now only wires the theme + Scaffold + `MainScreen()` and no longer contains the UI implementation.
- `ui/MainScreen.kt`: New composable with power icon, animations, and resource-driven texts.
- `strings.xml`: Added status and accessibility strings.
- Dependencies: Added `material-icons-extended`, `compose-animation`, and `runtime-saveable` (for `rememberSaveable`).

## 8) Battery Monitor Service Implementation

The battery monitoring service has been implemented! Here's a comprehensive explanation of how it works:

### Overview

The `BatteryMonitorService` is an Android **Foreground Service** that continuously monitors battery levels and alerts users when the battery reaches critical thresholds (too low or too high).

### Why a Foreground Service?

Android heavily restricts background execution to preserve battery life. For an app that needs to continuously monitor something (like battery level), we have a few options:

1. **WorkManager** - Good for periodic tasks, but not for real-time monitoring
2. **Foreground Service** - Best for user-initiated, long-running tasks that need continuous execution
3. **Background Service** - Heavily restricted since Android 8.0, not recommended

We chose a **Foreground Service** because:
- The user explicitly enables monitoring (it's not happening secretly)
- We need to respond immediately when battery crosses thresholds
- Android requires showing a notification, which is actually useful for our use case
- The service survives even if the app is closed

### Key Components

#### 1. BatteryMonitorService.kt

Located at: `app/src/main/java/com/example/battery_alarm/service/BatteryMonitorService.kt`

This is the main service class. Here's what each part does:

**Companion Object (Static Members)**
```kotlin
companion object {
    const val CHANNEL_ID_SERVICE = "battery_monitor_service_channel"
    const val CHANNEL_ID_ALERTS = "battery_alert_channel"
    const val DEFAULT_LOW_THRESHOLD = 20
    const val DEFAULT_HIGH_THRESHOLD = 80
    // ... helper functions start() and stop()
}
```
- Defines constants for notification channels and thresholds
- Provides static `start()` and `stop()` functions so any part of the app can easily control the service

**BroadcastReceiver for Battery Changes**
```kotlin
private val batteryReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        // Extract battery level and charging status
        // Call checkAndAlert() to determine if we should notify user
    }
}
```
- Android broadcasts `ACTION_BATTERY_CHANGED` whenever battery status changes
- This is a "sticky" broadcast - we get the current value immediately upon registration
- We extract: battery level (0-100%), charging status (charging/discharging/full)

**Service Lifecycle Methods**
```kotlin
override fun onCreate() { /* Create notification channels */ }
override fun onStartCommand() { /* Start foreground, register receiver */ }
override fun onDestroy() { /* Cleanup: unregister receiver, stop alerts */ }
```
- `onCreate`: Called once when service is first created. We set up notification channels here.
- `onStartCommand`: Called each time the service is started. We start as foreground and register for battery updates.
- `onDestroy`: Cleanup to prevent memory leaks.

**Alert Logic (checkAndAlert)**

This is the brain of the service. It implements the progressive alert strategy:

```
Initial state: No alerts sent yet
┌─────────────────────────────────────────────────────────────┐
│                                                             │
│  DISCHARGING (not plugged in)          CHARGING            │
│  ─────────────────────────────         ────────            │
│                                                             │
│  100%                                  100% ← Alert every   │
│   │                                     │     30 minutes    │
│   │                                     │                   │
│   │                                    80% ← First alert    │
│   │                                     │     (HIGH_THRESHOLD)
│   │                                    85% ← Alert (+5%)    │
│   │                                    90% ← Alert (+5%)    │
│   │                                    93% ← Alert (+3%)    │
│   │                                    96% ← Alert (+3%)    │
│   │                                    99% ← Alert (+3%)    │
│   │                                                         │
│  20% ← First alert (LOW_THRESHOLD)                         │
│  15% ← Alert (-5%)                                         │
│  10% ← Alert (-5%)                                         │
│   7% ← Alert (-3%)                                         │
│   4% ← Alert (-3%)                                         │
│   1% ← Alert (-3%)                                         │
│   0%                                                        │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

**Rate Limiting**
- Minimum 10 minutes between any two alerts
- At 100% charge, alert every 30 minutes (not on every battery broadcast)
- This prevents annoying users if battery level fluctuates rapidly

**Playing Alerts**
```kotlin
private fun triggerAlert(batteryLevel: Int) {
    showAlertNotification(batteryLevel)
    playAlertSound()
    startVibration()
    handler.postDelayed(stopAlertRunnable, ALERT_DURATION_MS)
}
```
- Shows a high-priority notification
- Plays the system alarm sound using `RingtoneManager`
- Vibrates with a pattern (500ms on, 200ms off, repeat)
- Automatically stops after 15 seconds

#### 2. Notification Channels

Android 8.0+ requires notification channels. We create two:

1. **Service Channel** (`IMPORTANCE_LOW`)
   - For the persistent "monitoring active" notification
   - Silent, no vibration, just shows in notification shade
   - User can see monitoring is active

2. **Alerts Channel** (`IMPORTANCE_HIGH`)
   - For battery threshold alerts
   - Plays sound, vibrates, shows as heads-up notification
   - Gets user's attention

#### 3. AndroidManifest.xml Updates

```xml
<!-- Permissions needed -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.VIBRATE" />

<!-- Service declaration -->
<service
    android:name=".service.BatteryMonitorService"
    android:foregroundServiceType="specialUse"
    android:exported="false">
    <property
        android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
        android:value="Battery level monitoring..." />
</service>
```

**Why `specialUse` foreground service type?**
- Android 14 requires declaring what type of foreground service you're running
- Battery monitoring doesn't fit predefined categories (camera, microphone, location, etc.)
- `specialUse` is the catch-all for valid use cases that don't fit elsewhere
- The `<property>` tag explains our use case to Google Play reviewers

#### 4. UI Integration (MainScreen.kt)

The main screen now:
1. Reads service state from SharedPreferences on startup
2. When user taps the power button:
   - Toggles state
   - Saves to SharedPreferences
   - Starts or stops the service

```kotlin
onClick = {
    val newState = !isServiceEnabled
    isServiceEnabled = newState
    prefs.edit().putBoolean("service_enabled", newState).apply()
    
    if (newState) {
        BatteryMonitorService.start(context)
    } else {
        BatteryMonitorService.stop(context)
    }
}
```

### Do Not Disturb (DND) Handling

The app respects user's DND settings automatically because:
1. We use `AudioAttributes.USAGE_ALARM` for sound and vibration
2. Android's audio system handles DND filtering based on user settings
3. Users can allow/block alarm sounds in their DND settings

### String Resources Added

All user-visible text is in `strings.xml` for localization:
- Notification channel names and descriptions
- Service notification title and content
- Alert titles and messages for low/high/full battery

### Future Improvements (Settings Screen)

The current implementation uses hardcoded thresholds (20% and 80%). When you implement the Settings screen, you'll want to:
1. Store thresholds in SharedPreferences
2. Read them in `BatteryMonitorService`
3. Potentially restart the service when settings change

## 9) Notification Permission Handling (Android 13+)

Starting from Android 13 (API 33), apps must request the `POST_NOTIFICATIONS` permission at runtime before showing any notifications. Without this permission:
- The foreground service notification won't be visible to the user
- Battery alert notifications won't appear
- The service will technically run, but the user won't see any notifications

### Why This Changed in Android 13

Before Android 13, notification permission was granted automatically when the app was installed. Google changed this to give users more control over which apps can send notifications, reducing notification spam.

### How We Handle It

#### 1. MainActivity.kt Changes

We added notification permission handling in `MainActivity.kt`:

```kotlin
// Track permission state with Compose state
private val hasNotificationPermission = mutableStateOf(false)

// Modern way to request permissions using Activity Result API
private val notificationPermissionLauncher = registerForActivityResult(
    ActivityResultContracts.RequestPermission()
) { isGranted: Boolean ->
    hasNotificationPermission.value = isGranted
}

// Check and request permission on activity creation
private fun checkAndRequestNotificationPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val permissionStatus = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        )
        if (permissionStatus == PackageManager.PERMISSION_GRANTED) {
            hasNotificationPermission.value = true
        } else {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    } else {
        // On Android 12 and below, permission is granted at install time
        hasNotificationPermission.value = true
    }
}
```

**Key Points:**
- We use `ActivityResultContracts.RequestPermission()` - the modern, lifecycle-aware way to request permissions
- We check the Android version (`TIRAMISU` = API 33 = Android 13) because the permission only exists on newer versions
- On older Android versions, we assume permission is granted (it was automatic at install time)

#### 2. MainScreen.kt Changes

The main screen now receives permission state and a callback:

```kotlin
@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    hasNotificationPermission: Boolean = true,
    onRequestNotificationPermission: () -> Unit = {}
) {
    // ...
    
    FloatingActionButton(
        onClick = {
            val newState = !isServiceEnabled
            
            // If enabling service without permission, request it first
            if (newState && !hasNotificationPermission) {
                onRequestNotificationPermission()
                return@FloatingActionButton
            }
            
            // ... rest of toggle logic
        }
    )
}
```

**Why This Design:**
- Permission state lives in `MainActivity` (the Activity owns the permission launcher)
- `MainScreen` is a stateless composable that receives state via parameters
- This follows Compose best practices: state hoisting and unidirectional data flow
- Default parameter values (`true` and `{}`) make the Preview still work

### User Experience Flow

1. **App Launch**: Permission dialog appears automatically
2. **User Grants**: App works normally, notifications visible
3. **User Denies**: 
   - User can still try to enable service
   - When they tap the power button, we request permission again
   - Service won't start until permission is granted

### AndroidManifest.xml

The permission was already declared:
```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

This tells Android we need the permission, but on Android 13+ we still need to request it at runtime.

## 10) Reboot Handling - Auto-Restart Service After Device Reboot

When a device reboots, all running services are stopped. Android does NOT automatically restart services after reboot - even foreground services with `START_STICKY`. This caused two problems:

1. **No persistent notification after reboot** - The service wasn't running, so no notification
2. **UI showed "ON" but service was off** - SharedPreferences still had `service_enabled = true`, but the service wasn't actually running

### The Solution: BootCompletedReceiver

We created a `BroadcastReceiver` that listens for the `BOOT_COMPLETED` system broadcast and restarts the service if it was enabled before reboot.

#### How It Works

```
Device Reboot Flow:
┌─────────────────────────────────────────────────────────────────┐
│ 1. User enables battery monitoring                              │
│    └─> Service starts                                           │
│    └─> SharedPreferences saves "service_enabled = true"         │
│                                                                 │
│ 2. Device reboots                                               │
│    └─> All services are killed                                  │
│    └─> SharedPreferences persists (survives reboot)             │
│                                                                 │
│ 3. Boot completes                                               │
│    └─> Android sends BOOT_COMPLETED broadcast                   │
│    └─> BootCompletedReceiver.onReceive() is called              │
│    └─> Checks SharedPreferences: was service enabled?           │
│    └─> If yes, calls BatteryMonitorService.start()              │
│                                                                 │
│ 4. Service is running again!                                    │
│    └─> Persistent notification appears                          │
│    └─> Battery monitoring resumes                               │
└─────────────────────────────────────────────────────────────────┘
```

#### Files Changed

**1. New File: `receiver/BootCompletedReceiver.kt`**

```kotlin
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        
        // Check if service was enabled before reboot
        val prefs = context.getSharedPreferences("battery_alarm_prefs", Context.MODE_PRIVATE)
        val wasServiceEnabled = prefs.getBoolean("service_enabled", false)
        
        if (wasServiceEnabled) {
            // Restart the service!
            BatteryMonitorService.start(context)
        }
    }
}
```

**2. AndroidManifest.xml**

Added the RECEIVE_BOOT_COMPLETED permission:
```xml
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
```

Registered the receiver:
```xml
<receiver
    android:name=".receiver.BootCompletedReceiver"
    android:enabled="true"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED" />
    </intent-filter>
</receiver>
```

**Why `exported="true"`?** The system needs to send us the broadcast. Without this, we wouldn't receive `BOOT_COMPLETED`.

**Why declared in manifest (not dynamically registered)?** The app might not be running when boot completes. Static receivers declared in the manifest are woken up by the system even if the app isn't running.

#### UI State Synchronization

Even with the boot receiver, there's a race condition: What if the user opens the app before the boot receiver fires? Or what if the service failed to start?

We added an `isRunning` flag to `BatteryMonitorService`:

```kotlin
companion object {
    @Volatile
    var isRunning: Boolean = false
        private set
}

override fun onCreate() {
    isRunning = true
    // ...
}

override fun onDestroy() {
    isRunning = false
    // ...
}
```

And updated `MainScreen.kt` to check the ACTUAL service state:

```kotlin
// Get the ACTUAL service running state (not just SharedPreferences)
val actualServiceRunning = BatteryMonitorService.isRunning

// If there's a mismatch, sync SharedPreferences with reality
if (savedServiceEnabled != actualServiceRunning) {
    prefs.edit().putBoolean("service_enabled", actualServiceRunning).apply()
}

// UI state is based on actual service state
var isServiceEnabled by rememberSaveable {
    mutableStateOf(actualServiceRunning)
}
```

This ensures the UI always reflects the true service state, not just what SharedPreferences says.

### Why @Volatile?

The `@Volatile` annotation on `isRunning` ensures that:
- Changes made by one thread are immediately visible to other threads
- The service runs on the main thread, but the UI might read this value from a different thread during recomposition
- Without `@Volatile`, the UI might see a stale cached value

## 11) Future steps (optional)
- Add navigation and a real Settings screen with configurable thresholds
- Use `DataStore` instead of SharedPreferences for better coroutine support
- Add a ViewModel to manage service state more cleanly
- Add UI tests for toggle behavior and accessibility checks
- Consider adding a "test alert" button for users to preview sound/vibration
- Show a rationale dialog explaining why notifications are needed if user denies permission
- Add a "Permission Denied" warning banner in the UI when notification permission is missing
