package com.example.battery_alarm.data

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Unit tests for [SettingsRepository].
 *
 * ## What Are We Testing?
 * SettingsRepository is the single source of truth for all user-configurable settings.
 * It wraps SharedPreferences and adds:
 *   - Default values (so the app works out-of-the-box)
 *   - Value clamping (so invalid values can't be stored)
 *   - Convenience millisecond conversions (used by the service)
 *   - A "reset to defaults" function
 *
 * We need to verify all of these behaviors work correctly.
 *
 * ## Why Robolectric?
 * SharedPreferences is an Android API — it needs a real `Context` to work.
 * Robolectric provides a fake Android environment that runs on the JVM (your
 * development machine), so these tests run fast without needing a phone or emulator.
 *
 * The `@RunWith(RobolectricTestRunner::class)` annotation tells JUnit to use
 * Robolectric's custom test runner, which sets up the fake Android environment
 * before each test.
 *
 * ## Test Naming Convention
 * Each test name follows the pattern:
 *   `propertyName_scenario_expectedBehavior`
 * For example: `lowThreshold_defaultValue_returns20`
 * This makes it easy to understand what each test checks at a glance.
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    // Use the latest SDK that Robolectric supports for maximum compatibility.
    sdk = [34]
)
class SettingsRepositoryTest {

    // ── Test fixtures ────────────────────────────────────────────
    // These are set up fresh before EVERY test so tests don't affect each other.

    /** The Android application context provided by Robolectric. */
    private lateinit var context: Context

    /** The repository instance we're testing. Created fresh for each test. */
    private lateinit var repository: SettingsRepository

    /**
     * The raw SharedPreferences file — used to verify that the repository
     * actually writes values to storage (not just keeping them in memory).
     */
    private lateinit var prefs: SharedPreferences

    /**
     * Runs before EVERY test method.
     *
     * @Before is a JUnit annotation that marks a setup method. JUnit calls this
     * before each @Test method, ensuring every test starts with a clean state.
     *
     * We create a fresh Context, clear any leftover SharedPreferences data,
     * and instantiate a new SettingsRepository.
     */
    @Before
    fun setUp() {
        // RuntimeEnvironment.getApplication() gives us a fake Application context
        // that Robolectric manages. It behaves like a real Android Context.
        context = RuntimeEnvironment.getApplication()

        // Get a direct reference to the SharedPreferences file so we can
        // verify what the repository actually wrote to disk.
        prefs = context.getSharedPreferences(
            SettingsRepository.PREFS_NAME,
            Context.MODE_PRIVATE
        )

        // Clear all data before each test so previous tests don't leak state.
        // This is critical for test isolation — each test must be independent.
        prefs.edit().clear().commit()

        // Create a fresh repository instance that reads from the clean SharedPreferences
        repository = SettingsRepository(context)
    }

    // ═════════════════════════════════════════════════════════════
    //  1. DEFAULT VALUES
    // ═════════════════════════════════════════════════════════════
    //
    // These tests verify that when no value has been stored yet,
    // the repository returns the correct default for each setting.
    // This is important because new users who haven't opened Settings
    // should get sensible defaults.
    // ═════════════════════════════════════════════════════════════

    @Test
    fun `lowThreshold - default value - returns 20`() {
        // When: we read the low threshold without ever setting it
        val result = repository.lowThreshold

        // Then: we should get the default value (20%)
        assertEquals(
            "Default low threshold should be 20%",
            SettingsRepository.DEFAULT_LOW_THRESHOLD,  // expected: 20
            result                                      // actual
        )
    }

    @Test
    fun `highThreshold - default value - returns 80`() {
        val result = repository.highThreshold
        assertEquals(
            "Default high threshold should be 80%",
            SettingsRepository.DEFAULT_HIGH_THRESHOLD,  // expected: 80
            result
        )
    }

    @Test
    fun `alertIntervalMinutes - default value - returns 10`() {
        val result = repository.alertIntervalMinutes
        assertEquals(
            "Default alert interval should be 10 minutes",
            SettingsRepository.DEFAULT_ALERT_INTERVAL_MINUTES,  // expected: 10
            result
        )
    }

