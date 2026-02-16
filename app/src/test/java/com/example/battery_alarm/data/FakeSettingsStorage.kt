package com.example.battery_alarm.data

/**
 * FakeSettingsStorage is a **test double** for [SettingsStorage] that stores
 * values in a simple [HashMap] instead of Android's SharedPreferences.
 *
 * ## Why This Exists
 * This fake allows [SettingsRepository] tests to run as **plain JUnit tests**
 * on the JVM — no Robolectric, no Android device, no emulator needed.
 * Tests run in milliseconds instead of seconds.
 *
 * ## How It Works
 * - All values are stored in an in-memory [MutableMap].
 * - `getInt` / `getBoolean` return the stored value, or the provided default
 *   if the key hasn't been written yet.
 * - `putInt` / `putBoolean` simply write to the map.
 * - Data is lost when the test finishes (no persistence) — which is exactly
 *   what we want for isolated, repeatable tests.
 *
 * ## Example Usage in Tests
 * ```kotlin
 * val fakeStorage = FakeSettingsStorage()
 * val repository = SettingsRepository(fakeStorage)
 *
 * // Test default values
 * assertEquals(20, repository.lowThreshold)
 *
 * // Test clamping
 * repository.lowThreshold = 999
 * assertEquals(95, repository.lowThreshold) // clamped to MAX_THRESHOLD
 * ```
 *
 * ## Persistence Simulation
 * Since all data lives in the same HashMap instance, creating a second
 * [SettingsRepository] with the same [FakeSettingsStorage] instance will
 * see the same data — simulating persistence across app restarts.
 */
class FakeSettingsStorage : SettingsStorage {

    /**
     * In-memory storage backing all reads and writes.
     * Keys are strings (same as SharedPreferences keys), values are Any
     * (we cast them back to Int or Boolean on read).
     */
    private val data = mutableMapOf<String, Any>()

    /**
     * Returns the integer stored at [key], or [default] if the key
     * has never been written. Uses a safe cast (`as? Int`) to avoid
     * ClassCastException if the key somehow holds a non-Int value.
     */
    override fun getInt(key: String, default: Int): Int =
        data[key] as? Int ?: default

    /**
     * Stores an integer value in the in-memory map.
     */
    override fun putInt(key: String, value: Int) {
        data[key] = value
    }

    /**
     * Returns the boolean stored at [key], or [default] if the key
     * has never been written.
     */
    override fun getBoolean(key: String, default: Boolean): Boolean =
        data[key] as? Boolean ?: default

    /**
     * Stores a boolean value in the in-memory map.
     */
    override fun putBoolean(key: String, value: Boolean) {
        data[key] = value
    }

    /**
     * Returns the string stored at [key], or [default] if the key
     * has never been written. Used for URI values like alarm sound.
     */
    override fun getString(key: String, default: String?): String? =
        if (data.containsKey(key)) data[key] as? String else default

    /**
     * Stores a string value in the in-memory map.
     * Passing null removes the key from the map (mirrors SharedPreferences behavior).
     */
    override fun putString(key: String, value: String?) {
        if (value != null) {
            data[key] = value
        } else {
            data.remove(key)
        }
    }
}
