package com.devomoikane.phoneautomations.drive

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class DriveSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val outcome = DriveSyncRunner.run(applicationContext)
        return when (outcome) {
            DriveSyncRunner.Result.SUCCESS -> {
                DriveSyncScheduler.reschedule(applicationContext)
                Result.success()
            }
            DriveSyncRunner.Result.NOT_AUTHORIZED,
            DriveSyncRunner.Result.FAILED -> Result.retry()
        }
    }
}
