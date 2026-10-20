package com.lockerlift.mobile.tracking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lockerlift.core.model.WorkoutTemplate
import com.lockerlift.mobile.LockerLiftMobileApp
import com.lockerlift.mobile.R
import com.lockerlift.mobile.tracking.components.StationCard
import com.lockerlift.mobile.tracking.components.UpNextPlanCard
import com.lockerlift.mobile.tracking.dialogs.AddExerciseDialog
import com.lockerlift.mobile.tracking.dialogs.DiscardWorkoutDialog
import com.lockerlift.mobile.tracking.dialogs.EditSetDialog
import com.lockerlift.mobile.tracking.dialogs.FinishWorkoutDialog
import com.lockerlift.mobile.tracking.dialogs.LogSetDialog
import com.lockerlift.mobile.tracking.dialogs.SwapExerciseDialog

private const val REST_TIMER_SECONDS = 90

/**
 * Entry point of the tracking tab. Start-workout overview when no session
 * is active, active workout screen otherwise. All state lives in
 * [TrackingViewModel]; this file only renders it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileTrackingScreen(app: LockerLiftMobileApp) {
    val viewModel: TrackingViewModel = viewModel(
        key = "tracking",
        factory = viewModelFactory {
            initializer {
                TrackingViewModel(
                    application = app,
                    database = app.database
                )
            }
        }
    )
    val state by viewModel.uiState.collectAsState()
    val machines by viewModel.machines.collectAsState()
    val templates by viewModel.templates.collectAsState()

    // Active session state (owned by the ViewModel, durable across process death)
    val isWorkoutActive = state.isWorkoutActive
    val currentTemplateId = state.templateId
    val currentTemplateName = state.templateName
    val elapsedSeconds = state.elapsedSeconds
    val restTimerRemainingSeconds = state.restTimerRemainingSeconds
    val isRestTimerRunning = state.isRestTimerRunning

    val sessionInstances = state.instances
    val loggedSets = state.setsByInstance

    // Dialog & sheet states
    var selectedInstanceIndexForSet by remember { mutableIntStateOf(-1) }
    var editingInstanceId by remember { mutableStateOf<String?>(null) }
    var editingSetIndex by remember { mutableIntStateOf(-1) }
    var showAddExerciseDialog by remember { mutableStateOf(false) }
    var showSwapDialogIndex by remember { mutableIntStateOf(-1) }
    var showEditNoteIndex by remember { mutableIntStateOf(-1) }
    var showFinishDialog by remember { mutableStateOf(false) }
    var showDiscardConfirmDialog by remember { mutableStateOf(false) }

    if (!isWorkoutActive) {
        // --- START WORKOUT SCREEN ---
        Scaffold(
            topBar = {
                TopAppBar(title = { Text(stringResource(R.string.start_workout_title)) })
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    UpNextPlanCard(
                        plans = viewModel.workoutPlans,
                        activityTypes = viewModel.activityTypes,
                        templates = templates
                    )
                }
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        onClick = { viewModel.startWorkout(null) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.start_free_workout),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = stringResource(R.string.start_free_workout_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }

                item {
                    Text(
                        text = stringResource(R.string.start_from_template_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (templates.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.no_exercises_in_template),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                } else {
                    itemsIndexed(templates) { _, template ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { viewModel.startWorkout(template) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = template.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    val desc = template.description
                                    if (!desc.isNullOrBlank()) {
                                        Text(
                                            text = desc,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Button(onClick = { viewModel.startWorkout(template) }) {
                                    Text(stringResource(R.string.btn_start))
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
        // --- ACTIVE WORKOUT SCREEN ---
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = currentTemplateName ?: stringResource(R.string.workout_free),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            val minutes = elapsedSeconds / 60
                            val seconds = elapsedSeconds % 60
                            Text(
                                text = "%02d:%02d".format(minutes, seconds),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { showDiscardConfirmDialog = true }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.discard_workout))
                        }
                        Button(
                            onClick = { showFinishDialog = true },
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(stringResource(R.string.finish_workout))
                        }
                    }
                )
            },
            bottomBar = {
                if (isRestTimerRunning && restTimerRemainingSeconds > 0) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val restMinutes = restTimerRemainingSeconds / 60
                            val restSecs = restTimerRemainingSeconds % 60
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.rest_timer_seconds_format, restMinutes, restSecs),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            Row {
                                TextButton(onClick = { viewModel.addRestTimerSeconds(15) }) {
                                    Text("+15s")
                                }
                                TextButton(onClick = { viewModel.skipRestTimer() }) {
                                    Text(stringResource(R.string.btn_skip_rest))
                                }
                            }
                        }
                    }
                }
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                itemsIndexed(sessionInstances) { index, instance ->
                    StationCard(
                        index = index,
                        instance = instance,
                        machine = machines.find { it.id == instance.machineId },
                        machines = machines,
                        sessionInstances = sessionInstances,
                        loggedSets = loggedSets[instance.id] ?: emptyList(),
                        setsByInstance = loggedSets,
                        historicalSetsProvider = { machineId -> viewModel.getHistoricalSetsForMachine(machineId) },
                        onAddSet = { selectedInstanceIndexForSet = it },
                        onEditSet = { instanceId, setIdx ->
                            editingInstanceId = instanceId
                            editingSetIndex = setIdx
                        },
                        onSwapExercise = { showSwapDialogIndex = it },
                        onEditSettings = { showEditNoteIndex = it },
                        onMove = { from, to -> viewModel.reorderInstance(from, to) },
                        onRemove = { viewModel.removeInstance(it) },
                        onJumpToStation = { selectedInstanceIndexForSet = it }
                    )
                }

                item {
                    OutlinedButton(
                        onClick = { showAddExerciseDialog = true },
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
    }

    // --- LOG SET DIALOG ---
    if (selectedInstanceIndexForSet in sessionInstances.indices) {
        val instance = sessionInstances[selectedInstanceIndexForSet]
        LogSetDialog(
            instance = instance,
            machine = machines.find { it.id == instance.machineId },
            existingSets = loggedSets[instance.id] ?: emptyList(),
            historicalSetsProvider = { machineId -> viewModel.getHistoricalSetsForMachine(machineId) },
            restTimerSeconds = REST_TIMER_SECONDS,
            onLogSet = { instanceId, set -> viewModel.logSet(instanceId, set) },
            onRestTimerStarted = { seconds -> viewModel.startRestTimer(seconds) },
            onDismiss = { selectedInstanceIndexForSet = -1 }
        )
    }

    // --- EDIT EXISTING SET DIALOG ---
    val currentEditingInstanceId = editingInstanceId
    if (currentEditingInstanceId != null && editingSetIndex >= 0) {
        val targetInstance = sessionInstances.find { it.id == currentEditingInstanceId }
        val machine = machines.find { it.id == targetInstance?.machineId }
        val currentList = loggedSets[currentEditingInstanceId] ?: emptyList()
        if (currentList.getOrNull(editingSetIndex) != null) {
            EditSetDialog(
                instanceId = currentEditingInstanceId,
                machine = machine,
                sets = currentList,
                setIndex = editingSetIndex,
                onUpdateSet = { instanceId, setIdx, weightKg, reps ->
                    viewModel.updateSet(instanceId, setIdx, weightKg, reps)
                },
                onDeleteSet = { instanceId, setIdx -> viewModel.deleteSet(instanceId, setIdx) },
                onDismiss = {
                    editingInstanceId = null
                    editingSetIndex = -1
                }
            )
        } else {
            editingInstanceId = null
            editingSetIndex = -1
        }
    }

    // --- SWAP EXERCISE DIALOG (PRESERVING SETS) ---
    if (showSwapDialogIndex in sessionInstances.indices) {
        val swapIndex = showSwapDialogIndex
        SwapExerciseDialog(
            machines = machines,
            onSwapMachine = { m -> viewModel.swapMachine(swapIndex, m) },
            onDismiss = { showSwapDialogIndex = -1 }
        )
    }

    // --- ADD EXERCISE DIALOG ---
    if (showAddExerciseDialog) {
        AddExerciseDialog(
            machines = machines,
            onAppendMachine = { m -> viewModel.appendMachine(m) },
            onCreateMachineAndAppend = { m -> viewModel.createMachineAndAppend(m) },
            onDismiss = { showAddExerciseDialog = false }
        )
    }

    // --- EDIT NOTE DIALOG ---
    if (showEditNoteIndex in sessionInstances.indices) {
        val editNoteIndex = showEditNoteIndex
        val instance = sessionInstances[editNoteIndex]
        var noteInput by remember(editNoteIndex) { mutableStateOf(instance.customSettingsNote ?: "") }

        AlertDialog(
            onDismissRequest = { showEditNoteIndex = -1 },
            title = { Text(stringResource(R.string.hint_settings_note)) },
            text = {
                OutlinedTextField(
                    value = noteInput,
                    onValueChange = { noteInput = it },
                    label = { Text(stringResource(R.string.hint_settings_note)) },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.saveStationNote(editNoteIndex, noteInput)
                        showEditNoteIndex = -1
                    }
                ) {
                    Text(stringResource(R.string.btn_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditNoteIndex = -1 }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    // --- FINISH WORKOUT DIALOG ---
    if (showFinishDialog) {
        val currentMachineIds = sessionInstances.filter { !it.isSkipped }.map { it.machineId }
        val hasVariations = currentTemplateId != null && currentMachineIds != state.initialMachineIds
        FinishWorkoutDialog(
            instances = sessionInstances,
            setsByInstance = loggedSets,
            hasVariations = hasVariations,
            onFinish = { consolidate, notes ->
                showFinishDialog = false
                viewModel.finishWorkout(
                    consolidateTemplate = consolidate,
                    notes = notes
                ) { }
            },
            onDismiss = { showFinishDialog = false }
        )
    }

    // --- DISCARD WORKOUT CONFIRM DIALOG ---
    if (showDiscardConfirmDialog) {
        DiscardWorkoutDialog(
            onDiscard = { viewModel.discardWorkout() },
            onDismiss = { showDiscardConfirmDialog = false }
        )
    }
}
