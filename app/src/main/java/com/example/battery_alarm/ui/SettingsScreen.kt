package com.example.battery_alarm.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.battery_alarm.R
import com.example.battery_alarm.data.SettingsRepository
import com.example.battery_alarm.ui.theme.BatteryAlarmTheme
import kotlin.math.roundToInt

// ═══════════════════════════════════════════════════════════════════
//  Slider Step-Size Constants
// ═══════════════════════════════════════════════════════════════════
// These define how much the slider value jumps with each "notch".
// Using coarser steps (5 instead of 1) makes the sliders easier to
// use on a touch screen — the user doesn't have to fight for
// precision on a tiny track.

/** Battery threshold sliders move in 5% increments (5, 10, 15 … 95). */
private const val THRESHOLD_STEP = 5

/** Alert-interval sliders (min alert interval & full-charge reminder)
 *  move in 5-minute increments (5, 10, 15 … 60 / 120). */
private const val INTERVAL_STEP_MINUTES = 5

/** Alert-duration slider moves in 5-second increments (5, 10, 15 … 60). */
private const val DURATION_STEP_SECONDS = 5

/**
 * Rounds [value] to the nearest multiple of [step].
 *
 * Even though the Compose Slider already snaps visually when the `steps`
 * parameter is set, the raw Float passed to `onValueChange` can sometimes
 * land between stops due to floating-point arithmetic. This helper
 * guarantees the persisted value is always a clean multiple of the step.
 *
 * Example: snapToStep(22.4f, 5) → 20.0f
 *          snapToStep(23.0f, 5) → 25.0f
 */
private fun snapToStep(value: Float, step: Int): Float {
    return (Math.round(value / step) * step).toFloat()
}

