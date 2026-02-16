# Main screen implementation Notes and Q&A

## 1) Splitting MainScreen into a separate file
- Why: Keeping `MainActivity` focused on Android lifecycle and `setContent` setup improves readability and maintainability. UI-related composables live in a dedicated file (`ui/MainScreen.kt`), making it easier to expand the UI, add previews, and test composables in isolation.
- Scalability: As the app grows (settings screen, KMP modules), this separation prevents `MainActivity` from becoming a “god class”.
- Compose best practice: Feature- or screen-level composables in their own files/packages is a common pattern in Compose apps.

## 2) Power icon instead of PlayArrow
- We switched to `Icons.Rounded.PowerSettingsNew` (from `material-icons-extended`). This icon semantically matches a power toggle and is widely recognized.
- We chose the Rounded style for a friendlier look aligned with Material You. The Filled/Outlined variants are also possible based on taste.

## 3) Visual ON/OFF emphasis and subtle animation
- Colors: The FAB uses animated state-based colors:
  - ON: `colorScheme.primary` with `onPrimary` content
  - OFF: `surfaceVariant` with `onSurfaceVariant` content
- Animation: We added small, tasteful animations using Compose Animation APIs:
  - `animateColorAsState` for smooth color transitions
  - `animateFloatAsState` + `graphicsLayer` for a subtle scale (1.0 -> 1.05) when ON
- Shape: FAB is inherently circular, matching your “round button” suggestion. Elevation is slightly higher when ON for extra emphasis.

## 4) Avoiding string duplication vs. concatenation
- Instead of concatenating strings in code (e.g., "Battery monitoring is " + "ON"), we use `stringResource` with formatting: `R.string.battery_monitoring_status` ("Battery monitoring is %1$s") and pass `R.string.on_label` or `R.string.off_label`.
- Why: This is best practice for localization. Different languages may need to reorder words or change spacing. String resources with placeholders handle these cases cleanly.

## 5) Accessibility
- Content descriptions are added for the Settings and Power buttons, with different descriptions for enabling/disabling. This improves TalkBack experience and overall accessibility.

## 6) KMP readiness
- While this is an Android app today, structuring UI and logic cleanly is a good first step for future KMP reuse. State and business logic can later move into shared modules, while platform-specific UI remains cleanly separated.

## 7) What changed in the codebase
- `MainActivity.kt`: Now only wires the theme + Scaffold + `MainScreen()` and no longer contains the UI implementation.
- `ui/MainScreen.kt`: New composable with power icon, animations, and resource-driven texts.
- `strings.xml`: Added status and accessibility strings.
- Dependencies: Added `material-icons-extended`, `compose-animation`, and `runtime-saveable` (for `rememberSaveable`).

---

## Future steps (optional)
- Add navigation and a real Settings screen with configurable thresholds
- Use `DataStore` instead of SharedPreferences for better coroutine support
- Add a ViewModel to manage service state more cleanly
- Add UI tests for toggle behavior and accessibility checks
- Consider adding a "test alert" button for users to preview sound/vibration
- Show a rationale dialog explaining why notifications are needed if user denies permission
- Add a "Permission Denied" warning banner in the UI when notification permission is missing
