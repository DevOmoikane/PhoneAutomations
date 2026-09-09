package com.devomoikane.phoneautomations.drive

import android.content.Context
import android.util.Log
import com.google.api.client.http.FileContent
import com.google.api.client.http.HttpResponseException
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File as DriveFile
import com.google.api.services.drive.model.FileList
import java.io.File
import java.io.FileOutputStream
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.UUID

object DriveFolderSync {

    private const val TAG = "DriveFolderSync"
    private const val MAX_RETRIES = 3
    private const val RETRY_BASE_DELAY_MS = 2_000L

    const val FOLDER_MIME = "application/vnd.google-apps.folder"
    private const val FIELDS =
        "nextPageToken, files(id, name, mimeType, modifiedTime)"

    private data class LocalEntry(val name: String, val node: LocalNode) {
        val isDir: Boolean get() = node.isDirectory
        val lastModified: Long get() = node.lastModified()
    }

    private data class RemoteEntry(
        val name: String,
        val id: String,
        val isDir: Boolean,
        val mtimeEpoch: Long
    )

    private fun <T> retryOnTransient(block: () -> T): T {
        var lastException: Exception? = null
        repeat(MAX_RETRIES) { attempt ->
            try {
                return block()
            } catch (e: SocketTimeoutException) {
                lastException = e
                Log.w(TAG, "Transient timeout (attempt ${attempt + 1}/$MAX_RETRIES)", e)
                if (attempt < MAX_RETRIES - 1) Thread.sleep(RETRY_BASE_DELAY_MS * (attempt + 1))
            } catch (e: SocketException) {
                lastException = e
                Log.w(TAG, "Transient socket error (attempt ${attempt + 1}/$MAX_RETRIES)", e)
                if (attempt < MAX_RETRIES - 1) Thread.sleep(RETRY_BASE_DELAY_MS * (attempt + 1))
            }
        }
        throw lastException!!
    }

    fun sync(
        context: Context,
        drive: Drive,
        localRoot: LocalNode,
        remoteRootId: String,
        state: SyncState,
        onProgress: (SyncState) -> Unit = {}
    ) {
        syncDirectory(context, drive, localRoot, remoteRootId, "", state, onProgress)
        onProgress(state)
    }

    private fun syncDirectory(
        context: Context,
        drive: Drive,
        localDir: LocalNode,
        remoteDirId: String,
        prefix: String,
        state: SyncState,
        onProgress: (SyncState) -> Unit
    ) {
        val local = listLocal(localDir)
        val remote = retryOnTransient { listRemote(drive, remoteDirId) }
        val names = (local.keys + remote.keys).toSet()

        for (name in names) {
            val path = if (prefix.isEmpty()) name else "$prefix/$name"
            val l = local[name]
            val r = remote[name]
            when {
                l != null && r == null ->
                    handleLocalOnly(context, drive, name, l, remoteDirId, path, state, onProgress)
                r != null && l == null ->
                    handleRemoteOnly(context, drive, localDir, name, r, path, state, onProgress)
                l != null && r != null ->
                    handleBoth(context, drive, localDir, name, l, r, remoteDirId, path, state, onProgress)
            }
        }
        onProgress(state)
    }

    private fun listLocal(dir: LocalNode): Map<String, LocalEntry> =
        dir.children().associate { LocalEntry(it.name, it).let { e -> e.name to e } }

    private fun listRemote(drive: Drive, folderId: String): Map<String, RemoteEntry> {
        val result = LinkedHashMap<String, RemoteEntry>()
        var pageToken: String? = null
        do {
            val request = drive.files().list()
                .setQ("'$folderId' in parents and trashed = false")
                .setFields(FIELDS)
                .setPageSize(100)
            pageToken?.let { request.setPageToken(it) }
            val response: FileList = request.execute()
            response.files.forEach { file ->
                val isDir = file.mimeType == FOLDER_MIME
                result[file.name] = RemoteEntry(
                    name = file.name,
                    id = file.id,
                    isDir = isDir,
                    mtimeEpoch = file.modifiedTime?.value ?: 0L
                )
            }
            pageToken = response.nextPageToken
        } while (pageToken != null)
        return result
    }

