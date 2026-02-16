package com.example.battery_alarm.receiver

/**
 * FakeServiceRestarter is a **test double** for [ServiceRestarter] that records
 * calls instead of performing real Android operations.
 *
 * ## Why This Exists
 * This fake allows [BootCompletedReceiver] tests to verify the receiver's
 * decision logic (should it restart the service?) without needing Robolectric
 * or a real Android environment.
 *
 * ## How It Works
 * - [isServiceEnabled] returns a configurable boolean ([serviceEnabled]).
 *   Tests set this to `true` or `false` to simulate different scenarios.
 * - [restartService] doesn't actually start a service — it just records
 *   that the call happened by setting [restartServiceCalled] to `true`.
 * - Tests can then assert on [restartServiceCalled] to verify the receiver
 *   made the correct decision.
 *
 * ## Example Usage in Tests
 * ```kotlin
 * val fake = FakeServiceRestarter(serviceEnabled = true)
 * val receiver = BootCompletedReceiver()
 * receiver.serviceRestarter = fake
 *
 * receiver.onReceive(context, bootCompletedIntent)
 *
 * assertTrue(fake.restartServiceCalled) // Receiver decided to restart!
 * ```
 *
 * @param serviceEnabled Whether [isServiceEnabled] should return `true` or `false`.
 *                       Simulates the SharedPreferences "service_enabled" flag.
 */
class FakeServiceRestarter(
    private val serviceEnabled: Boolean = false
) : ServiceRestarter {

    /**
     * Tracks whether [restartService] was called.
     * Tests assert on this to verify the receiver's decision logic.
     * Starts as `false` and becomes `true` when [restartService] is called.
     */
    var restartServiceCalled: Boolean = false
        private set

    /**
     * Returns the pre-configured [serviceEnabled] value.
     * This simulates reading the "service_enabled" flag from SharedPreferences.
     */
    override fun isServiceEnabled(): Boolean = serviceEnabled

    /**
     * Records that a restart was requested (doesn't actually start a service).
     * Tests check [restartServiceCalled] to verify this was called.
     */
    override fun restartService() {
        restartServiceCalled = true
    }
}
