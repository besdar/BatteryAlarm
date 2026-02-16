package com.example.battery_alarm.update

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * GitHubUpdateChecker is the **production implementation** of [UpdateChecker].
 * It fetches the latest release version from the GitHub Releases API.
 *
 * ## How It Works
 * 1. Makes an HTTP GET request to the GitHub API endpoint for the latest release.
 * 2. Parses the JSON response to extract the `tag_name` field.
 * 3. Strips the leading "v" prefix if present (e.g., "v1.2" → "1.2").
 * 4. Returns the version string, or null if anything goes wrong.
 *
 * ## Why HttpURLConnection Instead of a Library?
 * We use the built-in [HttpURLConnection] to avoid adding a heavy networking
 * dependency (like Retrofit or OkHttp) for a single, simple GET request.
 * This keeps the APK size small and the dependency footprint minimal.
 *
 * ## Thread Safety
 * The network call runs on [Dispatchers.IO] via [withContext], so it's safe
 * to call this from any coroutine context (including the main thread).
 *
 * ## GitHub API Rate Limits
 * Unauthenticated requests are limited to 60/hour per IP. Since we only
 * check once a month (via WorkManager) and once per app open, this is
 * well within limits.
 *
 * @param apiUrl The GitHub API URL for the latest release. Uses a template
 *               string by default — replace it with the actual repository URL.
 */
class GitHubUpdateChecker(
    private val apiUrl: String = GITHUB_LATEST_RELEASE_URL
) : UpdateChecker {

    companion object {
        private const val TAG = "GitHubUpdateChecker"

        /**
         * Template URL for the GitHub Releases API endpoint.
         * Replace "OWNER" and "REPO" with the actual GitHub repository owner and name.
         *
         * The GitHub API returns JSON with a `tag_name` field containing the version,
         * e.g., {"tag_name": "v1.2", "name": "Release 1.2", ...}
         */
        const val GITHUB_LATEST_RELEASE_URL =
            "https://api.github.com/repos/OWNER/REPO/releases/latest"

        /**
         * URL template for opening the latest release page in a browser.
         * Replace "OWNER" and "REPO" with the actual GitHub repository owner and name.
         */
        const val GITHUB_LATEST_RELEASE_PAGE_URL =
            "https://github.com/OWNER/REPO/releases/latest"

        /** Timeout for connecting to GitHub API (in milliseconds). */
        private const val CONNECT_TIMEOUT_MS = 10_000  // 10 seconds

        /** Timeout for reading the response from GitHub API (in milliseconds). */
        private const val READ_TIMEOUT_MS = 10_000  // 10 seconds
    }

    /**
     * Fetches the latest version from the GitHub Releases API.
     *
     * ## Network Call Details:
     * - Endpoint: GET /repos/{owner}/{repo}/releases/latest
     * - Response format: JSON object with `tag_name` field
     * - Example response: {"tag_name": "v1.2", "name": "Release 1.2", ...}
     *
     * ## Error Handling:
     * Any exception (network error, JSON parse error, etc.) is caught and logged.
     * The method returns null on failure — the caller should treat null as
     * "unable to determine latest version" and not show an update prompt.
     *
     * @return The latest version string (e.g., "1.2"), or null on failure.
     */
    override suspend fun getLatestVersion(): String? = withContext(Dispatchers.IO) {
        try {
            // Open connection to the GitHub API
            val url = URL(apiUrl)
            val connection = url.openConnection() as HttpURLConnection

            connection.apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                // GitHub API requires a User-Agent header for all requests
                setRequestProperty("User-Agent", "BatteryAlarm-Android")
                // Request JSON response
                setRequestProperty("Accept", "application/vnd.github.v3+json")
            }

            // Check the HTTP response code
            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "GitHub API returned HTTP $responseCode")
                connection.disconnect()
                return@withContext null
            }

            // Read the JSON response body
            val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()

            // Parse the JSON to extract the tag_name field
            // Example: {"tag_name": "v1.2", ...} → we extract "v1.2"
            val json = JSONObject(responseBody)
            val tagName = json.optString("tag_name", "")

            if (tagName.isEmpty()) {
                Log.w(TAG, "GitHub API response missing 'tag_name' field")
                return@withContext null
            }

            // Strip the leading "v" prefix if present (e.g., "v1.2" → "1.2")
            // This normalizes the version string for comparison with our versionName
            val version = tagName.removePrefix("v").removePrefix("V")

            Log.d(TAG, "Latest version from GitHub: $version (tag: $tagName)")
            version
        } catch (e: Exception) {
            // Catch all exceptions (network errors, JSON parse errors, etc.)
            // and return null to indicate failure. We don't want a failed update
            // check to crash the app or disrupt the user experience.
            Log.e(TAG, "Failed to check for updates: ${e.message}", e)
            null
        }
    }
}
