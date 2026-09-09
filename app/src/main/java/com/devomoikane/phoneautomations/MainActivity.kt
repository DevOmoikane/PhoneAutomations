package com.devomoikane.phoneautomations

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.ArrayDeque
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.devomoikane.phoneautomations.automations.AutomationEvaluator
import com.devomoikane.phoneautomations.automations.AutomationSettings
import com.devomoikane.phoneautomations.automations.PcConnectionDetector
import com.devomoikane.phoneautomations.automations.StayAwakeAutomation
import com.devomoikane.phoneautomations.drive.DriveAuth
import com.devomoikane.phoneautomations.drive.DriveSyncCoordinator
import com.devomoikane.phoneautomations.drive.DriveSyncRunner
import com.devomoikane.phoneautomations.drive.DriveSyncScheduler
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.materialswitch.MaterialSwitch

class MainActivity : AppCompatActivity() {

    private data class SettingRow(
        val title: String,
        val description: String,
        val checked: () -> Boolean,
        val onChecked: (Boolean) -> Unit
    )

    companion object {
        private val INTERVAL_OPTIONS = linkedMapOf(
            1L to R.id.drive_interval_1m,
            15L to R.id.drive_interval_15m,
            60L to R.id.drive_interval_1h,
            240L to R.id.drive_interval_4h,
            1440L to R.id.drive_interval_1d
        )
    }

    private lateinit var scrollView: ScrollView
    private lateinit var connectionText: TextView
    private lateinit var systemSettingText: TextView
    private lateinit var permissionBanner: View
    private lateinit var adbCommandText: TextView
    private lateinit var copyCommandButton: MaterialButton
    private lateinit var settingsContainer: LinearLayout

    private lateinit var driveStatus: TextView
    private lateinit var driveFolderPath: TextView
    private lateinit var driveRemoteFolderPath: TextView
    private lateinit var driveEnable: MaterialSwitch
    private lateinit var driveConnectButton: MaterialButton
    private lateinit var driveFolderButton: MaterialButton
    private lateinit var driveRemoteFolderButton: MaterialButton
    private lateinit var driveSyncButton: MaterialButton
    private lateinit var driveIntervalGroup: MaterialButtonToggleGroup
    private lateinit var driveInterval1m: MaterialButton
    private lateinit var driveInterval15m: MaterialButton
    private lateinit var driveInterval1h: MaterialButton
    private lateinit var driveInterval4h: MaterialButton
    private lateinit var driveInterval1d: MaterialButton
    private val intervalButtons: MutableMap<Long, MaterialButton> = mutableMapOf()

    private var pendingAfterAuth: ((String) -> Unit)? = null

