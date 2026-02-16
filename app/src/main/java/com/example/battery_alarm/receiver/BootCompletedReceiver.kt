package com.example.battery_alarm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * BootCompletedReceiver listens for the device boot completion event and restarts
 * the BatteryMonitorService if it was enabled before the device was rebooted.
 *
 * ## Why is this needed?
 * When a device reboots, all running services are stopped. Android does NOT automatically
 * restart services after a reboot - even foreground services with START_STICKY.
 * 
 * The problem:
 * 1. User enables battery monitoring (service starts, SharedPreferences saves "enabled = true")
 * 2. Device reboots
 * 3. Service is killed during reboot and doesn't restart
 * 4. User opens app - UI shows "ON" (from SharedPreferences) but service isn't running
 * 5. No battery monitoring is happening!
 *
 * The solution:
 * 1. Register this BroadcastReceiver in AndroidManifest.xml to listen for BOOT_COMPLETED
 * 2. When device finishes booting, Android sends the BOOT_COMPLETED broadcast
 * 3. This receiver checks [ServiceRestarter.isServiceEnabled] — was the service enabled?
 * 4. If yes, calls [ServiceRestarter.restartService] to restart automatically
 * 5. Now the service is running again without user intervention!
 *
 * ## Abstraction: ServiceRestarter Interface
 * Previously, this receiver directly accessed SharedPreferences and called
 * `BatteryMonitorService.start(context)`. Now it delegates to a [ServiceRestarter]
 * interface, which makes the decision logic testable without Robolectric:
 *
 * ```
 * BootCompletedReceiver ──uses──▶ ServiceRestarter (interface)
 *                                          │
 *                                 ┌────────┴────────┐
 *                                 ▼                  ▼
 *                   AndroidServiceRestarter    FakeServiceRestarter
 *                   (real app — Android)      (tests — plain Kotlin)
 * ```
 *
 * Note: The receiver still extends Android's `BroadcastReceiver` (that can't be avoided),
 * but the **business logic** (should we restart?) is now behind the interface.
 *
 * ## Important Notes:
 * - Requires RECEIVE_BOOT_COMPLETED permission in manifest
 * - The receiver must be declared in the manifest (not registered dynamically)
 *   because the app might not be running when the broadcast is sent
 * - BOOT_COMPLETED is sent after the device has finished booting and the user
 *   has unlocked the device at least once (on devices with Direct Boot)
 *
 * ## Security Considerations:
 * - We set android:exported="true" because the system needs to send us the broadcast
 * - We filter only for BOOT_COMPLETED action to prevent misuse
 */
class BootCompletedReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootCompletedReceiver"
    }

    /**
     * Optional [ServiceRestarter] that can be injected for testing.
     *
     * When null (the default in production), [onReceive] creates an
     * [AndroidServiceRestarter] using the broadcast's context. This allows:
     * - **Production**: Receiver works normally with no extra setup.
     * - **Testing**: Tests set this field to a [FakeServiceRestarter] before
     *   calling [onReceive], enabling pure-JUnit testing of the decision logic.
     *
     * In a larger app you'd use a dependency injection framework (Hilt/Koin)
     * instead of this manual approach, but for a small app this is sufficient.
     */
    var serviceRestarter: ServiceRestarter? = null

    /**
     * Called when a broadcast is received that this receiver is registered for.
     * 
     * In our case, this is called when the device finishes booting (BOOT_COMPLETED).
     * We check if the battery monitoring service was enabled before the reboot
     * and restart it if necessary.
     *
     * The actual decision logic is delegated to [ServiceRestarter], which can be
     * a real Android implementation or a test fake.
     *
     * @param context The Context in which the receiver is running
     * @param intent The Intent being received (contains the action BOOT_COMPLETED)
     */
    override fun onReceive(context: Context?, intent: Intent?) {
        // Safety checks - context and intent should never be null for BOOT_COMPLETED
        // but it's good practice to verify
        if (context == null) {
            Log.e(TAG, "Received broadcast with null context - cannot proceed")
            return
        }
        
        if (intent == null) {
            Log.e(TAG, "Received broadcast with null intent - cannot proceed")
            return
        }

        // Verify this is actually the BOOT_COMPLETED action
        // This is a safety check since we only registered for this action,
        // but it's good defensive programming
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) {
            Log.w(TAG, "Received unexpected action: ${intent.action}")
            return
        }

        Log.d(TAG, "Device boot completed - checking if battery service should restart")

        // Use the injected ServiceRestarter if available (for testing),
        // otherwise create the real Android implementation.
        val restarter = serviceRestarter ?: AndroidServiceRestarter(context)

        if (restarter.isServiceEnabled()) {
            // The service was enabled before reboot - restart it!
            Log.i(TAG, "Service was enabled before reboot - restarting BatteryMonitorService")
            
            try {
                // Delegate the actual service start to the restarter
                restarter.restartService()
                Log.i(TAG, "BatteryMonitorService restart initiated successfully")
            } catch (e: Exception) {
                // Log any errors but don't crash - the user can manually restart
                Log.e(TAG, "Failed to restart BatteryMonitorService: ${e.message}", e)
            }
        } else {
            // Service was not enabled - nothing to do
            Log.d(TAG, "Service was not enabled before reboot - no action needed")
        }
    }
}
