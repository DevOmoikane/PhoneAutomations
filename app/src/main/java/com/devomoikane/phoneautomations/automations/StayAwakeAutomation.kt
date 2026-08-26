package com.devomoikane.phoneautomations.automations

import android.content.Context
import android.os.BatteryManager
import android.provider.Settings
import androidx.core.content.ContextCompat

object StayAwakeAutomation {

    val PLUGGED_MASK: Int =
        BatteryManager.BATTERY_PLUGGED_AC or BatteryManager.BATTERY_PLUGGED_USB

    fun targetValue(featureEnabled: Boolean, pcConnected: Boolean): Int? =
        when {
            !featureEnabled -> null
            pcConnected -> PLUGGED_MASK
            else -> 0
        }

    fun currentValue(context: Context): Int =
        Settings.Global.getInt(
            context.contentResolver,
            Settings.Global.STAY_ON_WHILE_PLUGGED_IN,
            0
        )

    fun hasWriteSecureSettings(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.WRITE_SECURE_SETTINGS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    fun applyToSystem(context: Context) {
        if (!hasWriteSecureSettings(context)) return
        val featureEnabled = AutomationSettings(context).stayAwakeOnPcEnabled
        val target = targetValue(featureEnabled, PcConnectionDetector.isPcConnected(context))
            ?: return
        if (currentValue(context) != target) {
            Settings.Global.putInt(
                context.contentResolver,
                Settings.Global.STAY_ON_WHILE_PLUGGED_IN,
                target
            )
        }
    }
}
