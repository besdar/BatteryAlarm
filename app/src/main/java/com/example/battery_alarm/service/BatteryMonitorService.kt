package com.example.battery_alarm.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.battery_alarm.MainActivity
import com.example.battery_alarm.R
import com.example.battery_alarm.data.SettingsRepository
import com.example.battery_alarm.data.SharedPreferencesStorage

// ── DND / Interruption-filter constants ──────────────────────
// We define these at file level so the rest of the file can reference
// them by name instead of magic numbers. They mirror the values in
// NotificationManager.INTERRUPTION_FILTER_* but are available even
// in plain-JVM unit tests where the Android SDK stubs are not loaded.

/** Matches [NotificationManager.INTERRUPTION_FILTER_ALL] – no DND restriction. */
const val INTERRUPTION_FILTER_ALL = 1

/** Matches [NotificationManager.INTERRUPTION_FILTER_PRIORITY] – only priority interruptions. */
const val INTERRUPTION_FILTER_PRIORITY = 2

/** Matches [NotificationManager.INTERRUPTION_FILTER_NONE] – total silence. */
const val INTERRUPTION_FILTER_NONE = 3

/** Matches [NotificationManager.INTERRUPTION_FILTER_ALARMS] – only alarms allowed. */
const val INTERRUPTION_FILTER_ALARMS = 4

/**
 * BatteryMonitorService is a foreground service that continuously monitors the device's
 * battery level and alerts the user when the battery reaches critical thresholds.
 *
 * ## Key Features:
 * - Monitors battery level changes via BroadcastReceiver
 * - Alerts user at low battery (20%) and high battery (80%) thresholds
 * - Plays sound and vibrates for 15 seconds when thresholds are crossed
 * - Uses progressive notification intervals (5%, 5%, then 3% decrements)
 * - Rate limits alerts to minimum 10 minutes apart
 * - Special handling for 100% charge (alerts every 30 minutes)
 * - Automatically restarts after device reboot (if it was enabled before)
 * - Respects Do Not Disturb (DND) mode: skips audible/vibration alerts when active
 * - Stops any playing alert immediately when the charger is plugged in or removed
 * - Uses VISIBILITY_PUBLIC so notifications appear on the lock screen even when
 *   "Show sensitive content" is off (the app has no sensitive data)
 *
 * ## Why a Foreground Service?
 * Android restricts background execution to preserve battery. A foreground service
 * with a persistent notification is the recommended approach for long-running
 * monitoring tasks that the user explicitly enabled.
 */
class BatteryMonitorService : Service() {

