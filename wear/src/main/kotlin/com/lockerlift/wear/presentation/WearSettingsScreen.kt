package com.lockerlift.wear.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lockerlift.core.sync.SyncUtils
import com.lockerlift.wear.LockerLiftWearApp
import com.lockerlift.wear.R
import com.lockerlift.wear.logic.WearSettingsLogic
import kotlinx.coroutines.launch

@Composable
fun WearSettingsScreen(
    app: LockerLiftWearApp,
    onNavigateBack: () -> Unit,
    onOpenLicenses: () -> Unit = {}
) {
    val context = LocalContext.current
    val settingsViewModel: WearSettingsViewModel = viewModel(
        key = "wearSettings",
        factory = viewModelFactory {
            initializer {
                WearSettingsViewModel(application = app, database = app.database)
            }
        }
    )

    val pendingQueueCount by settingsViewModel.pendingQueueCount.collectAsState()
    val companionStatus by settingsViewModel.companionStatus.collectAsState()
    val lastSyncTimestamp by settingsViewModel.lastSyncTimestamp.collectAsState()
    val isSyncing by settingsViewModel.isSyncing.collectAsState()
    var syncStatusMessage by remember { mutableStateOf<String?>(null) }

    val listState = rememberScalingLazyListState()
    val focusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()

    val strConnectedFmt = stringResource(R.string.settings_companion_connected_format)
    val strDisconnected = stringResource(R.string.settings_companion_disconnected)
    val strChecking = stringResource(R.string.settings_checking_connection)
    val strPendingFmt = stringResource(R.string.settings_queue_pending_format)
    val strQueueEmpty = stringResource(R.string.settings_queue_empty)
    val strSyncSuccessFmt = stringResource(R.string.settings_sync_success_format)
    val strSyncUpToDate = stringResource(R.string.settings_sync_up_to_date)
    val strSyncFailedUnreachable = stringResource(R.string.settings_sync_failed_unreachable)
    val strSyncFailedFmt = stringResource(R.string.settings_sync_failed_format)
    val strNever = stringResource(R.string.settings_sync_never)

    val appVersion = remember { WearSettingsLogic.getAppVersionString(context) }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        settingsViewModel.refreshStatus()
    }

    ScalingLazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .onRotaryScrollEvent {
                coroutineScope.launch {
                    listState.scrollBy(it.verticalScrollPixels)
                }
                true
            }
            .focusRequester(focusRequester)
            .focusable(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Title
        item {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        // Connection Status Card
        item {
            Card(
                onClick = {
                    settingsViewModel.refreshStatus()
                },
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .padding(vertical = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val isConn = companionStatus?.isConnected == true
                    val statusColor = if (isConn) Color(0xFF4CAF50) else Color(0xFFFF9800)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(color = statusColor, shape = CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = WearSettingsLogic.formatConnectionStatus(
                                status = companionStatus,
                                connectedFmt = strConnectedFmt,
                                disconnectedStr = strDisconnected,
                                checkingStr = strChecking
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // Sync Queue & Last Sync Card
        item {
            Card(
                onClick = {},
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .padding(vertical = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = WearSettingsLogic.formatQueueStatus(
                            pendingCount = pendingQueueCount,
                            pendingFmt = strPendingFmt,
                            allSyncedStr = strQueueEmpty
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = String.format(
                            stringResource(R.string.settings_last_sync_format),
                            SyncUtils.formatSyncTimestamp(lastSyncTimestamp, strNever)
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Sync Now Button
        item {
            Button(
                onClick = {
                    if (isSyncing) return@Button
                    syncStatusMessage = null
                    settingsViewModel.syncNow { outcome ->
                        when (outcome) {
                            is SyncOutcome.Success -> {
                                WearSettingsLogic.triggerHapticSuccess(context)
                                syncStatusMessage = if (outcome.itemsSyncedCount > 0) {
                                    String.format(strSyncSuccessFmt, outcome.itemsSyncedCount)
                                } else {
                                    strSyncUpToDate
                                }
                            }
                            SyncOutcome.NoCompanionFound -> {
                                WearSettingsLogic.triggerHapticError(context)
                                syncStatusMessage = strSyncFailedUnreachable
                            }
                            is SyncOutcome.Error -> {
                                WearSettingsLogic.triggerHapticError(context)
                                syncStatusMessage = String.format(strSyncFailedFmt, outcome.message)
                            }
                        }
                    }
                },
                enabled = !isSyncing,
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .padding(vertical = 4.dp)
            ) {
                if (isSyncing) {
                    Text(
                        text = stringResource(R.string.settings_syncing),
                        style = MaterialTheme.typography.labelMedium
                    )
                } else {
                    Text(
                        text = stringResource(R.string.settings_btn_sync_now),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }

        // Sync Status Message Banner if present
        if (syncStatusMessage != null) {
            item {
                Text(
                    text = syncStatusMessage!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }

        // Open-Source Licenses (Issue #39)
        item {
            Button(
                onClick = onOpenLicenses,
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_btn_open_licenses),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
        // App Version
        item {
            Text(
                text = String.format(stringResource(R.string.settings_version_format), appVersion),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }

        // Back Button
        item {
            Button(
                onClick = onNavigateBack,
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .padding(top = 4.dp, bottom = 12.dp)
            ) {
                Text(stringResource(R.string.btn_back))
            }
        }
    }
}
