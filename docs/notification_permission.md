# Notification Permission Handling (Android 13+)

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