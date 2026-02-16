package com.example.battery_alarm.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.battery_alarm.BuildConfig
import com.example.battery_alarm.R
import java.util.concurrent.TimeUnit

/**
 * UpdateCheckWorker is a [CoroutineWorker] that periodically checks for app updates
 * in the background using WorkManager.
 *
 * ## Why WorkManager?
 * WorkManager is the recommended solution for deferrable, guaranteed background work
 * on Android. It handles:
 * - **Battery optimization**: Work is batched with other apps' tasks.
 * - **Doze mode**: Work is deferred but guaranteed to run eventually.
 * - **App restarts**: Work survives app kills and device reboots.
 * - **Constraints**: We could add network constraints if needed.
 *
 * ## How It Works
 * 1. WorkManager triggers this worker approximately once a month (30 days).
 *    Due to Android's battery optimizations (Doze mode, App Standby), the actual
 *    interval may be longer — this is expected and acceptable for update checks.
 * 2. The worker fetches the latest version from GitHub using [GitHubUpdateChecker].
 * 3. It compares the latest version with the current app version ([BuildConfig.VERSION_NAME]).
 * 4. If an update is available, it shows a notification that opens the GitHub releases page.
 *
 * ## Notification Channel
 * Uses its own notification channel ("update_check_channel") separate from battery alerts,
 * so users can independently control update notification behavior in system settings.
 *
 * ## Not Configurable in Settings
 * Per the design requirement, this monthly update check is NOT configurable —
 * it always runs in the background. The user cannot disable it from the Settings screen.
 */
class UpdateCheckWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "UpdateCheckWorker"

        /** Unique name for the periodic work request, used to avoid duplicate scheduling. */
        const val WORK_NAME = "update_check_work"

        /** Notification channel ID for update-available notifications. */
        const val CHANNEL_ID_UPDATES = "update_check_channel"

        /** Notification ID for the update-available notification. */
        private const val NOTIFICATION_ID_UPDATE = 100

        /** Repeat interval for the periodic check: 30 days (approximately monthly). */
        private const val CHECK_INTERVAL_DAYS = 30L

        /**
         * Schedules the periodic update check using WorkManager.
         *
         * This should be called once from [MainActivity.onCreate]. WorkManager
         * automatically deduplicates requests with the same [WORK_NAME], so
         * calling this multiple times is safe (it won't create duplicate workers).
         *
         * ## ExistingPeriodicWorkPolicy.KEEP
         * If a periodic work request with the same name already exists, KEEP it
         * and don't replace it. This preserves the existing schedule and avoids
         * resetting the timer every time the app opens.
         *
         * @param context The application context (used to get WorkManager instance).
         */
        fun schedule(context: Context) {
            // Build a periodic work request that runs approximately every 30 days.
            // Android may delay execution due to Doze mode and battery optimization,
            // so the actual interval could be longer — this is by design.
            val workRequest = PeriodicWorkRequestBuilder<UpdateCheckWorker>(
                CHECK_INTERVAL_DAYS, TimeUnit.DAYS
            ).build()

            // Enqueue the work with a unique name. KEEP policy means:
            // "If work with this name already exists, don't replace it."
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )

            Log.d(TAG, "Scheduled periodic update check (every $CHECK_INTERVAL_DAYS days)")
        }
    }

    /**
     * The main work method called by WorkManager in the background.
     *
     * ## Steps:
     * 1. Fetch latest version from GitHub.
     * 2. Compare with current app version.
     * 3. Show notification if update is available.
     * 4. Return [Result.success] (even on failure — we don't want retries for this).
     *
     * @return [Result.success] always. We don't use [Result.retry] because a failed
     *         update check is not critical — we'll check again next month.
     */
    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting update check...")

        // Fetch the latest version from GitHub
        val updateChecker = GitHubUpdateChecker()
        val latestVersion = updateChecker.getLatestVersion()

        if (latestVersion == null) {
            // Failed to fetch — could be no network, API error, etc.
            // We'll try again next month, no need to retry now.
            Log.w(TAG, "Failed to fetch latest version, will try again next cycle")
            return Result.success()
        }

        // Get the current app version from BuildConfig (set in build.gradle.kts)
        val currentVersion = BuildConfig.VERSION_NAME
        Log.d(TAG, "Current version: $currentVersion, Latest version: $latestVersion")

        // Compare versions to determine if an update is available
        if (VersionComparator.isNewerVersion(currentVersion, latestVersion)) {
            Log.d(TAG, "Update available! Showing notification.")
            showUpdateNotification(latestVersion)
        } else {
            Log.d(TAG, "App is up to date (or newer than latest release).")
        }

        return Result.success()
    }

    /**
     * Shows a notification informing the user that an app update is available.
     *
     * When tapped, the notification opens the GitHub releases page in the default browser
     * so the user can download the latest APK.
     *
     * @param latestVersion The new version string to display in the notification.
     */
    private fun showUpdateNotification(latestVersion: String) {
        val notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create the notification channel (required for Android 8.0+).
        // Creating a channel that already exists is a no-op, so this is safe
        // to call every time.
        val channel = NotificationChannel(
            CHANNEL_ID_UPDATES,
            applicationContext.getString(R.string.notification_channel_updates_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description =
                applicationContext.getString(R.string.notification_channel_updates_description)
        }
        notificationManager.createNotificationChannel(channel)

        // Create an intent to open the GitHub releases page in the browser
        val browserIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse(GitHubUpdateChecker.GITHUB_LATEST_RELEASE_PAGE_URL)
        )
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            browserIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build and show the notification
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID_UPDATES)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(applicationContext.getString(R.string.notification_update_title))
            .setContentText(
                applicationContext.getString(R.string.notification_update_content, latestVersion)
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)  // Dismiss when tapped
            .build()

        notificationManager.notify(NOTIFICATION_ID_UPDATE, notification)
        Log.d(TAG, "Update notification shown for version $latestVersion")
    }
}
