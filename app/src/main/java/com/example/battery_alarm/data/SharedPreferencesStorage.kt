package com.example.battery_alarm.data

import android.content.Context
import android.content.SharedPreferences

/**
 * SharedPreferencesStorage is the **real Android implementation** of [SettingsStorage].
 *
 * ## Role in the Architecture
 * This class is the **only place** in the app that directly touches Android's
 * [SharedPreferences] API for settings storage. All other code (including
 * [SettingsRepository]) works through the [SettingsStorage] interface and has
 * zero Android imports for storage.
 *
 * ```
 * SettingsRepository ──uses──▶ SettingsStorage (interface)
 *                                      │
 *                             ┌────────┴────────┐
 *                             ▼                  ▼
 *               SharedPreferencesStorage    FakeSettingsStorage
 *               (this class — real app)    (tests — plain Kotlin)
 * ```
 *
 * ## Why Isolate SharedPreferences Here?
 * - **Testability**: [SettingsRepository] can be tested with a simple fake
 *   ([FakeSettingsStorage]) that uses a HashMap — no Robolectric or Android device needed.
 * - **Swappability**: If the app later migrates to Jetpack DataStore, Room, or any
 *   other storage mechanism, only this class needs to change.
 * - **Clarity**: Any developer looking for "where do we talk to SharedPreferences?"
 *   finds exactly one file.
 *
 * ## Thread Safety
 * SharedPreferences reads are thread-safe. Writes use `.apply()` which is
 * asynchronous and thread-safe. This matches Android's recommended pattern.
 *
 * @param context Android context used to access SharedPreferences.
 *                Using the Application context is preferred to avoid memory leaks.
 */
class SharedPreferencesStorage(context: Context) : SettingsStorage {

    /**
     * The underlying SharedPreferences instance.
     *
     * Uses the same file name ("battery_alarm_prefs") that the app has always used,
     * so existing user settings are preserved after this refactoring.
     * MODE_PRIVATE means only this app can read/write this file.
     */
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Reads an integer from SharedPreferences.
     * Returns [default] if the key doesn't exist yet.
     */
    override fun getInt(key: String, default: Int): Int =
        prefs.getInt(key, default)

    /**
     * Writes an integer to SharedPreferences asynchronously.
     * Uses `.apply()` (non-blocking) rather than `.commit()` (blocking)
     * because we don't need to wait for the write to complete.
     */
    override fun putInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }

    /**
     * Reads a boolean from SharedPreferences.
     * Returns [default] if the key doesn't exist yet.
     */
    override fun getBoolean(key: String, default: Boolean): Boolean =
        prefs.getBoolean(key, default)

    /**
     * Writes a boolean to SharedPreferences asynchronously.
     * Uses `.apply()` for the same reasons as [putInt].
     */
    override fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    /**
     * Reads a string from SharedPreferences.
     * Returns [default] if the key doesn't exist yet.
     * Used for storing URI strings (e.g., the user's chosen alarm sound).
     */
    override fun getString(key: String, default: String?): String? =
        prefs.getString(key, default)

    /**
     * Writes a string to SharedPreferences asynchronously.
     * Passing null clears the stored value for the given key.
     * Uses `.apply()` for the same reasons as [putInt].
     */
    override fun putString(key: String, value: String?) {
        prefs.edit().putString(key, value).apply()
    }

    companion object {
        /**
         * The SharedPreferences file name.
         * This is the same file the app has always used, ensuring backward compatibility
         * with existing user data after the refactoring.
         */
        const val PREFS_NAME = "battery_alarm_prefs"
    }
}