/**
 * SettingsScreen is the composable that renders the full settings page.
 *
 * ## Architecture Overview
 * This screen reads initial values from [SettingsRepository] and keeps local
 * mutable state for each setting while the user interacts with sliders and toggles.
 * Every change is **immediately persisted** back to SharedPreferences through the
 * repository, so even if the user kills the app, their changes are saved.
 *
 * ## Why Sliders Instead of Text Fields?
 * - Sliders enforce valid ranges visually — the user can't type "999" for a percentage.
 * - They feel more natural for bounded numeric settings on mobile.
 * - We show the current numeric value next to each slider so the user has precision.
 *
 * ## Navigation
 * This screen uses a simple callback (`onNavigateBack`) for navigation rather than
 * a full navigation library. This is fine for a two-screen app and avoids adding
 * extra dependencies. If the app grows to many screens, switching to Jetpack
 * Navigation Compose would be recommended.
 *
 * @param onNavigateBack Callback invoked when the user taps the back arrow.
 *                       The parent composable / activity handles actual navigation.
 * @param modifier       Optional Modifier applied to the root Scaffold.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // ── Obtain the SettingsRepository ──────────────────────────
    // We create the repository here using LocalContext. In a larger app you'd
    // use dependency injection (Hilt/Koin), but for a small app this is fine.
    val context = LocalContext.current
    val repository = remember { SettingsRepository(context) }

    // ── Local mutable state for each setting ──────────────────
    // We read the initial value from the repository once (via `remember`),
    // then hold it in Compose state so the UI recomposes instantly on change.
    // Each setter also writes back to the repository for persistence.

    // Battery thresholds (stored as Int percentages)
    var lowThreshold by remember { mutableFloatStateOf(repository.lowThreshold.toFloat()) }
    var highThreshold by remember { mutableFloatStateOf(repository.highThreshold.toFloat()) }

    // Timing settings
    var alertIntervalMinutes by remember {
        mutableFloatStateOf(repository.alertIntervalMinutes.toFloat())
    }
    var fullChargeIntervalMinutes by remember {
        mutableFloatStateOf(repository.fullChargeAlertIntervalMinutes.toFloat())
    }
    var alertDurationSeconds by remember {
        mutableFloatStateOf(repository.alertDurationSeconds.toFloat())
    }

    // Boolean toggles
    var isSoundEnabled by remember { mutableStateOf(repository.isSoundEnabled) }
    var isVibrationEnabled by remember { mutableStateOf(repository.isVibrationEnabled) }

    // Controls the "Reset to Defaults?" confirmation dialog
    var showResetDialog by remember { mutableStateOf(false) }

    // ── Scaffold with TopAppBar ───────────────────────────────
    // Scaffold provides the standard Material 3 layout structure.
    // TopAppBar gives us a title and a back-navigation button.
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(text = stringResource(R.string.settings_title))
                },
                navigationIcon = {
                    // Back arrow button — calls the parent's navigation callback
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_navigate_back)
                        )
                    }
                },
                // Use the surface color scheme for a clean, non-intrusive top bar
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        // ── Scrollable content area ───────────────────────────
        // verticalScroll allows the settings list to scroll if it's taller
        // than the screen (especially important on small devices or landscape).
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            // ═══════════════════════════════════════════════════
            //  SECTION 1: Battery Thresholds
            // ═══════════════════════════════════════════════════
            SectionHeader(text = stringResource(R.string.settings_section_thresholds))

            // ── Low Battery Threshold slider ──
            // Step size = 5%: the range is 5..95 = 90 units wide, so we need
            // (90 / 5) - 1 = 17 intermediate stops (Compose counts only the
            // positions BETWEEN the two endpoints).
            SettingsSliderItem(
                title = stringResource(R.string.settings_low_threshold_title),
                summary = stringResource(R.string.settings_low_threshold_summary, lowThreshold.roundToInt()),
                value = lowThreshold,
                valueRange = SettingsRepository.MIN_THRESHOLD.toFloat()..SettingsRepository.MAX_THRESHOLD.toFloat(),
                steps = (SettingsRepository.MAX_THRESHOLD - SettingsRepository.MIN_THRESHOLD) / THRESHOLD_STEP - 1,
                valueLabel = stringResource(R.string.unit_percent, lowThreshold.roundToInt()),
                contentDescription = stringResource(R.string.cd_low_threshold_slider),
                onValueChange = { newValue ->
                    // Snap to the nearest multiple of the step size so the
                    // persisted value is always a clean number (10, 15, 20 …).
                    val snapped = snapToStep(newValue, THRESHOLD_STEP)
                    lowThreshold = snapped
                    // Persist immediately so the value survives app kill
                    repository.lowThreshold = snapped.roundToInt()
                }
            )

            // ── High Battery Threshold slider ──
            // Same 5% step logic as the low-threshold slider above.
            SettingsSliderItem(
                title = stringResource(R.string.settings_high_threshold_title),
                summary = stringResource(R.string.settings_high_threshold_summary, highThreshold.roundToInt()),
                value = highThreshold,
                valueRange = SettingsRepository.MIN_THRESHOLD.toFloat()..SettingsRepository.MAX_THRESHOLD.toFloat(),
                steps = (SettingsRepository.MAX_THRESHOLD - SettingsRepository.MIN_THRESHOLD) / THRESHOLD_STEP - 1,
                valueLabel = stringResource(R.string.unit_percent, highThreshold.roundToInt()),
                contentDescription = stringResource(R.string.cd_high_threshold_slider),
                onValueChange = { newValue ->
                    val snapped = snapToStep(newValue, THRESHOLD_STEP)
                    highThreshold = snapped
                    repository.highThreshold = snapped.roundToInt()
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // ═══════════════════════════════════════════════════
            //  SECTION 2: Alert Timing
            // ═══════════════════════════════════════════════════
            SectionHeader(text = stringResource(R.string.settings_section_timing))

            // ── Minimum Alert Interval slider ──
            // Step size = 5 minutes: range is 5..60 = 55 units → (55 / 5) - 1 = 10 stops.
            SettingsSliderItem(
                title = stringResource(R.string.settings_alert_interval_title),
                summary = stringResource(
                    R.string.settings_alert_interval_summary,
                    alertIntervalMinutes.roundToInt()
                ),
                value = alertIntervalMinutes,
                valueRange = SettingsRepository.MIN_ALERT_INTERVAL_MINUTES.toFloat()..SettingsRepository.MAX_ALERT_INTERVAL_MINUTES.toFloat(),
                steps = (SettingsRepository.MAX_ALERT_INTERVAL_MINUTES - SettingsRepository.MIN_ALERT_INTERVAL_MINUTES) / INTERVAL_STEP_MINUTES - 1,
                valueLabel = stringResource(R.string.unit_minutes, alertIntervalMinutes.roundToInt()),
                contentDescription = stringResource(R.string.cd_alert_interval_slider),
                onValueChange = { newValue ->
                    val snapped = snapToStep(newValue, INTERVAL_STEP_MINUTES)
                    alertIntervalMinutes = snapped
                    repository.alertIntervalMinutes = snapped.roundToInt()
                }
            )

            // ── Full Charge Reminder Interval slider ──
            // Step size = 5 minutes: range is 5..120 = 115 units → (115 / 5) - 1 = 22 stops.
            SettingsSliderItem(
                title = stringResource(R.string.settings_full_charge_interval_title),
                summary = stringResource(
                    R.string.settings_full_charge_interval_summary,
                    fullChargeIntervalMinutes.roundToInt()
                ),
                value = fullChargeIntervalMinutes,
                valueRange = SettingsRepository.MIN_FULL_CHARGE_INTERVAL_MINUTES.toFloat()..SettingsRepository.MAX_FULL_CHARGE_INTERVAL_MINUTES.toFloat(),
                steps = (SettingsRepository.MAX_FULL_CHARGE_INTERVAL_MINUTES - SettingsRepository.MIN_FULL_CHARGE_INTERVAL_MINUTES) / INTERVAL_STEP_MINUTES - 1,
                valueLabel = stringResource(R.string.unit_minutes, fullChargeIntervalMinutes.roundToInt()),
                contentDescription = stringResource(R.string.cd_full_charge_interval_slider),
                onValueChange = { newValue ->
                    val snapped = snapToStep(newValue, INTERVAL_STEP_MINUTES)
                    fullChargeIntervalMinutes = snapped
                    repository.fullChargeAlertIntervalMinutes = snapped.roundToInt()
                }
            )

            // ── Alert Duration slider ──
            // Step size = 5 seconds: range is 5..60 = 55 units → (55 / 5) - 1 = 10 stops.
            SettingsSliderItem(
                title = stringResource(R.string.settings_alert_duration_title),
                summary = stringResource(
                    R.string.settings_alert_duration_summary,
                    alertDurationSeconds.roundToInt()
                ),
                value = alertDurationSeconds,
                valueRange = SettingsRepository.MIN_ALERT_DURATION_SECONDS.toFloat()..SettingsRepository.MAX_ALERT_DURATION_SECONDS.toFloat(),
                steps = (SettingsRepository.MAX_ALERT_DURATION_SECONDS - SettingsRepository.MIN_ALERT_DURATION_SECONDS) / DURATION_STEP_SECONDS - 1,
                valueLabel = stringResource(R.string.unit_seconds, alertDurationSeconds.roundToInt()),
                contentDescription = stringResource(R.string.cd_alert_duration_slider),
                onValueChange = { newValue ->
                    val snapped = snapToStep(newValue, DURATION_STEP_SECONDS)
                    alertDurationSeconds = snapped
                    repository.alertDurationSeconds = snapped.roundToInt()
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // ═══════════════════════════════════════════════════
            //  SECTION 3: Alert Behavior (toggles)
            // ═══════════════════════════════════════════════════
            SectionHeader(text = stringResource(R.string.settings_section_alerts))

            // ── Sound toggle ──
            SettingsToggleItem(
                title = stringResource(R.string.settings_sound_title),
                summary = if (isSoundEnabled) {
                    stringResource(R.string.settings_sound_summary_on)
                } else {
                    stringResource(R.string.settings_sound_summary_off)
                },
                checked = isSoundEnabled,
                onCheckedChange = { newValue ->
                    isSoundEnabled = newValue
                    repository.isSoundEnabled = newValue
                }
            )

            // ── Vibration toggle ──
            SettingsToggleItem(
                title = stringResource(R.string.settings_vibration_title),
                summary = if (isVibrationEnabled) {
                    stringResource(R.string.settings_vibration_summary_on)
                } else {
                    stringResource(R.string.settings_vibration_summary_off)
                },
                checked = isVibrationEnabled,
                onCheckedChange = { newValue ->
                    isVibrationEnabled = newValue
                    repository.isVibrationEnabled = newValue
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // ═══════════════════════════════════════════════════
            //  Reset to Defaults Button
            // ═══════════════════════════════════════════════════
            Spacer(modifier = Modifier.height(8.dp))

            // OutlinedButton gives a less prominent appearance than a filled button,
            // which is appropriate for a destructive/reset action.
            OutlinedButton(
                onClick = { showResetDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.settings_reset_defaults))
            }

            // Bottom spacer so the last item isn't flush with the edge
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // ── Reset Confirmation Dialog ─────────────────────────────
    // We show a confirmation dialog before resetting to prevent accidental data loss.
    // AlertDialog is the standard Material 3 dialog composable.
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = {
                // User tapped outside the dialog or pressed back — cancel
                showResetDialog = false
            },
            title = {
                Text(text = stringResource(R.string.settings_reset_defaults))
            },
            text = {
                Text(text = stringResource(R.string.settings_reset_confirmation))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        // Reset all values in the repository
                        repository.resetToDefaults()

                        // Update local state to reflect the reset values
                        // This triggers recomposition so sliders/toggles snap to defaults
                        lowThreshold = SettingsRepository.DEFAULT_LOW_THRESHOLD.toFloat()
                        highThreshold = SettingsRepository.DEFAULT_HIGH_THRESHOLD.toFloat()
                        alertIntervalMinutes =
                            SettingsRepository.DEFAULT_ALERT_INTERVAL_MINUTES.toFloat()
                        fullChargeIntervalMinutes =
                            SettingsRepository.DEFAULT_FULL_CHARGE_INTERVAL_MINUTES.toFloat()
                        alertDurationSeconds =
                            SettingsRepository.DEFAULT_ALERT_DURATION_SECONDS.toFloat()
                        isSoundEnabled = SettingsRepository.DEFAULT_SOUND_ENABLED
                        isVibrationEnabled = SettingsRepository.DEFAULT_VIBRATION_ENABLED

                        // Dismiss the dialog
                        showResetDialog = false
                    }
                ) {
                    Text(text = stringResource(R.string.settings_reset_confirm_button))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(text = stringResource(R.string.settings_reset_cancel_button))
                }
            }
        )
    }
}

// ═════════════════════════════════════════════════════════════════
//  Reusable Settings UI Components
// ═════════════════════════════════════════════════════════════════

/**
 * A section header that visually separates groups of related settings.
 *
 * Uses the primary color and a slightly larger / bolder font to stand out
 * from the individual setting items below it.
 *
 * @param text The section title to display (e.g., "Battery Thresholds").
 */