    @Test
    fun `fullChargeAlertIntervalMinutes - default value - returns 30`() {
        val result = repository.fullChargeAlertIntervalMinutes
        assertEquals(
            "Default full-charge interval should be 30 minutes",
            SettingsRepository.DEFAULT_FULL_CHARGE_INTERVAL_MINUTES,  // expected: 30
            result
        )
    }

    @Test
    fun `alertDurationSeconds - default value - returns 15`() {
        val result = repository.alertDurationSeconds
        assertEquals(
            "Default alert duration should be 15 seconds",
            SettingsRepository.DEFAULT_ALERT_DURATION_SECONDS,  // expected: 15
            result
        )
    }

    @Test
    fun `isSoundEnabled - default value - returns true`() {
        val result = repository.isSoundEnabled
        assertTrue(
            "Sound should be enabled by default",
            result
        )
    }

    @Test
    fun `isVibrationEnabled - default value - returns true`() {
        val result = repository.isVibrationEnabled
        assertTrue(
            "Vibration should be enabled by default",
            result
        )
    }

    @Test
    fun `isServiceEnabled - default value - returns false`() {
        // The service should NOT be enabled by default — user must explicitly turn it on
        val result = repository.isServiceEnabled
        assertFalse(
            "Service should be disabled by default",
            result
        )
    }

    // ═════════════════════════════════════════════════════════════
    //  2. READ-WRITE ROUND-TRIP (normal values)
    // ═════════════════════════════════════════════════════════════
    //
    // These tests verify that when we write a valid value, we can
    // read it back correctly. This is the most basic "does it work?"
    // check for each setting.
    // ═════════════════════════════════════════════════════════════

    @Test
    fun `lowThreshold - set valid value - reads back correctly`() {
        // When: we set the low threshold to 30%
        repository.lowThreshold = 30

        // Then: reading it back should return 30
        assertEquals(30, repository.lowThreshold)
    }

    @Test
    fun `highThreshold - set valid value - reads back correctly`() {
        repository.highThreshold = 70
        assertEquals(70, repository.highThreshold)
    }

    @Test
    fun `alertIntervalMinutes - set valid value - reads back correctly`() {
        repository.alertIntervalMinutes = 25
        assertEquals(25, repository.alertIntervalMinutes)
    }

    @Test
    fun `fullChargeAlertIntervalMinutes - set valid value - reads back correctly`() {
        repository.fullChargeAlertIntervalMinutes = 60
        assertEquals(60, repository.fullChargeAlertIntervalMinutes)
    }

    @Test
    fun `alertDurationSeconds - set valid value - reads back correctly`() {
        repository.alertDurationSeconds = 30
        assertEquals(30, repository.alertDurationSeconds)
    }

    @Test
    fun `isSoundEnabled - set to false - reads back false`() {
        repository.isSoundEnabled = false
        assertFalse(repository.isSoundEnabled)
    }

    @Test
    fun `isVibrationEnabled - set to false - reads back false`() {
        repository.isVibrationEnabled = false
        assertFalse(repository.isVibrationEnabled)
    }

    @Test
    fun `isServiceEnabled - set to true - reads back true`() {
        repository.isServiceEnabled = true
        assertTrue(repository.isServiceEnabled)
    }

    // ═════════════════════════════════════════════════════════════
    //  3. PERSISTENCE (values survive new repository instances)
    // ═════════════════════════════════════════════════════════════
    //
    // SharedPreferences persists data to an XML file on disk.
    // These tests verify that values written by one SettingsRepository
    // instance can be read by a NEW instance — simulating what happens
    // when the app is closed and reopened.
    // ═════════════════════════════════════════════════════════════

    @Test
    fun `lowThreshold - persists across repository instances`() {
        // Given: we set a value using the first repository instance
        repository.lowThreshold = 45

        // When: we create a brand new repository instance (simulating app restart)
        val newRepository = SettingsRepository(context)

        // Then: the new instance should read the same value from SharedPreferences
        assertEquals(
            "Value should persist across repository instances",
            45,
            newRepository.lowThreshold
        )
    }

    @Test
    fun `boolean settings - persist across repository instances`() {
        // Given: we change boolean settings
        repository.isSoundEnabled = false
        repository.isVibrationEnabled = false
        repository.isServiceEnabled = true

        // When: we create a new repository (simulating app restart)
        val newRepository = SettingsRepository(context)

        // Then: all values should be preserved
        assertFalse("Sound setting should persist", newRepository.isSoundEnabled)
        assertFalse("Vibration setting should persist", newRepository.isVibrationEnabled)
        assertTrue("Service enabled setting should persist", newRepository.isServiceEnabled)
    }

