package com.devomoikane.phoneautomations.automations

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.devomoikane.phoneautomations.PhoneAutomationsService

class ConnectionEventReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_POWER_CONNECTED,
            Intent.ACTION_POWER_DISCONNECTED,
            Intent.ACTION_BOOT_COMPLETED -> {
                AutomationEvaluator.evaluate(context)
                startForegroundServiceIfNeeded(context)
            }
        }
    }

    private fun startForegroundServiceIfNeeded(context: Context) {
        val serviceIntent = Intent(context, PhoneAutomationsService::class.java)
        ContextCompat.startForegroundService(context, serviceIntent)
    }
}