    private fun handleLocalOnly(
        context: Context,
        drive: Drive,
        name: String,
        entry: LocalEntry,
        parentId: String,
        path: String,
        state: SyncState,
        onProgress: (SyncState) -> Unit
    ) {
        val known = state[path]
        if (known != null && known.driveId != null) {
            entry.node.delete()
            state.remove(path)
            return
        }
        if (entry.isDir) {
            val id = retryOnTransient { createFolder(drive, name, parentId) }
            state[path] = SyncState.EntryMeta(entry.lastModified, 0L, id, true)
            syncDirectory(context, drive, entry.node, id, path, state, onProgress)
        } else {
            val remoteFile = retryOnTransient { createFile(context, drive, name, parentId, entry.node) }
            val mtime = remoteFile.modifiedTime?.value ?: entry.lastModified
            state[path] = SyncState.EntryMeta(entry.lastModified, mtime, remoteFile.id, false)
        }
    }

    private fun handleRemoteOnly(
        context: Context,
        drive: Drive,
        localDir: LocalNode,
        name: String,
        entry: RemoteEntry,
        path: String,
        state: SyncState,
        onProgress: (SyncState) -> Unit
    ) {
        if (state[path] != null) {
            retryOnTransient { drive.files().delete(entry.id).execute() }
            state.remove(path)
            return
        }
        if (entry.isDir) {
            val dir = localDir.ensureChildDirectory(name)
            state[path] = SyncState.EntryMeta(0L, entry.mtimeEpoch, entry.id, true)
            syncDirectory(context, drive, dir, entry.id, path, state, onProgress)
        } else {
            val file = localDir.ensureChildFile(name)
            retryOnTransient { downloadFile(context, drive, entry.id, file) }
            state[path] = SyncState.EntryMeta(file.lastModified(), entry.mtimeEpoch, entry.id, false)
        }
    }

    private fun handleBoth(
        context: Context,
        drive: Drive,
        localDir: LocalNode,
        name: String,
        local: LocalEntry,
        remote: RemoteEntry,
        parentId: String,
        path: String,
        state: SyncState,
        onProgress: (SyncState) -> Unit
    ) {
        if (local.isDir && remote.isDir) {
            state[path] = SyncState.EntryMeta(
                local.lastModified, remote.mtimeEpoch, remote.id, true
            )
            syncDirectory(context, drive, local.node, remote.id, path, state, onProgress)
            return
        }

        if (!local.isDir && !remote.isDir) {
            syncFile(context, drive, local, remote, path, state)
            return
        }

        resolveTypeConflict(context, drive, localDir, name, local, remote, parentId, path, state, onProgress)
    }

    private fun syncFile(
        context: Context,
        drive: Drive,
        local: LocalEntry,
        remote: RemoteEntry,
        path: String,
        state: SyncState
    ) {
        val known = state[path]
        val localChanged = known == null || local.lastModified != known.localVersion
        val remoteChanged = known == null || remote.mtimeEpoch != known.remoteMtime

        when {
            localChanged && remoteChanged -> {
                if (local.lastModified > remote.mtimeEpoch) {
                    uploadFile(context, drive, local, remote.id, path, state)
                } else {
                    downloadAndRecord(context, drive, local, remote, path, state)
                }
            }
            localChanged -> uploadFile(context, drive, local, remote.id, path, state)
            remoteChanged -> downloadAndRecord(context, drive, local, remote, path, state)
        }
    }

    private fun uploadFile(
        context: Context,
        drive: Drive,
        local: LocalEntry,
        remoteId: String,
        path: String,
        state: SyncState
    ) {
        val temp = copyToTemp(context, local.node)
        try {
            val remoteFile = retryOnTransient {
                drive.files()
                    .update(remoteId, DriveFile(), FileContent("application/octet-stream", temp))
                    .setFields("id, modifiedTime")
                    .execute()
            }
            val mtime = remoteFile.modifiedTime?.value ?: local.lastModified
            state[path] = SyncState.EntryMeta(local.lastModified, mtime, remoteId, false)
        } finally {
            temp.delete()
        }
    }

