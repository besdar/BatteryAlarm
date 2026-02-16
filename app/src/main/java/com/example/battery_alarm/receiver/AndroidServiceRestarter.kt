package com.example.battery_alarm.receiver

import android.content.Context
import com.example.battery_alarm.data.SettingsRepository
import com.example.battery_alarm.data.SharedPreferencesStorage
import com.example.battery_alarm.service.BatteryMonitorService

/**
 * AndroidServiceRestarter is the **real Android implementation** of [ServiceRestarter].
 *
 * ## Role in the Architecture
 * This class bridges the gap between the pure-Kotlin [ServiceRestarter] interface
 * and the actual Android APIs needed to check settings and start the service.
 *
 * ```
 * BootCompletedReceiver ──uses──▶ ServiceRestarter (interface)
 *                                          │
 *                                 ┌────────┴────────┐
 *                                 ▼                  ▼
 *                   AndroidServiceRestarter    FakeServiceRestarter
 *                   (this class — real app)   (tests — plain Kotlin)
 * ```
 *
 * ## Why Not Put This Logic Directly in BootCompletedReceiver?
 * By extracting it behind an interface, we can:
 * - **Test the receiver's decision logic** with a fake (no Robolectric needed).
 * - **Swap implementations** if the service start mechanism changes.
 * - **Keep Android dependencies isolated** in this one class.
 *
 * @param context Android context used to read SharedPreferences and start the service.
 *                The BroadcastReceiver's context is typically the application context.
 */
class AndroidServiceRestarter(private val context: Context) : ServiceRestarter {

    /**
     * The settings repository used to check if the service was enabled.
     * Creates a [SharedPreferencesStorage] under the hood to read from the
     * same preferences file the app always uses.
     */
    private val repository = SettingsRepository(SharedPreferencesStorage(context))

    /**
     * Checks SharedPreferences to see if the service was enabled before the reboot.
     * Reads the "service_enabled" flag from the repository.
     */
    override fun isServiceEnabled(): Boolean = repository.isServiceEnabled

    /**
     * Starts the BatteryMonitorService using Android's startForegroundService().
     * This is the same method the UI uses to start the service.
     */
    override fun restartService() {
        BatteryMonitorService.start(context)
    }
}
