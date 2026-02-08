package com.example.battery_alarm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.battery_alarm.service.BatteryMonitorService

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
 * 3. This receiver checks SharedPreferences - was the service enabled before reboot?
 * 4. If yes, restart the service automatically
 * 5. Now the service is running again without user intervention!
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
        
        // SharedPreferences constants - must match the ones used in MainScreen.kt
        // These are the same keys used by the UI to persist service state
        private const val PREFS_NAME = "battery_alarm_prefs"
        private const val KEY_SERVICE_ENABLED = "service_enabled"
    }

    /**
     * Called when a broadcast is received that this receiver is registered for.
     * 
     * In our case, this is called when the device finishes booting (BOOT_COMPLETED).
     * We check if the battery monitoring service was enabled before the reboot
     * and restart it if necessary.
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

        // Read the service enabled state from SharedPreferences
        // This was saved by MainScreen when the user enabled/disabled the service
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val wasServiceEnabled = prefs.getBoolean(KEY_SERVICE_ENABLED, false)

        if (wasServiceEnabled) {
            // The service was enabled before reboot - restart it!
            Log.i(TAG, "Service was enabled before reboot - restarting BatteryMonitorService")
            
            try {
                // Use the same start() method that the UI uses
                // This ensures consistent behavior
                BatteryMonitorService.start(context)
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
