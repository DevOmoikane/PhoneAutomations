package com.devomoikane.phoneautomations.drive

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.devomoikane.phoneautomations.automations.AutomationSettings
import java.util.concurrent.TimeUnit

/**
 * Schedules drive sync as a chain of one-time workers that re-schedule themselves.
 * Unlike the periodic API, which is limited to a 15 minute minimum, this honors
 * sub-15 minute intervals (e.g. 1 minute).
 */
object DriveSyncScheduler {

    private const val UNIQUE_WORK_NAME = "drive_sync_chain"

    fun apply(context: Context) {
        val settings = AutomationSettings(context)
        if (settings.driveSyncEnabled) {
            enqueueNext(context, settings.driveSyncIntervalMinutes)
        } else {
            cancel(context)
        }
    }

    /** Reschedule the next run after the worker has executed. */
    fun reschedule(context: Context) {
        val settings = AutomationSettings(context)
        if (settings.driveSyncEnabled) {
            enqueueNext(context, settings.driveSyncIntervalMinutes)
        }
    }

    private fun enqueueNext(context: Context, intervalMinutes: Long) {
        val interval = intervalMinutes.coerceAtLeast(1)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<DriveSyncWorker>()
            .setInitialDelay(interval, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    private fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
    }
}