    // ═════════════════════════════════════════════════════════════
    //  4. VALUE CLAMPING (out-of-range values are constrained)
    // ═════════════════════════════════════════════════════════════
    //
    // The repository uses `.coerceIn()` to clamp values to valid ranges.
    // This prevents bugs where an invalid value (e.g., -5% battery
    // threshold) could cause unexpected behavior in the service.
    //
    // We test both directions: values too low AND values too high.
    // ═════════════════════════════════════════════════════════════

    // ── Low Threshold clamping ───────────────────────────────────

    @Test
    fun `lowThreshold - value below minimum - clamped to MIN_THRESHOLD`() {
        // When: we try to set a value below the minimum (5%)
        repository.lowThreshold = 1  // below MIN_THRESHOLD (5)

        // Then: the value should be clamped to the minimum
        assertEquals(
            "Values below minimum should be clamped to MIN_THRESHOLD (5)",
            SettingsRepository.MIN_THRESHOLD,  // expected: 5
            repository.lowThreshold
        )
    }

    @Test
    fun `lowThreshold - value above maximum - clamped to MAX_THRESHOLD`() {
        // When: we try to set a value above the maximum (95%)
        repository.lowThreshold = 100  // above MAX_THRESHOLD (95)

        // Then: the value should be clamped to the maximum
        assertEquals(
            "Values above maximum should be clamped to MAX_THRESHOLD (95)",
            SettingsRepository.MAX_THRESHOLD,  // expected: 95
            repository.lowThreshold
        )
    }

    @Test
    fun `lowThreshold - negative value - clamped to MIN_THRESHOLD`() {
        // Edge case: what about negative numbers?
        repository.lowThreshold = -10

        assertEquals(
            "Negative values should be clamped to MIN_THRESHOLD",
            SettingsRepository.MIN_THRESHOLD,
            repository.lowThreshold
        )
    }

    // ── High Threshold clamping ──────────────────────────────────

    @Test
    fun `highThreshold - value below minimum - clamped to MIN_THRESHOLD`() {
        repository.highThreshold = 0
        assertEquals(SettingsRepository.MIN_THRESHOLD, repository.highThreshold)
    }

    @Test
    fun `highThreshold - value above maximum - clamped to MAX_THRESHOLD`() {
        repository.highThreshold = 200
        assertEquals(SettingsRepository.MAX_THRESHOLD, repository.highThreshold)
    }

    // ── Alert Interval clamping ──────────────────────────────────

    @Test
    fun `alertIntervalMinutes - value below minimum - clamped to MIN`() {
        repository.alertIntervalMinutes = 1  // below MIN (5)
        assertEquals(
            "Alert interval below minimum should be clamped to 5",
            SettingsRepository.MIN_ALERT_INTERVAL_MINUTES,
            repository.alertIntervalMinutes
        )
    }

    @Test
    fun `alertIntervalMinutes - value above maximum - clamped to MAX`() {
        repository.alertIntervalMinutes = 999  // above MAX (60)
        assertEquals(
            "Alert interval above maximum should be clamped to 60",
            SettingsRepository.MAX_ALERT_INTERVAL_MINUTES,
            repository.alertIntervalMinutes
        )
    }

    // ── Full Charge Interval clamping ────────────────────────────

    @Test
    fun `fullChargeAlertIntervalMinutes - value below minimum - clamped to MIN`() {
        repository.fullChargeAlertIntervalMinutes = 2  // below MIN (5)
        assertEquals(
            SettingsRepository.MIN_FULL_CHARGE_INTERVAL_MINUTES,
            repository.fullChargeAlertIntervalMinutes
        )
    }

    @Test
    fun `fullChargeAlertIntervalMinutes - value above maximum - clamped to MAX`() {
        repository.fullChargeAlertIntervalMinutes = 500  // above MAX (120)
        assertEquals(
            SettingsRepository.MAX_FULL_CHARGE_INTERVAL_MINUTES,
            repository.fullChargeAlertIntervalMinutes
        )
    }

    // ── Alert Duration clamping ──────────────────────────────────

