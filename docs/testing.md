# Testing Guide — Battery Alarm

This document explains **what tests were written**, **why each test exists**, **how they work**, and **how to run them**.

---

## Overview

We added **three test files** covering the core logic of the app:

| Test File | Class Under Test | # Tests | Needs Robolectric? |
|---|---|---|---|
| `SettingsRepositoryTest.kt` | `SettingsRepository` | ~40 | ✅ Yes |
| `BootCompletedReceiverTest.kt` | `BootCompletedReceiver` | 7 | ✅ Yes |
| `BatteryMonitorServiceTest.kt` | `BatteryMonitorService` | 12 | ❌ No (plain JVM) |

All tests are **local unit tests** — they run on your development machine (JVM), not on a phone or emulator. This makes them fast (seconds, not minutes).

---

## What Is Robolectric and Why Do We Need It?

Android APIs like `SharedPreferences`, `Context`, and `Intent` only work inside the Android runtime. Normally, you'd need a phone or emulator to test code that uses them.

**Robolectric** solves this by providing a fake Android environment that runs on your regular JVM. It:
- Simulates `SharedPreferences` (reads/writes to in-memory storage)
- Provides a fake `Context` via `RuntimeEnvironment.getApplication()`
- Records service starts, broadcasts, etc. via "shadow" objects
- Runs 10–100x faster than instrumented (on-device) tests

### When Do You Need Robolectric?

| Your test uses... | Plain JUnit | Robolectric needed |
|---|---|---|
| Only Kotlin/Java logic (math, strings, data classes) | ✅ | ❌ |
| `SharedPreferences`, `Context` | ❌ | ✅ |
| `Intent`, `BroadcastReceiver` | ❌ | ✅ |
| Full UI (Compose), notifications, sensors | ❌ | Use instrumented tests instead |

---

## Test File Details

### 1. `SettingsRepositoryTest.kt`

**Location:** `app/src/test/java/com/example/battery_alarm/data/SettingsRepositoryTest.kt`

**What it tests:** The `SettingsRepository` class, which is the single source of truth for all user settings (battery thresholds, alert intervals, sound/vibration toggles, etc.).

**Why it matters:** If the repository has a bug (e.g., returns wrong defaults, doesn't clamp values, doesn't persist), the entire app breaks — the service would use wrong thresholds, or user settings would be lost on restart.

#### Test Categories:

1. **Default Values (8 tests)**
   - Verifies that every setting returns the correct default when nothing has been stored yet.
   - *Why:* New users who never open Settings must get sensible defaults (20% low, 80% high, etc.).

2. **Read-Write Round-Trip (8 tests)**
   - Sets a valid value, then reads it back and checks it matches.
   - *Why:* The most basic "does it work?" check for each property.

3. **Persistence Across Instances (2 tests)**
   - Writes a value, creates a **new** `SettingsRepository` instance, and checks the value is still there.
   - *Why:* Simulates what happens when the app is closed and reopened. SharedPreferences should persist data to disk.

4. **Value Clamping (10 tests)**
   - Tries to set values outside valid ranges (e.g., -10% battery, 200% battery, 999 minutes).
   - Verifies the value is "clamped" (constrained) to the valid range.
   - Also tests **boundary values** (exactly at min/max) to make sure they're accepted.
   - *Why:* Prevents impossible values from reaching the service. Without clamping, a bug could set the threshold to -5%, and the alert would never fire.

5. **Millisecond Conversions (6 tests)**
   - Verifies that `alertIntervalMs`, `fullChargeAlertIntervalMs`, and `alertDurationMs` correctly convert from human-readable units (minutes/seconds) to milliseconds.
   - *Why:* The service internally uses milliseconds for timing. If the conversion math is wrong (e.g., off by a factor of 60), alerts would fire at completely wrong intervals.

