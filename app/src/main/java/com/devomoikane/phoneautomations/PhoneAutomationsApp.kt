package com.devomoikane.phoneautomations

import android.app.Application
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.devomoikane.phoneautomations.automations.AutomationEvaluator
import com.devomoikane.phoneautomations.automations.PcConnectionDetector
import com.devomoikane.phoneautomations.automations.UsbStateReceiver

class PhoneAutomationsApp : Application() {

    override fun onCreate() {
        super.onCreate()
        AutomationEvaluator.evaluate(this)
        registerReceiver(
            UsbStateReceiver(),
            IntentFilter(PcConnectionDetector.ACTION_USB_STATE),
            RECEIVER_NOT_EXPORTED
        )
        startForegroundService()
    }

    private fun startForegroundService() {
        val intent = Intent(this, PhoneAutomationsService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }
}