    @Test
    fun `alertDurationSeconds - value below minimum - clamped to MIN`() {
        repository.alertDurationSeconds = 1  // below MIN (5)
        assertEquals(
            SettingsRepository.MIN_ALERT_DURATION_SECONDS,
            repository.alertDurationSeconds
        )
    }

    @Test
    fun `alertDurationSeconds - value above maximum - clamped to MAX`() {
        repository.alertDurationSeconds = 120  // above MAX (60)
        assertEquals(
            SettingsRepository.MAX_ALERT_DURATION_SECONDS,
            repository.alertDurationSeconds
        )
    }

    // ── Boundary values (exactly at min/max) ─────────────────────
    // These verify that the clamping doesn't accidentally exclude
    // the boundary values themselves.

    @Test
    fun `lowThreshold - exact minimum value - accepted without clamping`() {
        repository.lowThreshold = SettingsRepository.MIN_THRESHOLD  // 5
        assertEquals(SettingsRepository.MIN_THRESHOLD, repository.lowThreshold)
    }

    @Test
    fun `lowThreshold - exact maximum value - accepted without clamping`() {
        repository.lowThreshold = SettingsRepository.MAX_THRESHOLD  // 95
        assertEquals(SettingsRepository.MAX_THRESHOLD, repository.lowThreshold)
    }

    @Test
    fun `alertIntervalMinutes - exact minimum value - accepted without clamping`() {
        repository.alertIntervalMinutes = SettingsRepository.MIN_ALERT_INTERVAL_MINUTES  // 5
        assertEquals(SettingsRepository.MIN_ALERT_INTERVAL_MINUTES, repository.alertIntervalMinutes)
    }

    @Test
    fun `alertIntervalMinutes - exact maximum value - accepted without clamping`() {
        repository.alertIntervalMinutes = SettingsRepository.MAX_ALERT_INTERVAL_MINUTES  // 60
        assertEquals(SettingsRepository.MAX_ALERT_INTERVAL_MINUTES, repository.alertIntervalMinutes)
    }

    // ═════════════════════════════════════════════════════════════
    //  5. MILLISECOND CONVENIENCE CONVERSIONS
    // ═════════════════════════════════════════════════════════════
    //
    // The service works in milliseconds internally, but users think
    // in minutes and seconds. The repository provides convenience
    // properties that do the conversion. We verify the math is correct.
    // ═════════════════════════════════════════════════════════════

    @Test
    fun `alertIntervalMs - converts minutes to milliseconds correctly`() {
        // Given: alert interval is set to 10 minutes
        repository.alertIntervalMinutes = 10

        // When: we read the millisecond value
        val result = repository.alertIntervalMs

        // Then: 10 minutes = 10 * 60 * 1000 = 600,000 ms
        assertEquals(
            "10 minutes should equal 600,000 milliseconds",
            600_000L,  // expected
            result     // actual
        )
    }

    @Test
    fun `alertIntervalMs - default value - equals 10 minutes in ms`() {
        // The default is 10 minutes = 600,000 ms
        assertEquals(
            10 * 60 * 1000L,
            repository.alertIntervalMs
        )
    }

    @Test
    fun `fullChargeAlertIntervalMs - converts minutes to milliseconds correctly`() {
        repository.fullChargeAlertIntervalMinutes = 30
        // 30 minutes = 30 * 60 * 1000 = 1,800,000 ms
        assertEquals(1_800_000L, repository.fullChargeAlertIntervalMs)
    }

    @Test
    fun `fullChargeAlertIntervalMs - custom value - converts correctly`() {
        repository.fullChargeAlertIntervalMinutes = 120  // maximum: 2 hours
        // 120 minutes = 120 * 60 * 1000 = 7,200,000 ms
        assertEquals(7_200_000L, repository.fullChargeAlertIntervalMs)
    }

    @Test
    fun `alertDurationMs - converts seconds to milliseconds correctly`() {
        repository.alertDurationSeconds = 15
        // 15 seconds = 15 * 1000 = 15,000 ms
        assertEquals(15_000L, repository.alertDurationMs)
    }

    @Test
    fun `alertDurationMs - maximum value - converts correctly`() {
        repository.alertDurationSeconds = 60  // maximum
        // 60 seconds = 60,000 ms
        assertEquals(60_000L, repository.alertDurationMs)
    }