    private val uiRefreshReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            render()
        }
    }

    private val driveConnectLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            val authResult = DriveAuth.handleActivityResult(this, result.data)
            if (authResult is DriveAuth.AuthResult.Authorized) {
                finishAuth(authResult.accessToken)
            } else {
                renderDriveStatus()
            }
        }

    private val folderPickerLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                AutomationSettings(this).driveSyncTreeUri = uri.toString()
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

        driveStatus = findViewById(R.id.drive_status)
        driveFolderPath = findViewById(R.id.drive_folder_path)
        driveRemoteFolderPath = findViewById(R.id.drive_remote_folder_path)
        driveEnable = findViewById(R.id.drive_enable)
        driveConnectButton = findViewById(R.id.drive_connect_button)
        driveFolderButton = findViewById(R.id.drive_folder_button)
        driveRemoteFolderButton = findViewById(R.id.drive_remote_folder_button)
        driveSyncButton = findViewById(R.id.drive_sync_button)
        driveIntervalGroup = findViewById(R.id.drive_interval_group)
        driveInterval1m = findViewById(R.id.drive_interval_1m)
        driveInterval15m = findViewById(R.id.drive_interval_15m)
        driveInterval1h = findViewById(R.id.drive_interval_1h)
        driveInterval4h = findViewById(R.id.drive_interval_4h)
        driveInterval1d = findViewById(R.id.drive_interval_1d)
        intervalButtons[1L] = driveInterval1m
        intervalButtons[15L] = driveInterval15m
        intervalButtons[60L] = driveInterval1h
        intervalButtons[240L] = driveInterval4h
        intervalButtons[1440L] = driveInterval1d

        adbCommandText.text = buildAdbGrantCommand()
        adbCommandText.setTextIsSelectable(true)
        copyCommandButton.setOnClickListener { copyAdbCommandToClipboard() }

        driveConnectButton.setOnClickListener { connectToDrive() }
        driveSyncButton.setOnClickListener { syncNow() }
        driveFolderButton.setOnClickListener { folderPickerLauncher.launch(null) }
        driveRemoteFolderButton.setOnClickListener { chooseRemoteFolder() }
        driveEnable.setOnCheckedChangeListener { _, isChecked ->
            val settings = AutomationSettings(this)
            settings.driveSyncEnabled = isChecked
            DriveSyncScheduler.apply(applicationContext)
            render()
        }
        driveIntervalGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) onIntervalSelected(checkedId)
        }

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
        renderDriveSync()
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

    private fun renderDriveSync() {
        renderDriveStatus()
    }

    private fun renderDriveStatus() {
        val authorized = AutomationSettings(this).driveSyncConnected
        driveStatus.text = getString(
            if (authorized) R.string.drive_status_connected else R.string.drive_status_disconnected
        )
        driveConnectButton.setText(
            if (authorized) R.string.drive_disconnect else R.string.drive_connect
        )
        driveConnectButton.setOnClickListener {
            if (authorized) disconnect() else connectToDrive()
        }

        val enabled = AutomationSettings(this).driveSyncEnabled
        driveEnable.setOnCheckedChangeListener(null)
        driveEnable.isChecked = enabled
        driveEnable.setOnCheckedChangeListener { _, isChecked ->
            val settings = AutomationSettings(this)
            settings.driveSyncEnabled = isChecked
            DriveSyncScheduler.apply(applicationContext)
            render()
        }

        val interval = AutomationSettings(this).driveSyncIntervalMinutes
        intervalButtons.forEach { (minutes, button) ->
            button.isChecked = minutes == interval
        }

        val treeUri = AutomationSettings(this).driveSyncTreeUri
        driveFolderPath.text = if (treeUri == null) {
            getString(R.string.drive_no_folder)
        } else {
            runCatching {
                val doc = androidx.documentfile.provider.DocumentFile.fromTreeUri(this, Uri.parse(treeUri))
                doc?.name ?: Uri.parse(treeUri).lastPathSegment.orEmpty()
            }.getOrNull().orEmpty()
        }

        val remoteName = AutomationSettings(this).driveSyncRootFolderName
        driveRemoteFolderPath.text = remoteName ?: getString(R.string.drive_remote_no_folder)
    }

    private fun connectToDrive() {
        DriveAuth.requestAuthorization(this) { authResult ->
            when (authResult) {
                is DriveAuth.AuthResult.Authorized ->
                    finishAuth(authResult.accessToken)
                is DriveAuth.AuthResult.RequiresUserConsent -> {
                    val request = IntentSenderRequest.Builder(authResult.pendingIntent.intentSender).build()
                    driveConnectLauncher.launch(request)
                }
                DriveAuth.AuthResult.Unavailable ->
                    runOnUiThread { driveStatus.text = getString(R.string.drive_status_disconnected) }
            }
        }
    }

    /** Run [block] with a valid token, requesting authorization first if needed. */
    private fun obtainTokenThen(block: (String) -> Unit) {
        val token = DriveAuth.retrieveAccessToken(this)
        if (token != null) {
            block(token)
        } else {
            pendingAfterAuth = block
            connectToDrive()
        }
    }

    /** Handle a successful auth (from direct result or the consent launcher). */
    private fun finishAuth(token: String) {
        DriveAuth.saveAccessToken(this, token)
        val pending = pendingAfterAuth
        pendingAfterAuth = null
        if (pending != null) {
            pending(token)
        } else {
            runSyncWithToken(token)
        }
    }

    private var folderPickerStack: ArrayDeque<Pair<String, String>>? = null
    private var folderPickerToken: String? = null
    private var folderPickerDialog: AlertDialog? = null

    private fun chooseRemoteFolder() {
        obtainTokenThen { token ->
            folderPickerStack = ArrayDeque()
            folderPickerStack!!.addLast("root" to getString(R.string.drive_remote_root_label))
            folderPickerToken = token
            loadPickerLevel()
        }
    }

    private fun loadPickerLevel() {
        val token = folderPickerToken ?: return
        val stack = folderPickerStack ?: return
        val (currentId, _) = stack.last()
        val drive = DriveAuth.buildDriveService(token)
        Thread {
            val folders = runCatching {
                DriveSyncCoordinator.listFoldersIn(drive, currentId)
            }.getOrElse { emptyList() }
            runOnUiThread { renderPickerDialog(folders) }
        }.start()
    }

    private fun renderPickerDialog(folders: List<Pair<String, String>>) {
        val stack = folderPickerStack ?: return
        val (currentId, currentName) = stack.last()
        val path = stack.joinToString(" / ") { it.second }
        val view = layoutInflater.inflate(R.layout.dialog_drive_folder_picker, null)
        val pathView = view.findViewById<TextView>(R.id.drive_picker_path)
        val listView = view.findViewById<ListView>(R.id.drive_picker_list)
        val newFolderButton = view.findViewById<MaterialButton>(R.id.drive_picker_new_folder)
        pathView.text = getString(R.string.drive_remote_path_label, path)

        val names = folders.map { it.second }
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, names)
        listView.adapter = adapter
        listView.setOnItemClickListener { _, _, position, _ ->
            val (id, name) = folders[position]
            folderPickerStack!!.addLast(id to name)
            loadPickerLevel()
        }
        newFolderButton.setOnClickListener { promptCreateRemoteFolder(currentId) }

        folderPickerDialog?.dismiss()
        folderPickerDialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.drive_remote_choose_title)
            .setView(view)
            .setPositiveButton(R.string.drive_remote_select_here) { _, _ ->
                applyRemoteFolder(currentId, currentName)
                folderPickerStack = null
                folderPickerDialog = null
            }
            .setNeutralButton(R.string.drive_remote_up) { _, _ ->
                if (folderPickerStack!!.size > 1) {
                    folderPickerStack!!.removeLast()
                }
                loadPickerLevel()
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                folderPickerStack = null
                folderPickerDialog = null
            }
            .show()
    }

    private fun promptCreateRemoteFolder(parentId: String? = null) {
        val input = TextInputEditText(this)
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.drive_create_new_folder)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = input.text?.toString()?.trim().orEmpty()
                if (name.isNotEmpty()) createRemoteFolder(name, parentId)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun createRemoteFolder(name: String, parentId: String? = null) {
        obtainTokenThen { token ->
            val settings = AutomationSettings(this)
            val parent = parentId ?: settings.driveSyncRootFolderId
            Thread {
                val result = runCatching {
                    val drive = DriveAuth.buildDriveService(token)
                    val meta = com.google.api.services.drive.model.File().apply {
                        this.name = name.trim()
                        mimeType = "application/vnd.google-apps.folder"
                        parent?.let { parents = listOf(it) }
                    }
                    drive.files().create(meta).setFields("id, name").execute()
                }
                runOnUiThread {
                    result.onSuccess { file ->
                        settings.driveSyncRootFolderId = file.id
                        settings.driveSyncRootFolderName = file.name
                        render()
                        Toast.makeText(this, R.string.drive_remote_created, Toast.LENGTH_SHORT).show()
                    }.onFailure {
                        Toast.makeText(this, R.string.drive_remote_error, Toast.LENGTH_SHORT).show()
                    }
                }
            }.start()
        }
    }

    private fun applyRemoteFolder(id: String, name: String) {
        val settings = AutomationSettings(this)
        settings.driveSyncRootFolderId = id
        settings.driveSyncRootFolderName = name
        render()
    }

    private fun disconnect() {
        DriveAuth.signOut(this)
        DriveAuth.clearAccessToken(this)
        val settings = AutomationSettings(this)
        settings.driveSyncEnabled = false
        settings.driveSyncConnected = false
        settings.driveSyncRootFolderId = null
        settings.driveSyncRootFolderName = null
        DriveSyncScheduler.apply(applicationContext)
        render()
    }

    private fun syncNow() {
        driveSyncButton.isEnabled = false
        driveStatus.text = getString(R.string.drive_sync_running)
        Thread {
            val result = runSync(null)
            runOnUiThread {
                driveSyncButton.isEnabled = true
                updateDriveSyncStatus(result)
            }
        }.start()
    }

    private fun runSyncWithToken(token: String) {
        AutomationSettings(this).driveSyncConnected = true
        driveStatus.text = getString(R.string.drive_sync_running)
        Thread {
            val result = runSync(token)
            runOnUiThread { updateDriveSyncStatus(result) }
        }.start()
    }

    private fun updateDriveSyncStatus(result: DriveSyncRunner.Result) {
        when (result) {
            DriveSyncRunner.Result.SUCCESS -> {
                AutomationSettings(this).driveSyncConnected = true
                driveStatus.text = getString(R.string.drive_sync_complete)
            }
            DriveSyncRunner.Result.NOT_AUTHORIZED -> {
                val connected = DriveAuth.retrieveAccessToken(this) != null
                AutomationSettings(this).driveSyncConnected = connected
                driveStatus.text = getString(
                    if (connected) R.string.drive_select_folder_hint
                    else R.string.drive_status_disconnected
                )
            }
            DriveSyncRunner.Result.FAILED ->
                driveStatus.text = getString(R.string.drive_sync_failed)
        }
    }

    private fun runSync(token: String?): DriveSyncRunner.Result {
        return if (token != null) {
            DriveSyncRunner.runWithToken(this, token)
        } else {
            DriveSyncRunner.run(this)
        }
    }

    private fun onIntervalSelected(checkedId: Int) {
        val minutes = INTERVAL_OPTIONS.entries.firstOrNull { it.value == checkedId }?.key ?: return
        AutomationSettings(this).driveSyncIntervalMinutes = minutes
        DriveSyncScheduler.apply(applicationContext)
    }

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
