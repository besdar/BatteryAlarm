package com.example.battery_alarm.receiver

/**
 * ServiceRestarter is a **pure Kotlin interface** that abstracts the decision-making
 * logic for restarting the battery monitoring service after a device reboot.
 *
 * ## Why This Interface Exists (Dependency Inversion)
 * Previously, [BootCompletedReceiver] directly accessed Android's `SharedPreferences`
 * to check if the service was enabled, and called `BatteryMonitorService.start(context)`
 * directly. This made it impossible to test the receiver's decision logic without
 * Robolectric.
 *
 * By extracting this interface, the receiver's **business logic** (the if/else decision)
 * becomes testable with a simple fake:
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
 * ## What This Interface Covers
 * Only two operations needed by the boot receiver:
 *   - [isServiceEnabled]: Check if the service was running before reboot.
 *   - [restartService]: Actually start the service.
 *
 * Note: [BootCompletedReceiver] still extends Android's `BroadcastReceiver`,
 * so it can't be fully free of Android dependencies. But the **decision logic**
 * (should we restart?) is now testable without Robolectric.
 */
interface ServiceRestarter {

    /**
     * Checks whether the battery monitoring service was enabled before the device rebooted.
     *
     * @return `true` if the service should be restarted, `false` otherwise.
     */
    fun isServiceEnabled(): Boolean

    /**
     * Starts (restarts) the battery monitoring service.
     *
     * In the real app, this calls `BatteryMonitorService.start(context)`.
     * In tests, this records that a restart was requested.
     */
    fun restartService()
}
