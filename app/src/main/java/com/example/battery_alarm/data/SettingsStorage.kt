package com.example.battery_alarm.data

/**
 * SettingsStorage is a **pure Kotlin interface** that defines how settings are
 * read from and written to persistent storage.
 *
 * ## Why This Interface Exists (Dependency Inversion Principle)
 * Previously, [SettingsRepository] directly depended on Android's
 * [android.content.SharedPreferences] and [android.content.Context].
 * This made it impossible to test the repository's business logic (value clamping,
 * default values, millisecond conversions) without either:
 *   - Running on a real Android device/emulator (slow), or
 *   - Using Robolectric to fake the Android environment (adds complexity).
 *
 * By extracting this interface, we **invert the dependency**:
 *   - [SettingsRepository] depends on this interface (pure Kotlin — no Android).
 *   - The real app provides [SharedPreferencesStorage] (the Android implementation).
 *   - Tests provide [FakeSettingsStorage] (a simple HashMap-based fake).
 *
 * This is the **Dependency Inversion Principle** (the "D" in SOLID):
 * > "High-level modules should not depend on low-level modules.
 * >  Both should depend on abstractions."
 *
 * ## What This Interface Covers
 * It exposes only the primitive operations needed by [SettingsRepository]:
 *   - `getInt` / `putInt` — for numeric settings (thresholds, intervals, durations)
 *   - `getBoolean` / `putBoolean` — for toggle settings (sound, vibration, service state)
 *   - `getString` / `putString` — for text/URI settings (alarm sound URI)
 *
 * ## Thread Safety Contract
 * Implementations should be thread-safe for concurrent reads. Writes should be
 * safe to call from any thread (the implementation decides whether to use
 * synchronous or asynchronous persistence).
 */
interface SettingsStorage {

    /**
     * Reads an integer value from storage.
     *
     * @param key     The unique key identifying the setting.
     * @param default The value to return if the key has never been written.
     * @return The stored integer, or [default] if not found.
     */
    fun getInt(key: String, default: Int): Int

    /**
     * Writes an integer value to storage.
     *
     * @param key   The unique key identifying the setting.
     * @param value The integer value to persist.
     */
    fun putInt(key: String, value: Int)

    /**
     * Reads a boolean value from storage.
     *
     * @param key     The unique key identifying the setting.
     * @param default The value to return if the key has never been written.
     * @return The stored boolean, or [default] if not found.
     */
    fun getBoolean(key: String, default: Boolean): Boolean

    /**
     * Writes a boolean value to storage.
     *
     * @param key   The unique key identifying the setting.
     * @param value The boolean value to persist.
     */
    fun putBoolean(key: String, value: Boolean)

    /**
     * Reads a string value from storage.
     *
     * Used for settings that store text or URI values, such as the
     * user's chosen alarm sound URI.
     *
     * @param key     The unique key identifying the setting.
     * @param default The value to return if the key has never been written.
     *                Can be null to indicate "no value stored".
     * @return The stored string, or [default] if not found.
     */
    fun getString(key: String, default: String?): String?

    /**
     * Writes a string value to storage.
     *
     * @param key   The unique key identifying the setting.
     * @param value The string value to persist. Can be null to clear the stored value.
     */
    fun putString(key: String, value: String?)
}
