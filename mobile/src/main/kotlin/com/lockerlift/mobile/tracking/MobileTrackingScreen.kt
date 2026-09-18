package com.lockerlift.mobile.tracking

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.lockerlift.core.database.entity.SyncQueueEntity
import com.lockerlift.core.database.entity.WorkoutSetEntity
import com.lockerlift.core.database.entity.toDomainModel
import com.lockerlift.core.database.entity.toEntity
import com.lockerlift.core.database.logic.ValidationResult
import com.lockerlift.core.database.logic.WorkoutTrackingLogic
import com.lockerlift.core.healthconnect.HealthConnectManager
import com.lockerlift.core.model.*
import com.lockerlift.core.sync.SessionMachineInstancePayload
import com.lockerlift.core.sync.SyncConstants
import com.lockerlift.core.sync.SyncPayloadSerializer
import com.lockerlift.core.sync.SyncQueueWorker
import com.lockerlift.core.sync.WearableDataLayerManager
import com.lockerlift.core.sync.WorkoutSessionPayload
import com.lockerlift.mobile.LockerLiftMobileApp
import com.lockerlift.mobile.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileTrackingScreen(app: LockerLiftMobileApp) {
    val database = app.database
    val machineDao = remember { database.machineDao() }
    val sessionDao = remember { database.workoutSessionDao() }
    val templateDao = remember { database.workoutTemplateDao() }
    val syncQueueDao = remember { database.syncQueueDao() }
    val healthConnectManager = remember { HealthConnectManager(app) }
    val coroutineScope = rememberCoroutineScope()

    val templateWithMachinesList by templateDao.getAllActiveTemplatesWithMachinesFlow().collectAsState(initial = emptyList())
    val machineEntities by machineDao.getAllMachinesFlow().collectAsState(initial = emptyList())

    val machines: List<Machine> = remember(machineEntities) { machineEntities.map { it.toDomainModel() } }
    val templates: List<WorkoutTemplate> = remember(templateWithMachinesList) { templateWithMachinesList.map { it.template.toDomainModel() } }

    // Active session state
    var isWorkoutActive by remember { mutableStateOf(false) }
    var currentSessionId by remember { mutableStateOf("") }
    var currentTemplateId by remember { mutableStateOf<String?>(null) }
    var currentTemplateName by remember { mutableStateOf<String?>(null) }
    var workoutStartTime by remember { mutableLongStateOf(0L) }
    var elapsedSeconds by remember { mutableIntStateOf(0) }

    val initialMachineIds = remember { mutableStateListOf<String>() }
    val sessionInstances = remember { mutableStateListOf<SessionMachineInstance>() }
    val loggedSets = remember { mutableStateMapOf<String, List<WorkoutSet>>() }

    // Dialog & sheet states
    var selectedInstanceIndexForSet by remember { mutableIntStateOf(-1) }
    var editingInstanceId by remember { mutableStateOf<String?>(null) }
    var editingSetIndex by remember { mutableIntStateOf(-1) }
    var expandedMenuIndex by remember { mutableIntStateOf(-1) }
    var showAddExerciseDialog by remember { mutableStateOf(false) }
    var showSwapDialogIndex by remember { mutableIntStateOf(-1) }
    var showEditNoteIndex by remember { mutableIntStateOf(-1) }
    var showFinishDialog by remember { mutableStateOf(false) }
    var showDiscardConfirmDialog by remember { mutableStateOf(false) }

    // Rest timer state
    var restTimerRemainingSeconds by remember { mutableIntStateOf(0) }
    var isRestTimerRunning by remember { mutableStateOf(false) }

    // Elapsed workout timer
    LaunchedEffect(isWorkoutActive) {
        if (isWorkoutActive) {
            while (true) {
                delay(1000L)
                elapsedSeconds = ((System.currentTimeMillis() - workoutStartTime) / 1000).toInt()
            }
        }
    }

    // Rest countdown timer
    LaunchedEffect(isRestTimerRunning, restTimerRemainingSeconds) {
        if (isRestTimerRunning && restTimerRemainingSeconds > 0) {
            delay(1000L)
            restTimerRemainingSeconds -= 1
            if (restTimerRemainingSeconds <= 0) {
                isRestTimerRunning = false
            }
        }
    }

    fun startWorkout(template: WorkoutTemplate?) {
        val newSessionId = UUID.randomUUID().toString()
        currentSessionId = newSessionId
        currentTemplateId = template?.id
        currentTemplateName = template?.name
        workoutStartTime = System.currentTimeMillis()
        elapsedSeconds = 0
        initialMachineIds.clear()
        sessionInstances.clear()
        loggedSets.clear()
        isRestTimerRunning = false
        restTimerRemainingSeconds = 0

        coroutineScope.launch {
            if (template != null) {
                val orderedEntities = templateDao.getMachinesForTemplateOrdered(template.id)
                orderedEntities.forEachIndexed { index, entity ->
                    val machine = entity.toDomainModel()
                    initialMachineIds.add(machine.id)
                    val instance = SessionMachineInstance(
                        sessionId = newSessionId,
                        machineId = machine.id,
                        executionOrder = index,
                        customSettingsNote = machine.machineSettingsNote
                    )
                    sessionInstances.add(instance)
                    loggedSets[instance.id] = emptyList()
                }
            }
            isWorkoutActive = true
        }
    }

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
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        onClick = { startWorkout(null) }
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
                            onClick = { startWorkout(template) }
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
                                Button(onClick = { startWorkout(template) }) {
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
                                TextButton(onClick = { restTimerRemainingSeconds += 15 }) {
                                    Text("+15s")
                                }
                                TextButton(onClick = { isRestTimerRunning = false; restTimerRemainingSeconds = 0 }) {
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
                    val machine = machines.find { it.id == instance.machineId }
                    val machineName = machine?.name ?: stringResource(R.string.hint_machine_name)
                    val muscleGroup = machine?.targetMuscleGroup ?: ""
                    val sets = loggedSets[instance.id] ?: emptyList()

                    // Query historical sets for this machine
                    var historicalSets by remember(instance.machineId) { mutableStateOf<List<WorkoutSetEntity>>(emptyList()) }
                    LaunchedEffect(instance.machineId) {
                        withContext(Dispatchers.IO) {
                            historicalSets = sessionDao.getLastCompletedSetsForMachine(instance.machineId)
                        }
                    }
                    val lastHistSets = remember(historicalSets) { WorkoutTrackingLogic.extractLastSessionSets(historicalSets) }

                    // Double progression proposal for next set
                    val lastSet = sets.lastOrNull()
                    val showProgressionSuggestion = lastSet != null && WorkoutTrackingLogic.isProgressionProposed(lastSet.reps)
                    val increment = machine?.defaultIncrementKg ?: 2.5f
                    val overloadedWeight = if (lastSet != null) WorkoutTrackingLogic.calculateNextWeight(lastSet.weightKg, increment, shouldProgress = true) else 0f

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // --- 1. Header: Name, Muscle, Note, 3-dots Menu ---
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${index + 1}. $machineName",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        if (muscleGroup.isNotBlank()) {
                                            Text(
                                                text = muscleGroup,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        val noteText = instance.customSettingsNote ?: ""
                                        if (noteText.isNotBlank()) {
                                            Text(
                                                text = "• ⚙️ $noteText",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }

                                // 3-dots Menu
                                Box {
                                    IconButton(onClick = { expandedMenuIndex = index }) {
                                        Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                                    }
                                    DropdownMenu(
                                        expanded = expandedMenuIndex == index,
                                        onDismissRequest = { expandedMenuIndex = -1 }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.menu_swap_exercise)) },
                                            onClick = {
                                                showSwapDialogIndex = index
                                                expandedMenuIndex = -1
                                            },
                                            leadingIcon = { Icon(Icons.Default.SwapHoriz, null) }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.menu_edit_settings)) },
                                            onClick = {
                                                showEditNoteIndex = index
                                                expandedMenuIndex = -1
                                            },
                                            leadingIcon = { Icon(Icons.Default.Settings, null) }
                                        )
                                        if (index > 0) {
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.menu_move_up)) },
                                                onClick = {
                                                    val reordered = WorkoutTrackingLogic.reorderInstances(sessionInstances, index, index - 1)
                                                    sessionInstances.clear()
                                                    sessionInstances.addAll(reordered)
                                                    expandedMenuIndex = -1
                                                },
                                                leadingIcon = { Icon(Icons.Default.ArrowUpward, null) }
                                            )
                                        }
                                        if (index < sessionInstances.size - 1) {
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.menu_move_down)) },
                                                onClick = {
                                                    val reordered = WorkoutTrackingLogic.reorderInstances(sessionInstances, index, index + 1)
                                                    sessionInstances.clear()
                                                    sessionInstances.addAll(reordered)
                                                    expandedMenuIndex = -1
                                                },
                                                leadingIcon = { Icon(Icons.Default.ArrowDownward, null) }
                                            )
                                        }
                                        HorizontalDivider()
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.menu_remove_exercise), color = MaterialTheme.colorScheme.error) },
                                            onClick = {
                                                val removed = WorkoutTrackingLogic.removeInstanceFromSession(sessionInstances, index)
                                                sessionInstances.clear()
                                                sessionInstances.addAll(removed)
                                                expandedMenuIndex = -1
                                            },
                                            leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
                                        )
                                    }
                                }
                            }

                            // Progression suggestion banner
                            if (showProgressionSuggestion) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(
                                            text = stringResource(
                                                R.string.progression_suggestion_format,
                                                WorkoutTrackingLogic.formatWeight(overloadedWeight)
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // --- 2. Set Table Header ---
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.table_header_set),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(36.dp)
                                )
                                Text(
                                    text = stringResource(R.string.table_header_prev),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = stringResource(R.string.table_header_weight),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(68.dp)
                                )
                                Text(
                                    text = stringResource(R.string.table_header_reps),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(44.dp)
                                )
                                Text(
                                    text = stringResource(R.string.table_header_status),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(72.dp)
                                )
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                            // --- 3. Completed Sets Table Rows ---
                            sets.forEachIndexed { setIdx, set ->
                                val prevSet = lastHistSets.getOrNull(setIdx)
                                val prevText = if (prevSet != null) {
                                    "${WorkoutTrackingLogic.formatWeight(prevSet.weightKg)} × ${prevSet.reps}"
                                } else "—"

                                Surface(
                                    onClick = {
                                        editingInstanceId = instance.id
                                        editingSetIndex = setIdx
                                    },
                                    shape = MaterialTheme.shapes.small,
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 4.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${setIdx + 1}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.width(36.dp)
                                        )
                                        Text(
                                            text = prevText,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = "${WorkoutTrackingLogic.formatWeight(set.weightKg)}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.width(68.dp)
                                        )
                                        Text(
                                            text = "${set.reps}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.width(44.dp)
                                        )
                                        // Status badge with checkmark (Clickable to edit)
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = MaterialTheme.shapes.extraSmall,
                                            modifier = Modifier.width(72.dp)
                                        ) {
                                            Row(
                                                horizontalArrangement = Arrangement.Center,
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(vertical = 3.dp, horizontal = 4.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(13.dp),
                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Text(
                                                    text = stringResource(R.string.set_status_logged),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // --- 4. Add Set Button ---
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = { selectedInstanceIndexForSet = index },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.btn_add_set))
                            }

                            // --- 5. Next Station Suggestion ---
                            if (sets.isNotEmpty()) {
                                val nextStationIndex = WorkoutTrackingLogic.findNextUnfinishedStationIndex(index, sessionInstances, loggedSets)
                                if (nextStationIndex != null && nextStationIndex in sessionInstances.indices) {
                                    val nextInst = sessionInstances[nextStationIndex]
                                    val nextMachine = machines.find { it.id == nextInst.machineId }
                                    val nextMachineName = nextMachine?.name ?: "Station ${nextStationIndex + 1}"
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                                        ),
                                        onClick = {
                                            selectedInstanceIndexForSet = nextStationIndex
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                                Icon(Icons.Default.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = stringResource(R.string.next_exercise_suggestion, nextMachineName),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                                )
                                            }
                                            Text(
                                                text = stringResource(R.string.btn_jump_to_exercise),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
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
        val machine = machines.find { it.id == instance.machineId }
        val machineName = machine?.name ?: ""
        val existingSets = loggedSets[instance.id] ?: emptyList()
        val nextSetNumber = existingSets.size + 1

        var historicalSets by remember { mutableStateOf<List<WorkoutSetEntity>>(emptyList()) }
        LaunchedEffect(instance.machineId) {
            withContext(Dispatchers.IO) {
                historicalSets = sessionDao.getLastCompletedSetsForMachine(instance.machineId)
            }
        }

        val (prefillWeight, prefillReps) = remember(selectedInstanceIndexForSet, existingSets.size) {
            WorkoutTrackingLogic.determinePrefillWeightAndReps(
                currentSets = existingSets,
                historicalSets = historicalSets,
                defaultWeight = 60f,
                defaultReps = 10
            )
        }

        var weightInput by remember(prefillWeight) { mutableStateOf(WorkoutTrackingLogic.formatWeight(prefillWeight)) }
        var repsInput by remember(prefillReps) { mutableStateOf(prefillReps.toString()) }

        val currentWeight = weightInput.toFloatOrNull() ?: 0f
        val currentReps = repsInput.toIntOrNull() ?: 0
        val increment = machine?.defaultIncrementKg ?: 2.5f

        val showProgressionSuggestion = WorkoutTrackingLogic.isProgressionProposed(currentReps)
        val overloadedWeight = WorkoutTrackingLogic.calculateNextWeight(currentWeight, increment, shouldProgress = true)

        AlertDialog(
            onDismissRequest = { selectedInstanceIndexForSet = -1 },
            title = {
                Text(stringResource(R.string.log_set_dialog_title, nextSetNumber, machineName))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Double progression proposal banner
                    if (showProgressionSuggestion) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = stringResource(
                                        R.string.progression_suggestion_format,
                                        WorkoutTrackingLogic.formatWeight(overloadedWeight)
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Button(
                                    onClick = {
                                        weightInput = WorkoutTrackingLogic.formatWeight(overloadedWeight)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                                ) {
                                    Text(
                                        text = stringResource(
                                            R.string.btn_apply_overload,
                                            WorkoutTrackingLogic.formatWeight(increment)
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // Weight Input & quick buttons
                    Text(text = stringResource(R.string.weight_label), fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = weightInput,
                        onValueChange = { weightInput = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(-5f, -2.5f, -1.25f, 1.25f, 2.5f, 5f).forEach { delta ->
                            OutlinedButton(
                                onClick = {
                                    val newW = WorkoutTrackingLogic.clampWeight((weightInput.toFloatOrNull() ?: 0f) + delta)
                                    weightInput = WorkoutTrackingLogic.formatWeight(newW)
                                },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (delta > 0) "+$delta" else "$delta", fontSize = 11.sp)
                            }
                        }
                    }

                    // Reps Input & quick buttons
                    Text(text = stringResource(R.string.reps_label), fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = repsInput,
                        onValueChange = { repsInput = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(-5, -1, 1, 5).forEach { delta ->
                            OutlinedButton(
                                onClick = {
                                    val newR = WorkoutTrackingLogic.clampReps((repsInput.toIntOrNull() ?: 0) + delta)
                                    repsInput = newR.toString()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (delta > 0) "+$delta" else "$delta")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val finalWeight = WorkoutTrackingLogic.clampWeight(weightInput.toFloatOrNull() ?: 0f)
                        val finalReps = WorkoutTrackingLogic.clampReps(repsInput.toIntOrNull() ?: 1)

                        val newSet = WorkoutSet(
                            sessionMachineId = instance.id,
                            setNumber = nextSetNumber,
                            reps = finalReps,
                            weightKg = finalWeight,
                            cadence = machine?.defaultCadence ?: "2-0-2-0",
                            setType = SetType.NORMAL
                        )

                        // Reactive state update (new list instance triggers Compose recomposition)
                        val currentList = loggedSets[instance.id] ?: emptyList()
                        loggedSets[instance.id] = currentList + newSet

                        // Start rest timer
                        restTimerRemainingSeconds = 90
                        isRestTimerRunning = true
                        selectedInstanceIndexForSet = -1
                    }
                ) {
                    Text(stringResource(R.string.btn_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedInstanceIndexForSet = -1 }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    // --- EDIT EXISTING SET DIALOG ---
    if (editingInstanceId != null && editingSetIndex >= 0) {
        val currentList = loggedSets[editingInstanceId!!] ?: emptyList()
        val setToEdit = currentList.getOrNull(editingSetIndex)
        val targetInstance = sessionInstances.find { it.id == editingInstanceId }
        val machine = machines.find { it.id == targetInstance?.machineId }
        val machineName = machine?.name ?: ""

        if (setToEdit != null) {
            var editWeightInput by remember(setToEdit) { mutableStateOf(WorkoutTrackingLogic.formatWeight(setToEdit.weightKg)) }
            var editRepsInput by remember(setToEdit) { mutableStateOf(setToEdit.reps.toString()) }

            AlertDialog(
                onDismissRequest = {
                    editingInstanceId = null
                    editingSetIndex = -1
                },
                title = {
                    Text(stringResource(R.string.edit_set_dialog_title, setToEdit.setNumber, machineName))
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Weight input & quick steppers
                        Text(text = stringResource(R.string.weight_label), fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = editWeightInput,
                            onValueChange = { editWeightInput = it },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(-5f, -2.5f, -1.25f, 1.25f, 2.5f, 5f).forEach { delta ->
                                OutlinedButton(
                                    onClick = {
                                        val newW = WorkoutTrackingLogic.clampWeight((editWeightInput.toFloatOrNull() ?: 0f) + delta)
                                        editWeightInput = WorkoutTrackingLogic.formatWeight(newW)
                                    },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(if (delta > 0) "+$delta" else "$delta", fontSize = 11.sp)
                                }
                            }
                        }

                        // Reps input & quick steppers
                        Text(text = stringResource(R.string.reps_label), fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = editRepsInput,
                            onValueChange = { editRepsInput = it },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(-5, -1, 1, 5).forEach { delta ->
                                OutlinedButton(
                                    onClick = {
                                        val newR = WorkoutTrackingLogic.clampReps((editRepsInput.toIntOrNull() ?: 0) + delta)
                                        editRepsInput = newR.toString()
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(if (delta > 0) "+$delta" else "$delta")
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedButton(
                            onClick = {
                                val updated = WorkoutTrackingLogic.deleteSetAndRenumber(currentList, editingSetIndex)
                                loggedSets[editingInstanceId!!] = updated
                                editingInstanceId = null
                                editingSetIndex = -1
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.btn_delete_set))
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val finalWeight = WorkoutTrackingLogic.clampWeight(editWeightInput.toFloatOrNull() ?: 0f)
                            val finalReps = WorkoutTrackingLogic.clampReps(editRepsInput.toIntOrNull() ?: 1)

                            val updated = WorkoutTrackingLogic.updateSetInList(
                                sets = currentList,
                                targetIndex = editingSetIndex,
                                weightKg = finalWeight,
                                reps = finalReps
                            )
                            loggedSets[editingInstanceId!!] = updated
                            editingInstanceId = null
                            editingSetIndex = -1
                        }
                    ) {
                        Text(stringResource(R.string.btn_save))
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        editingInstanceId = null
                        editingSetIndex = -1
                    }) {
                        Text(stringResource(R.string.btn_cancel))
                    }
                }
            )
        } else {
            editingInstanceId = null
            editingSetIndex = -1
        }
    }

    // --- SWAP EXERCISE DIALOG (PRESERVING SETS) ---
    if (showSwapDialogIndex in sessionInstances.indices) {
        AlertDialog(
            onDismissRequest = { showSwapDialogIndex = -1 },
            title = { Text(stringResource(R.string.dialog_swap_exercise_title)) },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(machines) { _, m ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                val updated = WorkoutTrackingLogic.swapMachinePreservingSets(sessionInstances, showSwapDialogIndex, m)
                                sessionInstances.clear()
                                sessionInstances.addAll(updated)
                                showSwapDialogIndex = -1
                            }
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(text = m.name, fontWeight = FontWeight.Bold)
                                Text(text = m.targetMuscleGroup, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSwapDialogIndex = -1 }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    // --- ADD EXERCISE DIALOG ---
    if (showAddExerciseDialog) {
        var showNewMachineForm by remember { mutableStateOf(false) }
        var newMachineName by remember { mutableStateOf("") }
        var newMuscleGroup by remember { mutableStateOf("") }
        var newSettingsNote by remember { mutableStateOf("") }
        var newIncrement by remember { mutableStateOf("2.5") }
        var errorMessage by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showAddExerciseDialog = false },
            title = {
                Text(stringResource(if (showNewMachineForm) R.string.dialog_new_machine_title else R.string.dialog_select_machine_title))
            },
            text = {
                if (showNewMachineForm) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = newMachineName,
                            onValueChange = { newMachineName = it; errorMessage = null },
                            label = { Text(stringResource(R.string.hint_machine_name)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = newMuscleGroup,
                            onValueChange = { newMuscleGroup = it; errorMessage = null },
                            label = { Text(stringResource(R.string.hint_muscle_group)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = newSettingsNote,
                            onValueChange = { newSettingsNote = it },
                            label = { Text(stringResource(R.string.hint_settings_note)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = newIncrement,
                            onValueChange = { newIncrement = it },
                            label = { Text(stringResource(R.string.hint_increment)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (errorMessage != null) {
                            Text(text = errorMessage!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(machines) { _, m ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    val appended = WorkoutTrackingLogic.appendMachineToSession(sessionInstances, currentSessionId, m)
                                    sessionInstances.add(appended)
                                    loggedSets[appended.id] = emptyList()
                                    showAddExerciseDialog = false
                                }
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(text = m.name, fontWeight = FontWeight.Bold)
                                    Text(text = m.targetMuscleGroup, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (showNewMachineForm) {
                    Button(
                        onClick = {
                            val validation = WorkoutTrackingLogic.validateNewMachine(newMachineName, machines.map { it.name })
                            if (validation is ValidationResult.EmptyName) {
                                errorMessage = "Name cannot be empty"
                                return@Button
                            }
                            if (validation is ValidationResult.DuplicateName) {
                                errorMessage = "Machine already exists"
                                return@Button
                            }

                            val newMachine = Machine(
                                id = UUID.randomUUID().toString(),
                                name = newMachineName.trim(),
                                targetMuscleGroup = newMuscleGroup.trim().ifEmpty { "General" },
                                machineSettingsNote = newSettingsNote.trim(),
                                defaultIncrementKg = newIncrement.toFloatOrNull() ?: 2.5f
                            )

                            coroutineScope.launch {
                                machineDao.insertMachine(newMachine.toEntity())
                                val appended = WorkoutTrackingLogic.appendMachineToSession(sessionInstances, currentSessionId, newMachine)
                                sessionInstances.add(appended)
                                loggedSets[appended.id] = emptyList()
                                showAddExerciseDialog = false
                            }
                        }
                    ) {
                        Text(stringResource(R.string.btn_save))
                    }
                } else {
                    Button(onClick = { showNewMachineForm = true }) {
                        Text(stringResource(R.string.add_machine))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddExerciseDialog = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    // --- EDIT NOTE DIALOG ---
    if (showEditNoteIndex in sessionInstances.indices) {
        val instance = sessionInstances[showEditNoteIndex]
        var noteInput by remember(showEditNoteIndex) { mutableStateOf(instance.customSettingsNote ?: "") }

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
                        val updated = sessionInstances.mapIndexed { idx, item ->
                            if (idx == showEditNoteIndex) item.copy(customSettingsNote = noteInput.trim()) else item
                        }
                        sessionInstances.clear()
                        sessionInstances.addAll(updated)
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
        var workoutNotes by remember { mutableStateOf("") }
        var consolidateTemplate by remember { mutableStateOf(false) }

        // Check if variations occurred from initial template
        val currentMachineIds = sessionInstances.filter { !it.isSkipped }.map { it.machineId }
        val hasVariations = currentTemplateId != null && currentMachineIds != initialMachineIds

        AlertDialog(
            onDismissRequest = { showFinishDialog = false },
            title = { Text(stringResource(R.string.finish_workout_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val totalSets = loggedSets.values.sumOf { it.size }
                    Text(text = stringResource(R.string.sets_completed_format, totalSets))

                    OutlinedTextField(
                        value = workoutNotes,
                        onValueChange = { workoutNotes = it },
                        label = { Text(stringResource(R.string.workout_notes_hint)) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (hasVariations) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = consolidateTemplate,
                                onCheckedChange = { consolidateTemplate = it }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.consolidate_template_title),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val finalEndTime = System.currentTimeMillis()
                        val session = WorkoutSession(
                            id = currentSessionId,
                            templateId = currentTemplateId,
                            startTime = workoutStartTime,
                            endTime = finalEndTime,
                            originDevice = "MOBILE",
                            syncStatus = SyncStatus.PENDING_SYNC,
                            notes = workoutNotes.trim()
                        )

                        coroutineScope.launch {
                            // 1. Save session, instances and sets to local Room DB
                            val allSets = loggedSets.values.flatten()
                            sessionDao.upsertFullSession(
                                session = session.toEntity(),
                                instances = sessionInstances.map { it.toEntity() },
                                sets = allSets.map { it.toEntity() }
                            )

                            // 2. Consolidate template if requested
                            if (consolidateTemplate && currentTemplateId != null) {
                                val activeMachineIds = sessionInstances.filter { !it.isSkipped }.map { it.machineId }
                                val templateWithMachines = templateDao.getTemplateWithMachinesById(currentTemplateId!!)
                                val existingTemplate = templateWithMachines?.template
                                if (existingTemplate != null) {
                                    templateDao.saveTemplateWithMachines(existingTemplate, activeMachineIds)
                                }
                            }

                            // 3. Export to Health Connect
                            if (healthConnectManager.isAvailable() && healthConnectManager.hasPermissions()) {
                                healthConnectManager.exportWorkoutSession(
                                    session = session,
                                    templateName = currentTemplateName
                                )
                            }

                            // 4. Enqueue in sync queue to transfer completed workout to Wear OS companion
                            val payload = WorkoutSessionPayload(
                                session = session,
                                templateName = currentTemplateName,
                                machineInstances = sessionInstances.map { inst ->
                                    val m = machines.find { it.id == inst.machineId } ?: Machine(id = inst.machineId, name = "Exercise", targetMuscleGroup = "General")
                                    val setsForInst = loggedSets[inst.id] ?: emptyList()
                                    SessionMachineInstancePayload(
                                        instance = inst,
                                        machine = m,
                                        sets = setsForInst
                                    )
                                }
                            )
                            val payloadJson = SyncPayloadSerializer.encodeSessionPayload(payload)
                            syncQueueDao.insertQueueItem(
                                SyncQueueEntity(
                                    id = UUID.randomUUID().toString(),
                                    sessionId = session.id,
                                    payloadJson = payloadJson
                                )
                            )

                            // Trigger WorkManager sync (Store-and-Forward)
                            SyncQueueWorker.enqueue(app)
                            runCatching {
                                WearableDataLayerManager(app).flushPendingQueue(syncQueueDao, SyncConstants.CAPABILITY_WEAR)
                            }

                            // Reset state
                            isWorkoutActive = false
                            showFinishDialog = false
                        }
                    }
                ) {
                    Text(stringResource(R.string.btn_save_and_finish))
                }
            },
            dismissButton = {
                TextButton(onClick = { showFinishDialog = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    // --- DISCARD WORKOUT CONFIRM DIALOG ---
    if (showDiscardConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirmDialog = false },
            title = { Text(stringResource(R.string.discard_workout)) },
            text = { Text(stringResource(R.string.discard_workout_confirm)) },
            confirmButton = {
                Button(
                    onClick = {
                        isWorkoutActive = false
                        showDiscardConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.discard_workout))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirmDialog = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }
}
