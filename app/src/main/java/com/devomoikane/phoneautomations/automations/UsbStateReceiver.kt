package com.devomoikane.phoneautomations.automations

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class UsbStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == PcConnectionDetector.ACTION_USB_STATE) {
            AutomationEvaluator.evaluate(context)
        }
    }
}
