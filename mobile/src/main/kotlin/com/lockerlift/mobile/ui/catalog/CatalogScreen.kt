package com.lockerlift.mobile.ui.catalog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lockerlift.core.database.entity.MachineEntity
import com.lockerlift.core.model.WorkoutLogic
import com.lockerlift.core.model.WeightDirection
import com.lockerlift.core.model.WeightUnit
import com.lockerlift.core.model.WeightConversion
import com.lockerlift.core.database.entity.toDomainModel
import com.lockerlift.mobile.LockerLiftMobileApp
import com.lockerlift.mobile.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(app: LockerLiftMobileApp) {
    val catalogViewModel: CatalogViewModel = viewModel(
        key = "catalog",
        factory = viewModelFactory {
            initializer {
                CatalogViewModel(application = app, database = app.database)
            }
        }
    )
    val machinesState by catalogViewModel.machines.collectAsState()
    val knownMuscleGroups by catalogViewModel.knownMuscleGroups.collectAsState()

    var addMuscleGroupSuggestionSelected by remember { mutableStateOf(false) }
    var editMuscleGroupSuggestionSelected by remember { mutableStateOf(false) }

    var showAddDialog by remember { mutableStateOf(false) }
    var machineName by remember { mutableStateOf("") }
    var muscleGroup by remember { mutableStateOf("") }
    var settingsNote by remember { mutableStateOf("") }
    var incrementKgText by remember { mutableStateOf("2.5") }
    var addWeightUnit by remember { mutableStateOf(WeightUnit.KG) }
    var addWeightDirection by remember { mutableStateOf(WeightDirection.NORMAL) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var deleteCandidate by remember { mutableStateOf<MachineEntity?>(null) }
    var infoMessage by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    var editingMachine by remember { mutableStateOf<MachineEntity?>(null) }
    var editMachineName by remember { mutableStateOf("") }
    var editMuscleGroup by remember { mutableStateOf("") }
    var editSettingsNote by remember { mutableStateOf("") }
    var editIncrementKgText by remember { mutableStateOf("2.5") }
    var editWeightUnit by remember { mutableStateOf(WeightUnit.KG) }
    var editWeightDirection by remember { mutableStateOf(WeightDirection.NORMAL) }
    var editErrorMessage by remember { mutableStateOf<String?>(null) }

    val errorRequiredFields = stringResource(R.string.error_required_fields)
    val errorMachineExists = stringResource(R.string.error_machine_exists)
    val context = LocalContext.current

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.catalog_title)) })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                machineName = ""
                muscleGroup = ""
                settingsNote = ""
                incrementKgText = "2.5"
                addWeightUnit = WeightUnit.KG
                addWeightDirection = WeightDirection.NORMAL
                errorMessage = null
                showAddDialog = true
            }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_machine))
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(machinesState, key = { it.id }) { machineEntity ->
                val machine = machineEntity.toDomainModel()
                val dismissState = rememberSwipeToDismissBoxState(
                    confirmValueChange = { value ->
                        if (value == SwipeToDismissBoxValue.EndToStart) {
                            deleteCandidate = machineEntity
                        }
                        false
                    }
                )
                SwipeToDismissBox(
                    state = dismissState,
                    enableDismissFromStartToEnd = false,
                    backgroundContent = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp)
                                .background(
                                    MaterialTheme.colorScheme.errorContainer,
                                    MaterialTheme.shapes.medium
                                ),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.btn_delete),
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            editingMachine = machineEntity
                            editMachineName = machineEntity.name
                            editMuscleGroup = machineEntity.targetMuscleGroup
                            editSettingsNote = machineEntity.machineSettingsNote ?: ""
                            editIncrementKgText = WeightConversion.fromKg(
                                machineEntity.defaultIncrementKg,
                                machineEntity.weightUnit
                            ).toString()
                            editWeightUnit = machineEntity.weightUnit
                            editWeightDirection = machineEntity.weightDirection
                            editErrorMessage = null
                        },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = machine.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = stringResource(R.string.muscle_group_format, machine.targetMuscleGroup),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val settingsNote = machine.machineSettingsNote
                        if (!settingsNote.isNullOrBlank()) {
                            Text(
                                text = stringResource(R.string.setup_format, settingsNote),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = stringResource(
                                R.string.increment_format,
                                WeightConversion.formatWeightForUnit(
                                    machine.defaultIncrementKg,
                                    machine.weightUnit
                                ) + " " + WeightConversion.unitSuffix(machine.weightUnit)
                            ),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                }
            }
        }

        LaunchedEffect(infoMessage) {
            infoMessage?.let {
                snackbarHostState.showSnackbar(it)
                infoMessage = null
            }
        }

        // Delete Machine Confirmation Dialog (Issue #40)
        val machineToDelete = deleteCandidate
        if (machineToDelete != null) {
            AlertDialog(
                onDismissRequest = { deleteCandidate = null },
                title = { Text(stringResource(R.string.dialog_delete_machine_title)) },
                text = { Text(stringResource(R.string.dialog_delete_machine_confirm, machineToDelete.name)) },
                confirmButton = {
                    Button(
                        onClick = {
                            val target = deleteCandidate
                            deleteCandidate = null
                            if (target != null) {
                                catalogViewModel.deleteMachine(target) { result ->
                                    if (result == MachineDeleteResult.BLOCKED_ACTIVE_WORKOUT) {
                                        infoMessage = context.getString(R.string.machine_delete_blocked_active_workout)
                                    } else {
                                        infoMessage = context.getString(R.string.machine_deleted_history_kept, target.name)
                                    }
                                }
                            }
                        }
                    ) {
                        Text(stringResource(R.string.btn_delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deleteCandidate = null }) {
                        Text(stringResource(R.string.btn_cancel))
                    }
                }
            )
        }

        // Add New Machine Dialog
        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = { Text(stringResource(R.string.dialog_new_machine_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = machineName,
                            onValueChange = { machineName = it },
                            label = { Text(stringResource(R.string.hint_machine_name)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        MuscleGroupAutocompleteField(
                            value = muscleGroup,
                            onValueChange = {
                                muscleGroup = it
                                addMuscleGroupSuggestionSelected = false
                            },
                            onSuggestionSelected = {
                                muscleGroup = it
                                addMuscleGroupSuggestionSelected = true
                            },
                            knownMuscleGroups = knownMuscleGroups,
                            suppressSuggestions = addMuscleGroupSuggestionSelected
                        )
                        OutlinedTextField(
                            value = settingsNote,
                            onValueChange = { settingsNote = it },
                            label = { Text(stringResource(R.string.hint_settings_note)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            WeightDirectionSelector(
                                selected = addWeightDirection,
                                onSelected = { addWeightDirection = it }
                            )
                            WeightUnitSelector(
                                selected = addWeightUnit,
                                onSelected = { unit ->
                                    val kg = WeightConversion.toKg(
                                        incrementKgText.toFloatOrNull() ?: 2.5f,
                                        addWeightUnit
                                    )
                                    addWeightUnit = unit
                                    incrementKgText = WorkoutLogic.formatWeight(
                                        WeightConversion.fromKg(kg, unit)
                                    )
                                }
                            )
                        }
                        OutlinedTextField(
                            value = incrementKgText,
                            onValueChange = { incrementKgText = it },
                            label = {
                                Text(
                                    stringResource(
                                        R.string.hint_increment_unit_format,
                                        WeightConversion.unitSuffix(addWeightUnit)
                                    )
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        val currentErrorMessage = errorMessage
                        if (currentErrorMessage != null) {
                            Text(text = currentErrorMessage, color = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        catalogViewModel.addMachine(
                            name = machineName,
                            muscleGroup = muscleGroup,
                            settingsNote = settingsNote,
                            incrementKgText = incrementKgText,
                            weightUnit = addWeightUnit,
                            weightDirection = addWeightDirection
                        ) { result ->
                            when (result) {
                                MachineEditResult.EMPTY_FIELDS -> errorMessage = errorRequiredFields
                                MachineEditResult.DUPLICATE_NAME -> errorMessage = errorMachineExists
                                MachineEditResult.SUCCESS -> showAddDialog = false
                            }
                        }
                    }) {
                        Text(stringResource(R.string.btn_save))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddDialog = false }) {
                        Text(stringResource(R.string.btn_cancel))
                    }
                }
            )
        }

        // Edit Existing Machine Dialog
        if (editingMachine != null) {
            AlertDialog(
                onDismissRequest = { editingMachine = null },
                title = { Text(stringResource(R.string.dialog_edit_machine_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = editMachineName,
                            onValueChange = { editMachineName = it },
                            label = { Text(stringResource(R.string.hint_machine_name)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        MuscleGroupAutocompleteField(
                            value = editMuscleGroup,
                            onValueChange = {
                                editMuscleGroup = it
                                editMuscleGroupSuggestionSelected = false
                            },
                            onSuggestionSelected = {
                                editMuscleGroup = it
                                editMuscleGroupSuggestionSelected = true
                            },
                            knownMuscleGroups = knownMuscleGroups,
                            suppressSuggestions = editMuscleGroupSuggestionSelected
                        )
                        OutlinedTextField(
                            value = editSettingsNote,
                            onValueChange = { editSettingsNote = it },
                            label = { Text(stringResource(R.string.hint_settings_note)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            WeightDirectionSelector(
                                selected = editWeightDirection,
                                onSelected = { editWeightDirection = it }
                            )
                            WeightUnitSelector(
                                selected = editWeightUnit,
                                onSelected = { unit ->
                                    val currentKg = WeightConversion.toKg(
                                        editIncrementKgText.toFloatOrNull() ?: 2.5f,
                                        editWeightUnit
                                    )
                                    editWeightUnit = unit
                                    editIncrementKgText = WorkoutLogic.formatWeight(
                                        WeightConversion.fromKg(currentKg, unit)
                                    )
                                }
                            )
                        }
                        OutlinedTextField(
                            value = editIncrementKgText,
                            onValueChange = { editIncrementKgText = it },
                            label = {
                                Text(
                                    stringResource(
                                        R.string.hint_increment_unit_format,
                                        WeightConversion.unitSuffix(editWeightUnit)
                                    )
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        val currentEditErrorMessage = editErrorMessage
                        if (currentEditErrorMessage != null) {
                            Text(text = currentEditErrorMessage, color = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        val current = editingMachine ?: return@Button
                        catalogViewModel.updateMachine(
                            current = current,
                            name = editMachineName,
                            muscleGroup = editMuscleGroup,
                            settingsNote = editSettingsNote,
                            incrementKgText = editIncrementKgText,
                            weightUnit = editWeightUnit,
                            weightDirection = editWeightDirection
                        ) { result ->
                            when (result) {
                                MachineEditResult.EMPTY_FIELDS -> editErrorMessage = errorRequiredFields
                                MachineEditResult.DUPLICATE_NAME -> editErrorMessage = errorMachineExists
                                MachineEditResult.SUCCESS -> editingMachine = null
                            }
                        }
                    }) {
                        Text(stringResource(R.string.btn_save))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { editingMachine = null }) {
                        Text(stringResource(R.string.btn_cancel))
                    }
                }
            )
        }
    }
}

@Composable
private fun MuscleGroupAutocompleteField(
    value: String,
    onValueChange: (String) -> Unit,
    onSuggestionSelected: (String) -> Unit,
    knownMuscleGroups: List<String>,
    suppressSuggestions: Boolean
) {
    val label = stringResource(R.string.hint_muscle_group)
    val noSuggestions = stringResource(R.string.muscle_group_no_suggestions)
    var fieldFocused by remember { mutableStateOf(false) }

    Box(modifier = Modifier.zIndex(1f)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { state -> fieldFocused = state.isFocused }
        )
        if (fieldFocused && !suppressSuggestions) {
            val suggestions = CatalogViewModel.filterMuscleGroupSuggestions(knownMuscleGroups, value)
            Surface(
                shape = MaterialTheme.shapes.small,
                tonalElevation = 4.dp,
                shadowElevation = 4.dp,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopStart)
            ) {
                if (suggestions.isEmpty()) {
                    Text(
                        text = noSuggestions,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 160.dp)) {
                        items(suggestions) { suggestion ->
                            Text(
                                text = suggestion,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSuggestionSelected(suggestion) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeightDirectionSelector(
    selected: WeightDirection,
    onSelected: (WeightDirection) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = selected == WeightDirection.INVERSE,
            onClick = { onSelected(WeightDirection.INVERSE) },
            label = { Text(stringResource(R.string.assisted_machine_chip)) }
        )
    }
}

private fun WeightUnitSelector(
    selected: WeightUnit,
    onSelected: (WeightUnit) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(WeightUnit.KG, WeightUnit.LBS).forEach { unit ->
            FilterChip(
                selected = selected == unit,
                onClick = { onSelected(unit) },
                label = { Text(WeightConversion.unitSuffix(unit)) }
            )
        }
    }
}
