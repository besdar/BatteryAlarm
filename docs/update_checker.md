# Update Checker Feature

## What Was Done

Added an in-app update checker that notifies users when a new version of Battery Alarm is available on GitHub, since the app is not distributed through the Google Play Store.

### Three Components Were Added:

1. **Update icon on the main screen** — A `SystemUpdate` icon appears in the top-left corner of the main screen when a newer version is detected. Tapping it opens the GitHub releases page in the default browser so the user can download the latest APK.

2. **Background update check via WorkManager** — A `CoroutineWorker` (`UpdateCheckWorker`) runs approximately every 30 days to check for updates. If a newer version is found, it posts a notification (on its own notification channel, "App Updates") that also opens the GitHub releases page when tapped. This is **not configurable** in Settings by design.

3. **Version comparison logic** — A `VersionComparator` utility compares dotted-numeric version strings (e.g., "1.0" vs "1.1") segment by segment, handling different segment counts and non-numeric edge cases safely.

## Why These Changes Were Made

- **No Play Store updates**: The app needs its own mechanism to inform users about new releases.
- **Minimal disruption**: The update icon is unobtrusive (only shown when relevant), and the background check runs monthly at most.
- **Separation of concerns**: The `UpdateChecker` interface allows swapping the real GitHub API client with a fake in tests, following the same Dependency Inversion pattern used by `SettingsStorage`.

## Architecture

```
UpdateChecker (interface)          ← Pure Kotlin, no Android dependencies
    │
    ├── GitHubUpdateChecker        ← Production: HTTP GET to GitHub Releases API
    │                                 Uses HttpURLConnection (no extra library needed)
    │
    └── FakeUpdateChecker          ← Tests: returns pre-configured version string
    
VersionComparator (object)         ← Pure Kotlin utility for version string comparison

UpdateCheckWorker (CoroutineWorker) ← WorkManager periodic worker (≈30 days)
                                      Shows notification if update available
```

### How the Main Screen Update Check Works

1. `MainScreen` composable accepts an `UpdateChecker` parameter (defaults to `GitHubUpdateChecker`).
2. A `LaunchedEffect(Unit)` fires once per composition lifecycle.
3. It calls `updateChecker.getLatestVersion()` (suspend function, runs on `Dispatchers.IO`).
4. If a version is returned, `VersionComparator.isNewerVersion()` compares it with `BuildConfig.VERSION_NAME`.
5. If newer, the update icon appears in `Alignment.TopStart`.

### How the Background Worker Works

1. `UpdateCheckWorker.schedule()` is called from `MainActivity.onCreate()`.
2. WorkManager enqueues a `PeriodicWorkRequest` with a 30-day interval using `KEEP` policy (won't reset existing schedule).
3. When triggered, the worker fetches the latest version, compares it, and shows a notification if newer.
4. Returns `Result.success()` always (failed checks are not retried — they'll run again next cycle).

## Files Added

| File | Purpose |
|------|---------|
| `update/UpdateChecker.kt` | Interface for fetching the latest version |
| `update/GitHubUpdateChecker.kt` | Production implementation using GitHub API |
| `update/VersionComparator.kt` | Version string comparison utility |
| `update/UpdateCheckWorker.kt` | WorkManager periodic worker + notification |
| `test/update/FakeUpdateChecker.kt` | Test double for UpdateChecker |
| `test/update/FakeUpdateCheckerTest.kt` | Tests for the fake |
| `test/update/VersionComparatorTest.kt` | 16 tests for version comparison logic |

## Files Modified

| File | Change |
|------|--------|
| `ui/MainScreen.kt` | Added update icon, LaunchedEffect for version check, UpdateChecker parameter |
| `MainActivity.kt` | Added `UpdateCheckWorker.schedule()` call in `onCreate()` |
| `AndroidManifest.xml` | Added `INTERNET` permission |
| `res/values/strings.xml` | Added update-related string resources |
| `app/build.gradle.kts` | Added WorkManager and coroutines-test dependencies, enabled BuildConfig |
| `gradle/libs.versions.toml` | Added `workRuntimeKtx` and `kotlinxCoroutinesTest` entries |

## Template URLs

The GitHub API URL and release page URL in `GitHubUpdateChecker` use placeholder values (`OWNER/REPO`) that should be replaced with the actual GitHub repository owner and name:

```kotlin
const val GITHUB_LATEST_RELEASE_URL =
    "https://api.github.com/repos/OWNER/REPO/releases/latest"

const val GITHUB_LATEST_RELEASE_PAGE_URL =
    "https://github.com/OWNER/REPO/releases/latest"
```

## Test Coverage

- **VersionComparatorTest**: 16 tests covering basic comparisons, different segment counts, edge cases (empty strings, non-numeric segments, large numbers).
- **FakeUpdateCheckerTest**: 2 tests verifying the test double's behavior.
- All 91 existing tests continue to pass.
