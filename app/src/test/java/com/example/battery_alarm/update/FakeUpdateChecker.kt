package com.example.battery_alarm.update

/**
 * FakeUpdateChecker is a **test double** for [UpdateChecker] that returns
 * a pre-configured version string without making any network calls.
 *
 * ## Why This Exists
 * This fake allows update-related tests to run as **plain JUnit tests**
 * on the JVM — no network access, no Android context, no delays.
 *
 * ## How It Works
 * - Pass the desired "latest version" string to the constructor.
 * - [getLatestVersion] immediately returns that value.
 * - Pass `null` to simulate a network failure (no version available).
 *
 * ## Example Usage in Tests
 * ```kotlin
 * // Simulate an available update
 * val checker = FakeUpdateChecker("2.0")
 * val latest = runBlocking { checker.getLatestVersion() }
 * assertEquals("2.0", latest)
 *
 * // Simulate a network failure
 * val failChecker = FakeUpdateChecker(null)
 * val result = runBlocking { failChecker.getLatestVersion() }
 * assertNull(result)
 * ```
 *
 * @param latestVersion The version string to return from [getLatestVersion].
 *                       Pass null to simulate a failed network call.
 */
class FakeUpdateChecker(
    private val latestVersion: String?
) : UpdateChecker {

    /**
     * Returns the pre-configured version string immediately.
     * No network call is made — this is purely for testing.
     */
    override suspend fun getLatestVersion(): String? = latestVersion
}
