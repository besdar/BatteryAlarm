package com.example.battery_alarm.service

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [BatteryMonitorService].
 *
 * ## What Can We Test Here?
 * BatteryMonitorService is a foreground Android Service with many system dependencies
 * (NotificationManager, Vibrator, RingtoneManager, BroadcastReceiver, etc.).
 * Testing the full service lifecycle requires an actual Android environment (or very
 * complex mocking), which is better suited for instrumented tests on a real device.
 *
 * However, we CAN test:
 * 1. **Companion object constants**: Verify that notification channel IDs, intent
 *    actions, and default thresholds have the expected values. These constants are
 *    used by other parts of the app, so accidental changes would break things.
 * 2. **The `isRunning` flag**: Verify its default state and behavior.
 * 3. **Static helper methods**: The `start()` and `stop()` methods are thin wrappers
 *    around Android APIs — they're better tested in integration tests.
 *
 * ## Why Not Test Everything?
 * The private methods (checkAndAlert, shouldAlertBasedOnProgression, triggerAlert, etc.)
 * are tightly coupled to Android system services. To test them properly, you'd need
 * either:
 * - **Instrumented tests** (run on a real device/emulator)
 * - **Refactoring** to extract the alert logic into a separate, testable class
 *
 * For this learning project, we focus on what's testable without over-engineering.
 * The companion object tests catch the most common bugs: accidental constant changes
 * and copy-paste errors.
 *
 * ## No Robolectric Needed Here!
 * Since we're only testing static constants and a volatile boolean flag, we don't
 * need an Android Context. These tests run as plain JVM unit tests — the fastest
 * kind of test.
 */
class BatteryMonitorServiceTest {

    // ═════════════════════════════════════════════════════════════
    //  1. NOTIFICATION CHANNEL IDs
    // ═════════════════════════════════════════════════════════════
    //
    // These IDs are used by the service to create notification channels
    // and by external code to reference them. If they change accidentally,
    // notifications would break or duplicate channels would be created.
    // ═════════════════════════════════════════════════════════════

    @Test
    fun `CHANNEL_ID_SERVICE - has expected value`() {
        // This channel is used for the persistent "monitoring active" notification.
        // It should be a descriptive string that doesn't change between app versions.
        assertEquals(
            "Service notification channel ID should be stable",
            "battery_monitor_service_channel",
            BatteryMonitorService.CHANNEL_ID_SERVICE
        )
    }

    @Test
    fun `CHANNEL_ID_ALERTS - has expected value`() {
        // This channel is used for battery alert notifications (high priority).
        assertEquals(
            "Alerts notification channel ID should be stable",
            "battery_alert_channel",
            BatteryMonitorService.CHANNEL_ID_ALERTS
        )
    }

    @Test
    fun `notification channel IDs are different from each other`() {
        // Android requires unique channel IDs. If both channels had the same ID,
        // they'd share settings and the service notification might make sounds,
        // or alerts might be silent.
        assertNotEquals(
            "Service and alert channels must have different IDs",
            BatteryMonitorService.CHANNEL_ID_SERVICE,
            BatteryMonitorService.CHANNEL_ID_ALERTS
        )
    }

    // ═════════════════════════════════════════════════════════════
    //  2. INTENT ACTIONS
    // ═════════════════════════════════════════════════════════════
    //
    // These action strings are used in PendingIntents attached to
    // notification buttons. If they change, the notification buttons
    // would stop working.
    // ═════════════════════════════════════════════════════════════

    @Test
    fun `ACTION_STOP_SERVICE - has expected value`() {
        // Used by the "Stop" button on the persistent service notification
        assertEquals(
            "com.example.battery_alarm.STOP_SERVICE",
            BatteryMonitorService.ACTION_STOP_SERVICE
        )
    }

    @Test
    fun `ACTION_DISMISS_ALERT - has expected value`() {
        // Used by the "Dismiss" button on alert notifications
        assertEquals(
            "com.example.battery_alarm.DISMISS_ALERT",
            BatteryMonitorService.ACTION_DISMISS_ALERT
        )
    }

