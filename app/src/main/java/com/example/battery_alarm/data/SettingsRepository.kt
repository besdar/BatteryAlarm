package com.example.battery_alarm.data

import android.content.Context
import android.content.SharedPreferences
import com.example.battery_alarm.service.BatteryMonitorService

/**
 * SettingsRepository is a centralized data layer for reading and writing
 * all user-configurable settings in the Battery Alarm app.
 *
 * ## Why a Repository?
 * Instead of scattering SharedPreferences calls throughout the codebase
 * (in the service, UI, receiver, etc.), we consolidate them here. This gives us:
 * - **Single source of truth**: All settings keys and defaults live in one place.
 * - **Encapsulation**: If we later migrate from SharedPreferences to DataStore or a
 *   database, only this file needs to change.
 * - **Readability**: The rest of the code just calls `repository.lowThreshold` instead
 *   of dealing with raw SharedPreferences.
 *
 * ## How SharedPreferences Work
 * SharedPreferences is Android's simple key-value storage backed by an XML file.
 * - It stores primitive types (Int, Long, Boolean, String, Float, Set<String>).
 * - It persists data across app restarts and device reboots.
 * - Data is stored in: `/data/data/<package>/shared_prefs/<name>.xml`
 * - It's synchronous for reads and asynchronous for writes (using `.apply()`).
 *
 * ## Thread Safety
 * SharedPreferences reads are thread-safe. Writes using `.apply()` are asynchronous
 * and also thread-safe. We use `.apply()` (non-blocking) instead of `.commit()`
 * (blocking) because we don't need to wait for the write to complete.
 *
 * @param context The application or activity context used to access SharedPreferences.
 *                Using application context is preferred to avoid memory leaks.
 */
class SettingsRepository(context: Context) {

    /**
     * The SharedPreferences instance that stores all our settings.
     *
     * We reuse the same preferences file ("battery_alarm_prefs") that the app
     * already uses for the service_enabled flag. This keeps all settings in one place.
     *
     * MODE_PRIVATE means only our app can access this file.
     */
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ─────────────────────────────────────────────────────────
    //  Low Battery Threshold (percentage, 5–95)
    // ─────────────────────────────────────────────────────────

    /**
     * The battery percentage at or below which a "low battery" alert fires.
     *
     * Default is 20% — a common threshold used by most phones' built-in alerts.
     * The user can change this in Settings if they want earlier or later warnings.
     *
     * Valid range: [MIN_THRESHOLD .. MAX_THRESHOLD] (5–95)
     */
    var lowThreshold: Int
        get() = prefs.getInt(KEY_LOW_THRESHOLD, DEFAULT_LOW_THRESHOLD)
        set(value) {
            // Clamp the value to valid range before saving
            val clamped = value.coerceIn(MIN_THRESHOLD, MAX_THRESHOLD)
            prefs.edit().putInt(KEY_LOW_THRESHOLD, clamped).apply()
        }

    // ─────────────────────────────────────────────────────────
    //  High Battery Threshold (percentage, 5–95)
    // ─────────────────────────────────────────────────────────

    /**
     * The battery percentage at or above which a "high battery" alert fires (while charging).
     *
     * Default is 80% — charging to ~80% and unplugging helps preserve long-term
     * battery health on lithium-ion batteries.
     *
     * Valid range: [MIN_THRESHOLD .. MAX_THRESHOLD] (5–95)
     */
    var highThreshold: Int
        get() = prefs.getInt(KEY_HIGH_THRESHOLD, DEFAULT_HIGH_THRESHOLD)
        set(value) {
            val clamped = value.coerceIn(MIN_THRESHOLD, MAX_THRESHOLD)
            prefs.edit().putInt(KEY_HIGH_THRESHOLD, clamped).apply()
        }

    // ─────────────────────────────────────────────────────────
    //  Minimum Alert Interval (in minutes)
    // ─────────────────────────────────────────────────────────

    /**
     * The minimum number of minutes between consecutive alerts.
     *
     * This prevents the app from annoying the user with too-frequent notifications.
     * Default is 10 minutes. The user can increase this for less frequent alerts
     * or decrease it (minimum 1 minute) for more responsive monitoring.
     *
     * Stored as minutes in preferences, converted to milliseconds when needed by the service.
     *
     * Valid range: [MIN_ALERT_INTERVAL_MINUTES .. MAX_ALERT_INTERVAL_MINUTES] (1–60)
     */
    var alertIntervalMinutes: Int
        get() = prefs.getInt(KEY_ALERT_INTERVAL_MINUTES, DEFAULT_ALERT_INTERVAL_MINUTES)
        set(value) {
            val clamped = value.coerceIn(MIN_ALERT_INTERVAL_MINUTES, MAX_ALERT_INTERVAL_MINUTES)
            prefs.edit().putInt(KEY_ALERT_INTERVAL_MINUTES, clamped).apply()
        }

