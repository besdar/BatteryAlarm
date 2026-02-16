# Abstracting Away Android Dependencies

## What Was Done

This refactoring applies the **Dependency Inversion Principle** (the "D" in SOLID) to
decouple the app's business logic from Android framework classes like `SharedPreferences`,
`Context`, and `Intent`. Instead of the business logic directly touching Android APIs,
it now talks to **pure Kotlin interfaces** that can be implemented by Android (for the
real app) or by simple fakes (for testing).

## Why This Was Done

Previously, classes like `SettingsRepository` and `BootCompletedReceiver` directly used
Android's `SharedPreferences` and `Context`. This created several problems:

1. **Testing required Robolectric** — a fake Android environment on the JVM — which adds
   startup overhead, complexity, and version compatibility risks.
2. **Tight coupling** — if the app ever migrated from SharedPreferences to DataStore, Room,
   or a network API, changes would ripple through the entire codebase.
3. **No platform flexibility** — sharing business logic across Kotlin Multiplatform would
   be impossible since `SharedPreferences` doesn't exist on non-Android platforms.

## Architecture: Before and After

### Before (direct dependency)
```
SettingsRepository ──directly uses──▶ SharedPreferences (Android)
BootCompletedReceiver ──directly uses──▶ Context, SharedPreferences (Android)
BatteryMonitorService ──directly uses──▶ SettingsRepository(context)
MainScreen ──directly uses──▶ SharedPreferences (Android)
SettingsScreen ──directly uses──▶ SettingsRepository(context)
```

### After (with interfaces)
```
SettingsRepository ──uses──▶ SettingsStorage (interface)
                                    │
                           ┌────────┴────────┐
                           ▼                  ▼
             SharedPreferencesStorage    FakeSettingsStorage
             (real app, uses Android)   (tests, plain Kotlin)

BootCompletedReceiver ──uses──▶ ServiceRestarter (interface)
                                        │
                               ┌────────┴────────┐
                               ▼                  ▼
                 AndroidServiceRestarter    FakeServiceRestarter
                 (real app, uses Android)  (tests, plain Kotlin)
```

## New Files Created

### Interfaces (pure Kotlin — zero Android imports)

| File | Purpose |
|------|---------|
| `data/SettingsStorage.kt` | Defines `getInt`, `putInt`, `getBoolean`, `putBoolean` — the operations SettingsRepository needs from storage. |
| `receiver/ServiceRestarter.kt` | Defines `isServiceEnabled()` and `restartService()` — the operations BootCompletedReceiver needs for its restart decision. |

### Real Implementations (Android-specific)

| File | Purpose |
|------|---------|
| `data/SharedPreferencesStorage.kt` | Implements `SettingsStorage` using Android's `SharedPreferences`. This is the **only** class that directly touches SharedPreferences for settings. |
| `receiver/AndroidServiceRestarter.kt` | Implements `ServiceRestarter` by reading settings via `SettingsRepository` and starting `BatteryMonitorService`. |

### Test Fakes (plain Kotlin — no Android needed)

| File | Purpose |
|------|---------|
| `test/.../data/FakeSettingsStorage.kt` | HashMap-based fake of `SettingsStorage`. Allows SettingsRepository tests to run as plain JUnit. |
| `test/.../receiver/FakeServiceRestarter.kt` | Configurable fake of `ServiceRestarter`. Records whether `restartService()` was called, enabling assertion without Robolectric shadows. |

## Files Modified

### `data/SettingsRepository.kt`
- **Constructor changed**: `SettingsRepository(context: Context)` → `SettingsRepository(storage: SettingsStorage)`
- **Removed**: `import android.content.Context`, `import android.content.SharedPreferences`
- **All `prefs.getInt()`/`prefs.putInt()` calls** replaced with `storage.getInt()`/`storage.putInt()`
- **`resetToDefaults()`** now calls individual `storage.putInt()` / `storage.putBoolean()` instead of using SharedPreferences' chained editor