    // ═════════════════════════════════════════════════════════════
    //  6. RESET TO DEFAULTS
    // ═════════════════════════════════════════════════════════════
    //
    // The resetToDefaults() method should restore all configurable
    // settings to their original values, but NOT touch the
    // service_enabled flag (which is a runtime state, not a "setting").
    // ═════════════════════════════════════════════════════════════

    @Test
    fun `resetToDefaults - all settings return to default values`() {
        // Given: we change every setting to a non-default value
        repository.lowThreshold = 50
        repository.highThreshold = 60
        repository.alertIntervalMinutes = 30
        repository.fullChargeAlertIntervalMinutes = 90
        repository.alertDurationSeconds = 45
        repository.isSoundEnabled = false
        repository.isVibrationEnabled = false

        // When: we reset to defaults
        repository.resetToDefaults()

        // Then: all settings should return to their default values
        assertEquals(
            "Low threshold should reset to default",
            SettingsRepository.DEFAULT_LOW_THRESHOLD,
            repository.lowThreshold
        )
        assertEquals(
            "High threshold should reset to default",
            SettingsRepository.DEFAULT_HIGH_THRESHOLD,
            repository.highThreshold
        )
        assertEquals(
            "Alert interval should reset to default",
            SettingsRepository.DEFAULT_ALERT_INTERVAL_MINUTES,
            repository.alertIntervalMinutes
        )
        assertEquals(
            "Full charge interval should reset to default",
            SettingsRepository.DEFAULT_FULL_CHARGE_INTERVAL_MINUTES,
            repository.fullChargeAlertIntervalMinutes
        )
        assertEquals(
            "Alert duration should reset to default",
            SettingsRepository.DEFAULT_ALERT_DURATION_SECONDS,
            repository.alertDurationSeconds
        )
        assertTrue(
            "Sound should be re-enabled after reset",
            repository.isSoundEnabled
        )
        assertTrue(
            "Vibration should be re-enabled after reset",
            repository.isVibrationEnabled
        )
    }

    @Test
    fun `resetToDefaults - does NOT reset service enabled state`() {
        // Given: the service is enabled
        repository.isServiceEnabled = true

        // And: we change some settings
        repository.lowThreshold = 50

        // When: we reset to defaults
        repository.resetToDefaults()

        // Then: the service should STILL be enabled (not reset)
        // This is by design — service_enabled is a runtime state, not a user preference
        assertTrue(
            "resetToDefaults should NOT change the service_enabled flag",
            repository.isServiceEnabled
        )
    }

    @Test
    fun `resetToDefaults - reset is persisted to SharedPreferences`() {
        // Given: we change settings and then reset
        repository.lowThreshold = 50
        repository.resetToDefaults()

        // When: we create a new repository instance (simulating app restart)
        val newRepository = SettingsRepository(context)

        // Then: the new instance should see the default values (not 50)
        assertEquals(
            "Reset values should persist across instances",
            SettingsRepository.DEFAULT_LOW_THRESHOLD,
            newRepository.lowThreshold
        )
    }

    // ═════════════════════════════════════════════════════════════
    //  7. SHARED PREFERENCES KEY CONSISTENCY
    // ═════════════════════════════════════════════════════════════
    //
    // These tests verify that the repository writes to the correct
    // SharedPreferences file and uses the expected keys.
    // This matters because the BatteryMonitorService and
    // BootCompletedReceiver read from the same file.
    // ═════════════════════════════════════════════════════════════

    @Test
    fun `repository writes to the correct SharedPreferences file`() {
        // When: we write a setting through the repository
        repository.lowThreshold = 42

        // Then: we should be able to read it from the raw SharedPreferences
        // using the same file name ("battery_alarm_prefs")
        val rawValue = prefs.getInt("low_threshold", -1)
        assertEquals(
            "Repository should write to 'battery_alarm_prefs' file",
            42,
            rawValue
        )
    }

    @Test
    fun `service enabled key matches the constant used by BootCompletedReceiver`() {
        // The KEY_SERVICE_ENABLED constant is "service_enabled".
        // BootCompletedReceiver also uses this key to check if the service
        // should restart after boot. They MUST match.
        repository.isServiceEnabled = true

        val rawValue = prefs.getBoolean(SettingsRepository.KEY_SERVICE_ENABLED, false)
        assertTrue(
            "The service_enabled key must be accessible via the public constant",
            rawValue
        )
    }