    /**
     * Convenience property: returns the alert interval in milliseconds.
     * This is what the service actually needs for its rate-limiting logic.
     */
    val alertIntervalMs: Long
        get() = alertIntervalMinutes * 60 * 1000L

    // ─────────────────────────────────────────────────────────
    //  Full Charge Alert Interval (in minutes)
    // ─────────────────────────────────────────────────────────

    /**
     * How often (in minutes) to repeat the alert when the battery is at 100%.
     *
     * When the phone is fully charged and still plugged in, this setting controls
     * how often the user is reminded. Default is 30 minutes.
     *
     * Valid range: [MIN_FULL_CHARGE_INTERVAL_MINUTES .. MAX_FULL_CHARGE_INTERVAL_MINUTES] (5–120)
     */
    var fullChargeAlertIntervalMinutes: Int
        get() = prefs.getInt(KEY_FULL_CHARGE_INTERVAL_MINUTES, DEFAULT_FULL_CHARGE_INTERVAL_MINUTES)
        set(value) {
            val clamped = value.coerceIn(
                MIN_FULL_CHARGE_INTERVAL_MINUTES,
                MAX_FULL_CHARGE_INTERVAL_MINUTES
            )
            prefs.edit().putInt(KEY_FULL_CHARGE_INTERVAL_MINUTES, clamped).apply()
        }

    /**
     * Convenience property: returns the full-charge alert interval in milliseconds.
     */
    val fullChargeAlertIntervalMs: Long
        get() = fullChargeAlertIntervalMinutes * 60 * 1000L

    // ─────────────────────────────────────────────────────────
    //  Alert Duration (in seconds)
    // ─────────────────────────────────────────────────────────

    /**
     * How long (in seconds) the alert sound and vibration play before auto-stopping.
     *
     * Default is 15 seconds — long enough to notice, short enough not to be annoying.
     * The user can adjust this based on preference.
     *
     * Valid range: [MIN_ALERT_DURATION_SECONDS .. MAX_ALERT_DURATION_SECONDS] (5–60)
     */
    var alertDurationSeconds: Int
        get() = prefs.getInt(KEY_ALERT_DURATION_SECONDS, DEFAULT_ALERT_DURATION_SECONDS)
        set(value) {
            val clamped = value.coerceIn(MIN_ALERT_DURATION_SECONDS, MAX_ALERT_DURATION_SECONDS)
            prefs.edit().putInt(KEY_ALERT_DURATION_SECONDS, clamped).apply()
        }

    /**
     * Convenience property: returns the alert duration in milliseconds.
     */
    val alertDurationMs: Long
        get() = alertDurationSeconds * 1000L

    // ─────────────────────────────────────────────────────────
    //  Sound & Vibration Toggles
    // ─────────────────────────────────────────────────────────

    /**
     * Whether the alert should play a sound.
     * Default is true — most users expect an audible alert.
     */
    var isSoundEnabled: Boolean
        get() = prefs.getBoolean(KEY_SOUND_ENABLED, DEFAULT_SOUND_ENABLED)
        set(value) {
            prefs.edit().putBoolean(KEY_SOUND_ENABLED, value).apply()
        }

    /**
     * Whether the alert should vibrate the device.
     * Default is true — vibration helps catch attention even when the phone is on silent.
     */
    var isVibrationEnabled: Boolean
        get() = prefs.getBoolean(KEY_VIBRATION_ENABLED, DEFAULT_VIBRATION_ENABLED)
        set(value) {
            prefs.edit().putBoolean(KEY_VIBRATION_ENABLED, value).apply()
        }

    // ─────────────────────────────────────────────────────────
    //  Service Enabled State (already existed in the app)
    // ─────────────────────────────────────────────────────────