### `receiver/BootCompletedReceiver.kt`
- **Removed**: Direct `SharedPreferences` access and `BatteryMonitorService.start(context)` call
- **Added**: `serviceRestarter` property (nullable) for dependency injection
- **Business logic** now delegates to `ServiceRestarter` interface
- In production, auto-creates `AndroidServiceRestarter(context)` if no restarter was injected

### `service/BatteryMonitorService.kt`
- **Changed**: `SettingsRepository(this)` → `SettingsRepository(SharedPreferencesStorage(this))`
- Added import for `SharedPreferencesStorage`

### `ui/MainScreen.kt`
- **Removed**: Direct `SharedPreferences` access (`context.getSharedPreferences(...)`)
- **Added**: Creates `SettingsRepository(SharedPreferencesStorage(context))` via `remember`
- Service state is now read/written through the repository (`repository.isServiceEnabled`)

### `ui/SettingsScreen.kt`
- **Changed**: `SettingsRepository(context)` → `SettingsRepository(SharedPreferencesStorage(context))`

## Test Changes

### `data/SettingsRepositoryTest.kt`
- **Removed Robolectric entirely**: No more `@RunWith(RobolectricTestRunner::class)`, `@Config`, `RuntimeEnvironment`, or `SharedPreferences` imports
- **Now uses `FakeSettingsStorage`** instead of real Android SharedPreferences
- **All 44 tests preserved** with identical behavior, now running as plain JUnit
- Persistence tests use the same `FakeSettingsStorage` instance to simulate app restarts
- **Removed**: Section 7 ("SharedPreferences Key Consistency") since the repository no longer exposes raw SharedPreferences. The keys are still tested indirectly through round-trip read/write tests.

### `receiver/BootCompletedReceiverTest.kt`
- **Still uses Robolectric** for `Context` and `Intent` (BroadcastReceiver is an Android class)
- **Removed**: `shadowOf()` usage and direct SharedPreferences manipulation
- **Now uses `FakeServiceRestarter`** — tests inject it before calling `onReceive()`
- Assertions are simpler: `assertTrue(fakeRestarter.restartServiceCalled)` instead of inspecting Robolectric shadow state

### `service/BatteryMonitorServiceTest.kt`
- **No changes** — was already plain JUnit (tests companion object constants)

## Key Design Decisions

1. **BootCompletedReceiver still needs Robolectric**: Since `BroadcastReceiver`, `Context`, and `Intent` are Android classes, we can't completely escape the Android dependency for the receiver itself. However, the **business logic** (should we restart?) is now behind `ServiceRestarter` and fully testable with fakes.

2. **Manual injection over DI framework**: For a small app, setting `receiver.serviceRestarter = fake` in tests is simpler than introducing Hilt or Koin. The `serviceRestarter` property defaults to `null`, and `onReceive` creates the real implementation if none was injected.

3. **SharedPreferencesStorage.PREFS_NAME preserved**: The file name `"battery_alarm_prefs"` is kept identical to ensure existing user settings are preserved after the refactoring. No data migration needed.

4. **SettingsRepository keys stayed private**: The storage keys (like `"low_threshold"`) remain as `private const` in `SettingsRepository.companion`. The storage layer doesn't need to know key names — that's a business-logic concern.

## What This Enables Going Forward

| Scenario | How The Abstraction Helps |
|----------|--------------------------|
| **Migrate to Jetpack DataStore** | Create `DataStoreStorage : SettingsStorage` — only one new file, no other changes |
| **Kotlin Multiplatform** | Each platform provides its own `SettingsStorage` implementation |
| **Add ViewModels** | ViewModels take `SettingsRepository` via constructor; tests pass `FakeSettingsStorage` |
| **Complex integration tests** | Mix real and fake implementations as needed |
| **Robolectric version breaks** | SettingsRepository tests are now immune — they're plain JUnit |
