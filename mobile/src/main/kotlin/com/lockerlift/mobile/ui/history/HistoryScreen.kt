package com.lockerlift.mobile.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.lockerlift.core.database.entity.*
import com.lockerlift.core.database.logic.WorkoutTrackingLogic
import com.lockerlift.core.database.model.WorkoutSessionWithDetails
import com.lockerlift.core.healthconnect.HealthConnectManager
import com.lockerlift.core.model.*
import com.lockerlift.core.sync.*
import com.lockerlift.mobile.LockerLiftMobileApp
import com.lockerlift.mobile.R
import kotlinx.coroutines.launch
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import com.lockerlift.mobile.sync.MobileMasterDataSync
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(app: LockerLiftMobileApp) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sessionDao = remember { app.database.workoutSessionDao() }
    val machineDao = remember { app.database.machineDao() }
    val syncQueueDao = remember { app.database.syncQueueDao() }
    val healthConnectManager = remember { HealthConnectManager(context) }
    val dataLayerManager = remember { WearableDataLayerManager(context) }
    val syncCoordinator = remember { SyncPullRefreshCoordinator(dataLayerManager) }

    val sessionsState by sessionDao.getAllSessionsWithDetailsFlow().collectAsState(initial = emptyList())
    val allMachinesState by machineDao.getAllMachinesFlow().collectAsState(initial = emptyList())
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }

    var sessionToDelete by remember { mutableStateOf<WorkoutSessionWithDetails?>(null) }
    var editingSession by remember { mutableStateOf<WorkoutSessionWithDetails?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    fun triggerRefresh() {
        if (isRefreshing) return
        isRefreshing = true
        coroutineScope.launch {
            val result = syncCoordinator.executeSync(
                syncMasterDataAction = {
                    MobileMasterDataSync.pushAllMasterData(app.database, dataLayerManager)
                }
            )
            isRefreshing = false
            when (result) {
                is PullRefreshSyncResult.Success -> {
                    val msg = if (result.itemsSyncedCount == 1) {
                        context.getString(R.string.sync_success_single)
                    } else {
                        context.getString(R.string.sync_success_format, result.itemsSyncedCount)
                    }
                    snackbarHostState.showSnackbar(msg)
                }
                is PullRefreshSyncResult.UpToDate -> {
                    snackbarHostState.showSnackbar(context.getString(R.string.sync_up_to_date))
                }
                is PullRefreshSyncResult.WatchUnreachable -> {
                    snackbarHostState.showSnackbar(context.getString(R.string.sync_watch_unreachable))
                }
                is PullRefreshSyncResult.Error -> {
                    snackbarHostState.showSnackbar(context.getString(R.string.sync_error_format, result.message))
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.history_title)) })
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { triggerRefresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (sessionsState.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.history_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                items(sessionsState, key = { it.session.id }) { item ->
                    val session = item.session.toDomainModel()
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.template?.name ?: stringResource(R.string.workout_free),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = dateFormat.format(Date(session.startTime)),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Badge(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                                        Text(
                                            text = session.originDevice,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    IconButton(onClick = { editingSession = item }) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = stringResource(R.string.history_edit_workout),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    IconButton(onClick = { sessionToDelete = item }) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = stringResource(R.string.history_delete_workout),
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }

                            session.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = notes,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontStyle = FontStyle.Italic,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Divider()
                            Spacer(modifier = Modifier.height(8.dp))

                            val noSetsText = stringResource(R.string.history_no_sets)
                            item.machineInstances.forEach { instanceWithDetails ->
                                val machineName = instanceWithDetails.machine.name
                                val sets = instanceWithDetails.sets.sortedBy { it.setNumber }
                                val setsSummary = sets.joinToString(", ") {
                                    "${WorkoutTrackingLogic.formatWeight(it.weightKg)}kg × ${it.reps}"
                                }

                                Text(
                                    text = "• $machineName: ${if (setsSummary.isNotBlank()) setsSummary else noSetsText}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // --- DELETE CONFIRMATION DIALOG (AK 2.5, AK 2.8) ---
    sessionToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { sessionToDelete = null },
            title = { Text(stringResource(R.string.history_delete_dialog_title)) },
            text = { Text(stringResource(R.string.history_delete_dialog_confirm)) },
            confirmButton = {
                Button(
                    onClick = {
                        val sessionId = target.session.id
                        coroutineScope.launch {
                            // 1. Delete session from Room (cascades to instances and sets)
                            sessionDao.deleteSession(sessionId)

                            // 2. Remove pending sync queue item
                            syncQueueDao.deleteQueueItemBySessionId(sessionId)

                            // 3. Queue deletion sync to Wear OS companion
                            syncQueueDao.insertQueueItem(
                                SyncQueueEntity(
                                    id = UUID.randomUUID().toString(),
                                    sessionId = sessionId,
                                    payloadJson = SyncConstants.ACTION_DELETE
                                )
                            )
                            val syncRequest = OneTimeWorkRequestBuilder<SyncQueueWorker>().build()
                            WorkManager.getInstance(context).enqueue(syncRequest)

                            // Attempt immediate direct delete transmission if node connected
                            val connectedNodes = dataLayerManager.getConnectedNodes()
                            if (connectedNodes.isNotEmpty()) {
                                dataLayerManager.sendWorkoutDelete(connectedNodes.first().id, sessionId)
                            }

                            // 4. Delete Health Connect record if previously exported (AK 2.8)
                            if (healthConnectManager.isAvailable() && healthConnectManager.hasPermissions()) {
                                healthConnectManager.deleteWorkoutSession(sessionId)
                            }

                            sessionToDelete = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.btn_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionToDelete = null }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    // --- EDIT WORKOUT SESSION DIALOG (AK 2.1 - AK 2.4, AK 2.6, AK 2.7) ---
    editingSession?.let { targetSession ->
        EditWorkoutSessionDialog(
            sessionWithDetails = targetSession,
            availableMachines = allMachinesState.map { it.toDomainModel() },
            onDismiss = { editingSession = null },
            onSave = { updatedSessionEntity, finalInstances, finalSets, payload ->
                coroutineScope.launch {
                    // 1. Persist updated graph to Room DB
                    sessionDao.upsertFullSession(
                        session = updatedSessionEntity,
                        instances = finalInstances,
                        sets = finalSets
                    )

                    // 2. Queue updated session sync to Wear OS companion
                    val payloadJson = SyncPayloadSerializer.encodeSessionPayload(payload)
                    syncQueueDao.deleteQueueItemBySessionId(updatedSessionEntity.id)
                    syncQueueDao.insertQueueItem(
                        SyncQueueEntity(
                            id = UUID.randomUUID().toString(),
                            sessionId = updatedSessionEntity.id,
                            payloadJson = payloadJson
                        )
                    )
                    val syncRequest = OneTimeWorkRequestBuilder<SyncQueueWorker>().build()
                    WorkManager.getInstance(context).enqueue(syncRequest)

                    // Immediate stream attempt if connected
                    val connectedNodes = dataLayerManager.getConnectedNodes()
                    if (connectedNodes.isNotEmpty()) {
                        dataLayerManager.sendWorkoutPayloadViaChannel(connectedNodes.first().id, payloadJson)
                    }

                    // 3. Update Health Connect if available
                    if (healthConnectManager.isAvailable() && healthConnectManager.hasPermissions()) {
                        healthConnectManager.exportWorkoutSession(
                            session = updatedSessionEntity.toDomainModel(),
                            templateName = targetSession.template?.name
                        )
                    }

                    editingSession = null
                }
            }
        )
    }
}

// Local helper data classes for editing state
private data class EditableSet(
    val id: String,
    val sessionMachineId: String,
    val setNumber: Int,
    val weightText: String,
    val repsText: String,
    val setType: SetType = SetType.NORMAL,
    val cadence: String? = null,
    val completedAt: Long = System.currentTimeMillis()
)

private data class EditableMachineInstance(
    val id: String,
    val sessionId: String,
    val machine: MachineEntity,
    val executionOrder: Int,
    val isSkipped: Boolean = false,
    val customSettingsNote: String? = null,
    val sets: List<EditableSet> = emptyList()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditWorkoutSessionDialog(
    sessionWithDetails: WorkoutSessionWithDetails,
    availableMachines: List<Machine>,
    onDismiss: () -> Unit,
    onSave: (
        session: WorkoutSessionEntity,
        instances: List<SessionMachineInstanceEntity>,
        sets: List<WorkoutSetEntity>,
        payload: WorkoutSessionPayload
    ) -> Unit
) {
    var notesText by remember { mutableStateOf(sessionWithDetails.session.notes ?: "") }
    var instances by remember {
        mutableStateOf(
            sessionWithDetails.machineInstances.map { instWithDetails ->
                EditableMachineInstance(
                    id = instWithDetails.instance.id,
                    sessionId = instWithDetails.instance.sessionId,
                    machine = instWithDetails.machine,
                    executionOrder = instWithDetails.instance.executionOrder,
                    isSkipped = instWithDetails.instance.isSkipped,
                    customSettingsNote = instWithDetails.instance.customSettingsNote,
                    sets = instWithDetails.sets.sortedBy { it.setNumber }.map { s ->
                        EditableSet(
                            id = s.id,
                            sessionMachineId = s.sessionMachineId,
                            setNumber = s.setNumber,
                            weightText = WorkoutTrackingLogic.formatWeight(s.weightKg),
                            repsText = s.reps.toString(),
                            setType = s.setType,
                            cadence = s.cadence,
                            completedAt = s.completedAt
                        )
                    }
                )
            }
        )
    }

    var instanceToRemove by remember { mutableStateOf<EditableMachineInstance?>(null) }
    var showAddExercisePicker by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.history_edit_title)) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.btn_cancel))
                        }
                    },
                    actions = {
                        Button(
                            onClick = {
                                // Clamp and sanitize all values upon saving
                                val sanitizedInstances = instances.mapIndexed { instIdx, inst ->
                                    inst.copy(
                                        executionOrder = instIdx,
                                        sets = inst.sets.mapIndexed { setIdx, s ->
                                            val rawW = s.weightText.toFloatOrNull() ?: 0f
                                            val clampedW = WorkoutTrackingLogic.clampWeight(rawW)
                                            val rawR = s.repsText.toIntOrNull() ?: 1
                                            val clampedR = WorkoutTrackingLogic.clampReps(rawR)
                                            s.copy(
                                                setNumber = setIdx + 1,
                                                weightText = WorkoutTrackingLogic.formatWeight(clampedW),
                                                repsText = clampedR.toString()
                                            )
                                        }
                                    )
                                }

                                val updatedSessionEntity = sessionWithDetails.session.copy(
                                    syncStatus = SyncStatus.PENDING_SYNC,
                                    notes = notesText.trim().ifEmpty { null }
                                )

                                val instanceEntities = sanitizedInstances.map { inst ->
                                    SessionMachineInstanceEntity(
                                        id = inst.id,
                                        sessionId = updatedSessionEntity.id,
                                        machineId = inst.machine.id,
                                        executionOrder = inst.executionOrder,
                                        isSkipped = inst.isSkipped,
                                        customSettingsNote = inst.customSettingsNote
                                    )
                                }

                                val setEntities = sanitizedInstances.flatMap { inst ->
                                    inst.sets.map { s ->
                                        WorkoutSetEntity(
                                            id = s.id,
                                            sessionMachineId = inst.id,
                                            setNumber = s.setNumber,
                                            reps = WorkoutTrackingLogic.clampReps(s.repsText.toIntOrNull() ?: 1),
                                            weightKg = WorkoutTrackingLogic.clampWeight(s.weightText.toFloatOrNull() ?: 0f),
                                            cadence = s.cadence,
                                            setType = s.setType,
                                            completedAt = s.completedAt
                                        )
                                    }
                                }

                                val payload = WorkoutSessionPayload(
                                    session = updatedSessionEntity.toDomainModel(),
                                    templateName = sessionWithDetails.template?.name,
                                    machineInstances = sanitizedInstances.map { inst ->
                                        SessionMachineInstancePayload(
                                            instance = SessionMachineInstance(
                                                id = inst.id,
                                                sessionId = updatedSessionEntity.id,
                                                machineId = inst.machine.id,
                                                executionOrder = inst.executionOrder,
                                                isSkipped = inst.isSkipped,
                                                customSettingsNote = inst.customSettingsNote
                                            ),
                                            machine = inst.machine.toDomainModel(),
                                            sets = inst.sets.map { s ->
                                                WorkoutSet(
                                                    id = s.id,
                                                    sessionMachineId = inst.id,
                                                    setNumber = s.setNumber,
                                                    reps = WorkoutTrackingLogic.clampReps(s.repsText.toIntOrNull() ?: 1),
                                                    weightKg = WorkoutTrackingLogic.clampWeight(s.weightText.toFloatOrNull() ?: 0f),
                                                    cadence = s.cadence,
                                                    setType = s.setType,
                                                    completedAt = s.completedAt
                                                )
                                            }
                                        )
                                    }
                                )

                                onSave(updatedSessionEntity, instanceEntities, setEntities, payload)
                            }
                        ) {
                            Text(stringResource(R.string.history_save_changes))
                        }
                    }
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Workout Notes input
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = notesText,
                        onValueChange = { notesText = it },
                        label = { Text(stringResource(R.string.history_notes)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Exercise Instances
                itemsIndexed(instances, key = { _, inst -> inst.id }) { instIndex, inst ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            // Exercise Header with Remove button (AK 2.4)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${instIndex + 1}. ${inst.machine.name}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (inst.machine.targetMuscleGroup.isNotBlank()) {
                                        Text(
                                            text = inst.machine.targetMuscleGroup,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                IconButton(onClick = { instanceToRemove = inst }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.history_remove_exercise),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Sets for this machine instance
                            inst.sets.forEach { currentSet ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        // Set Number badge
                                        Text(
                                            text = stringResource(R.string.history_set_format, currentSet.setNumber),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.width(52.dp)
                                        )

                                        // Weight field + Steppers (AK 2.2)
                                        Row(
                                            modifier = Modifier.weight(1.1f),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            IconButton(
                                                onClick = {
                                                    val curr = currentSet.weightText.toFloatOrNull() ?: 0f
                                                    val step = inst.machine.defaultIncrementKg.takeIf { it > 0f } ?: 2.5f
                                                    val updatedW = WorkoutTrackingLogic.clampWeight(curr - step)
                                                    instances = updateSetWeight(instances, inst.id, currentSet.id, WorkoutTrackingLogic.formatWeight(updatedW))
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Text("-", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                            }
                                            OutlinedTextField(
                                                value = currentSet.weightText,
                                                onValueChange = { newW ->
                                                    instances = updateSetWeight(instances, inst.id, currentSet.id, newW)
                                                },
                                                label = { Text("kg", fontSize = 10.sp) },
                                                singleLine = true,
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                modifier = Modifier.weight(1f),
                                                textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center)
                                            )
                                            IconButton(
                                                onClick = {
                                                    val curr = currentSet.weightText.toFloatOrNull() ?: 0f
                                                    val step = inst.machine.defaultIncrementKg.takeIf { it > 0f } ?: 2.5f
                                                    val updatedW = WorkoutTrackingLogic.clampWeight(curr + step)
                                                    instances = updateSetWeight(instances, inst.id, currentSet.id, WorkoutTrackingLogic.formatWeight(updatedW))
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Text("+", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                            }
                                        }

                                        // Reps field + Steppers (AK 2.2)
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            IconButton(
                                                onClick = {
                                                    val curr = currentSet.repsText.toIntOrNull() ?: 1
                                                    val updatedR = WorkoutTrackingLogic.clampReps(curr - 1)
                                                    instances = updateSetReps(instances, inst.id, currentSet.id, updatedR.toString())
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Text("-", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                            }
                                            OutlinedTextField(
                                                value = currentSet.repsText,
                                                onValueChange = { newR ->
                                                    instances = updateSetReps(instances, inst.id, currentSet.id, newR)
                                                },
                                                label = { Text(stringResource(R.string.history_reps), fontSize = 9.sp) },
                                                singleLine = true,
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                modifier = Modifier.weight(1f),
                                                textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center)
                                            )
                                            IconButton(
                                                onClick = {
                                                    val curr = currentSet.repsText.toIntOrNull() ?: 1
                                                    val updatedR = WorkoutTrackingLogic.clampReps(curr + 1)
                                                    instances = updateSetReps(instances, inst.id, currentSet.id, updatedR.toString())
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Text("+", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                            }
                                        }

                                        // Delete Set button (AK 2.3)
                                        IconButton(
                                            onClick = {
                                                instances = deleteSetFromInstance(instances, inst.id, currentSet.id)
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = stringResource(R.string.history_delete_set),
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Add Set to this exercise (AK 2.3)
                            OutlinedButton(
                                onClick = {
                                    instances = addSetToInstance(instances, inst.id)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.history_add_set))
                            }
                        }
                    }
                }

                // Button to add an exercise from catalog
                item {
                    OutlinedButton(
                        onClick = { showAddExercisePicker = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.btn_add_exercise))
                    }
                }
            }
        }

        // Remove Exercise confirmation dialog (AK 2.4)
        instanceToRemove?.let { targetInst ->
            AlertDialog(
                onDismissRequest = { instanceToRemove = null },
                title = { Text(stringResource(R.string.history_remove_exercise)) },
                text = { Text(stringResource(R.string.history_remove_exercise_confirm)) },
                confirmButton = {
                    Button(
                        onClick = {
                            instances = instances.filter { it.id != targetInst.id }
                            instanceToRemove = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(stringResource(R.string.btn_delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { instanceToRemove = null }) {
                        Text(stringResource(R.string.btn_cancel))
                    }
                }
            )
        }

        // Add Exercise from Catalog picker
        if (showAddExercisePicker) {
            AlertDialog(
                onDismissRequest = { showAddExercisePicker = false },
                title = { Text(stringResource(R.string.dialog_select_machine_title)) },
                text = {
                    if (availableMachines.isEmpty()) {
                        Text(stringResource(R.string.no_machines_in_catalog))
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(availableMachines, key = { it.id }) { machine ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = {
                                        val newInstId = UUID.randomUUID().toString()
                                        val newInstance = EditableMachineInstance(
                                            id = newInstId,
                                            sessionId = sessionWithDetails.session.id,
                                            machine = machine.toEntity(),
                                            executionOrder = instances.size,
                                            sets = listOf(
                                                EditableSet(
                                                    id = UUID.randomUUID().toString(),
                                                    sessionMachineId = newInstId,
                                                    setNumber = 1,
                                                    weightText = WorkoutTrackingLogic.formatWeight(60f),
                                                    repsText = "10"
                                                )
                                            )
                                        )
                                        instances = instances + newInstance
                                        showAddExercisePicker = false
                                    }
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(text = machine.name, fontWeight = FontWeight.Bold)
                                        Text(
                                            text = machine.targetMuscleGroup,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showAddExercisePicker = false }) {
                        Text(stringResource(R.string.btn_cancel))
                    }
                }
            )
        }
    }
}

// Pure helper functions for updating list state
private fun updateSetWeight(
    instances: List<EditableMachineInstance>,
    instanceId: String,
    setId: String,
    newWeightText: String
): List<EditableMachineInstance> {
    return instances.map { inst ->
        if (inst.id == instanceId) {
            inst.copy(
                sets = inst.sets.map { s ->
                    if (s.id == setId) s.copy(weightText = newWeightText) else s
                }
            )
        } else {
            inst
        }
    }
}

private fun updateSetReps(
    instances: List<EditableMachineInstance>,
    instanceId: String,
    setId: String,
    newRepsText: String
): List<EditableMachineInstance> {
    return instances.map { inst ->
        if (inst.id == instanceId) {
            inst.copy(
                sets = inst.sets.map { s ->
                    if (s.id == setId) s.copy(repsText = newRepsText) else s
                }
            )
        } else {
            inst
        }
    }
}

private fun deleteSetFromInstance(
    instances: List<EditableMachineInstance>,
    instanceId: String,
    setId: String
): List<EditableMachineInstance> {
    return instances.map { inst ->
        if (inst.id == instanceId) {
            val remainingSets = inst.sets.filter { it.id != setId }
            inst.copy(
                sets = remainingSets.mapIndexed { index, s ->
                    s.copy(setNumber = index + 1)
                }
            )
        } else {
            inst
        }
    }
}

private fun addSetToInstance(
    instances: List<EditableMachineInstance>,
    instanceId: String
): List<EditableMachineInstance> {
    return instances.map { inst ->
        if (inst.id == instanceId) {
            val lastSet = inst.sets.lastOrNull()
            val newWeightText = lastSet?.weightText ?: "60"
            val newRepsText = lastSet?.repsText ?: "10"
            val newSet = EditableSet(
                id = UUID.randomUUID().toString(),
                sessionMachineId = inst.id,
                setNumber = inst.sets.size + 1,
                weightText = newWeightText,
                repsText = newRepsText
            )
            inst.copy(sets = inst.sets + newSet)
        } else {
            inst
        }
    }
}
