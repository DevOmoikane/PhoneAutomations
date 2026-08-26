package com.devomoikane.phoneautomations

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.devomoikane.phoneautomations.automations.AutomationEvaluator
import com.devomoikane.phoneautomations.automations.AutomationSettings
import com.devomoikane.phoneautomations.automations.PcConnectionDetector
import com.devomoikane.phoneautomations.automations.StayAwakeAutomation
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch

class MainActivity : AppCompatActivity() {

    private data class SettingRow(
        val title: String,
        val description: String,
        val checked: () -> Boolean,
        val onChecked: (Boolean) -> Unit
    )

    private lateinit var scrollView: ScrollView
    private lateinit var connectionText: TextView
    private lateinit var systemSettingText: TextView
    private lateinit var permissionBanner: View
    private lateinit var adbCommandText: TextView
    private lateinit var copyCommandButton: MaterialButton
    private lateinit var settingsContainer: LinearLayout

    private val uiRefreshReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            render()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        scrollView = findViewById(R.id.scroll_view)
        connectionText = findViewById(R.id.connection_text)
        systemSettingText = findViewById(R.id.system_setting_text)
        permissionBanner = findViewById(R.id.permission_banner)
        adbCommandText = findViewById(R.id.adb_command_text)
        copyCommandButton = findViewById(R.id.copy_command_button)
        settingsContainer = findViewById(R.id.settings_container)

        adbCommandText.text = buildAdbGrantCommand()
        adbCommandText.setTextIsSelectable(true)
        copyCommandButton.setOnClickListener { copyAdbCommandToClipboard() }

        ViewCompat.setOnApplyWindowInsetsListener(scrollView) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, insets.top, view.paddingRight, insets.bottom)
            windowInsets
        }

        render()
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(PcConnectionDetector.ACTION_USB_STATE)
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        registerReceiver(uiRefreshReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(uiRefreshReceiver)
    }

    private fun render() {
        renderStatus()
        renderSettings()
    }

    private fun renderStatus() {
        connectionText.setText(
            when (PcConnectionDetector.connectionState(this)) {
                PcConnectionDetector.ConnectionState.COMPUTER -> R.string.status_connection_computer
                PcConnectionDetector.ConnectionState.CHARGER -> R.string.status_connection_charger
                PcConnectionDetector.ConnectionState.DISCONNECTED -> R.string.status_connection_battery
            }
        )

        val systemOn = StayAwakeAutomation.currentValue(this) != 0
        systemSettingText.text =
            getString(R.string.system_stay_awake_value, getString(if (systemOn) R.string.value_on else R.string.value_off))

        val granted = StayAwakeAutomation.hasWriteSecureSettings(this)
        permissionBanner.visibility = if (granted) View.GONE else View.VISIBLE
    }

    private fun renderSettings() {
        settingsContainer.removeAllViews()
        settingRows().forEach { row ->
            val view = layoutInflater.inflate(R.layout.item_setting, settingsContainer, true)
            view.findViewById<TextView>(R.id.setting_title).text = row.title
            view.findViewById<TextView>(R.id.setting_description).text = row.description
            val toggle = view.findViewById<MaterialSwitch>(R.id.setting_toggle)
            toggle.setOnCheckedChangeListener(null)
            toggle.isChecked = row.checked()
            toggle.setOnCheckedChangeListener { _, checked -> row.onChecked(checked) }
        }
    }

    private fun settingRows(): List<SettingRow> = listOf(
        SettingRow(
            title = getString(R.string.setting_stay_awake_title),
            description = getString(R.string.setting_stay_awake_desc),
            checked = { AutomationSettings(this).stayAwakeOnPcEnabled },
            onChecked = { checked ->
                AutomationSettings(this).stayAwakeOnPcEnabled = checked
                AutomationEvaluator.evaluate(applicationContext)
                render()
            }
        )
    )

    private fun copyAdbCommandToClipboard() {
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard?.setPrimaryClip(
            ClipData.newPlainText("adb command", buildAdbGrantCommand())
        )
        Toast.makeText(this, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
    }

    private fun buildAdbGrantCommand(): String =
        "adb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS"
}