@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
    )
}

/**
 * A single settings item with a title, summary, a slider, and a value label.
 *
 * ## Layout
 * ```
 * Title                      [value label]
 * Summary text
 * ───────────── slider ──────────────
 * ```
 *
 * ## Why Separate Composable?
 * We have five slider-based settings. Extracting this into a reusable composable
 * eliminates code duplication and ensures a consistent look across all slider items.
 *
 * @param title              The name of the setting (e.g., "Low Battery Threshold").
 * @param summary            A human-readable description of the current value.
 * @param value              The current slider position.
 * @param valueRange         The min..max range for the slider.
 * @param steps              Number of discrete steps between min and max (inclusive minus 1).
 * @param valueLabel         Formatted string showing the current value (e.g., "20%").
 * @param contentDescription Accessibility description for the slider.
 * @param onValueChange      Callback when the user drags the slider.
 */
@Composable
private fun SettingsSliderItem(
    title: String,
    summary: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueLabel: String,
    contentDescription: String,
    onValueChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        // Row containing the title on the left and the value label on the right
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Setting title (takes all available space, pushing the label to the end)
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            // Current value shown in a badge-like style
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
        }

        // Summary text below the title — gives context about what the value means
        Text(
            text = summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // The actual slider control
        // `steps` controls how many discrete positions exist between min and max.
        // For example, for a 5–95 range with steps = 89, each step is 1 unit.
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { this.contentDescription = contentDescription }
        )
    }
}

/**
 * A single settings item with a title, summary, and a toggle switch.
 *
 * ## Layout
 * ```
 * Title                      [Switch]
 * Summary text
 * ```
 *
 * @param title          The name of the setting (e.g., "Alert Sound").
 * @param summary        A description that changes based on the toggle state.
 * @param checked        Whether the switch is currently ON.
 * @param onCheckedChange Callback when the user flips the switch.
 */
@Composable
private fun SettingsToggleItem(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Text column (title + summary) takes all available space
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Small gap between text and switch
        Spacer(modifier = Modifier.width(16.dp))

        // Material 3 Switch — visually indicates on/off state
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

// ═════════════════════════════════════════════════════════════════
//  Previews
// ═════════════════════════════════════════════════════════════════

/**
 * Preview of the SettingsScreen for Android Studio's design view.
 * The `onNavigateBack` callback is a no-op in the preview.
 */
@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    BatteryAlarmTheme {
        SettingsScreen(onNavigateBack = {})
    }
}
