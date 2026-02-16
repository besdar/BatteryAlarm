package com.example.battery_alarm.receiver

import android.content.Context
import android.content.Intent
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Unit tests for [BootCompletedReceiver].
 *
 * ## What Does BootCompletedReceiver Do?
 * After a device reboot, all running services are stopped. This BroadcastReceiver
 * listens for the BOOT_COMPLETED system broadcast and delegates to a [ServiceRestarter]
 * to check if the BatteryMonitorService was running before the reboot. If it was,
 * the receiver tells the restarter to restart the service automatically.
 *
 * ## What Are We Testing?
 * 1. **Null safety**: The receiver should handle null context/intent gracefully
 *    (no crashes).
 * 2. **Action filtering**: The receiver should only act on BOOT_COMPLETED, ignoring
 *    any other action.
 * 3. **Service restart logic**: When the service WAS enabled before reboot,
 *    the receiver should call [ServiceRestarter.restartService].
 * 4. **No-op when disabled**: When the service was NOT enabled, the receiver
 *    should NOT call [ServiceRestarter.restartService].
 *
 * ## Why Still Robolectric?
 * Even after the abstraction refactoring, we still need Robolectric here because:
 * - [BootCompletedReceiver] extends Android's `BroadcastReceiver` (an Android class).
 * - `onReceive()` takes `Context` and `Intent` parameters (Android classes).
 * - `Intent.ACTION_BOOT_COMPLETED` is an Android constant.
 *
 * However, the **business logic** (the decision to restart) is now tested through
 * [FakeServiceRestarter] — no SharedPreferences or shadow checking needed!
 * This is a significant improvement: we no longer need `shadowOf(application)`
 * to verify that a service was started. Instead, we simply check
 * `fakeRestarter.restartServiceCalled`.
 *
 * ## Key Improvement Over Previous Tests
 * **Before** (direct Android dependency):
 * - Had to set up SharedPreferences manually with raw keys
 * - Had to use `shadowOf(RuntimeEnvironment.getApplication()).nextStartedService`
 * - Tests were tightly coupled to Android internals
 *
 * **After** (with ServiceRestarter abstraction):
 * - Inject a [FakeServiceRestarter] with the desired state
 * - Assert on `fakeRestarter.restartServiceCalled` — simple boolean check
 * - Business logic tests are cleaner and more focused
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
     * We create a new receiver instance for each test to ensure clean state.
     * Note: we no longer need to clear SharedPreferences since the business
     * logic is now delegated to FakeServiceRestarter.
     */
    @Before
    fun setUp() {
        // Create a fresh receiver for each test
        receiver = BootCompletedReceiver()

        // Get the Robolectric-managed application context.
        // Still needed because onReceive() requires a Context parameter.
        context = RuntimeEnvironment.getApplication()
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
    fun `onReceive - wrong action - does not restart service`() {
        // Given: the service was enabled before "reboot"
        val fakeRestarter = FakeServiceRestarter(serviceEnabled = true)
        receiver.serviceRestarter = fakeRestarter

        // And: we receive an intent with the WRONG action
        val wrongIntent = Intent("com.example.WRONG_ACTION")

        // When: the receiver processes the wrong intent
        receiver.onReceive(context, wrongIntent)

        // Then: the restarter should NOT have been called
        assertFalse(
            "Service should not be restarted for wrong action",
            fakeRestarter.restartServiceCalled
        )
    }

    // ═════════════════════════════════════════════════════════════
    //  3. SERVICE RESTART LOGIC
    // ═════════════════════════════════════════════════════════════
    //
    // The core behavior: if the service was enabled before reboot,
    // the receiver should restart it via the ServiceRestarter.
    // ═════════════════════════════════════════════════════════════

    @Test
    fun `onReceive - service was enabled - restarts service`() {
        // Given: the service was enabled before the "reboot"
        // (simulated by configuring FakeServiceRestarter with serviceEnabled = true)
        val fakeRestarter = FakeServiceRestarter(serviceEnabled = true)
        receiver.serviceRestarter = fakeRestarter

        // And: we receive the BOOT_COMPLETED broadcast
        val bootIntent = Intent(Intent.ACTION_BOOT_COMPLETED)

        // When: the receiver processes the broadcast
        receiver.onReceive(context, bootIntent)

        // Then: the service restarter should have been called
        assertTrue(
            "Service should be restarted after boot when it was enabled",
            fakeRestarter.restartServiceCalled
        )
    }

    // ═════════════════════════════════════════════════════════════
    //  4. NO-OP WHEN DISABLED
    // ═════════════════════════════════════════════════════════════
    //
    // If the service was NOT enabled before reboot, the receiver
    // should do nothing — no service restart, no errors.
    // ═════════════════════════════════════════════════════════════

    @Test
    fun `onReceive - service was not enabled - does not restart service`() {
        // Given: the service was NOT enabled (default state)
        val fakeRestarter = FakeServiceRestarter(serviceEnabled = false)
        receiver.serviceRestarter = fakeRestarter

        // And: we receive the BOOT_COMPLETED broadcast
        val bootIntent = Intent(Intent.ACTION_BOOT_COMPLETED)

        // When: the receiver processes the broadcast
        receiver.onReceive(context, bootIntent)

        // Then: the service restarter should NOT have been called
        assertFalse(
            "Service should not be restarted when it was not enabled before reboot",
            fakeRestarter.restartServiceCalled
        )
    }

    @Test
    fun `onReceive - service explicitly disabled - does not restart service`() {
        // Given: the service was explicitly disabled (serviceEnabled = false)
        val fakeRestarter = FakeServiceRestarter(serviceEnabled = false)
        receiver.serviceRestarter = fakeRestarter

        // And: we receive the BOOT_COMPLETED broadcast
        val bootIntent = Intent(Intent.ACTION_BOOT_COMPLETED)

        // When: the receiver processes the broadcast
        receiver.onReceive(context, bootIntent)

        // Then: the service should NOT be restarted
        assertFalse(
            "Service should not be restarted when explicitly disabled",
            fakeRestarter.restartServiceCalled
        )
    }
}