    /**
     * Whether the battery monitoring service should be running.
     * This was previously accessed directly in MainScreen and BootCompletedReceiver.
     * Now it's centralized here for consistency.
     */
    var isServiceEnabled: Boolean
        get() = prefs.getBoolean(KEY_SERVICE_ENABLED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_SERVICE_ENABLED, value).apply()
        }

    // ─────────────────────────────────────────────────────────
    //  Reset to Defaults
    // ─────────────────────────────────────────────────────────

    /**
     * Resets all configurable settings back to their default values.
     * Does NOT reset the service_enabled flag (that's a runtime state, not a "setting").
     *
     * Uses a single `.edit()` block with `.apply()` at the end so all changes
     * are written atomically in one batch.
     */
    fun resetToDefaults() {
        prefs.edit()
            .putInt(KEY_LOW_THRESHOLD, DEFAULT_LOW_THRESHOLD)
            .putInt(KEY_HIGH_THRESHOLD, DEFAULT_HIGH_THRESHOLD)
            .putInt(KEY_ALERT_INTERVAL_MINUTES, DEFAULT_ALERT_INTERVAL_MINUTES)
            .putInt(KEY_FULL_CHARGE_INTERVAL_MINUTES, DEFAULT_FULL_CHARGE_INTERVAL_MINUTES)
            .putInt(KEY_ALERT_DURATION_SECONDS, DEFAULT_ALERT_DURATION_SECONDS)
            .putBoolean(KEY_SOUND_ENABLED, DEFAULT_SOUND_ENABLED)
            .putBoolean(KEY_VIBRATION_ENABLED, DEFAULT_VIBRATION_ENABLED)
            .apply()
    }

    companion object {
        // ── SharedPreferences file name ──
        // This is the same file the app already uses for "service_enabled"
        const val PREFS_NAME = "battery_alarm_prefs"

        // ── SharedPreferences keys ──
        // Each setting has a unique key used to store/retrieve its value
        private const val KEY_LOW_THRESHOLD = "low_threshold"
        private const val KEY_HIGH_THRESHOLD = "high_threshold"
        private const val KEY_ALERT_INTERVAL_MINUTES = "alert_interval_minutes"
        private const val KEY_FULL_CHARGE_INTERVAL_MINUTES = "full_charge_alert_interval_minutes"
        private const val KEY_ALERT_DURATION_SECONDS = "alert_duration_seconds"
        private const val KEY_SOUND_ENABLED = "sound_enabled"
        private const val KEY_VIBRATION_ENABLED = "vibration_enabled"
        const val KEY_SERVICE_ENABLED = "service_enabled"

        // ── Default values ──
        // These match the original hardcoded constants in BatteryMonitorService

        /** Default low battery threshold: 20% */
        const val DEFAULT_LOW_THRESHOLD = BatteryMonitorService.DEFAULT_LOW_THRESHOLD   // 20

        /** Default high battery threshold: 80% */
        const val DEFAULT_HIGH_THRESHOLD = BatteryMonitorService.DEFAULT_HIGH_THRESHOLD  // 80

        /** Default minimum alert interval: 10 minutes */
        const val DEFAULT_ALERT_INTERVAL_MINUTES = 10

        /** Default full-charge reminder interval: 30 minutes */
        const val DEFAULT_FULL_CHARGE_INTERVAL_MINUTES = 30

        /** Default alert sound/vibration duration: 15 seconds */
        const val DEFAULT_ALERT_DURATION_SECONDS = 15

        /** Default: sound alerts are ON */
        const val DEFAULT_SOUND_ENABLED = true

        /** Default: vibration alerts are ON */
        const val DEFAULT_VIBRATION_ENABLED = true

        // ── Validation ranges ──
        // These define the minimum and maximum values the user can set

        /** Minimum battery threshold: 5% */
        const val MIN_THRESHOLD = 5

        /** Maximum battery threshold: 95% */
        const val MAX_THRESHOLD = 95

        /** Minimum alert interval: 5 minutes */
        const val MIN_ALERT_INTERVAL_MINUTES = 5

        /** Maximum alert interval: 60 minutes */
        const val MAX_ALERT_INTERVAL_MINUTES = 60

        /** Minimum full-charge alert interval: 5 minutes */
        const val MIN_FULL_CHARGE_INTERVAL_MINUTES = 5

        /** Maximum full-charge alert interval: 120 minutes (2 hours) */
        const val MAX_FULL_CHARGE_INTERVAL_MINUTES = 120

        /** Minimum alert duration: 5 seconds */
        const val MIN_ALERT_DURATION_SECONDS = 5

        /** Maximum alert duration: 60 seconds */
        const val MAX_ALERT_DURATION_SECONDS = 60
    }
}
