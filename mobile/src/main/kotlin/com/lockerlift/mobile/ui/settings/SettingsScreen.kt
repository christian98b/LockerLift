package com.lockerlift.mobile.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.lockerlift.mobile.LockerLiftMobileApp
import com.lockerlift.mobile.R
import com.lockerlift.mobile.backup.BackupResult
import com.lockerlift.mobile.backup.LocalBackupManager
import com.lockerlift.mobile.backup.LocalBackupWorker
import kotlinx.coroutines.launch

// SharedPreferences keys
private const val PREFS_NAME = "lockerlift_backup_prefs"
private const val PREF_BACKUP_DIR_URI = "pref_backup_dir_uri"
private const val PREF_BACKUP_SCHEDULE = "pref_backup_schedule"
private const val PREF_BACKUP_KEEP_COUNT = "pref_backup_keep_count"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(app: LockerLiftMobileApp) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // --- Shared Preferences ---
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE) }

    var backupDirUriStr by remember { mutableStateOf(prefs.getString(PREF_BACKUP_DIR_URI, "") ?: "") }
    var backupSchedule by remember { mutableStateOf(prefs.getString(PREF_BACKUP_SCHEDULE, "disabled") ?: "disabled") }
    var keepCount by remember { mutableStateOf(prefs.getInt(PREF_BACKUP_KEEP_COUNT, 5)) }

    // --- Snackbar ---
    val snackbarHostState = remember { SnackbarHostState() }

    // String resources needed inside lambdas
    val strBackupSuccess = stringResource(R.string.settings_backup_success)
    val strBackupError = stringResource(R.string.settings_backup_error)
    val strRestoreSuccess = stringResource(R.string.settings_restore_success)
    val strRestoreError = stringResource(R.string.settings_restore_error)
    val strNoBackup = stringResource(R.string.settings_no_backup_to_share)
    val strFolderSelectedFmt = stringResource(R.string.settings_folder_selected_format)
    val strFolderNotSet = stringResource(R.string.settings_backup_folder_not_set)

    // --- SAF launchers ---
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            // Persist read+write permission across reboots
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            backupDirUriStr = uri.toString()
            prefs.edit().putString(PREF_BACKUP_DIR_URI, backupDirUriStr).apply()
            // Re-apply schedule with new URI
            if (backupSchedule != "disabled" && backupDirUriStr.isNotEmpty()) {
                val intervalDays = if (backupSchedule == "daily") 1L else 7L
                LocalBackupWorker.schedule(context, backupDirUriStr, intervalDays, keepCount)
            }
        }
    }

    val restoreFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val result = LocalBackupManager(context, app.database).restoreFromFile(uri)
                val msg = when (result) {
                    is BackupResult.Success -> strRestoreSuccess
                    is BackupResult.Error -> String.format(strRestoreError, result.message)
                }
                snackbarHostState.showSnackbar(msg)
            }
        }
    }

    // --- Derived display values ---
    val folderDisplayName = remember(backupDirUriStr) {
        if (backupDirUriStr.isEmpty()) null
        else DocumentFile.fromTreeUri(context, Uri.parse(backupDirUriStr))?.name
    }

    // --- Schedule dropdown state ---
    var scheduleDropdownExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.settings_title)) })
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {

            // ----------------------------------------------------------------
            // Section: Backup Folder
            // ----------------------------------------------------------------
            item {
                SectionCard(title = stringResource(R.string.settings_backup_folder_section)) {
                    val displayText = if (folderDisplayName != null) {
                        String.format(strFolderSelectedFmt, folderDisplayName)
                    } else {
                        strFolderNotSet
                    }
                    Text(
                        text = displayText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (folderDisplayName != null)
                            MaterialTheme.colorScheme.onSurface
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { folderPickerLauncher.launch(null) }) {
                        Text(stringResource(R.string.settings_btn_choose_folder))
                    }
                }
            }

            // ----------------------------------------------------------------
            // Section: Manual Backup
            // ----------------------------------------------------------------
            item {
                SectionCard(title = stringResource(R.string.settings_manual_backup_section)) {
                    Button(
                        onClick = {
                            if (backupDirUriStr.isEmpty()) {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(strFolderNotSet)
                                }
                                return@Button
                            }
                            coroutineScope.launch {
                                val dirUri = Uri.parse(backupDirUriStr)
                                val result = LocalBackupManager(context, app.database)
                                    .exportToDirectory(dirUri)
                                val msg = when (result) {
                                    is BackupResult.Success ->
                                        String.format(strBackupSuccess, result.fileName)
                                    is BackupResult.Error ->
                                        String.format(strBackupError, result.message)
                                }
                                snackbarHostState.showSnackbar(msg)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.settings_btn_export))
                    }
                }
            }

            // ----------------------------------------------------------------
            // Section: Restore
            // ----------------------------------------------------------------
            item {
                SectionCard(title = stringResource(R.string.settings_restore_section)) {
                    Button(
                        onClick = {
                            restoreFileLauncher.launch(
                                arrayOf("application/octet-stream", "*/*")
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.settings_btn_restore))
                    }
                }
            }

            // ----------------------------------------------------------------
            // Section: Scheduled Backups
            // ----------------------------------------------------------------
            item {
                SectionCard(title = stringResource(R.string.settings_schedule_section)) {

                    // Schedule interval dropdown
                    Text(
                        text = stringResource(R.string.settings_schedule_label),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    val scheduleOptions = listOf(
                        "disabled" to stringResource(R.string.settings_schedule_disabled),
                        "daily" to stringResource(R.string.settings_schedule_daily),
                        "weekly" to stringResource(R.string.settings_schedule_weekly)
                    )
                    val selectedScheduleLabel = scheduleOptions
                        .firstOrNull { it.first == backupSchedule }?.second
                        ?: stringResource(R.string.settings_schedule_disabled)

                    ExposedDropdownMenuBox(
                        expanded = scheduleDropdownExpanded,
                        onExpandedChange = { scheduleDropdownExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedScheduleLabel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.settings_schedule_label)) },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = scheduleDropdownExpanded)
                            },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = scheduleDropdownExpanded,
                            onDismissRequest = { scheduleDropdownExpanded = false }
                        ) {
                            scheduleOptions.forEach { (value, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        backupSchedule = value
                                        prefs.edit().putString(PREF_BACKUP_SCHEDULE, value).apply()
                                        scheduleDropdownExpanded = false
                                        if (value == "disabled") {
                                            LocalBackupWorker.cancel(context)
                                        } else if (backupDirUriStr.isNotEmpty()) {
                                            val intervalDays = if (value == "daily") 1L else 7L
                                            LocalBackupWorker.schedule(
                                                context, backupDirUriStr, intervalDays, keepCount
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Keep count chips
                    Text(
                        text = stringResource(R.string.settings_keep_count_label),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(3, 5, 10).forEach { count ->
                            FilterChip(
                                selected = keepCount == count,
                                onClick = {
                                    keepCount = count
                                    prefs.edit().putInt(PREF_BACKUP_KEEP_COUNT, count).apply()
                                    if (backupSchedule != "disabled" && backupDirUriStr.isNotEmpty()) {
                                        val intervalDays = if (backupSchedule == "daily") 1L else 7L
                                        LocalBackupWorker.schedule(
                                            context, backupDirUriStr, intervalDays, count
                                        )
                                    }
                                },
                                label = { Text(count.toString()) }
                            )
                        }
                    }
                }
            }

            // ----------------------------------------------------------------
            // Section: Share Backup
            // ----------------------------------------------------------------
            item {
                SectionCard(title = stringResource(R.string.settings_share_section)) {
                    Button(
                        onClick = {
                            if (backupDirUriStr.isEmpty()) {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(strNoBackup)
                                }
                                return@Button
                            }
                            val dirUri = Uri.parse(backupDirUriStr)
                            val dir = DocumentFile.fromTreeUri(context, dirUri)
                            val latestFile = dir?.listFiles()
                                ?.filter { it.name?.startsWith(LocalBackupManager.BACKUP_PREFIX) == true }
                                ?.maxByOrNull { it.lastModified() }

                            if (latestFile == null) {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(strNoBackup)
                                }
                                return@Button
                            }

                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/octet-stream"
                                putExtra(Intent.EXTRA_STREAM, latestFile.uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, null))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.settings_btn_share))
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Reusable section card composable
// ---------------------------------------------------------------------------

@Composable
private fun SectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}
