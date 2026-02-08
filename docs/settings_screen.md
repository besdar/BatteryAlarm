# Settings Screen — Implementation Guide

This document explains **what was built**, **how it works**, and **why** each decision was made when adding the Settings screen to Battery Alarm.

---

## Overview

The Settings screen lets users configure every default value used by the battery monitoring service:

| Setting | Default | Range | Description |
|---|---|---|---|
| Low Battery Threshold | 20% | 5–95% | Alert when battery drops to this level |
| High Battery Threshold | 80% | 5–95% | Alert when battery rises to this level (charging) |
| Minimum Alert Interval | 10 min | 5–60 min | Minimum time between any two alerts |
| Full Charge Reminder | 30 min | 5–120 min | How often to remind when at 100% |
| Alert Duration | 15 sec | 5–60 sec | How long sound/vibration plays |
| Alert Sound | ON | on/off | Whether to play a sound on alert |
| Vibration | ON | on/off | Whether to vibrate on alert |

---

## Architecture — How the Pieces Fit Together

```
┌─────────────────────┐
│    MainActivity      │  ← Manages navigation state (showSettings)
│  (Navigation Host)   │
└──────┬──────┬────────┘
       │      │
       ▼      ▼
┌───────────┐ ┌──────────────┐
│ MainScreen│ │SettingsScreen│  ← Compose UI screens
└─────┬─────┘ └──────┬───────┘
      │               │
      │               ▼
      │        ┌──────────────────┐
      │        │SettingsRepository│  ← Reads/writes SharedPreferences
      │        └──────┬───────────┘
      │               │
      ▼               ▼
┌──────────────────────────────┐
│    SharedPreferences file    │  ← "battery_alarm_prefs" XML file
│  (battery_alarm_prefs.xml)   │
└──────────────┬───────────────┘
               │
               ▼
┌──────────────────────────────┐
│   BatteryMonitorService      │  ← Reads settings on startup
└──────────────────────────────┘
```

### Data Flow

1. **User changes a slider/toggle** in `SettingsScreen`
2. The composable updates its local state (instant UI feedback) **and** writes to `SettingsRepository`
3. `SettingsRepository` writes to `SharedPreferences` via `.apply()` (async, non-blocking)
4. When the `BatteryMonitorService` starts (or restarts), it reads from `SettingsRepository`
5. The service uses those values for threshold checks, timing, and alert behavior

---

## New Files Created

### 1. `data/SettingsRepository.kt`

**What:** A data layer class that encapsulates all SharedPreferences access.

**Why a Repository pattern?**
- Before this change, SharedPreferences were accessed directly in `MainScreen`, `BootCompletedReceiver`, etc. Each place had to know the key names and default values.
- The repository centralizes all of this: keys, defaults, validation ranges, and read/write logic.
- If we ever migrate to Jetpack DataStore or Room, only this file changes.

**Key design decisions:**
- **Clamping (`.coerceIn()`)**: Every setter clamps the value to a valid range before saving. This prevents invalid values even if something goes wrong in the UI.
- **Convenience `*Ms` properties**: The service needs milliseconds, but users think in minutes/seconds. The repository stores human-friendly units and provides `alertIntervalMs`, `fullChargeAlertIntervalMs`, `alertDurationMs` computed properties.
- **`resetToDefaults()`**: Uses a single `.edit()` block for atomicity — all values reset together, not one at a time.
- **Same SharedPreferences file**: Reuses `"battery_alarm_prefs"` so the existing `service_enabled` key continues to work.

### 2. `ui/SettingsScreen.kt`

**What:** The Compose UI for the settings page.

**Structure:**
```
Scaffold
  └─ TopAppBar (title + back arrow)
  └─ Column (scrollable)
       ├─ Section: "Battery Thresholds"
       │    ├─ Low Battery Threshold (Slider)
       │    └─ High Battery Threshold (Slider)
       ├─ Section: "Alert Timing"
       │    ├─ Minimum Alert Interval (Slider)
       │    ├─ Full Charge Reminder (Slider)
       │    └─ Alert Duration (Slider)
       ├─ Section: "Alert Behavior"
       │    ├─ Alert Sound (Switch)
       │    └─ Vibration (Switch)
       └─ Reset to Defaults (Button + Dialog)
```

**Why Sliders instead of text fields?**
- Sliders visually enforce valid ranges — the user can't type "999" for a percentage
- They feel natural on mobile for bounded numeric values
- Each slider shows its current value as text (e.g., "20%") for precision

**Reusable components:**
- `SectionHeader` — bold primary-colored text for grouping
- `SettingsSliderItem` — title + summary + value label + slider
- `SettingsToggleItem` — title + summary + switch

These are `private` composables because they're only used within this file. If other screens needed them, we'd move them to a shared `components/` package.

**State management:**
- Each setting is held in a `remember { mutableFloatStateOf(...) }` or `mutableStateOf(...)`
- Initial values come from `SettingsRepository` (reads from SharedPreferences once)
- Every `onValueChange` updates both the local state (for instant UI) and the repository (for persistence)

---

## Modified Files

### 3. `MainActivity.kt`

**What changed:** Added navigation between `MainScreen` and `SettingsScreen`.

**Navigation approach: Simple boolean state**
```kotlin
var showSettings by rememberSaveable { mutableStateOf(false) }

if (showSettings) {
    SettingsScreen(onNavigateBack = { showSettings = false })
} else {
    MainScreen(onNavigateToSettings = { showSettings = true })
}
```

**Why not Jetpack Navigation Compose?**
- Navigation Compose adds a dependency and learning curve
- With only 2 screens, a boolean state is the simplest correct solution
- `rememberSaveable` ensures the state survives screen rotation
- If the app grows to 3+ screens, migrating to Navigation Compose would be the right next step

