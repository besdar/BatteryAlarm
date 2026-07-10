# Testing Guide

Battery Alarm has 104 local JVM tests. Android-dependent behavior uses Robolectric, while
pure constants and version-comparison tests run as ordinary JUnit tests.

| Test file | Focus | Tests |
|---|---|---:|
| `SettingsRepositoryTest.kt` | Defaults, persistence, validation, conversions, reset behavior | 53 |
| `BootCompletedReceiverTest.kt` | Restart only when persisted monitoring intent is enabled | 7 |
| `BatteryMonitorServiceTest.kt` | Service constants and alert-state invariants | 18 |
| `BatteryMonitorServiceLifecycleTest.kt` | Stop persistence, live reload, receiver registration | 3 |
| `VersionComparatorTest.kt` | Version comparison, tag normalization, GitHub endpoints | 20 |
| `UpdateCheckWorkerTest.kt` | Connected-network constraint | 1 |
| `MainActivityTest.kt` | No notification prompt at application launch | 1 |
| `ExampleUnitTest.kt` | Basic example | 1 |

## Run locally

```bash
./gradlew testDebugUnitTest
./gradlew lint
./gradlew assembleDebug
./gradlew assembleRelease
```

The unit-test report is written to
`app/build/reports/tests/testDebugUnitTest/index.html`.

## Manual device checks

On an Android 13+ device or emulator, verify that enabling monitoring prompts for
notifications, a denial keeps monitoring off and exposes retry, and granting then allows
monitoring to start. Also verify that Stop in the foreground notification keeps monitoring
off after reboot, settings changes affect active monitoring immediately, and update checks
do not notify while notifications are disabled.
