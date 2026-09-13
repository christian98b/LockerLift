package com.lockerlift.wear.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.*
import com.lockerlift.core.database.LockerLiftDatabase
import com.lockerlift.core.database.entity.SyncQueueEntity
import com.lockerlift.core.database.entity.toDomainModel
import com.lockerlift.core.database.entity.toEntity
import com.lockerlift.core.model.*
import com.lockerlift.core.sync.SessionMachineInstancePayload
import com.lockerlift.core.sync.SyncPayloadSerializer
import com.lockerlift.core.sync.WorkoutSessionPayload
import com.lockerlift.wear.LockerLiftWearApp
import com.lockerlift.wear.tracking.WorkoutForegroundService
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun ActiveWorkoutScreen(
    app: LockerLiftWearApp,
    templateId: String?,
    templateName: String?,
    onFinishWorkout: () -> Unit
) {
    val database = app.database
    val machineDao = remember { database.machineDao() }
    val sessionDao = remember { database.workoutSessionDao() }
    val syncQueueDao = remember { database.syncQueueDao() }
    val coroutineScope = rememberCoroutineScope()

    val currentSessionId = remember { UUID.randomUUID().toString() }
    val initialMachines = remember { mutableStateListOf<Machine>() }
    val sessionInstances = remember { mutableStateListOf<SessionMachineInstance>() }
    val loggedSets = remember { mutableStateMapOf<String, MutableList<WorkoutSet>>() }

    var selectedInstanceIndex by remember { mutableIntStateOf(-1) }
    var showRestTimer by remember { mutableStateOf(false) }
    var showConsolidationDialog by remember { mutableStateOf(false) }
    var isWorkoutStarted by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        WorkoutForegroundService.startService(app)
        // Load initial machines from template if provided
        if (templateId != null) {
            val templateWithMachines = database.workoutTemplateDao().getTemplateWithMachinesById(templateId)
            templateWithMachines?.machines?.forEachIndexed { index, entity ->
                val machine = entity.toDomainModel()
                initialMachines.add(machine)
                val instance = SessionMachineInstance(
                    sessionId = currentSessionId,
                    machineId = machine.id,
                    executionOrder = index,
                    customSettingsNote = machine.machineSettingsNote
                )
                sessionInstances.add(instance)
                loggedSets[instance.id] = mutableListOf()
            }
        }
        isWorkoutStarted = true
    }

    if (showRestTimer) {
        RestTimerScreen(initialSeconds = 90) {
            showRestTimer = false
        }
        return
    }

    val defaultExerciseName = androidx.compose.ui.res.stringResource(com.lockerlift.wear.R.string.exercise_default_name)
    val defaultStationName = androidx.compose.ui.res.stringResource(com.lockerlift.wear.R.string.station_default_name)
    val fullBodyMuscleGroup = androidx.compose.ui.res.stringResource(com.lockerlift.wear.R.string.full_body_muscle_group)
    val freeWorkoutTitle = androidx.compose.ui.res.stringResource(com.lockerlift.wear.R.string.workout_free)

    if (selectedInstanceIndex >= 0 && selectedInstanceIndex < sessionInstances.size) {
        val currentInstance = sessionInstances[selectedInstanceIndex]
        val machine = initialMachines.find { it.id == currentInstance.machineId }
            ?: Machine(id = currentInstance.machineId, name = defaultExerciseName, targetMuscleGroup = "")
        val currentSets = loggedSets.getOrPut(currentInstance.id) { mutableListOf() }
        val nextSetNumber = currentSets.size + 1
        val lastSet = currentSets.lastOrNull()

        RepsWeightInputScreen(
            machine = machine,
            setNumber = nextSetNumber,
            lastWeight = lastSet?.weightKg ?: 60f,
            lastReps = lastSet?.reps ?: 10,
            onSaveSet = { newSet ->
                val setWithCorrectId = newSet.copy(sessionMachineId = currentInstance.id)
                currentSets.add(setWithCorrectId)
                selectedInstanceIndex = -1
                showRestTimer = true
            },
            onCancel = {
                selectedInstanceIndex = -1
            }
        )
        return
    }

    val listState = rememberScalingLazyListState()

    ScalingLazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Text(
                text = templateName ?: freeWorkoutTitle,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        itemsIndexed(sessionInstances) { index, instance ->
            val machine = initialMachines.find { it.id == instance.machineId }
            val sets = loggedSets[instance.id] ?: emptyList()

            Card(
                onClick = { selectedInstanceIndex = index },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = if (instance.isSkipped) {
                    CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                } else {
                    CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                }
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(
                        text = "${index + 1}. ${machine?.name ?: defaultStationName}",
                        style = MaterialTheme.typography.titleSmall
                    )
                    if (!machine?.machineSettingsNote.isNullOrBlank()) {
                        Text(
                            text = androidx.compose.ui.res.stringResource(
                                com.lockerlift.wear.R.string.setup_format,
                                machine?.machineSettingsNote!!
                            ),
                            style = MaterialTheme.typography.bodyExtraSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    Text(
                        text = if (instance.isSkipped) {
                            androidx.compose.ui.res.stringResource(com.lockerlift.wear.R.string.skipped_status)
                        } else {
                            androidx.compose.ui.res.stringResource(
                                com.lockerlift.wear.R.string.sets_completed_format,
                                sets.size
                            )
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        item {
            Button(
                onClick = {
                    // Ad-hoc Machine addition (Cold-Start US 3.2)
                    coroutineScope.launch {
                        val adHocMachine = Machine(
                            name = "Station ${sessionInstances.size + 1}",
                            targetMuscleGroup = fullBodyMuscleGroup
                        )
                        machineDao.insertMachine(adHocMachine.toEntity())
                        initialMachines.add(adHocMachine)
                        val instance = SessionMachineInstance(
                            sessionId = currentSessionId,
                            machineId = adHocMachine.id,
                            executionOrder = sessionInstances.size
                        )
                        sessionInstances.add(instance)
                        loggedSets[instance.id] = mutableListOf()
                    }
                },
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                Text(androidx.compose.ui.res.stringResource(com.lockerlift.wear.R.string.add_exercise_button))
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    val isModified = templateId != null && sessionInstances.size != initialMachines.size
                    if (isModified) {
                        showConsolidationDialog = true
                    } else {
                        finishAndSaveWorkout(
                            app = app,
                            sessionId = currentSessionId,
                            templateId = templateId,
                            templateName = templateName,
                            sessionInstances = sessionInstances,
                            loggedSets = loggedSets,
                            onFinish = onFinishWorkout
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                Text(androidx.compose.ui.res.stringResource(com.lockerlift.wear.R.string.finish_workout_button))
            }
        }
    }
}

private fun finishAndSaveWorkout(
    app: LockerLiftWearApp,
    sessionId: String,
    templateId: String?,
    templateName: String?,
    sessionInstances: List<SessionMachineInstance>,
    loggedSets: Map<String, List<WorkoutSet>>,
    onFinish: () -> Unit
) {
    val database = app.database
    val session = WorkoutSession(
        id = sessionId,
        templateId = templateId,
        startTime = System.currentTimeMillis() - 3600000L,
        endTime = System.currentTimeMillis(),
        originDevice = "WEAR_OS",
        syncStatus = SyncStatus.PENDING_SYNC
    )

    val allSets = loggedSets.values.flatten()

    val payload = WorkoutSessionPayload(
        session = session,
        templateName = templateName,
        machineInstances = sessionInstances.map { inst ->
            SessionMachineInstancePayload(
                instance = inst,
                machine = Machine(id = inst.machineId, name = "Station", targetMuscleGroup = "General"),
                sets = loggedSets[inst.id] ?: emptyList()
            )
        }
    )

    val payloadJson = SyncPayloadSerializer.encodeSessionPayload(payload)
    val syncItem = SyncQueueEntity(
        id = UUID.randomUUID().toString(),
        sessionId = sessionId,
        payloadJson = payloadJson,
        status = QueueStatus.PENDING
    )

    kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
        database.workoutSessionDao().upsertFullSession(
            session = session.toEntity(),
            instances = sessionInstances.map { it.toEntity() },
            sets = allSets.map { it.toEntity() }
        )
        database.syncQueueDao().insertQueueItem(syncItem)
        WorkoutForegroundService.stopService(app)
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
            onFinish()
        }
    }
}
