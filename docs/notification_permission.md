# Notification Permission Handling

On Android 13+ Battery Alarm declares `POST_NOTIFICATIONS` and checks its state at app
launch without prompting. The permission dialog appears only when the user tries to enable
battery monitoring.

If permission is denied, the app does not start monitoring and keeps the persisted
monitoring intent off. The main screen displays an explicit retry action; the user can also
tap the central enable control again. On Android 12 and below, notifications are available
at install time and monitoring can start immediately.

This keeps the required foreground-service notification and battery alerts visible only
when the user has opted in, while avoiding a launch-time permission interruption.

## Reboot behavior

`SettingsRepository.isServiceEnabled` is the durable user intent. The boot receiver starts
the foreground service only when that value is true. A process-local service flag is used
solely for live settings reloads and never overwrites the persisted preference. The Stop
action in the service notification clears that preference before stopping the service, so it
will not restart after reboot.
