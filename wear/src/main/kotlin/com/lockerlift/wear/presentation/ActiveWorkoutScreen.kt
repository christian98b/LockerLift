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
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.CompactButton
import androidx.wear.compose.material3.CompactChip
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lockerlift.core.database.entity.WorkoutSetEntity
import com.lockerlift.core.database.entity.toDomainModel
import com.lockerlift.core.model.*
import com.lockerlift.wear.LockerLiftWearApp
import com.lockerlift.wear.R
import com.lockerlift.wear.logic.WearPreferences
import com.lockerlift.wear.logic.WearWorkoutLogic
import com.lockerlift.wear.tracking.ActiveWorkoutViewModel
import com.lockerlift.wear.tracking.WorkoutForegroundService

private enum class ActiveWorkoutSubScreen {
    OVERVIEW,
    ADD_MACHINE_PICKER,
    REPLACE_MACHINE_PICKER,
    CREATE_MACHINE,
    EDIT_NOTE,
    CONSOLIDATION_CONFIRM,
    FINISH_CONFIRM
}

@Composable
fun ActiveWorkoutScreen(
    app: LockerLiftWearApp,
    templateId: String?,
    templateName: String?,
    onFinishWorkout: () -> Unit
) {
    val context = LocalContext.current

    val viewModel: ActiveWorkoutViewModel = viewModel(
        key = "activeWorkout",
        factory = viewModelFactory {
            initializer {
                ActiveWorkoutViewModel(
                    application = app,
                    database = app.database,
                    templateId = templateId,
                    templateName = templateName
                )
            }
        }
    )
    val state by viewModel.uiState.collectAsState()
    val catalogMachines by viewModel.catalogMachines.collectAsState()

    val sessionInstances = state.instances
    val loggedSets = state.setsByInstance
    val initialMachines = state.initialMachines
    val pauseState = state.pauseState

    var selectedInstanceIndex by remember { mutableIntStateOf(-1) }
    var targetInstanceIndex by remember { mutableIntStateOf(-1) }
    var editingInstanceIndex by remember { mutableIntStateOf(-1) }
    var editingSetIndex by remember { mutableIntStateOf(-1) }

    var lastLoggedStationIndex by remember { mutableIntStateOf(-1) }
    var lastLoggedSetNumber by remember { mutableIntStateOf(1) }
    var lastLoggedMachineName by remember { mutableStateOf<String?>(null) }

    var showRestTimer by remember { mutableStateOf(false) }
    var currentSubScreen by remember { mutableStateOf(ActiveWorkoutSubScreen.OVERVIEW) }
    var createMachineMode by remember { mutableStateOf(CreateMachineMode.ADD_NEW) }

    // Invariant 5: the active workout runs inside a foreground service
    LaunchedEffect(Unit) {
        WorkoutForegroundService.startService(context)
    }

    // Paused State Screen (AK 3.1, AK 3.2, AK 3.3)
    if (pauseState.isPaused) {
        val listState = rememberScalingLazyListState()
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            item {
                Text(
                    text = stringResource(R.string.workout_paused_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            item {
                Text(
                    text = stringResource(R.string.workout_paused_text),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            }
            item {
                Button(
                    onClick = {
                        viewModel.resumeWorkout()
                    },
                    modifier = Modifier.fillMaxWidth(0.9f).padding(vertical = 4.dp)
                ) {
                    Text(stringResource(R.string.btn_resume_workout))
                }
            }
            item {
                CompactButton(
                    onClick = {
                        viewModel.finishWorkout(onFinished = onFinishWorkout)
                    },
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(stringResource(R.string.finish_workout_button), fontSize = 11.sp)
                }
            }
        }
        return
    }

    // Rest Timer Screen (AK 3.4, AK 3.5, AK 3.8, AK 3.9, AK 3.10)
    if (showRestTimer) {
        val defaultRest = remember { WearPreferences.getDefaultRestSeconds(context) }
        RestTimerScreen(
            initialSeconds = defaultRest,
            machineName = lastLoggedMachineName,
            completedSetNumber = lastLoggedSetNumber,
            isWorkoutPaused = pauseState.isPaused,
            hasNextExercise = WearWorkoutLogic.hasNextStation(lastLoggedStationIndex, sessionInstances),
            onNextSet = {
                showRestTimer = false
                selectedInstanceIndex = lastLoggedStationIndex
            },
            onNextExercise = {
                showRestTimer = false
                val nextIdx = WearWorkoutLogic.findNextStationIndex(lastLoggedStationIndex, sessionInstances)
                selectedInstanceIndex = if (nextIdx != -1) nextIdx else -1
            },
            onSkipRest = {
                showRestTimer = false
                selectedInstanceIndex = -1
            },
            onFinishExercise = {
                showRestTimer = false
                selectedInstanceIndex = -1
            },
            onDefaultDurationChanged = { newDefault ->
                WearPreferences.setDefaultRestSeconds(context, newDefault)
            }
        )
        return
    }

    val defaultExerciseName = stringResource(R.string.exercise_default_name)
    val defaultStationName = stringResource(R.string.station_default_name)
    val fullBodyMuscleGroup = stringResource(R.string.full_body_muscle_group)
    val freeWorkoutTitle = stringResource(R.string.workout_free)

    // Edit Existing Set Screen (AK 3.11, AK 3.12)
    if (editingInstanceIndex in sessionInstances.indices && editingSetIndex >= 0) {
        val targetInstance = sessionInstances[editingInstanceIndex]
        val machine = catalogMachines.find { it.id == targetInstance.machineId }
            ?: initialMachines.find { it.id == targetInstance.machineId }
            ?: Machine(id = targetInstance.machineId, name = defaultExerciseName, targetMuscleGroup = "")
        val currentSets = loggedSets[targetInstance.id] ?: emptyList()
        val setToEdit = currentSets.getOrNull(editingSetIndex)

        if (setToEdit != null) {
            RepsWeightInputScreen(
                machine = machine,
                setNumber = setToEdit.setNumber,
                lastWeight = setToEdit.weightKg,
                lastReps = setToEdit.reps,
                cadence = setToEdit.cadence ?: machine.defaultCadence,
                historicalPerformanceText = null,
                isEditing = true,
                onSaveSet = { updatedSet ->
                    viewModel.updateSet(
                        instanceId = targetInstance.id,
                        targetIndex = editingSetIndex,
                        weightKg = updatedSet.weightKg,
                        reps = updatedSet.reps,
                        cadence = updatedSet.cadence
                    )
                    editingInstanceIndex = -1
                    editingSetIndex = -1
                },
                onDeleteSet = {
                    viewModel.deleteSet(
                        instanceId = targetInstance.id,
                        targetIndex = editingSetIndex
                    )
                    editingInstanceIndex = -1
                    editingSetIndex = -1
                },
                onCancel = {
                    editingInstanceIndex = -1
                    editingSetIndex = -1
                }
            )
            return
        } else {
            editingInstanceIndex = -1
            editingSetIndex = -1
        }
    }

    // Set Logging Screen (US 4.1, AK 4.1.1)
    if (selectedInstanceIndex >= 0 && selectedInstanceIndex < sessionInstances.size) {
        val currentInstance = sessionInstances[selectedInstanceIndex]
        val machine = catalogMachines.find { it.id == currentInstance.machineId }
            ?: initialMachines.find { it.id == currentInstance.machineId }
            ?: Machine(id = currentInstance.machineId, name = defaultExerciseName, targetMuscleGroup = "")
        val currentSets = loggedSets[currentInstance.id] ?: emptyList()
        val nextSetNumber = currentSets.size + 1

        var historicalSets by remember(currentInstance.id) { mutableStateOf<List<WorkoutSetEntity>>(emptyList()) }

        LaunchedEffect(currentInstance.id) {
            historicalSets = viewModel.getHistoricalSetsForMachine(machine.id)
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
            isEditing = false,
            onSaveSet = { newSet ->
                viewModel.logSet(
                    instanceId = currentInstance.id,
                    set = newSet.copy(sessionMachineId = currentInstance.id)
                )
                lastLoggedStationIndex = selectedInstanceIndex
                lastLoggedSetNumber = nextSetNumber
                lastLoggedMachineName = machine.name
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
            items(catalogMachines, key = { it.id }) { machine ->
                Card(
                    onClick = {
                        viewModel.appendMachine(machine)
                        currentSubScreen = ActiveWorkoutSubScreen.OVERVIEW
                    },
                    modifier = Modifier.fillMaxWidth(0.9f).padding(vertical = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(6.dp)) {
                        Text(text = machine.name, style = MaterialTheme.typography.titleSmall)
                        if (!machine.targetMuscleGroup.isBlank()) {
                            Text(
                                text = machine.targetMuscleGroup,
                                style = MaterialTheme.typography.bodySmall,
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
            items(availableMachines, key = { it.id }) { machine ->
                Card(
                    onClick = {
                        if (targetInstanceIndex in sessionInstances.indices) {
                            viewModel.replaceMachine(targetInstanceIndex, machine)
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
                                style = MaterialTheme.typography.bodySmall,
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
        var weightUnit by remember { mutableStateOf(WeightUnit.KG) }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        var selectedMuscleGroup by remember { mutableStateOf("") }
        val knownMuscleGroups = catalogMachines
            .map { it.targetMuscleGroup }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
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

            // Muscle Group Suggestions (Issue #36)
            if (knownMuscleGroups.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.hint_muscle_group),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                items(knownMuscleGroups) { group ->
                    CompactButton(
                        onClick = {
                            selectedMuscleGroup = if (selectedMuscleGroup == group) "" else group
                        },
                        modifier = Modifier.fillMaxWidth(0.85f).padding(vertical = 1.dp)
                    ) {
                        Text(
                            text = group,
                            fontSize = 12.sp,
                            fontWeight = if (selectedMuscleGroup == group) FontWeight.Bold else FontWeight.Normal
                        )
                    }
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
                    listOf(
                        WeightUnit.KG,
                        WeightUnit.LBS
                    ).forEach { unitOption ->
                        val isSelected = weightUnit == unitOption
                        CompactChip(
                            label = {
                                Text(
                                    WeightConversion.unitSuffix(unitOption) +
                                        if (isSelected) " ✓" else "",
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            onClick = {
                                val currentKg = WeightConversion.toKg(incrementKg, weightUnit)
                                weightUnit = unitOption
                                incrementKg = WeightConversion.fromKg(currentKg, unitOption)
                            },
                            modifier = Modifier.padding(horizontal = 2.dp)
                        )
                    }
                }
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
                        text = WeightConversion.formatWeightForUnit(
                            incrementKg, weightUnit
                        ) + " " + WeightConversion.unitSuffix(weightUnit),
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

            val currentErrorMessage = errorMessage
            if (currentErrorMessage != null) {
                item {
                    Text(
                        text = currentErrorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
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
                                viewModel.createMachine(
                                    name = machineName,
                                    incrementKg = incrementKg,
                                    weightUnit = weightUnit,
                                    targetMuscleGroup = selectedMuscleGroup.ifBlank { fullBodyMuscleGroup },
                                    existingMachineNames = catalogMachines.map { it.name },
                                    mode = createMachineMode,
                                    targetIndex = targetInstanceIndex
                                )
                                currentSubScreen = ActiveWorkoutSubScreen.OVERVIEW
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
        val machine = catalogMachines.find { it.id == targetInstance?.machineId }
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
                    style = MaterialTheme.typography.bodySmall,
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
                            viewModel.saveStationNote(targetInstanceIndex, clean)
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
                            viewModel.saveStationNote(targetInstanceIndex, clean)
                            viewModel.saveMachineNoteToCatalog(targetInstance.machineId, clean)
                        }
                        currentSubScreen = ActiveWorkoutSubScreen.OVERVIEW
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

    // Subscreen: Finish Confirmation (Issue 38: accidental-tap protection)
    if (currentSubScreen == ActiveWorkoutSubScreen.FINISH_CONFIRM) {
        val finishListState = rememberScalingLazyListState()
        ScalingLazyColumn(
            state = finishListState,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Text(
                    text = stringResource(R.string.dialog_finish_confirm_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            item {
                Text(
                    text = stringResource(R.string.dialog_finish_confirm_message),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            item {
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
                            viewModel.finishWorkout(onFinished = onFinishWorkout)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    Text(stringResource(R.string.btn_yes))
                }
            }
            item {
                Button(
                    onClick = { currentSubScreen = ActiveWorkoutSubScreen.OVERVIEW },
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    Text(stringResource(R.string.btn_no))
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
                        viewModel.consolidateTemplateAndFinish(onFinished = onFinishWorkout)
                    },
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    Text(stringResource(R.string.btn_yes))
                }
            }
            item {
                Button(
                    onClick = {
                        viewModel.finishWorkout(onFinished = onFinishWorkout)
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
        // Title & Pause affordance header (AK 3.1)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(0.92f).padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = templateName ?: freeWorkoutTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                CompactButton(
                    onClick = {
                        viewModel.pauseWorkout()
                    },
                    modifier = Modifier.padding(start = 4.dp)
                ) {
                    Text(stringResource(R.string.btn_pause), fontSize = 11.sp)
                }
            }
        }

        itemsIndexed(sessionInstances) { index, instance ->
            val machine = catalogMachines.find { it.id == instance.machineId }
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
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    // Status and completed sets list (AK 3.11, AK 3.12)
                    if (instance.isSkipped) {
                        Text(
                            text = stringResource(R.string.skipped_status),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(R.string.skipped_exercise_info),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.sets_completed_format, sets.size),
                            style = MaterialTheme.typography.bodySmall
                        )

                        // In-workout logged sets list with edit trigger (AK 3.11)
                        if (sets.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            sets.forEachIndexed { setIdx, set ->
                                CompactButton(
                                    onClick = {
                                        editingInstanceIndex = index
                                        editingSetIndex = setIdx
                                    },
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "S${set.setNumber}: ${WeightConversion.formatWeightForUnit(set.weightKg, machine?.weightUnit ?: WeightUnit.KG)} ${WeightConversion.unitSuffix(machine?.weightUnit ?: WeightUnit.KG)} × ${set.reps}",
                                            fontSize = 11.sp
                                        )
                                        Text(
                                            text = stringResource(R.string.btn_edit_short),
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }

                        // Explicit button to log the next set
                        Spacer(modifier = Modifier.height(4.dp))
                        Button(
                            onClick = { selectedInstanceIndex = index },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                        ) {
                            Text(
                                text = if (sets.isEmpty()) {
                                    stringResource(R.string.btn_start_exercise)
                                } else {
                                    stringResource(R.string.btn_log_next_set, sets.size + 1)
                                },
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Clear Skip exercise action (AK 3.6, AK 3.7)
                        CompactButton(
                            onClick = {
                                viewModel.toggleSkipStation(index)
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
                    currentSubScreen = ActiveWorkoutSubScreen.FINISH_CONFIRM
                },
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                Text(stringResource(R.string.finish_workout_button))
            }
        }
    }
}
