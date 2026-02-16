# Battery Monitor Service Implementation

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