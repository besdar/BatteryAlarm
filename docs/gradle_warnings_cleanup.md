# Gradle Warnings Cleanup

**Date:** 2026-02-15  
**Scope:** `gradle.properties` — removed deprecated and unnecessary Android Gradle Plugin (AGP) property overrides

---

## Background

When building the project with **AGP 9.0.1** and **Gradle 9.2.1**, the build output
showed **11 warnings**:

| Count | Warning | Root Cause |
|-------|---------|------------|
| 7 | `"The option setting 'android.XXX' is deprecated"` | `gradle.properties` explicitly overrides defaults that changed in AGP 9.x |
| 4 | `"The property android.dependency.excludeLibraryComponentsFromConstraints…"` | `android.dependency.useConstraints=true` is set but not needed for a small project |

All warnings came from **our own `gradle.properties`**, not from plugin internals
(unlike the separate `Configuration.isVisible` deprecation, which is inside AGP/Kotlin
plugin code and cannot be fixed from user-side).

---

## What Was Changed

### Properties Removed (8 lines deleted)

These properties were either deprecated overrides of AGP 9.x defaults or legacy flags
that are no longer relevant. Removing them silences the warnings and adopts the modern
defaults that AGP 10.0 will enforce anyway.

| Property | Old Value | AGP 9.x Default | Why Removed |
|----------|-----------|------------------|-------------|
| `android.defaults.buildfeatures.resvalues` | `true` | `false` | Deprecated override; new default disables res values build feature by default |
| `android.sdk.defaultTargetSdkToCompileSdkIfUnset` | `false` | `true` | Deprecated override; AGP now auto-sets targetSdk to compileSdk when unset |
| `android.enableAppCompileTimeRClass` | `false` | `true` | Deprecated override; compile-time R class generation is now always on |
| `android.usesSdkInManifest.disallowed` | `false` | `true` | Deprecated override; SDK info in manifest is now disallowed by default |
| `android.uniquePackageNames` | `false` | `true` | Legacy flag, not triggering a warning yet but similarly outdated |
| `android.dependency.useConstraints` | `true` | N/A | Not needed for a small single-module project; caused 4 "excludeLibraryComponentsFromConstraints" performance warnings |
| `android.r8.strictFullModeForKeepRules` | `false` | `true` | Legacy flag, not triggering a warning yet but similarly outdated |
| `android.r8.optimizedResourceShrinking` | `false` | `true` | Deprecated override; optimized resource shrinking is now the default |

### Properties Kept (2 lines retained with comments)

These two properties **cannot be removed** without breaking the build, because the
current Kotlin plugin version (2.2.10) is not yet compatible with the new AGP 9.x
defaults for these settings.

| Property | Value | Why It Must Stay |
|----------|-------|------------------|
| `android.builtInKotlin=false` | `false` | Disables AGP's built-in Kotlin support. Without this, both AGP and the separate `org.jetbrains.kotlin.android` plugin try to register a `"kotlin"` extension, causing a **"Cannot add extension with name 'kotlin'"** build failure. |
| `android.newDsl=false` | `false` | Keeps the legacy AGP DSL model. The new DSL (default in AGP 9.x) uses a different internal class hierarchy (`ApplicationExtensionImpl$AgpDecorated_Decorated`) that the current Kotlin plugin cannot cast to `BaseExtension`, causing a **ClassCastException** at plugin apply time. |

> **Future action:** When a newer Kotlin plugin version adds support for AGP's new DSL
> and built-in Kotlin handling, these two properties can also be removed. Watch the
> [Kotlin changelog](https://kotlinlang.org/docs/whatsnew2210.html) and
> [AGP release notes](https://developer.android.com/studio/releases/gradle-plugin)
> for compatibility updates.

---

## Final `gradle.properties` (after cleanup)

```properties
# Project-wide Gradle settings.
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true

# Required: Disables AGP's built-in Kotlin support so the separate
# org.jetbrains.kotlin.android plugin manages Kotlin compilation.
android.builtInKotlin=false

# Required: Keeps the legacy AGP DSL model that the current Kotlin plugin
# (2.2.10) expects.
android.newDsl=false
```

---

## Warnings Resolved

| Before | After |
|--------|-------|
| 7 deprecated-option warnings | **2 remaining** (for the 2 properties we must keep) |
| 4 `excludeLibraryComponentsFromConstraints` warnings | **0** (removed the `useConstraints` property) |
| **11 total warnings** | **2 total warnings** (unavoidable until Kotlin plugin update) |

---

## How This Was Validated

1. Removed all 10 deprecated/legacy/unnecessary properties.
2. Build failed → `android.builtInKotlin=false` is required (Kotlin extension conflict).
3. Added it back → build failed again → `android.newDsl=false` is also required (DSL ClassCastException).
4. Added it back → **all 20 unit tests pass**, build succeeds.
5. Each retained property has an explanatory comment in `gradle.properties` documenting
   why it exists and when it can be removed.

---

## Related Documentation

- [`docs/abstraction_refactoring.md`](abstraction_refactoring.md) — Dependency Inversion refactoring
- [`docs/alarm_sound_and_icon.md`](alarm_sound_and_icon.md) — Alarm sound picker & app icon
- [Gradle 9.x Migration Guide](https://docs.gradle.org/9.1.0/userguide/upgrading_version_9.html)
- [AGP Release Notes](https://developer.android.com/studio/releases/gradle-plugin)
