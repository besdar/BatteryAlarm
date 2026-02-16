package com.example.battery_alarm.update

/**
 * UpdateChecker is a **pure Kotlin interface** that defines how the app checks
 * for available updates from a remote source (GitHub releases).
 *
 * ## Why an Interface?
 * Following the same Dependency Inversion pattern used by [SettingsStorage]:
 * - The UI and WorkManager code depend on this interface, not a concrete class.
 * - In production, [GitHubUpdateChecker] performs a real HTTP call.
 * - In tests, [FakeUpdateChecker] returns pre-configured results instantly.
 *
 * This keeps our update-check logic testable without network access.
 */
interface UpdateChecker {

    /**
     * Fetches the latest available version string from the remote source.
     *
     * This is a **suspend** function because it performs a network call
     * (HTTP GET to GitHub API) which should not block the main thread.
     *
     * @return The latest version string (e.g., "1.2"), or null if the check
     *         failed (no network, API error, parse error, etc.).
     */
    suspend fun getLatestVersion(): String?
}