    companion object {
        // Logging tag for debugging purposes
        private const val TAG = "BatteryMonitorService"

        // Notification channel IDs - Android 8.0+ requires channels for notifications
        // We use two channels: one for the persistent service notification (silent)
        // and one for battery alert notifications (with sound)
        const val CHANNEL_ID_SERVICE = "battery_monitor_service_channel"
        const val CHANNEL_ID_ALERTS = "battery_alert_channel"

        // Notification IDs to identify and update specific notifications
        private const val NOTIFICATION_ID_SERVICE = 1
        private const val NOTIFICATION_ID_ALERT = 2

        // Default battery thresholds (in percentage)
        // LOW_THRESHOLD: Alert when battery drops to or below this level
        // HIGH_THRESHOLD: Alert when battery rises to or above this level (while charging)
        const val DEFAULT_LOW_THRESHOLD = 20
        const val DEFAULT_HIGH_THRESHOLD = 80

        // Rate limiting constants (in milliseconds)
        // MIN_ALERT_INTERVAL: Minimum time between any two alerts (10 minutes)
        // FULL_CHARGE_ALERT_INTERVAL: Time between alerts when battery is at 100% (30 minutes)
        private const val MIN_ALERT_INTERVAL_MS = 10 * 60 * 1000L  // 10 minutes
        private const val FULL_CHARGE_ALERT_INTERVAL_MS = 30 * 60 * 1000L  // 30 minutes

        // Duration for sound and vibration alerts (15 seconds)
        private const val ALERT_DURATION_MS = 15 * 1000L

        // Intent action to stop the service from notification
        const val ACTION_STOP_SERVICE = "com.example.battery_alarm.STOP_SERVICE"

        // Intent action to dismiss an alert (stop sound/vibration)
        const val ACTION_DISMISS_ALERT = "com.example.battery_alarm.DISMISS_ALERT"

        /**
         * Static flag to track whether the service is currently running.
         * 
         * ## Why is this needed?
         * After a device reboot, the service is stopped but SharedPreferences still shows
         * "service_enabled = true". We need a way to check the ACTUAL service state
         * to sync the UI properly when the app opens.
         * 
         * This flag is:
         * - Set to true in onCreate()
         * - Set to false in onDestroy()
         * - Checked by UI to determine actual service state
         * 
         * Note: @Volatile ensures visibility across threads
         */
        @Volatile
        var isRunning: Boolean = false
            private set  // Only the service can change this value

        /**
         * Helper function to start the service from any context (e.g., from UI).
         * Uses startForegroundService() which is required for Android 8.0+.
         *
         * @param context The context from which to start the service
         */
        fun start(context: Context) {
            val intent = Intent(context, BatteryMonitorService::class.java)
            // startForegroundService() tells Android we'll show a notification within 5 seconds
            context.startForegroundService(intent)
        }

        /**
         * Helper function to stop the service from any context.
         *
         * @param context The context from which to stop the service
         */
        fun stop(context: Context) {
            val intent = Intent(context, BatteryMonitorService::class.java)
            context.stopService(intent)
        }
    }

    // Handler for scheduling delayed operations on the main thread
    // Used for stopping alerts after the configured duration and rate limiting
    private val handler = Handler(Looper.getMainLooper())

    // ── Settings Repository ───────────────────────────────────
    // Lazily initialized because we need `this` (a Context) which isn't
    // available until after the Service constructor finishes.
    // `lateinit` tells Kotlin "I promise to initialize this before I use it".
    private lateinit var settingsRepository: SettingsRepository

    // Current battery thresholds — loaded from user settings on service start.
    // These are read from SettingsRepository so users can customize them
    // via the Settings screen.
    private var lowThreshold = DEFAULT_LOW_THRESHOLD
    private var highThreshold = DEFAULT_HIGH_THRESHOLD

    // Timing values — loaded from user settings on service start.
    // Stored as milliseconds for direct use in rate-limiting logic.
    private var minAlertIntervalMs = 10 * 60 * 1000L   // default 10 min
    private var fullChargeAlertIntervalMs = 30 * 60 * 1000L  // default 30 min
    private var alertDurationMs = 15 * 1000L  // default 15 sec

    // Alert behavior flags — loaded from user settings on service start.
    private var soundEnabled = true
    private var vibrationEnabled = true

    // The user's chosen alarm sound URI (null = system default alarm sound).
    // Loaded from SettingsRepository on service start.
    private var alarmSoundUriString: String? = null

    // Timestamp of the last alert - used for rate limiting
    // We don't want to annoy users with too frequent alerts
    private var lastAlertTimeMs = 0L

    // The battery level at which we last alerted the user
    // Used to track when to alert again based on progressive intervals
    private var lastAlertedLevel = -1

    // Flag to track if we've alerted for 100% charge
    // Special handling needed since battery stays at 100% while plugged in
    private var alertedAt100Percent = false

    // Flag to track if an alert is currently playing
    // Prevents multiple simultaneous alerts
    private var isAlertPlaying = false

    // Tracks whether battery is currently charging
    // Determines which threshold logic to apply
    private var isCharging = false

    // Current battery level (0-100)
    private var currentBatteryLevel = -1

    // Runnable to stop the alert after ALERT_DURATION_MS
    private val stopAlertRunnable = Runnable {
        stopAlert()
    }