    // ═════════════════════════════════════════════════════════════
    //  8. COMPANION OBJECT CONSTANTS SANITY CHECKS
    // ═════════════════════════════════════════════════════════════
    //
    // These tests verify that the constant values are sensible and
    // that ranges are valid (min < max). This catches copy-paste
    // errors or accidental constant changes.
    // ═════════════════════════════════════════════════════════════

    @Test
    fun `threshold range is valid - min is less than max`() {
        assertTrue(
            "MIN_THRESHOLD should be less than MAX_THRESHOLD",
            SettingsRepository.MIN_THRESHOLD < SettingsRepository.MAX_THRESHOLD
        )
    }

    @Test
    fun `alert interval range is valid - min is less than max`() {
        assertTrue(
            "MIN_ALERT_INTERVAL_MINUTES should be less than MAX_ALERT_INTERVAL_MINUTES",
            SettingsRepository.MIN_ALERT_INTERVAL_MINUTES < SettingsRepository.MAX_ALERT_INTERVAL_MINUTES
        )
    }

    @Test
    fun `full charge interval range is valid - min is less than max`() {
        assertTrue(
            SettingsRepository.MIN_FULL_CHARGE_INTERVAL_MINUTES <
                    SettingsRepository.MAX_FULL_CHARGE_INTERVAL_MINUTES
        )
    }

    @Test
    fun `alert duration range is valid - min is less than max`() {
        assertTrue(
            SettingsRepository.MIN_ALERT_DURATION_SECONDS <
                    SettingsRepository.MAX_ALERT_DURATION_SECONDS
        )
    }

    @Test
    fun `default values are within their valid ranges`() {
        // Each default must fall within its [min..max] range
        assertTrue(
            "Default low threshold should be within range",
            SettingsRepository.DEFAULT_LOW_THRESHOLD in
                    SettingsRepository.MIN_THRESHOLD..SettingsRepository.MAX_THRESHOLD
        )
        assertTrue(
            "Default high threshold should be within range",
            SettingsRepository.DEFAULT_HIGH_THRESHOLD in
                    SettingsRepository.MIN_THRESHOLD..SettingsRepository.MAX_THRESHOLD
        )
        assertTrue(
            "Default alert interval should be within range",
            SettingsRepository.DEFAULT_ALERT_INTERVAL_MINUTES in
                    SettingsRepository.MIN_ALERT_INTERVAL_MINUTES..SettingsRepository.MAX_ALERT_INTERVAL_MINUTES
        )
        assertTrue(
            "Default full-charge interval should be within range",
            SettingsRepository.DEFAULT_FULL_CHARGE_INTERVAL_MINUTES in
                    SettingsRepository.MIN_FULL_CHARGE_INTERVAL_MINUTES..SettingsRepository.MAX_FULL_CHARGE_INTERVAL_MINUTES
        )
        assertTrue(
            "Default alert duration should be within range",
            SettingsRepository.DEFAULT_ALERT_DURATION_SECONDS in
                    SettingsRepository.MIN_ALERT_DURATION_SECONDS..SettingsRepository.MAX_ALERT_DURATION_SECONDS
        )
    }

    // ═════════════════════════════════════════════════════════════
    //  9. OVERWRITE BEHAVIOR
    // ═════════════════════════════════════════════════════════════
    //
    // Verify that writing a new value overwrites the old one
    // (not appending or ignoring).
    // ═════════════════════════════════════════════════════════════

    @Test
    fun `lowThreshold - overwriting value - new value replaces old`() {
        // Given: we set an initial value
        repository.lowThreshold = 30
        assertEquals(30, repository.lowThreshold)

        // When: we set a different value
        repository.lowThreshold = 50

        // Then: the new value should replace the old one
        assertEquals(
            "New value should replace the old value",
            50,
            repository.lowThreshold
        )
    }

    @Test
    fun `boolean toggle - toggling back and forth - final value sticks`() {
        // Toggle sound on → off → on → off
        repository.isSoundEnabled = true
        assertTrue(repository.isSoundEnabled)

        repository.isSoundEnabled = false
        assertFalse(repository.isSoundEnabled)

        repository.isSoundEnabled = true
        assertTrue(repository.isSoundEnabled)

        repository.isSoundEnabled = false
        assertFalse("Final toggle state should be false", repository.isSoundEnabled)
    }
}
