package com.devomoikane.phoneautomations.automations

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

object PcConnectionDetector {

    const val ACTION_USB_STATE = "android.hardware.usb.action.USB_STATE"

    private const val EXTRA_CONNECTED = "connected"
    private const val EXTRA_CONFIGURED = "configured"
    private const val EXTRA_ADB = "adb"

    enum class ConnectionState {
        COMPUTER,
        CHARGER,
        DISCONNECTED
    }

    fun connectionState(context: Context): ConnectionState {
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return ConnectionState.DISCONNECTED
        val plugged = battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        if (plugged == 0) return ConnectionState.DISCONNECTED

        val usbState = context.registerReceiver(null, IntentFilter(ACTION_USB_STATE))
            ?: return ConnectionState.CHARGER
        val connected = usbState.getBooleanExtra(EXTRA_CONNECTED, false)
        val configured = usbState.getBooleanExtra(EXTRA_CONFIGURED, false)
        val adbActive = usbState.getBooleanExtra(EXTRA_ADB, false)

        return if (connected && (configured || adbActive)) {
            ConnectionState.COMPUTER
        } else {
            ConnectionState.CHARGER
        }
    }

    fun isPcConnected(context: Context): Boolean =
        connectionState(context) == ConnectionState.COMPUTER
}