### 4. `ui/MainScreen.kt`

**What changed:**
- Added `onNavigateToSettings` parameter (a callback)
- Replaced the `TODO` in the Settings `IconButton` with `onClick = onNavigateToSettings`

**Why a callback instead of navigating directly?**
- MainScreen doesn't know _how_ navigation works — it just signals "the user wants settings"
- This is called "inversion of control" and is a Compose best practice
- It makes MainScreen testable in isolation (you can verify the callback fires without needing a real navigation stack)

### 5. `service/BatteryMonitorService.kt`

**What changed:**
- Added `SettingsRepository` integration — creates the repository in `onCreate()` and reads all settings via `loadSettingsFromRepository()`
- Replaced hardcoded `MIN_ALERT_INTERVAL_MS`, `FULL_CHARGE_ALERT_INTERVAL_MS`, `ALERT_DURATION_MS` with instance variables loaded from settings
- Added `soundEnabled` / `vibrationEnabled` flags that conditionally skip `playAlertSound()` / `startVibration()`

**Why load settings once at startup instead of on every battery event?**
- Battery broadcasts fire frequently; reading SharedPreferences each time would add unnecessary I/O
- Loading once at startup keeps the hot path fast
- If the user changes settings while the service is running, they can toggle the service off/on to apply immediately

### 6. `res/values/strings.xml`

**What changed:** Added ~30 new string resources for:
- Section headers, setting titles and summaries
- Reset dialog text
- Accessibility content descriptions for sliders
- Unit labels (%, min, sec)

**Why string resources instead of hardcoded strings?**
- Android best practice for localization (i18n)
- String resources with `%1$d` formatting placeholders handle different languages that may reorder words
- Content descriptions improve accessibility (TalkBack reads them to visually impaired users)

---

## How Settings Reach the Service

Here's the complete lifecycle of a setting change:

```
1. User drags "Low Battery Threshold" slider to 30%

2. SettingsScreen.onValueChange fires:
   - lowThreshold = 30f                    // local state → UI updates instantly
   - repository.lowThreshold = 30          // writes to SharedPreferences

3. SharedPreferences file now contains:
   <int name="low_threshold" value="30" />

4. Later, when the service starts (or restarts):
   - onCreate() → loadSettingsFromRepository()
   - lowThreshold = settingsRepository.lowThreshold  // reads 30 from SharedPreferences

5. Battery drops to 30%:
   - checkAndAlert() → batteryLevel <= lowThreshold (30 <= 30) → ALERT!
```

---

## Edge Cases Handled

| Scenario | How it's handled |
|---|---|
| User never opens Settings | All values fall back to defaults (same as before this feature) |
| User sets low threshold > high threshold | Technically allowed — they're independent settings. The service handles them separately (low for discharging, high for charging). |
| Service is running when settings change | Service uses old values until restarted. Toggle off/on to apply. |
| App killed while on Settings screen | SharedPreferences `.apply()` is async but buffered by Android — values are safe |
| Screen rotation on Settings | `remember` state rebuilds from repository; `rememberSaveable` on MainActivity preserves `showSettings` |
| Reset to Defaults | Confirmation dialog prevents accidental reset; all values update atomically |

---

## Slider Step Sizes

All sliders now use **coarser step increments** instead of 1-unit steps. This was changed because 1-unit steps created far too many tiny notches on the slider track, making it frustrating to hit an exact value on a touch screen.

| Slider | Step Size | Resulting Values |
|---|---|---|
| Low Battery Threshold | 5% | 5, 10, 15, 20 … 95 |
| High Battery Threshold | 5% | 5, 10, 15, 20 … 95 |
| Minimum Alert Interval | 5 min | 5, 10, 15, 20 … 60 |
| Full Charge Reminder | 5 min | 5, 10, 15, 20 … 120 |
| Alert Duration | 5 sec | 5, 10, 15, 20 … 60 |

### How the Compose `Slider` `steps` Parameter Works

The `steps` parameter in Compose's `Slider` sets the number of **intermediate** stop positions **between** the start and end of the range (the endpoints are always included automatically). So the formula is:

```
steps = (rangeMax - rangeMin) / stepSize - 1
```

For example, the low battery threshold slider:
- Range: 5..95 (90 units wide)
- Step size: 5
- stops = 90 / 5 = 18 total positions (including both endpoints)
- `steps` parameter = 18 - 2 endpoints = **17 intermediate stops** → which equals `(90 / 5) - 1`

### The `snapToStep` Helper

Even with the `steps` parameter set, the raw `Float` passed to `onValueChange` can occasionally land slightly between stops due to floating-point arithmetic. The `snapToStep()` function rounds to the nearest clean multiple:

```kotlin
private fun snapToStep(value: Float, step: Int): Float {
    return (Math.round(value / step) * step).toFloat()
}
```

This ensures the value saved to SharedPreferences is always a clean number like 20, 25, 30 — never 19.99 or 25.01.

---

## Testing Checklist

To verify the settings screen works correctly:

1. **Open Settings**: Tap the gear icon on the main screen → Settings screen appears
2. **Navigate back**: Tap the back arrow → returns to main screen
3. **Change a threshold**: Move the Low Battery slider → value label and summary update in real-time
4. **Persistence**: Change a value, kill the app, reopen → the value should be preserved
5. **Service integration**: Change a threshold, toggle the service off then on, verify the new threshold takes effect
6. **Reset to Defaults**: Tap "Reset to Defaults" → confirm → all sliders/toggles snap to default values
7. **Sound/Vibration toggles**: Disable sound, trigger an alert → no sound should play (vibration still works if enabled)
8. **Rotation**: Change some values, rotate the device → values should persist
