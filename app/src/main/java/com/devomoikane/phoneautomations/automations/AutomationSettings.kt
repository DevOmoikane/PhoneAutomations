package com.devomoikane.phoneautomations.automations

import android.content.Context

class AutomationSettings(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var stayAwakeOnPcEnabled: Boolean
        get() = prefs.getBoolean(KEY_STAY_AWAKE_ON_PC, DEFAULT_STAY_AWAKE_ON_PC)
        set(value) {
            prefs.edit().putBoolean(KEY_STAY_AWAKE_ON_PC, value).apply()
        }

    var driveSyncEnabled: Boolean
        get() = prefs.getBoolean(KEY_DRIVE_SYNC_ENABLED, DEFAULT_DRIVE_SYNC_ENABLED)
        set(value) {
            prefs.edit().putBoolean(KEY_DRIVE_SYNC_ENABLED, value).apply()
        }

    var driveSyncIntervalMinutes: Long
        get() = prefs.getLong(KEY_DRIVE_SYNC_INTERVAL, DEFAULT_DRIVE_SYNC_INTERVAL)
        set(value) {
            prefs.edit().putLong(KEY_DRIVE_SYNC_INTERVAL, value).apply()
        }

    var driveSyncTreeUri: String?
        get() = prefs.getString(KEY_DRIVE_SYNC_TREE_URI, null)
        set(value) {
            prefs.edit().putString(KEY_DRIVE_SYNC_TREE_URI, value).apply()
        }

    var driveSyncRootFolderId: String?
        get() = prefs.getString(KEY_DRIVE_SYNC_ROOT_FOLDER_ID, null)
        set(value) {
            prefs.edit().putString(KEY_DRIVE_SYNC_ROOT_FOLDER_ID, value).apply()
        }

    var driveSyncRootFolderName: String?
        get() = prefs.getString(KEY_DRIVE_SYNC_ROOT_FOLDER_NAME, null)
        set(value) {
            prefs.edit().putString(KEY_DRIVE_SYNC_ROOT_FOLDER_NAME, value).apply()
        }

    var driveSyncConnected: Boolean
        get() = prefs.getBoolean(KEY_DRIVE_SYNC_CONNECTED, DEFAULT_DRIVE_SYNC_CONNECTED)
        set(value) {
            prefs.edit().putBoolean(KEY_DRIVE_SYNC_CONNECTED, value).apply()
        }

    private companion object {
        const val PREFS_NAME = "phone_automations"
        const val KEY_STAY_AWAKE_ON_PC = "stay_awake_on_pc"
        const val DEFAULT_STAY_AWAKE_ON_PC = true
        const val KEY_DRIVE_SYNC_ENABLED = "drive_sync_enabled"
        const val DEFAULT_DRIVE_SYNC_ENABLED = false
        const val KEY_DRIVE_SYNC_INTERVAL = "drive_sync_interval_minutes"
        const val DEFAULT_DRIVE_SYNC_INTERVAL = 60L
        const val KEY_DRIVE_SYNC_TREE_URI = "drive_sync_tree_uri"
        const val KEY_DRIVE_SYNC_ROOT_FOLDER_ID = "drive_sync_root_folder_id"
        const val KEY_DRIVE_SYNC_ROOT_FOLDER_NAME = "drive_sync_root_folder_name"
        const val KEY_DRIVE_SYNC_CONNECTED = "drive_sync_connected"
        const val DEFAULT_DRIVE_SYNC_CONNECTED = false
    }
}