    private fun downloadAndRecord(
        context: Context,
        drive: Drive,
        local: LocalEntry,
        remote: RemoteEntry,
        path: String,
        state: SyncState
    ) {
        retryOnTransient { downloadFile(context, drive, remote.id, local.node) }
        state[path] = SyncState.EntryMeta(local.node.lastModified(), remote.mtimeEpoch, remote.id, false)
    }

    private fun resolveTypeConflict(
        context: Context,
        drive: Drive,
        localDir: LocalNode,
        name: String,
        local: LocalEntry,
        remote: RemoteEntry,
        parentId: String,
        path: String,
        state: SyncState,
        onProgress: (SyncState) -> Unit
    ) {
        val known = state[path]
        val localChanged = known == null || local.lastModified != known.localVersion
        val remoteChanged = known == null || remote.mtimeEpoch != known.remoteMtime
        val localWins = when {
            localChanged && remoteChanged -> local.lastModified >= remote.mtimeEpoch
            localChanged -> true
            else -> false
        }

        if (localWins) {
            retryOnTransient { drive.files().delete(remote.id).execute() }
            state.remove(path)
            if (local.isDir) {
                val id = retryOnTransient { createFolder(drive, name, parentId) }
                state[path] = SyncState.EntryMeta(local.lastModified, 0L, id, true)
                syncDirectory(context, drive, local.node, id, path, state, onProgress)
            } else {
                val newId = retryOnTransient { createFile(context, drive, name, parentId, local.node) }
                val mtime = newId.modifiedTime?.value ?: local.lastModified
                state[path] = SyncState.EntryMeta(local.lastModified, mtime, newId.id, false)
            }
        } else {
            local.node.delete()
            state.remove(path)
            if (remote.isDir) {
                val dir = localDir.ensureChildDirectory(name)
                state[path] = SyncState.EntryMeta(0L, remote.mtimeEpoch, remote.id, true)
                syncDirectory(context, drive, dir, remote.id, path, state, onProgress)
            } else {
                val file = localDir.ensureChildFile(name)
                retryOnTransient { downloadFile(context, drive, remote.id, file) }
                state[path] = SyncState.EntryMeta(file.lastModified(), remote.mtimeEpoch, remote.id, false)
            }
        }
    }

    private fun createFile(
        context: Context,
        drive: Drive,
        name: String,
        parentId: String,
        node: LocalNode
    ): DriveFile {
        val meta = DriveFile().apply {
            this.name = name
            parents = listOf(parentId)
        }
        val temp = copyToTemp(context, node)
        try {
            return drive.files()
                .create(meta, FileContent("application/octet-stream", temp))
                .setFields("id, modifiedTime")
                .execute()
        } finally {
            temp.delete()
        }
    }

    private fun createFolder(drive: Drive, name: String, parentId: String): String {
        val meta = DriveFile().apply {
            this.name = name
            mimeType = FOLDER_MIME
            parents = listOf(parentId)
        }
        return drive.files().create(meta).setFields("id").execute().id
    }

    private fun downloadFile(context: Context, drive: Drive, id: String, target: LocalNode) {
        target.openOutputStream().use { out ->
            try {
                drive.files().get(id).executeMediaAndDownloadTo(out)
            } catch (e: HttpResponseException) {
                if (e.statusCode == 416) {
                    Log.w(TAG, "downloadFile: empty file (416) for $id, writing empty file")
                } else {
                    throw e
                }
            }
        }
    }

    private fun copyToTemp(context: Context, node: LocalNode): File {
        val temp = File(context.cacheDir, "upload_${UUID.randomUUID()}.tmp")
        FileOutputStream(temp).use { out ->
            node.openInputStream().use { it.copyTo(out) }
        }
        return temp
    }
}
