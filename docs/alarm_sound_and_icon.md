# Alarm Sound Selection & App Icon

This document describes two features added to the Battery Alarm app:
1. **Alarm Sound Picker** — lets users choose which sound plays during battery alerts
2. **Custom App Icon** — a battery-themed adaptive icon replacing the default Android icon

---

## 1. Alarm Sound Selection

### What Was Added

Users can now choose a custom alarm sound in **Settings → Alert Behavior → Alarm Sound**.
Tapping the item opens Android's built-in ringtone picker, which shows all system
alarm sounds, ringtones, and notification sounds. The user can also select "Silent"
(no sound) or "Default" (system default alarm).

### Architecture & Data Flow

The alarm sound URI flows through the same abstraction layers as all other settings:

```
User taps "Alarm Sound"
        │
        ▼
SettingsScreen (Compose UI)
   │  launches RingtoneManager.ACTION_RINGTONE_PICKER intent
   │  receives EXTRA_RINGTONE_PICKED_URI in result
   │
   ▼
SettingsRepository.alarmSoundUri  (pure Kotlin — no Android imports)
   │  stores URI as a String (not android.net.Uri) to stay platform-agnostic
   │
   ▼
SettingsStorage.getString / putString  (interface)
   │
   ├──▶ SharedPreferencesStorage  (real app — writes to SharedPreferences)
   └──▶ FakeSettingsStorage       (tests — writes to HashMap)
```

When the service fires an alert:
```
BatteryMonitorService.playAlertSound()
   │  reads alarmSoundUriString (loaded from repository at service start)
   │  if non-null: Uri.parse(alarmSoundUriString) → plays that sound
   │  if null: RingtoneManager.getDefaultUri(TYPE_ALARM) → system default
   ▼
RingtoneManager.getRingtone(context, uri) → plays the sound
```

### Files Changed

| File | What Changed |
|---|---|
| `data/SettingsStorage.kt` | Added `getString()` / `putString()` methods to the interface |
| `data/SharedPreferencesStorage.kt` | Implemented `getString()` / `putString()` using SharedPreferences |
| `data/SettingsRepository.kt` | Added `alarmSoundUri` property, `KEY_ALARM_SOUND_URI` key, `DEFAULT_ALARM_SOUND_URI` (null), updated `resetToDefaults()` |
| `service/BatteryMonitorService.kt` | Added `alarmSoundUriString` field, loads from repository, `playAlertSound()` uses the stored URI |
| `ui/SettingsScreen.kt` | Added ringtone picker launcher, `SettingsClickableItem` composable, alarm sound name resolution |
| `res/values/strings.xml` | Added 4 new strings for the alarm sound picker UI |
| `test/.../FakeSettingsStorage.kt` | Implemented `getString()` / `putString()` using HashMap |
| `test/.../SettingsRepositoryTest.kt` | Added 4 new tests for `alarmSoundUri` (default, read/write, null, persistence, reset) |

### Why Store the URI as a String?

`android.net.Uri` is an Android class. Storing it as a `String` in `SettingsRepository`
keeps the repository free of Android imports, maintaining the Dependency Inversion
Principle established in the previous refactoring. The conversion to/from `Uri` only
happens at the Android boundary:
- **SettingsScreen**: `Uri.parse(alarmSoundUri)` when launching the picker
- **BatteryMonitorService**: `Uri.parse(alarmSoundUriString)` when playing the sound

### Default Behavior

When `alarmSoundUri` is `null` (the default), the service falls back to:
1. `RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)` — the system alarm sound
2. If that's null: `RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)` — notification sound

This matches the previous hardcoded behavior, so existing users are unaffected.

---

## 2. Custom App Icon

### What Was Added

The default Android robot icon was replaced with a custom **battery + lightning bolt**
design — a white battery outline with a ⚡ symbol inside, on a dark green background.

### Icon Design

The icon uses Android's **adaptive icon** system (introduced in Android 8.0):
- **Foreground** (`drawable/ic_launcher_foreground.xml`): White battery with lightning bolt
- **Background** (`drawable/ic_launcher_background.xml`): Solid Material Green 800 (`#2E7D32`)

The foreground design stays within the adaptive icon safe zone (the inner 66dp of the
108dp canvas), so it looks correct in any mask shape (circle, squircle, rounded square, etc.).

### Design Elements

```
    ┌────────┐   ← Battery cap (positive terminal)
  ┌─┤        ├─┐
  │ └────────┘ │
  │            │
  │    ╲  ╱    │ ← Lightning bolt (⚡)
  │     ╲╱     │
  │     ╱╲     │
  │    ╱  ╲    │
  │            │
  └────────────┘ ← Battery body (rounded rectangle)
```

Color choices:
- **Green background** (`#2E7D32`): Evokes "battery/energy/health" — universally
  associated with power levels and battery indicators
- **White foreground**: Maximum contrast against the dark green, ensures visibility
  on any home screen wallpaper

### Files Changed

| File | What Changed |
|---|---|
| `drawable/ic_launcher_foreground.xml` | Replaced Android robot with battery + lightning bolt vector |
| `drawable/ic_launcher_background.xml` | Replaced grid pattern with solid dark green background |

### How to Further Customize the Icon

If you want to create a more polished or branded icon, here are your options:

#### Option A: Use Android Studio's Image Asset Studio (Recommended)
1. Right-click `res/` → **New → Image Asset**
2. Choose "Launcher Icons (Adaptive and Legacy)"
3. For the foreground layer, you can:
   - Use a **clip art** icon (Android Studio has a built-in battery icon)
   - Import a **custom SVG or PNG** image
4. For the background layer, pick a color or import an image
5. Click "Next" → "Finish" — it generates all mipmap sizes automatically

#### Option B: Use a Design Tool
1. Design your icon at **108×108dp** (432×432px at xxxhdpi) in Figma/Illustrator
2. Keep the main content within the inner **66×66dp** safe zone (centered)
3. Export as SVG → convert to Android Vector Drawable via Android Studio
4. Replace `ic_launcher_foreground.xml` with your design

#### Option C: Use an Online Generator
- [Android Asset Studio](https://romannurik.github.io/AndroidAssetStudio/icons-launcher.html)
  generates all required mipmap sizes from a single source image
- [Figma Community](https://www.figma.com/community) has free adaptive icon templates

#### Important Notes on Icon Files
- The `.webp` files in `mipmap-*dpi/` folders are **legacy rasterized icons** for
  older Android versions. The current icon change only updates the vector drawables
  used by adaptive icons (Android 8.0+).
- To generate new `.webp` files for all densities, use Android Studio's Image Asset
  Studio (Option A above) — it does this automatically.
- The `mipmap-anydpi/ic_launcher.xml` and `ic_launcher_round.xml` files reference
  the vector drawables, so they don't need changes.

---

## Tests

4 new tests were added to `SettingsRepositoryTest.kt`:

| Test | What It Verifies |
|---|---|
| `alarmSoundUri - default value - returns null` | Default is null (system default sound) |
| `alarmSoundUri - set valid URI - reads back correctly` | Round-trip read/write works |
| `alarmSoundUri - set to null - reads back null` | Can clear back to system default |
| `alarmSoundUri - persists across repository instances` | Survives "app restart" (same storage) |

The existing `resetToDefaults` test was also updated to verify that `alarmSoundUri`
resets to `null`.

**Total test count: 73** (up from 69, all passing).
