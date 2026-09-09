package com.devomoikane.phoneautomations.drive

import android.content.Context
import android.net.Uri
import com.devomoikane.phoneautomations.automations.AutomationSettings
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File as DriveFile
import java.io.File

object DriveSyncCoordinator {

    private const val ROOT_FOLDER_NAME = "Phone Automations"

    fun localRootNode(context: Context): LocalNode? {
        val uriString = AutomationSettings(context).driveSyncTreeUri ?: return null
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return null
        val node = SafLocalNode(context, uri)
        return if (node.isDirectory) node else null
    }

    fun loadState(context: Context): SyncState {
        val file = stateFile(context)
        if (!file.exists()) return SyncState.empty()
        return try {
            SyncState.fromJson(org.json.JSONObject(file.readText()))
        } catch (e: Exception) {
            SyncState.empty()
        }
    }

    fun saveState(context: Context, state: SyncState) {
        stateFile(context).writeText(state.toJson().toString())
    }

    fun ensureRemoteRoot(drive: Drive, context: Context): String {
        AutomationSettings(context).driveSyncRootFolderId?.let { return it }
        val meta = DriveFile().apply {
            name = ROOT_FOLDER_NAME
            mimeType = DriveFolderSync.FOLDER_MIME
            parents = listOf("root")
        }
        val id = drive.files().create(meta).setFields("id").execute().id
        val settings = AutomationSettings(context)
        settings.driveSyncRootFolderId = id
        settings.driveSyncRootFolderName = ROOT_FOLDER_NAME
        return id
    }

    /** Direct subfolders of [parentId] this app can see (within the drive.file scope). */
    fun listFoldersIn(drive: Drive, parentId: String): List<Pair<String, String>> {
        val result = ArrayList<Pair<String, String>>()
        var pageToken: String? = null
        do {
            val request = drive.files().list()
                .setQ("'$parentId' in parents and mimeType='${DriveFolderSync.FOLDER_MIME}' and trashed = false")
                .setFields("nextPageToken, files(id, name)")
                .setPageSize(100)
            pageToken?.let { request.setPageToken(it) }
            val response = request.execute()
            response.files.forEach { file ->
                result.add(file.id to (file.name ?: ""))
            }
            pageToken = response.nextPageToken
        } while (pageToken != null)
        return result
    }

    private fun stateFile(context: Context): File =
        File(context.filesDir, "drive_sync_state.json")
}
