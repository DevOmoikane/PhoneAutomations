# Phone Automations

A collection of Android tools that automate common phone behaviors based on connection state and scheduled syncs.

## Features

### Stay Awake on PC Connection

Automatically enables "Stay awake while plugged in" when your phone is connected to a computer via USB, and disables it when disconnected. This keeps your screen on during development or file transfers without manually toggling the setting.

- Detects USB connection to a computer vs. a charger
- Requires the `WRITE_SECURE_SETTINGS` permission (granted via ADB)

### Google Drive Sync

Syncs files between a local folder on your device and a Google Drive folder. Configure the sync interval, pick local and remote folders, and let the app handle the rest.

- Configurable sync interval: 1 min, 15 min, 1 hr, 4 hr, 1 day
- Browse and create remote folders from within the app
- Uses WorkManager for reliable background scheduling

### More Tools Coming Soon

This project is a work in progress. Additional automation tools will be added over time.

## Requirements

- Android 16 (API 36) or higher
- ADB access for the initial permission grant (Stay Awake feature)

## Setup

### Build

Clone the repo and open it in Android Studio, or build from the command line:

```bash
./gradlew assembleDebug
```

### Grant Permissions

The Stay Awake feature requires a special permission that cannot be granted through the UI. Run this once via ADB:

```bash
adb shell pm grant com.devomoikane.phoneautomations android.permission.WRITE_SECURE_SETTINGS
```

The app will display this command for you and let you copy it directly.

### Google Drive Sync

1. Open the app and navigate to the Drive Sync section
2. Tap "Connect" to authenticate with your Google account
3. Select a local folder to sync from
4. Choose or create a remote folder on Google Drive
5. Enable sync and pick an interval

## Project Structure

```
app/src/main/java/com/devomoikane/phoneautomations/
├── MainActivity.kt                  # Main UI and settings
├── PhoneAutomationsApp.kt           # Application class
├── PhoneAutomationsService.kt       # Foreground service
├── automations/
│   ├── AutomationEvaluator.kt       # Evaluates and applies all automations
│   ├── AutomationSettings.kt        # SharedPreferences wrapper
│   ├── ConnectionEventReceiver.kt   # Boot and power broadcast receiver
│   ├── PcConnectionDetector.kt      # Detects USB connection type
│   ├── StayAwakeAutomation.kt       # Stay awake logic
│   └── UsbStateReceiver.kt          # USB state broadcast receiver
└── drive/
    ├── DriveAuth.kt                 # Google Drive authentication
    ├── DriveFolderSync.kt           # Folder-level sync logic
    ├── DriveSyncCoordinator.kt      # Orchestrates the sync process
    ├── DriveSyncRunner.kt           # Entry point for running sync
    ├── DriveSyncScheduler.kt        # WorkManager scheduling
    ├── DriveSyncWorker.kt           # WorkManager worker
    ├── LocalNode.kt                 # Local file tree representation
    └── SyncState.kt                 # Sync state tracking
```

## Tech Stack

- **Language:** Kotlin
- **UI:** Android Views with Material Design 3
- **Background Work:** WorkManager
- **Cloud:** Google Drive API, Google Play Services Auth
- **Build:** Gradle with Kotlin DSL