6. **Reset to Defaults (3 tests)**
   - Changes all settings, calls `resetToDefaults()`, and verifies everything returns to defaults.
   - Also verifies that `resetToDefaults()` does **NOT** reset the `service_enabled` flag (that's a runtime state, not a user preference).
   - *Why:* The Settings screen has a "Reset to Defaults" button. If it doesn't work correctly, users can't recover from bad settings.

7. **SharedPreferences Key Consistency (2 tests)**
   - Verifies the repository writes to the correct file (`"battery_alarm_prefs"`).
   - Verifies the `service_enabled` key is accessible and matches what `BootCompletedReceiver` reads.
   - *Why:* The repository, service, and boot receiver all share the same SharedPreferences file. If the keys don't match, they'd silently read different data.

8. **Constants Sanity Checks (4 tests)**
   - Verifies that min < max for all ranges, and that defaults fall within their valid ranges.
   - *Why:* Catches copy-paste errors when someone changes a constant value.

9. **Overwrite Behavior (2 tests)**
   - Verifies that writing a new value replaces the old one (not appending or ignoring).
   - Tests rapid toggling of boolean settings.
   - *Why:* Makes sure SharedPreferences behaves as expected for our use case.

---

### 2. `BootCompletedReceiverTest.kt`

**Location:** `app/src/test/java/com/example/battery_alarm/receiver/BootCompletedReceiverTest.kt`

**What it tests:** The `BootCompletedReceiver` BroadcastReceiver, which restarts the battery monitoring service after a device reboot.

**Why it matters:** Without this receiver working correctly, users would lose battery monitoring every time their phone reboots — and they might not notice until their battery dies.

#### Test Categories:

1. **Null Safety (3 tests)**
   - Calls `onReceive()` with null context, null intent, and both null.
   - *Why:* BroadcastReceivers should never crash. Even if the system sends unexpected nulls, the receiver should gracefully handle them.

2. **Action Filtering (1 test)**
   - Sends an intent with the wrong action (not `BOOT_COMPLETED`).
   - Verifies no service is started.
   - *Why:* Defensive programming — the receiver should only respond to the specific broadcast it's designed for.

3. **Service Restart Logic (1 test)**
   - Sets `service_enabled = true` in SharedPreferences, then sends `BOOT_COMPLETED`.
   - Verifies that `BatteryMonitorService` is started.
   - Uses Robolectric's **shadow** to check which services were started.
   - *Why:* This is the core functionality of the receiver — the whole reason it exists.

4. **No-Op When Disabled (2 tests)**
   - Tests both the "never set" case (clean SharedPreferences) and the "explicitly disabled" case.
   - Verifies no service is started in either case.
   - *Why:* The receiver should not start the service if the user hasn't enabled it.

#### Key Concept: Robolectric Shadows

The tests use `shadowOf(RuntimeEnvironment.getApplication())` to get a "shadow" of the Application. This shadow records all calls to `startService()` and `startForegroundService()`, so we can assert:
- **Was** a service started? (`assertNotNull(shadowApp.nextStartedService)`)
- **Which** service was started? (`assertEquals("...BatteryMonitorService", ...)`)
- **Wasn't** any service started? (`assertNull(shadowApp.nextStartedService)`)

---

### 3. `BatteryMonitorServiceTest.kt`

**Location:** `app/src/test/java/com/example/battery_alarm/service/BatteryMonitorServiceTest.kt`

**What it tests:** The `BatteryMonitorService` companion object — constants, intent actions, and the `isRunning` flag.

**Why it matters:** These constants are used across the app (by notifications, PendingIntents, and other components). Accidental changes would silently break features.

#### Why Not Test the Full Service?

The service's core logic (`checkAndAlert`, `triggerAlert`, `playAlertSound`, etc.) is tightly coupled to Android system services (NotificationManager, Vibrator, RingtoneManager). Testing these would require either:
- **Instrumented tests** on a real device (slow but thorough)
- **Major refactoring** to extract the alert logic into a pure Kotlin class (good but out of scope for now)

For a learning project, we prioritize tests that give the best value with the least complexity.

#### Test Categories:

1. **Notification Channel IDs (3 tests)**
   - Verifies the channel ID strings are correct and different from each other.
   - *Why:* If both channels had the same ID, the persistent service notification would inherit the alert channel's high-priority settings (making sound every time it updates).

2. **Intent Actions (3 tests)**
   - Verifies the action strings for "stop service" and "dismiss alert" are correct and different.
   - *Why:* These strings are used in PendingIntents on notification buttons. If they match, pressing "Dismiss Alert" would stop the entire monitoring service.

3. **Default Thresholds (4 tests)**
   - Verifies the default low (20%) and high (80%) thresholds.
   - Checks they're in valid percentage range (0–100).
   - Checks low < high (catches accidental swaps).
   - *Why:* `SettingsRepository` references these constants. If they change, the whole app's defaults change.

4. **isRunning Flag (1 test)**
   - Verifies the default state is `false`.
   - *Why:* The UI reads this flag to sync the toggle button. If it defaulted to `true`, the UI would show "ON" even when the service isn't running.

5. **Constants Relationships (1 test)**
   - Verifies there's at least a 20% gap between low and high thresholds.
   - *Why:* If the gap were tiny, the app would be in a constant alert state.

---

## How to Run the Tests

### From the Terminal

```bash
# Run ALL unit tests
./gradlew testDebugUnitTest

# Run only SettingsRepository tests
./gradlew testDebugUnitTest --tests "com.example.battery_alarm.data.SettingsRepositoryTest"

# Run only BootCompletedReceiver tests
./gradlew testDebugUnitTest --tests "com.example.battery_alarm.receiver.BootCompletedReceiverTest"

# Run only BatteryMonitorService tests
./gradlew testDebugUnitTest --tests "com.example.battery_alarm.service.BatteryMonitorServiceTest"

# Run a single specific test
./gradlew testDebugUnitTest --tests "com.example.battery_alarm.data.SettingsRepositoryTest.lowThreshold - default value - returns 20"
```

### From Android Studio

1. **Run all tests:** Right-click the `app/src/test/java` folder → "Run Tests"
2. **Run one test file:** Open the file → click the green play button next to the class name
3. **Run one test:** Click the green play button next to any `@Test` function

### Understanding Test Results

- **Green ✅:** Test passed — the code behaves as expected
- **Red ❌:** Test failed — either the code has a bug, or the test expectation is wrong
- **Yellow ⚠️:** Test was skipped or ignored

Test reports are generated at:
```
app/build/reports/tests/testDebugUnitTest/index.html
```
Open this file in a browser for a detailed HTML report.

---

## What Was Added to the Build

### `gradle/libs.versions.toml`
- Added `robolectric = "4.14.1"` version
- Added `robolectric` library entry

### `app/build.gradle.kts`
- Added `testImplementation(libs.robolectric)` dependency
- Added `testOptions { unitTests { isIncludeAndroidResources = true } }` — this tells the Android Gradle Plugin to make Android resources (like `strings.xml`) available to Robolectric tests

---

## Test Naming Convention

All tests follow this pattern:

```
`subject - scenario - expected behavior`
```

Examples:
- `lowThreshold - default value - returns 20`
- `onReceive - null context - does not crash`
- `CHANNEL_ID_SERVICE - has expected value`

This makes test output easy to read and understand at a glance.

---

## Future Testing Opportunities

If you want to expand test coverage later, here are some ideas:

1. **Extract alert logic into a testable class**: Move `checkAndAlert()` and `shouldAlertBasedOnProgression()` from `BatteryMonitorService` into a separate `AlertDecisionEngine` class that doesn't depend on Android APIs. Then you can test all the progressive alert logic with plain JUnit tests.

2. **Compose UI tests**: Use `androidTestImplementation(libs.androidx.ui.test.junit4)` (already in your dependencies!) to write tests that verify the Settings screen renders correctly, sliders change values, and the reset dialog works.

3. **Integration tests**: Use instrumented tests to verify the full flow: enable service → receive battery broadcast → trigger alert → dismiss alert.
