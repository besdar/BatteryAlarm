# Update Checks

Battery Alarm checks GitHub Releases for a newer APK without doing network work from the
UI. The sole scheduling path is `UpdateCheckWorker.schedule()` in `MainActivity`.

## Behavior

- WorkManager enqueues one uniquely named periodic request with a 30-day interval and a
  `NetworkType.CONNECTED` constraint.
- The worker queries `https://api.github.com/repos/besdar/BatteryAlarm/releases/latest`.
- Release tags are normalized by removing a leading `v` or `V`, then compared with the
  installed `BuildConfig.VERSION_NAME`.
- When a newer release exists, the notification opens
  `https://github.com/besdar/BatteryAlarm/releases/latest`.
- No update notification is posted when app notifications are disabled.

The main screen deliberately owns no update checker, launch-time network request, or
update icon. Scheduling uses `ExistingPeriodicWorkPolicy.KEEP`, so activity recreation
does not reset the existing interval.

## Tests

- `VersionComparatorTest` verifies version comparison, tag normalization, and the GitHub
  endpoints.
- `UpdateCheckWorkerTest` verifies the periodic request requires a connected network.
