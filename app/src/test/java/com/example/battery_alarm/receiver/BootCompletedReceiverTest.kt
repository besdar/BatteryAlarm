package com.example.battery_alarm.receiver

import android.content.Context
import android.content.Intent
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Unit tests for [BootCompletedReceiver].
 *
 * ## What Does BootCompletedReceiver Do?
 * After a device reboot, all running services are stopped. This BroadcastReceiver
 * listens for the BOOT_COMPLETED system broadcast and checks SharedPreferences
 * to see if the BatteryMonitorService was running before the reboot. If it was,
 * the receiver restarts the service automatically.
 *
 * ## What Are We Testing?
 * 1. **Null safety**: The receiver should handle null context/intent gracefully
 *    (no crashes).
 * 2. **Action filtering**: The receiver should only act on BOOT_COMPLETED, ignoring
 *    any other action.
 * 3. **Service restart logic**: When the service WAS enabled before reboot,
 *    the receiver should start BatteryMonitorService.
 * 4. **No-op when disabled**: When the service was NOT enabled, the receiver
 *    should do nothing.
 *
 * ## Why Robolectric?
 * We need a real Android `Context` to:
 * - Create SharedPreferences (to set the "service_enabled" flag)
 * - Verify that `startForegroundService()` was called (using Robolectric's shadow)
 *
 * Robolectric's "shadow" system intercepts Android API calls and records them,
 * so we can assert that the correct service was started without running on a
 * real device.
 *
 * ## Key Concept: Shadow Objects
 * Robolectric replaces real Android classes with "shadows" — test doubles that
 * record method calls. For example:
 * - `shadowOf(application)` gives us a `ShadowApplication` that records all
 *   started services, so we can check if `BatteryMonitorService` was started.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BootCompletedReceiverTest {

    // ── Test fixtures ────────────────────────────────────────────

    /** The receiver instance we're testing. */
    private lateinit var receiver: BootCompletedReceiver

    /** The fake Android application context from Robolectric. */
    private lateinit var context: Context

    /**
     * Set up fresh test fixtures before each test.
     *
     * We create a new receiver instance and clear SharedPreferences
     * so each test starts from a known clean state.
     */
    @Before
    fun setUp() {
        // Create a fresh receiver for each test
        receiver = BootCompletedReceiver()

        // Get the Robolectric-managed application context
        context = RuntimeEnvironment.getApplication()

        // Clear SharedPreferences to ensure a clean starting state.
        // The receiver reads "service_enabled" from "battery_alarm_prefs",
        // so we need to make sure there's no leftover data from other tests.
        context.getSharedPreferences("battery_alarm_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    // ═════════════════════════════════════════════════════════════
    //  1. NULL SAFETY
    // ═════════════════════════════════════════════════════════════
    //
    // BroadcastReceivers can theoretically receive null context or
    // intent in edge cases. The receiver should handle these without
    // crashing (graceful degradation).
    // ═════════════════════════════════════════════════════════════

    @Test
    fun `onReceive - null context - does not crash`() {
        // Given: a valid BOOT_COMPLETED intent
        val intent = Intent(Intent.ACTION_BOOT_COMPLETED)

        // When: we call onReceive with null context
        // Then: it should return without crashing (no exception thrown)
        receiver.onReceive(null, intent)

        // If we get here without an exception, the test passes.
        // The receiver logs an error and returns early when context is null.
    }

    @Test
    fun `onReceive - null intent - does not crash`() {
        // When: we call onReceive with null intent
        // Then: it should return without crashing
        receiver.onReceive(context, null)
    }

    @Test
    fun `onReceive - both null - does not crash`() {
        // The most extreme edge case: both parameters are null
        receiver.onReceive(null, null)
    }

    // ═════════════════════════════════════════════════════════════
    //  2. ACTION FILTERING
    // ═════════════════════════════════════════════════════════════
    //
    // The receiver should ONLY respond to BOOT_COMPLETED.
    // If it receives a different action (which shouldn't happen in
    // normal operation, but could in testing or unusual system
    // configurations), it should ignore it.
    // ═════════════════════════════════════════════════════════════

    @Test
    fun `onReceive - wrong action - does not start service`() {
        // Given: the service was enabled before "reboot"
        setServiceEnabled(true)

        // And: we receive an intent with the WRONG action
        val wrongIntent = Intent("com.example.WRONG_ACTION")

        // When: the receiver processes the wrong intent
        receiver.onReceive(context, wrongIntent)

        // Then: no service should have been started
        // We check Robolectric's shadow to see if any service start was attempted
        val shadowApp = shadowOf(RuntimeEnvironment.getApplication())
        val startedService = shadowApp.nextStartedService
        assertNull(
            "No service should be started for wrong action",
            startedService
        )
    }

    // ═════════════════════════════════════════════════════════════
    //  3. SERVICE RESTART LOGIC
    // ═════════════════════════════════════════════════════════════
    //
    // The core behavior: if the service was enabled before reboot,
    // the receiver should restart it.
    // ═════════════════════════════════════════════════════════════

    @Test
    fun `onReceive - service was enabled - starts BatteryMonitorService`() {
        // Given: the service was enabled before the "reboot"
        // (we simulate this by writing to SharedPreferences directly)
        setServiceEnabled(true)

        // And: we receive the BOOT_COMPLETED broadcast
        val bootIntent = Intent(Intent.ACTION_BOOT_COMPLETED)

        // When: the receiver processes the broadcast
        receiver.onReceive(context, bootIntent)

        // Then: the BatteryMonitorService should have been started
        // Robolectric's shadow records all service start requests
        val shadowApp = shadowOf(RuntimeEnvironment.getApplication())
        val startedService = shadowApp.nextStartedService

        assertNotNull(
            "BatteryMonitorService should be started after boot when it was enabled",
            startedService
        )

        // Verify it's specifically the BatteryMonitorService that was started
        // (not some other service)
        assertEquals(
            "The started service should be BatteryMonitorService",
            "com.example.battery_alarm.service.BatteryMonitorService",
            startedService!!.component?.className
        )
    }

    // ═════════════════════════════════════════════════════════════
    //  4. NO-OP WHEN DISABLED
    // ═════════════════════════════════════════════════════════════
    //
    // If the service was NOT enabled before reboot, the receiver
    // should do nothing — no service start, no errors.
    // ═════════════════════════════════════════════════════════════

    @Test
    fun `onReceive - service was not enabled - does not start service`() {
        // Given: the service was NOT enabled (default state)
        // SharedPreferences is clean (cleared in setUp), so "service_enabled" defaults to false

        // And: we receive the BOOT_COMPLETED broadcast
        val bootIntent = Intent(Intent.ACTION_BOOT_COMPLETED)

        // When: the receiver processes the broadcast
        receiver.onReceive(context, bootIntent)

        // Then: no service should have been started
        val shadowApp = shadowOf(RuntimeEnvironment.getApplication())
        val startedService = shadowApp.nextStartedService
        assertNull(
            "No service should be started when service was not enabled before reboot",
            startedService
        )
    }

    @Test
    fun `onReceive - service explicitly disabled - does not start service`() {
        // Given: the service was explicitly disabled (set to false)
        setServiceEnabled(false)

        // And: we receive the BOOT_COMPLETED broadcast
        val bootIntent = Intent(Intent.ACTION_BOOT_COMPLETED)

        // When: the receiver processes the broadcast
        receiver.onReceive(context, bootIntent)

        // Then: no service should be started
        val shadowApp = shadowOf(RuntimeEnvironment.getApplication())
        assertNull(
            "No service should be started when service was explicitly disabled",
            shadowApp.nextStartedService
        )
    }

    // ═════════════════════════════════════════════════════════════
    //  HELPER METHODS
    // ═════════════════════════════════════════════════════════════

    /**
     * Helper method to set the "service_enabled" flag in SharedPreferences.
     *
     * This simulates what happens in the real app when the user enables or
     * disables the service via the MainScreen toggle button. The
     * BootCompletedReceiver reads this value to decide whether to restart
     * the service after a reboot.
     *
     * We use `.commit()` instead of `.apply()` here because we need the
     * write to complete synchronously before the test proceeds. In the real
     * app, `.apply()` is fine because there's no immediate read after write.
     *
     * @param enabled Whether the service should be marked as enabled.
     */
    private fun setServiceEnabled(enabled: Boolean) {
        context.getSharedPreferences("battery_alarm_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("service_enabled", enabled)
            .commit()  // commit() is synchronous — important for test reliability
    }
}
