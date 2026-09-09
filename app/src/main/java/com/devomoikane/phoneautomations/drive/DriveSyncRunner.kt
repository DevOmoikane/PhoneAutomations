package com.devomoikane.phoneautomations.drive

import android.content.Context
import android.util.Log
import com.google.api.services.drive.Drive

object DriveSyncRunner {

    private const val TAG = "DriveSyncRunner"

    enum class Result {
        SUCCESS,
        NOT_AUTHORIZED,
        FAILED
    }

    fun run(context: Context): Result {
        val token = DriveAuth.retrieveAccessToken(context) ?: return Result.NOT_AUTHORIZED
        return runWithToken(context, token)
    }

    fun runWithToken(context: Context, token: String): Result {
        DriveAuth.saveAccessToken(context, token)
        return try {
            val localRoot = DriveSyncCoordinator.localRootNode(context)
            if (localRoot == null) {
                Log.w(TAG, "runWithToken: localRootNode is null, NOT_AUTHORIZED")
                return Result.NOT_AUTHORIZED
            }
            val drive = DriveAuth.buildDriveService(token)
            val remoteRootId = DriveSyncCoordinator.ensureRemoteRoot(drive, context)
            val state = DriveSyncCoordinator.loadState(context)
            DriveFolderSync.sync(
                context,
                drive,
                localRoot,
                remoteRootId,
                state,
                onProgress = { DriveSyncCoordinator.saveState(context, it) }
            )
            DriveSyncCoordinator.saveState(context, state)
            Log.i(TAG, "runWithToken: SUCCESS")
            Result.SUCCESS
        } catch (e: Exception) {
            Log.e(TAG, "runWithToken: FAILED", e)
            Result.FAILED
        }
    }
}