    /**
     * BroadcastReceiver that listens for battery status changes.
     *
     * Android sends ACTION_BATTERY_CHANGED whenever the battery status updates.
     * This includes level changes, charging state changes, etc.
     *
     * Note: This broadcast can only be received by registering dynamically
     * (not in manifest) because it's a "sticky" broadcast.
     */
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // Safety check - shouldn't happen but good practice
            if (intent?.action != Intent.ACTION_BATTERY_CHANGED) return

            // Extract battery level from the intent extras
            // EXTRA_LEVEL gives the current level, EXTRA_SCALE gives max (usually 100)
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)

            // Calculate percentage (handle edge case of scale being 0)
            val batteryPct = if (scale > 0) (level * 100) / scale else level

            // Extract charging status from the intent
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val wasCharging = isCharging
            isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL

            // Log for debugging
            Log.d(TAG, "Battery update: level=$batteryPct%, charging=$isCharging")

            // ── FIX: Stop alert on charger state change ──────────────
            // If the charging state just changed (charger plugged in or removed)
            // AND an alert is currently playing, stop it immediately.
            //
            // WHY? When the user plugs in or removes the charger, the battery
            // situation has changed — the alarm that was playing is now stale.
            // For example, a "low battery" alarm is no longer relevant once
            // the charger is connected. Continuing to play would be annoying
            // and confusing.
            if (isCharging != wasCharging && isAlertPlaying) {
                Log.d(TAG, "Charging state changed while alert playing — stopping alert")
                stopAlert()
            }

            // Store the current level
            currentBatteryLevel = batteryPct

            // Update the persistent notification with current battery status
            updateServiceNotification()

            // Check if we need to alert the user based on the new battery level
            checkAndAlert(batteryPct, wasCharging)
        }
    }

    /**
     * Called when the service is first created.
     * We set up notification channels here because they need to exist
     * before we can post any notifications.
     */
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")

        // Mark the service as running - this is checked by the UI to sync state
        isRunning = true

        // ── Initialize settings from the repository ───────────
        // The repository reads from SharedPreferencesStorage, which wraps Android's
        // SharedPreferences behind a pure-Kotlin interface (SettingsStorage).
        // If the user never opened Settings, the defaults are used.
        settingsRepository = SettingsRepository(SharedPreferencesStorage(this))
        loadSettingsFromRepository()

        // Create notification channels (required for Android 8.0+)
        createNotificationChannels()
    }

    /**
     * Called every time the service is started via startService() or startForegroundService().
     *
     * @param intent The Intent that was used to start the service
     * @param flags Additional data about the start request
     * @param startId A unique integer representing this specific start request
     * @return How the system should handle the service if it's killed
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand, action=${intent?.action}")

        // Handle special actions from notification buttons
        when (intent?.action) {
            ACTION_STOP_SERVICE -> {
                // User clicked "Stop" on the notification
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_DISMISS_ALERT -> {
                // User clicked "Dismiss" on an alert notification
                stopAlert()
                return START_STICKY
            }
        }

        // Start as a foreground service with a persistent notification
        // This must be called within 5 seconds of startForegroundService()
        startForeground(NOTIFICATION_ID_SERVICE, createServiceNotification())

        // Register for battery change broadcasts
        // Using RECEIVER_NOT_EXPORTED for security (Android 13+)
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(batteryReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(batteryReceiver, filter)
        }

        // START_STICKY tells Android to recreate the service if it's killed
        // This is appropriate for a monitoring service the user explicitly enabled
        return START_STICKY
    }

    /**
     * Called when the service is being destroyed (stopped).
     * Clean up resources to prevent memory leaks and unexpected behavior.
     */
    override fun onDestroy() {
        Log.d(TAG, "Service onDestroy")

        // Mark the service as not running - this is checked by the UI to sync state
        isRunning = false

        // Stop any ongoing alert
        stopAlert()

        // Unregister the battery receiver to prevent leaks
        try {
            unregisterReceiver(batteryReceiver)
        } catch (e: IllegalArgumentException) {
            // Receiver wasn't registered - can happen if service is destroyed
            // before onStartCommand completed
            Log.w(TAG, "Receiver not registered: ${e.message}")
        }

        // Remove any pending callbacks
        handler.removeCallbacksAndMessages(null)

        super.onDestroy()
    }

    /**
     * This service doesn't support binding (it's a started service, not bound).
     * Returning null indicates that clients cannot bind to this service.
     */
    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Reads all user-configurable values from the SettingsRepository and copies
     * them into the service's instance variables.
     *
     * ## Why Copy Instead of Reading on Every Use?
     * - Battery change broadcasts can fire frequently; reading SharedPreferences
     *   each time would add unnecessary I/O overhead.
     * - By loading once at startup, we keep the hot path (battery change events) fast.
     * - If the user changes settings while the service is running, the service
     *   will pick up the new values next time it restarts. For an alarm app this
     *   is acceptable — the user can toggle the service off/on to apply changes
     *   immediately.
     */
    private fun loadSettingsFromRepository() {
        lowThreshold = settingsRepository.lowThreshold
        highThreshold = settingsRepository.highThreshold
        minAlertIntervalMs = settingsRepository.alertIntervalMs
        fullChargeAlertIntervalMs = settingsRepository.fullChargeAlertIntervalMs
        alertDurationMs = settingsRepository.alertDurationMs
        soundEnabled = settingsRepository.isSoundEnabled
        vibrationEnabled = settingsRepository.isVibrationEnabled
        alarmSoundUriString = settingsRepository.alarmSoundUri

        Log.d(TAG, "Settings loaded: low=$lowThreshold%, high=$highThreshold%, " +
                "alertInterval=${minAlertIntervalMs/1000}s, " +
                "fullChargeInterval=${fullChargeAlertIntervalMs/1000}s, " +
                "alertDuration=${alertDurationMs/1000}s, " +
                "sound=$soundEnabled, vibration=$vibrationEnabled, " +
                "alarmSound=${alarmSoundUriString ?: "system default"}")
    }

    /**
     * Creates the notification channels required for Android 8.0 (API 26) and above.
     *
     * We create two channels:
     * 1. Service channel: For the persistent "monitoring active" notification (low priority, silent)
     * 2. Alerts channel: For battery threshold alerts (high priority, with sound)
     *
     * Channels allow users to customize notification behavior per-channel in system settings.
     */
    private fun createNotificationChannels() {
        val notificationManager = getSystemService(NotificationManager::class.java)

        // Channel for the persistent service notification
        // IMPORTANCE_LOW = no sound, appears in shade but not status bar
        val serviceChannel = NotificationChannel(
            CHANNEL_ID_SERVICE,
            getString(R.string.notification_channel_service_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_service_description)
            // Disable all alerting behavior for this channel
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)

            // ── FIX: Show notification on the lock screen ────────────
            // By default, Android uses VISIBILITY_PRIVATE which hides the
            // notification content on the lock screen when the user has
            // "Show sensitive content" turned OFF in system settings.
            //
            // VISIBILITY_PUBLIC tells Android: "this notification contains
            // NO sensitive data — always show it in full on the lock screen."
            //
            // This is appropriate for BatteryAlarm because the notification
            // only shows battery percentage and a "Stop" action — there is
            // no personal, financial, or private information.
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        // Channel for battery alert notifications
        // IMPORTANCE_HIGH = can make sound and appear as heads-up notification
        val alertsChannel = NotificationChannel(
            CHANNEL_ID_ALERTS,
            getString(R.string.notification_channel_alerts_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.notification_channel_alerts_description)
            // Enable all alerting features
            enableLights(true)
            enableVibration(true)
            setShowBadge(true)

            // ── FIX: Show alert notification on the lock screen ───────
            // Same reasoning as the service channel above — battery alerts
            // ("Battery low: 15%") contain no sensitive information and
            // should be visible on the lock screen regardless of the
            // "Show sensitive content" system setting.
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        // Register both channels with the system
        notificationManager.createNotificationChannel(serviceChannel)
        notificationManager.createNotificationChannel(alertsChannel)
    }

    /**
     * Creates the persistent notification shown while the service is running.
     *
     * This notification is required for foreground services and lets users know
     * the app is actively monitoring the battery. It also provides a way to
     * stop the service without opening the app.
     *
     * @return The notification to display
     */
    private fun createServiceNotification(): Notification {
        // Intent to open the app when notification is tapped
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Intent to stop the service from the notification
        val stopIntent = Intent(this, BatteryMonitorService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build the notification text based on current battery state
        val contentText = if (currentBatteryLevel >= 0) {
            getString(R.string.notification_service_content_with_level, currentBatteryLevel)
        } else {
            getString(R.string.notification_service_content)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID_SERVICE)
            .setContentTitle(getString(R.string.notification_service_title))
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)  // Can't be swiped away
            .setContentIntent(openAppPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.notification_action_stop),
                stopPendingIntent
            )
            // ── FIX: Always show full notification on the lock screen ──
            // VISIBILITY_PUBLIC means this notification's content is safe
            // to display on a secure lock screen. Without this, when the
            // user's "Show sensitive content" setting is OFF, Android
            // hides the notification entirely or shows only "Contents
            // hidden" — making it look like the service isn't running.
            //
            // BatteryAlarm only shows battery percentage and a "Stop"
            // button, so there is zero sensitive data to protect.
            //
            // NOTE: We set this on BOTH the channel (lockscreenVisibility)
            // and the individual notification (setVisibility). The channel
            // setting is the system-level default; the per-notification
            // setting acts as an explicit declaration of intent and is
            // needed for NotificationCompat back-compatibility on older APIs.
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    /**
     * Updates the persistent service notification with the current battery level.
     * Called whenever the battery level changes.
     */
    private fun updateServiceNotification() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID_SERVICE, createServiceNotification())
    }

    /**
     * Core logic to determine if we should alert the user based on battery level changes.
     *
     * This method implements the progressive alert strategy:
     * - First alert at threshold (20% or 80%)
     * - Then alert after 5% change, then another 5%, then every 3%
     * - Rate limit: minimum 10 minutes between alerts
     * - Special case: At 100%, alert every 30 minutes
     *
     * @param batteryLevel Current battery percentage (0-100)
     * @param wasCharging Whether the device was charging before this update
     */
    private fun checkAndAlert(batteryLevel: Int, wasCharging: Boolean) {
        val currentTime = System.currentTimeMillis()

        // Reset alert tracking when charging state changes
        // This allows fresh alerts when user plugs in or unplugs
        if (isCharging != wasCharging) {
            Log.d(TAG, "Charging state changed, resetting alert tracking")
            lastAlertedLevel = -1
            alertedAt100Percent = false
        }

        // Determine if we're in an alert condition
        val shouldAlert = when {
            // When charging: check if we hit or exceeded high threshold
            isCharging -> {
                when {
                    // Special case: battery at 100%
                    batteryLevel >= 100 -> {
                        // Alert at the user-configured interval when at 100%
                        if (!alertedAt100Percent ||
                            (currentTime - lastAlertTimeMs >= fullChargeAlertIntervalMs)) {
                            Log.d(TAG, "Battery at 100%, should alert")
                            true
                        } else {
                            false
                        }
                    }
                    // First time crossing high threshold
                    batteryLevel >= highThreshold && lastAlertedLevel < highThreshold -> {
                        Log.d(TAG, "Battery crossed high threshold ($highThreshold%), should alert")
                        true
                    }
                    // Progressive alerts while above threshold
                    batteryLevel >= highThreshold && lastAlertedLevel >= highThreshold -> {
                        shouldAlertBasedOnProgression(batteryLevel, increasing = true)
                    }
                    else -> false
                }
            }
            // When not charging: check if we hit or dropped below low threshold
            else -> {
                when {
                    // First time crossing low threshold
                    batteryLevel <= lowThreshold && lastAlertedLevel > lowThreshold -> {
                        Log.d(TAG, "Battery crossed low threshold ($lowThreshold%), should alert")
                        true
                    }
                    // Already below threshold and never alerted
                    batteryLevel <= lowThreshold && lastAlertedLevel == -1 -> {
                        Log.d(TAG, "Battery below low threshold, first alert")
                        true
                    }
                    // Progressive alerts while below threshold
                    batteryLevel <= lowThreshold && lastAlertedLevel <= lowThreshold -> {
                        shouldAlertBasedOnProgression(batteryLevel, increasing = false)
                    }
                    else -> false
                }
            }
        }

        // Apply rate limiting - don't alert more often than MIN_ALERT_INTERVAL_MS
        // (except this is already handled for 100% case above)
        if (shouldAlert) {
            val timeSinceLastAlert = currentTime - lastAlertTimeMs

            if (lastAlertTimeMs > 0 && timeSinceLastAlert < minAlertIntervalMs) {
                Log.d(TAG, "Rate limiting: only ${timeSinceLastAlert/1000}s since last alert, " +
                        "need ${minAlertIntervalMs/1000}s")
                return
            }

            // Trigger the alert!
            triggerAlert(batteryLevel)

            // Update tracking variables
            lastAlertTimeMs = currentTime
            lastAlertedLevel = batteryLevel
            if (batteryLevel >= 100) {
                alertedAt100Percent = true
            }
        }
    }

    /**
     * Determines if we should alert based on the progressive interval strategy.
     *
     * The strategy is:
     * - First alert at threshold
     * - Next alert after 5% change
     * - Next alert after another 5% change
     * - Then alert every 3% change
     *
     * @param currentLevel Current battery percentage
     * @param increasing True if battery is increasing (charging), false if decreasing
     * @return True if we should alert based on progression
     */
    private fun shouldAlertBasedOnProgression(currentLevel: Int, increasing: Boolean): Boolean {
        if (lastAlertedLevel == -1) return false

        // Calculate how far we've moved from the threshold
        val threshold = if (increasing) highThreshold else lowThreshold
        val lastDistance = kotlin.math.abs(lastAlertedLevel - threshold)
        val currentDistance = kotlin.math.abs(currentLevel - threshold)

        // Determine the required interval based on how many alerts we've had
        // First two alerts after threshold: 5% intervals
        // After that: 3% intervals
        val interval = when {
            lastDistance < 5 -> 5   // First interval: 5%
            lastDistance < 10 -> 5  // Second interval: 5%
            else -> 3               // Subsequent intervals: 3%
        }

        // Check if we've moved enough in the right direction
        val levelChange = kotlin.math.abs(currentLevel - lastAlertedLevel)

        // For low battery (decreasing), we alert as it gets lower
        // For high battery (increasing), we alert as it gets higher
        val movedInAlertDirection = if (increasing) {
            currentLevel > lastAlertedLevel
        } else {
            currentLevel < lastAlertedLevel
        }

        val shouldAlert = movedInAlertDirection && levelChange >= interval

        if (shouldAlert) {
            Log.d(TAG, "Progressive alert: moved $levelChange% (interval=$interval%), " +
                    "lastAlerted=$lastAlertedLevel, current=$currentLevel")
        }

        return shouldAlert
    }

    /**
     * Triggers a battery alert - shows notification and plays sound/vibration.
     *
     * Before producing any audible/haptic output the method checks the system's
     * Do Not Disturb (DND) state via [NotificationManager.getCurrentInterruptionFilter].
     * If DND is active (any mode other than INTERRUPTION_FILTER_ALL) the sound and
     * vibration are **skipped**, but the visual notification is still posted so the
     * user sees it when they next look at their phone.
     *
     * @param batteryLevel The battery level that triggered this alert
     */
    private fun triggerAlert(batteryLevel: Int) {
        Log.d(TAG, "Triggering alert for battery level: $batteryLevel%")

        // Don't start a new alert if one is already playing
        if (isAlertPlaying) {
            Log.d(TAG, "Alert already playing, skipping")
            return
        }

        isAlertPlaying = true

        // Show the alert notification — always, even in DND.
        // The notification itself is silent (.setSound(null)) so it won't
        // violate DND; it only provides a visual record of the alert.
        showAlertNotification(batteryLevel)

        // ── FIX: Respect Do Not Disturb mode ─────────────────────
        // Check the current interruption filter (DND state).
        // INTERRUPTION_FILTER_ALL (1) means "no DND — all sounds allowed".
        // Any other value means some form of DND is active:
        //   INTERRUPTION_FILTER_PRIORITY (2) — only priority interruptions
        //   INTERRUPTION_FILTER_NONE     (3) — total silence
        //   INTERRUPTION_FILTER_ALARMS   (4) — only alarms (but our battery
        //          alert is not a system alarm the user set, so we should
        //          still be respectful and stay silent)
        //
        // WHY NOT rely on USAGE_ALARM to "auto-respect DND"?
        // AudioAttributes.USAGE_ALARM is allowed to break through DND on
        // many devices, because it is designed for user-set alarms (clock
        // alarms). Our battery alert is NOT an alarm the user intentionally
        // scheduled to wake them — it is a background monitoring alert.
        // Playing it through DND would wake users at night, which is the
        // exact bug reported in Issue #1.
        val isDndActive = isDndModeActive()

        if (isDndActive) {
            Log.d(TAG, "Do Not Disturb is active — skipping sound and vibration")
        }

        // Play sound and vibration based on user preferences.
        // The user can disable either or both in Settings.
        // Additionally, we now skip them entirely when DND is active.
        if (soundEnabled && !isDndActive) {
            playAlertSound()
        } else if (!soundEnabled) {
            Log.d(TAG, "Sound is disabled in settings, skipping")
        }

        if (vibrationEnabled && !isDndActive) {
            startVibration()
        } else if (!vibrationEnabled) {
            Log.d(TAG, "Vibration is disabled in settings, skipping")
        }

        // Schedule the alert to stop after the user-configured duration.
        // Even if sound/vibration were skipped (DND), we still schedule
        // the stop so that `isAlertPlaying` is reset correctly.
        handler.postDelayed(stopAlertRunnable, alertDurationMs)
    }

    /**
     * Shows a high-priority notification alerting the user about battery level.
     *
     * @param batteryLevel The current battery percentage
     */
    private fun showAlertNotification(batteryLevel: Int) {
        // Intent to dismiss the alert
        val dismissIntent = Intent(this, BatteryMonitorService::class.java).apply {
            action = ACTION_DISMISS_ALERT
        }
        val dismissPendingIntent = PendingIntent.getService(
            this, 1, dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Intent to open the app
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 2, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Choose appropriate title and message based on whether charging
        val (title, message) = if (isCharging) {
            if (batteryLevel >= 100) {
                Pair(
                    getString(R.string.alert_title_fully_charged),
                    getString(R.string.alert_message_fully_charged)
                )
            } else {
                Pair(
                    getString(R.string.alert_title_high_battery),
                    getString(R.string.alert_message_high_battery, batteryLevel)
                )
            }
        } else {
            Pair(
                getString(R.string.alert_title_low_battery),
                getString(R.string.alert_message_low_battery, batteryLevel)
            )
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID_ALERTS)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(openAppPendingIntent)
            .setAutoCancel(true)  // Dismiss when tapped
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.notification_action_dismiss),
                dismissPendingIntent
            )
            // Don't use the notification's built-in sound - we handle it ourselves
            // for more control over duration
            .setSound(null)
            // ── FIX: Always show alert on the lock screen ─────────────
            // Same as the service notification — battery level alerts
            // contain no sensitive data, so they should always be fully
            // visible on the lock screen even when "Show sensitive content"
            // is turned off. See createServiceNotification() for the full
            // explanation of VISIBILITY_PUBLIC.
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID_ALERT, notification)
    }

    // ── DND helper ────────────────────────────────────────────

    /**
     * Checks whether Do Not Disturb (DND) mode is currently active.
     *
     * Uses [NotificationManager.getCurrentInterruptionFilter] which returns
     * one of the INTERRUPTION_FILTER_* constants. Only
     * [NotificationManager.INTERRUPTION_FILTER_ALL] (value 1) means "DND is
     * OFF — all sounds are allowed".  Every other value means some form of
     * DND is enabled.
     *
     * This method is `open` so that unit tests can override it without
     * needing a real Android NotificationManager.
     *
     * @return `true` when DND is active (sounds should be suppressed),
     *         `false` when all sounds are allowed.
     */
    open fun isDndModeActive(): Boolean {
        val notificationManager = getSystemService(NotificationManager::class.java)
        val filter = notificationManager.currentInterruptionFilter
        // INTERRUPTION_FILTER_ALL == 1 means NO DND
        val dndActive = filter != NotificationManager.INTERRUPTION_FILTER_ALL
        Log.d(TAG, "DND check: filter=$filter, dndActive=$dndActive")
        return dndActive
    }

    /**
     * Plays the default alarm sound for ALERT_DURATION_MS.
     *
     * Uses the system default alarm ringtone. The caller is responsible for
     * checking DND status before calling this method — if DND is active the
     * caller should skip calling [playAlertSound] altogether.
     */
    private fun playAlertSound() {
        try {
            // Use the user's chosen alarm sound URI if set, otherwise fall back
            // to the system default alarm sound, then notification sound as last resort.
            val alarmUri = if (alarmSoundUriString != null) {
                Uri.parse(alarmSoundUriString)
            } else {
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            }

            // Get the ringtone and play it
            val ringtone = RingtoneManager.getRingtone(this, alarmUri)

            // Set audio attributes to use alarm stream (respects DND for alarms)
            ringtone?.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            ringtone?.play()

            // Store reference to stop later (using a field would be cleaner but
            // we're keeping it simple for this learning project)
            currentRingtone = ringtone

            Log.d(TAG, "Started playing alert sound")
        } catch (e: Exception) {
            Log.e(TAG, "Error playing alert sound: ${e.message}")
        }
    }

    // Reference to the currently playing ringtone (to stop it later)
    private var currentRingtone: android.media.Ringtone? = null

    /**
     * Starts device vibration for ALERT_DURATION_MS.
     *
     * Uses a pattern that's noticeable but not too aggressive:
     * 500ms vibration, 200ms pause, repeated for the duration.
     */
    private fun startVibration() {
        try {
            // Get the vibrator service (different API for Android 12+)
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(VibratorManager::class.java)
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as? Vibrator
            }

            if (vibrator?.hasVibrator() != true) {
                Log.d(TAG, "Device doesn't have a vibrator")
                return
            }

            // Create a vibration pattern: wait, vibrate, wait, vibrate...
            // Pattern: 0ms delay, 500ms vibrate, 200ms pause, 500ms vibrate, ...
            // -1 for repeat index means don't repeat automatically (we'll handle duration ourselves)
            val pattern = longArrayOf(0, 500, 200, 500, 200, 500, 200)

            // Create vibration effect with pattern, repeating from index 1
            val effect = VibrationEffect.createWaveform(pattern, 1)

            // Start vibration with alarm attributes (respects DND)
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            vibrator.vibrate(effect, audioAttributes)

            currentVibrator = vibrator
            Log.d(TAG, "Started vibration")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting vibration: ${e.message}")
        }
    }

    // Reference to vibrator to stop it later
    private var currentVibrator: Vibrator? = null

    /**
     * Stops the current alert - silences sound and stops vibration.
     * Called when the alert duration expires or user dismisses.
     */
    private fun stopAlert() {
        Log.d(TAG, "Stopping alert")

        // Stop the ringtone
        try {
            currentRingtone?.stop()
            currentRingtone = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping ringtone: ${e.message}")
        }

        // Stop vibration
        try {
            currentVibrator?.cancel()
            currentVibrator = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping vibration: ${e.message}")
        }

        // Remove the pending stop runnable (in case we stopped early)
        handler.removeCallbacks(stopAlertRunnable)

        isAlertPlaying = false

        // Dismiss the alert notification
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.cancel(NOTIFICATION_ID_ALERT)
    }
}
