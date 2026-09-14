package com.lockerlift.wear.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.*
import com.lockerlift.core.database.entity.SyncQueueEntity
import com.lockerlift.core.database.entity.WorkoutSetEntity
import com.lockerlift.core.database.entity.toDomainModel
import com.lockerlift.core.database.entity.toEntity
import com.lockerlift.core.model.*
import com.lockerlift.core.sync.SessionMachineInstancePayload
import com.lockerlift.core.sync.SyncPayloadSerializer
import com.lockerlift.core.sync.WorkoutSessionPayload
import com.lockerlift.wear.LockerLiftWearApp
import com.lockerlift.wear.R
import com.lockerlift.core.database.logic.CreateMachineMode
import com.lockerlift.core.database.logic.ValidationResult
import com.lockerlift.wear.logic.WearWorkoutLogic
import com.lockerlift.wear.tracking.WorkoutForegroundService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

private enum class ActiveWorkoutSubScreen {
    OVERVIEW,
    ADD_MACHINE_PICKER,
    REPLACE_MACHINE_PICKER,
    CREATE_MACHINE,
    EDIT_NOTE,
    CONSOLIDATION_CONFIRM
}

@Composable
fun ActiveWorkoutScreen(
    app: LockerLiftWearApp,
    templateId: String?,
    templateName: String?,
    onFinishWorkout: () -> Unit
) {
    val context = LocalContext.current
    val database = app.database
    val machineDao = remember { database.machineDao() }
    val sessionDao = remember { database.workoutSessionDao() }
    val templateDao = remember { database.workoutTemplateDao() }
    val syncQueueDao = remember { database.syncQueueDao() }
    val coroutineScope = rememberCoroutineScope()

    val currentSessionId = remember { UUID.randomUUID().toString() }
    val initialMachines = remember { mutableStateListOf<Machine>() }
    val sessionInstances = remember { mutableStateListOf<SessionMachineInstance>() }
    val loggedSets = remember { mutableStateMapOf<String, MutableList<WorkoutSet>>() }

    val catalogFlow = remember { machineDao.getAllMachinesFlow() }
    val catalogMachines by catalogFlow.collectAsState(initial = emptyList())

    var selectedInstanceIndex by remember { mutableIntStateOf(-1) }
    var targetInstanceIndex by remember { mutableIntStateOf(-1) }
    var showRestTimer by remember { mutableStateOf(false) }
    var currentSubScreen by remember { mutableStateOf(ActiveWorkoutSubScreen.OVERVIEW) }
    var createMachineMode by remember { mutableStateOf(CreateMachineMode.ADD_NEW) }

    LaunchedEffect(Unit) {
        WorkoutForegroundService.startService(context)
        // Load initial machines from template if provided
        if (templateId != null) {
            val orderedEntities = templateDao.getMachinesForTemplateOrdered(templateId)
            val machines = orderedEntities.map { it.toDomainModel() }
            initialMachines.addAll(machines)
            val instances = WearWorkoutLogic.initializeSessionInstances(machines, currentSessionId)
            sessionInstances.addAll(instances)
            instances.forEach { instance ->
                loggedSets[instance.id] = mutableListOf()
            }
        }
    }

    if (showRestTimer) {
        RestTimerScreen(initialSeconds = 90) {
            showRestTimer = false
        }
        return
    }

    val defaultExerciseName = stringResource(R.string.exercise_default_name)
    val defaultStationName = stringResource(R.string.station_default_name)
    val fullBodyMuscleGroup = stringResource(R.string.full_body_muscle_group)
    val freeWorkoutTitle = stringResource(R.string.workout_free)

    // Set Logging Screen (US 4.1, AK 4.1.1)
    if (selectedInstanceIndex >= 0 && selectedInstanceIndex < sessionInstances.size) {
        val currentInstance = sessionInstances[selectedInstanceIndex]
        val machine = catalogMachines.find { it.id == currentInstance.machineId }?.toDomainModel()
            ?: initialMachines.find { it.id == currentInstance.machineId }
            ?: Machine(id = currentInstance.machineId, name = defaultExerciseName, targetMuscleGroup = "")
        val currentSets = loggedSets.getOrPut(currentInstance.id) { mutableListOf() }
        val nextSetNumber = currentSets.size + 1

        var historicalSets by remember(currentInstance.id) { mutableStateOf<List<WorkoutSetEntity>>(emptyList()) }

        LaunchedEffect(currentInstance.id) {
            historicalSets = sessionDao.getLastCompletedSetsForMachine(machine.id)
        }

        val historicalPerformanceText = remember(historicalSets) {
            val setsString = WearWorkoutLogic.formatHistoricalSetsString(historicalSets)
            if (setsString != null) {
                context.getString(R.string.historical_performance_format, setsString)
            } else {
                null
            }
        }

        val (prefillWeight, prefillReps) = remember(historicalSets, currentSets.size) {
            WearWorkoutLogic.determinePrefillWeightAndReps(
                currentSets = currentSets,
                historicalSets = historicalSets,
                defaultWeight = 60f,
                defaultReps = 10
            )
        }

        RepsWeightInputScreen(
            machine = machine,
            setNumber = nextSetNumber,
            lastWeight = prefillWeight,
            lastReps = prefillReps,
            cadence = machine.defaultCadence,
            historicalPerformanceText = historicalPerformanceText,
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

    // Subscreen: Add Machine Picker (US 3.2, AK 3.2.2, AK 3.2.3)
    if (currentSubScreen == ActiveWorkoutSubScreen.ADD_MACHINE_PICKER) {
        val listState = rememberScalingLazyListState()
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Text(
                    text = stringResource(R.string.dialog_select_machine),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            item {
                Button(
                    onClick = {
                        createMachineMode = CreateMachineMode.ADD_NEW
                        currentSubScreen = ActiveWorkoutSubScreen.CREATE_MACHINE
                    },
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    Text(stringResource(R.string.btn_new_machine))
                }
            }
            items(catalogMachines, key = { it.id }) { machineEntity ->
                val machine = machineEntity.toDomainModel()
                Card(
                    onClick = {
                        val newInstance = WearWorkoutLogic.appendMachineToSession(
                            instances = sessionInstances,
                            sessionId = currentSessionId,
                            machine = machine
                        )
                        sessionInstances.add(newInstance)
                        if (!initialMachines.any { it.id == machine.id }) {
                            initialMachines.add(machine)
                        }
                        loggedSets[newInstance.id] = mutableListOf()
                        currentSubScreen = ActiveWorkoutSubScreen.OVERVIEW
                    },
                    modifier = Modifier.fillMaxWidth(0.9f).padding(vertical = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(6.dp)) {
                        Text(text = machine.name, style = MaterialTheme.typography.titleSmall)
                        if (!machine.targetMuscleGroup.isBlank()) {
                            Text(
                                text = machine.targetMuscleGroup,
                                style = MaterialTheme.typography.bodyExtraSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            item {
                CompactButton(
                    onClick = { currentSubScreen = ActiveWorkoutSubScreen.OVERVIEW },
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        }
        return
    }

    // Subscreen: Replace Machine Picker (US 3.3, AK 3.3.2)
    if (currentSubScreen == ActiveWorkoutSubScreen.REPLACE_MACHINE_PICKER) {
        val listState = rememberScalingLazyListState()
        val currentInstance = sessionInstances.getOrNull(targetInstanceIndex)

        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Text(
                    text = stringResource(R.string.dialog_replace_machine),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            item {
                Button(
                    onClick = {
                        createMachineMode = CreateMachineMode.REPLACE_CURRENT
                        currentSubScreen = ActiveWorkoutSubScreen.CREATE_MACHINE
                    },
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    Text(stringResource(R.string.btn_new_machine))
                }
            }
            val availableMachines = catalogMachines.filter { it.id != currentInstance?.machineId }
            items(availableMachines, key = { it.id }) { machineEntity ->
                val machine = machineEntity.toDomainModel()
                Card(
                    onClick = {
                        if (targetInstanceIndex in sessionInstances.indices) {
                            val updated = WearWorkoutLogic.replaceMachineInSession(
                                instances = sessionInstances,
                                targetIndex = targetInstanceIndex,
                                newMachine = machine
                            )
                            sessionInstances.clear()
                            sessionInstances.addAll(updated)
                            if (!initialMachines.any { it.id == machine.id }) {
                                initialMachines.add(machine)
                            }
                        }
                        currentSubScreen = ActiveWorkoutSubScreen.OVERVIEW
                    },
                    modifier = Modifier.fillMaxWidth(0.9f).padding(vertical = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(6.dp)) {
                        Text(text = machine.name, style = MaterialTheme.typography.titleSmall)
                        if (!machine.targetMuscleGroup.isBlank()) {
                            Text(
                                text = machine.targetMuscleGroup,
                                style = MaterialTheme.typography.bodyExtraSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            item {
                CompactButton(
                    onClick = { currentSubScreen = ActiveWorkoutSubScreen.OVERVIEW },
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        }
        return
    }

    // Subscreen: Create New Machine Directly on Watch (US 1.1, AK 1.1.2, AK 1.1.3)
    if (currentSubScreen == ActiveWorkoutSubScreen.CREATE_MACHINE) {
        var machineName by remember { mutableStateOf("") }
        var incrementKg by remember { mutableFloatStateOf(2.5f) }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        val listState = rememberScalingLazyListState()

        val quickPresets = listOf(
            "Chest Press", "Shoulder Press", "Lat Pulldown",
            "Cable Row", "Leg Press", "Leg Curl", "Leg Extension", "Bicep Curl"
        )

        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Text(
                    text = stringResource(R.string.title_new_machine),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Text Input Box
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    if (machineName.isEmpty()) {
                        Text(
                            text = stringResource(R.string.hint_machine_name),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    BasicTextField(
                        value = machineName,
                        onValueChange = {
                            machineName = it
                            errorMessage = null
                        },
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Quick Preset Suggestions
            item {
                Text(
                    text = "Suggestions:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            items(quickPresets) { preset ->
                CompactButton(
                    onClick = {
                        machineName = preset
                        errorMessage = null
                    },
                    modifier = Modifier.fillMaxWidth(0.85f).padding(vertical = 1.dp)
                ) {
                    Text(preset, fontSize = 12.sp)
                }
            }

            // Increment adjustment
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.hint_increment),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Button(
                        onClick = { if (incrementKg > 1.25f) incrementKg -= 1.25f },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Text("-", fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${WearWorkoutLogic.formatWeight(incrementKg)} kg",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { incrementKg += 1.25f },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Text("+", fontSize = 16.sp)
                    }
                }
            }

            if (errorMessage != null) {
                item {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyExtraSmall
                    )
                }
            }

            // Save Machine Action
            item {
                Spacer(modifier = Modifier.height(6.dp))
                Button(
                    onClick = {
                        val validation = WearWorkoutLogic.validateNewMachine(
                            name = machineName,
                            existingMachineNames = catalogMachines.map { it.name }
                        )
                        when (validation) {
                            ValidationResult.EmptyName -> {
                                errorMessage = context.getString(R.string.error_empty_name)
                            }
                            ValidationResult.DuplicateName -> {
                                errorMessage = context.getString(R.string.error_machine_exists)
                            }
                            ValidationResult.Valid -> {
                                coroutineScope.launch {
                                    val newMachine = Machine(
                                        name = machineName.trim(),
                                        targetMuscleGroup = fullBodyMuscleGroup,
                                        defaultIncrementKg = incrementKg
                                    )
                                    machineDao.insertMachine(newMachine.toEntity())
                                    if (createMachineMode == CreateMachineMode.ADD_NEW) {
                                        val newInstance = WearWorkoutLogic.appendMachineToSession(
                                            instances = sessionInstances,
                                            sessionId = currentSessionId,
                                            machine = newMachine
                                        )
                                        sessionInstances.add(newInstance)
                                        initialMachines.add(newMachine)
                                        loggedSets[newInstance.id] = mutableListOf()
                                    } else if (targetInstanceIndex in sessionInstances.indices) {
                                        val updated = WearWorkoutLogic.replaceMachineInSession(
                                            instances = sessionInstances,
                                            targetIndex = targetInstanceIndex,
                                            newMachine = newMachine
                                        )
                                        sessionInstances.clear()
                                        sessionInstances.addAll(updated)
                                        initialMachines.add(newMachine)
                                    }
                                    currentSubScreen = ActiveWorkoutSubScreen.OVERVIEW
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    Text(stringResource(R.string.btn_save_machine))
                }
            }

            item {
                CompactButton(
                    onClick = { currentSubScreen = ActiveWorkoutSubScreen.OVERVIEW },
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        }
        return
    }

    // Subscreen: Edit Setup Note (US 1.2, AK 1.2.2)
    if (currentSubScreen == ActiveWorkoutSubScreen.EDIT_NOTE) {
        val targetInstance = sessionInstances.getOrNull(targetInstanceIndex)
        val machine = catalogMachines.find { it.id == targetInstance?.machineId }?.toDomainModel()
            ?: initialMachines.find { it.id == targetInstance?.machineId }
        var noteText by remember(targetInstanceIndex) {
            mutableStateOf(targetInstance?.customSettingsNote ?: machine?.machineSettingsNote ?: "")
        }
        val listState = rememberScalingLazyListState()

        val quickNotes = listOf("Seat 1", "Seat 2", "Seat 3", "Seat 4", "Pin 3", "Pin 4", "Pin 5", "Peg 2")

        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Text(
                    text = stringResource(R.string.title_machine_setup),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            item {
                Text(
                    text = machine?.name ?: defaultStationName,
                    style = MaterialTheme.typography.bodyExtraSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Note text field
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    if (noteText.isEmpty()) {
                        Text(
                            text = stringResource(R.string.hint_setup_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    BasicTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Quick note suggestions
            items(quickNotes) { suggestion ->
                CompactButton(
                    onClick = { noteText = suggestion },
                    modifier = Modifier.fillMaxWidth(0.85f).padding(vertical = 1.dp)
                ) {
                    Text(suggestion, fontSize = 12.sp)
                }
            }

            // Action: Save for Workout only (AK 1.2.2)
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = {
                        val clean = noteText.trim().ifEmpty { null }
                        if (targetInstanceIndex in sessionInstances.indices) {
                            sessionInstances[targetInstanceIndex] = sessionInstances[targetInstanceIndex].copy(
                                customSettingsNote = clean
                            )
                        }
                        currentSubScreen = ActiveWorkoutSubScreen.OVERVIEW
                    },
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    Text(stringResource(R.string.action_save_workout_only))
                }
            }

            // Action: Save to Catalog permanently (AK 1.2.2)
            item {
                Button(
                    onClick = {
                        val clean = noteText.trim().ifEmpty { null }
                        if (targetInstanceIndex in sessionInstances.indices && targetInstance != null) {
                            sessionInstances[targetInstanceIndex] = sessionInstances[targetInstanceIndex].copy(
                                customSettingsNote = clean
                            )
                            coroutineScope.launch {
                                val entity = machineDao.getMachineById(targetInstance.machineId)
                                if (entity != null) {
                                    machineDao.updateMachine(
                                        entity.copy(
                                            machineSettingsNote = clean,
                                            updatedAt = System.currentTimeMillis()
                                        )
                                    )
                                }
                                currentSubScreen = ActiveWorkoutSubScreen.OVERVIEW
                            }
                        } else {
                            currentSubScreen = ActiveWorkoutSubScreen.OVERVIEW
                        }
                    },
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    Text(stringResource(R.string.action_save_note_to_catalog))
                }
            }

            item {
                CompactButton(
                    onClick = { currentSubScreen = ActiveWorkoutSubScreen.OVERVIEW },
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        }
        return
    }

    // Subscreen: Template Consolidation Confirmation (US 3.2, US 3.3)
    if (currentSubScreen == ActiveWorkoutSubScreen.CONSOLIDATION_CONFIRM) {
        val listState = rememberScalingLazyListState()
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Text(
                    text = stringResource(R.string.dialog_consolidation_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            item {
                Text(
                    text = stringResource(R.string.dialog_consolidation_message),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            item {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            if (templateId != null) {
                                val templateEntity = templateDao.getTemplateWithMachinesById(templateId)?.template
                                if (templateEntity != null) {
                                    val nonSkippedMachineIds = sessionInstances.filter { !it.isSkipped }.map { it.machineId }
                                    templateDao.saveTemplateWithMachines(
                                        template = templateEntity.copy(updatedAt = System.currentTimeMillis()),
                                        machineIdsInOrder = nonSkippedMachineIds
                                    )
                                }
                            }
                            finishAndSaveWorkout(
                                app = app,
                                sessionId = currentSessionId,
                                templateId = templateId,
                                templateName = templateName,
                                sessionInstances = sessionInstances,
                                loggedSets = loggedSets,
                                catalogMachines = catalogMachines.map { it.toDomainModel() },
                                initialMachines = initialMachines,
                                onFinish = onFinishWorkout
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    Text(stringResource(R.string.btn_yes))
                }
            }
            item {
                Button(
                    onClick = {
                        finishAndSaveWorkout(
                            app = app,
                            sessionId = currentSessionId,
                            templateId = templateId,
                            templateName = templateName,
                            sessionInstances = sessionInstances,
                            loggedSets = loggedSets,
                            catalogMachines = catalogMachines.map { it.toDomainModel() },
                            initialMachines = initialMachines,
                            onFinish = onFinishWorkout
                        )
                    },
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    Text(stringResource(R.string.btn_no))
                }
            }
        }
        return
    }

    // Default Overview Screen
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
            val machine = catalogMachines.find { it.id == instance.machineId }?.toDomainModel()
                ?: initialMachines.find { it.id == instance.machineId }
            val sets = loggedSets[instance.id] ?: emptyList()
            val setupNote = instance.customSettingsNote ?: machine?.machineSettingsNote

            Card(
                onClick = {
                    if (!instance.isSkipped) {
                        selectedInstanceIndex = index
                    }
                },
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
                    if (!setupNote.isNullOrBlank()) {
                        Text(
                            text = stringResource(R.string.setup_format, setupNote),
                            style = MaterialTheme.typography.bodyExtraSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    Text(
                        text = if (instance.isSkipped) {
                            stringResource(R.string.skipped_status)
                        } else {
                            stringResource(R.string.sets_completed_format, sets.size)
                        },
                        style = MaterialTheme.typography.bodySmall
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Skip station action (AK 3.3.1)
                        CompactButton(
                            onClick = {
                                val updated = WearWorkoutLogic.toggleSkipStation(sessionInstances, index)
                                sessionInstances.clear()
                                sessionInstances.addAll(updated)
                            }
                        ) {
                            Text(
                                text = if (instance.isSkipped) {
                                    stringResource(R.string.btn_unskip)
                                } else {
                                    stringResource(R.string.btn_skip_exercise)
                                },
                                fontSize = 11.sp
                            )
                        }
                        // Machine replacement action (AK 3.3.2)
                        CompactButton(
                            onClick = {
                                targetInstanceIndex = index
                                currentSubScreen = ActiveWorkoutSubScreen.REPLACE_MACHINE_PICKER
                            }
                        ) {
                            Text(stringResource(R.string.btn_replace), fontSize = 11.sp)
                        }
                        // Machine settings note update (AK 1.2.2)
                        CompactButton(
                            onClick = {
                                targetInstanceIndex = index
                                currentSubScreen = ActiveWorkoutSubScreen.EDIT_NOTE
                            }
                        ) {
                            Text(stringResource(R.string.btn_note), fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Add Machine Button (US 3.2, AK 3.2.2)
        item {
            Button(
                onClick = {
                    currentSubScreen = ActiveWorkoutSubScreen.ADD_MACHINE_PICKER
                },
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                Text(stringResource(R.string.add_exercise_button))
            }
        }

        // Finish Workout Button
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    val isModified = templateId != null && (
                        sessionInstances.size != initialMachines.size ||
                        sessionInstances.any { it.isSkipped } ||
                        sessionInstances.mapIndexed { idx, inst -> inst.machineId != initialMachines.getOrNull(idx)?.id }.any { it }
                    )
                    if (isModified) {
                        currentSubScreen = ActiveWorkoutSubScreen.CONSOLIDATION_CONFIRM
                    } else {
                        finishAndSaveWorkout(
                            app = app,
                            sessionId = currentSessionId,
                            templateId = templateId,
                            templateName = templateName,
                            sessionInstances = sessionInstances,
                            loggedSets = loggedSets,
                            catalogMachines = catalogMachines.map { it.toDomainModel() },
                            initialMachines = initialMachines,
                            onFinish = onFinishWorkout
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                Text(stringResource(R.string.finish_workout_button))
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
    catalogMachines: List<Machine>,
    initialMachines: List<Machine>,
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
            val machine = catalogMachines.find { it.id == inst.machineId }
                ?: initialMachines.find { it.id == inst.machineId }
                ?: Machine(id = inst.machineId, name = "Station", targetMuscleGroup = "General")
            SessionMachineInstancePayload(
                instance = inst,
                machine = machine,
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

    kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
        database.workoutSessionDao().upsertFullSession(
            session = session.toEntity(),
            instances = sessionInstances.map { it.toEntity() },
            sets = allSets.map { it.toEntity() }
        )
        database.syncQueueDao().insertQueueItem(syncItem)
        WorkoutForegroundService.stopService(app)
        withContext(Dispatchers.Main) {
            onFinish()
        }
    }
}