    @Test
    fun `intent actions are different from each other`() {
        // If both actions had the same string, pressing "Dismiss" on an alert
        // would stop the entire service, or vice versa.
        assertNotEquals(
            "Stop and dismiss actions must be different",
            BatteryMonitorService.ACTION_STOP_SERVICE,
            BatteryMonitorService.ACTION_DISMISS_ALERT
        )
    }

    // ═════════════════════════════════════════════════════════════
    //  3. DEFAULT THRESHOLDS
    // ═════════════════════════════════════════════════════════════
    //
    // These default values are used by SettingsRepository and the
    // service itself. They represent the battery percentages at which
    // alerts should fire.
    // ═════════════════════════════════════════════════════════════

    @Test
    fun `DEFAULT_LOW_THRESHOLD - is 20 percent`() {
        // 20% is a standard low-battery warning level used by most phones.
        assertEquals(
            "Default low threshold should be 20%",
            20,
            BatteryMonitorService.DEFAULT_LOW_THRESHOLD
        )
    }

    @Test
    fun `DEFAULT_HIGH_THRESHOLD - is 80 percent`() {
        // 80% is recommended to preserve lithium-ion battery health.
        assertEquals(
            "Default high threshold should be 80%",
            80,
            BatteryMonitorService.DEFAULT_HIGH_THRESHOLD
        )
    }

    @Test
    fun `default thresholds are in valid percentage range`() {
        // Battery percentages must be between 0 and 100
        assertTrue(
            "Low threshold must be >= 0",
            BatteryMonitorService.DEFAULT_LOW_THRESHOLD >= 0
        )
        assertTrue(
            "Low threshold must be <= 100",
            BatteryMonitorService.DEFAULT_LOW_THRESHOLD <= 100
        )
        assertTrue(
            "High threshold must be >= 0",
            BatteryMonitorService.DEFAULT_HIGH_THRESHOLD >= 0
        )
        assertTrue(
            "High threshold must be <= 100",
            BatteryMonitorService.DEFAULT_HIGH_THRESHOLD <= 100
        )
    }

    @Test
    fun `low threshold is less than high threshold`() {
        // It wouldn't make sense for the "low" alert to trigger at a higher
        // level than the "high" alert. This catches accidental swaps.
        assertTrue(
            "Low threshold should be lower than high threshold",
            BatteryMonitorService.DEFAULT_LOW_THRESHOLD < BatteryMonitorService.DEFAULT_HIGH_THRESHOLD
        )
    }

    // ═════════════════════════════════════════════════════════════
    //  4. isRunning FLAG
    // ═════════════════════════════════════════════════════════════
    //
    // The `isRunning` flag is a @Volatile static boolean that tracks
    // whether the service is currently active. The UI reads this to
    // sync the toggle button state after a reboot.
    // ═════════════════════════════════════════════════════════════

    @Test
    fun `isRunning - default state - is false`() {
        // The service shouldn't be reported as "running" unless it has
        // actually been started. In a test environment (no service lifecycle),
        // this should always be false.
        //
        // Note: This test could be affected by other tests that set isRunning
        // to true via the service lifecycle. In isolation, it should be false.
        // Since isRunning is set to true only in onCreate() and false in onDestroy(),
        // and we don't start the service in this test, it should be false.
        assertFalse(
            "isRunning should be false when service hasn't been started",
            BatteryMonitorService.isRunning
        )
    }

    // ═════════════════════════════════════════════════════════════
    //  5. CONSTANTS RELATIONSHIP CHECKS
    // ═════════════════════════════════════════════════════════════
    //
    // These tests verify logical relationships between constants
    // that, if violated, would cause bugs in the alert logic.
    // ═════════════════════════════════════════════════════════════

    @Test
    fun `thresholds leave room for normal battery operation`() {
        // There should be a meaningful gap between low and high thresholds.
        // If they were too close (e.g., low=79, high=80), the app would be
        // essentially useless — it would always be in an "alert" state.
        val gap = BatteryMonitorService.DEFAULT_HIGH_THRESHOLD -
                BatteryMonitorService.DEFAULT_LOW_THRESHOLD

        assertTrue(
            "There should be at least a 20% gap between low and high thresholds " +
                    "(actual gap: $gap%)",
            gap >= 20
        )
    }
}
