package com.example.battery_alarm.update

/**
 * VersionComparator provides utility functions for comparing semantic version strings.
 *
 * ## Why a Separate Object?
 * Version comparison logic is non-trivial (splitting, parsing, comparing segments)
 * and is used in multiple places (MainScreen UI and WorkManager worker). Extracting
 * it into a dedicated object makes it:
 * - **Testable**: Easy to unit-test with various version string formats.
 * - **Reusable**: Both the UI and the background worker can use the same logic.
 * - **Readable**: The comparison algorithm is documented in one place.
 *
 * ## Version Format
 * Versions are expected in dotted-numeric format: "major.minor.patch" (e.g., "1.0", "2.1.3").
 * - Each segment is parsed as an integer.
 * - Missing segments are treated as 0 (e.g., "1.0" is equivalent to "1.0.0").
 * - Non-numeric segments cause the comparison to return false (no update).
 */
object VersionComparator {

    /**
     * Checks whether [latestVersion] is newer than [currentVersion].
     *
     * ## Comparison Algorithm:
     * 1. Split both version strings by "." to get individual segments.
     * 2. Compare each segment as an integer, left to right.
     * 3. If a segment in [latestVersion] is greater, return true (update available).
     * 4. If a segment in [latestVersion] is smaller, return false (no update).
     * 5. If both versions are equal up to the shorter one, pad with 0s and continue.
     *
     * ## Examples:
     * ```
     * isNewerVersion("1.0", "1.1")   → true   // 1.1 > 1.0
     * isNewerVersion("1.0", "1.0")   → false  // same version
     * isNewerVersion("1.1", "1.0")   → false  // current is newer
     * isNewerVersion("1.0", "1.0.1") → true   // 1.0.1 > 1.0.0
     * isNewerVersion("2.0", "1.9.9") → false  // 2.0 > 1.9.9
     * ```
     *
     * @param currentVersion The app's current version string (e.g., "1.0").
     * @param latestVersion  The latest version string from GitHub (e.g., "1.1").
     * @return True if [latestVersion] is strictly greater than [currentVersion].
     *         Returns false if either version string is invalid (non-numeric segments).
     */
    fun isNewerVersion(currentVersion: String, latestVersion: String): Boolean {
        // Split version strings into their numeric segments
        // e.g., "1.2.3" → ["1", "2", "3"]
        val currentParts = currentVersion.split(".")
        val latestParts = latestVersion.split(".")

        // Compare up to the length of the longer version string
        // Missing segments are treated as 0 (e.g., "1.0" == "1.0.0")
        val maxLength = maxOf(currentParts.size, latestParts.size)

        for (i in 0 until maxLength) {
            // Parse each segment as an integer, defaulting to 0 for missing segments.
            // If a segment is not a valid integer (e.g., "beta"), we return false
            // to be safe — we don't want to show a false update prompt.
            val currentPart = currentParts.getOrNull(i)?.toIntOrNull() ?: 0
            val latestPart = latestParts.getOrNull(i)?.toIntOrNull() ?: 0

            // If the latest version has a higher segment, it's newer
            if (latestPart > currentPart) return true

            // If the latest version has a lower segment, it's older (no update)
            if (latestPart < currentPart) return false

            // If equal, continue to the next segment
        }

        // All segments are equal — same version, no update needed
        return false
    }
}
