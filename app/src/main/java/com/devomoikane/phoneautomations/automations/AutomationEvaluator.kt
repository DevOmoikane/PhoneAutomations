package com.devomoikane.phoneautomations.automations

import android.content.Context
import android.os.Handler
import android.os.Looper

object AutomationEvaluator {

    private const val RECHECK_DELAY_MS = 2500L

    private val handler = Handler(Looper.getMainLooper())
    private var pendingRecheck: Runnable? = null

    fun evaluate(context: Context) {
        val appContext = context.applicationContext
        StayAwakeAutomation.applyToSystem(appContext)

        pendingRecheck?.let(handler::removeCallbacks)
        val recheck = Runnable { StayAwakeAutomation.applyToSystem(appContext) }
        pendingRecheck = recheck
        handler.postDelayed(recheck, RECHECK_DELAY_MS)
    }
}
