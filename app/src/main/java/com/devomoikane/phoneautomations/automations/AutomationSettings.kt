package com.devomoikane.phoneautomations.automations

import android.content.Context

class AutomationSettings(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var stayAwakeOnPcEnabled: Boolean
        get() = prefs.getBoolean(KEY_STAY_AWAKE_ON_PC, DEFAULT_STAY_AWAKE_ON_PC)
        set(value) {
            prefs.edit().putBoolean(KEY_STAY_AWAKE_ON_PC, value).apply()
        }

    private companion object {
        const val PREFS_NAME = "phone_automations"
        const val KEY_STAY_AWAKE_ON_PC = "stay_awake_on_pc"
        const val DEFAULT_STAY_AWAKE_ON_PC = true
    }
}
