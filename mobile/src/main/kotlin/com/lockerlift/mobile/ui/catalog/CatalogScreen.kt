package com.lockerlift.mobile.ui.catalog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lockerlift.core.database.entity.MachineEntity
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

    var showAddDialog by remember { mutableStateOf(false) }
    var machineName by remember { mutableStateOf("") }
    var muscleGroup by remember { mutableStateOf("") }
    var settingsNote by remember { mutableStateOf("") }
    var incrementKgText by remember { mutableStateOf("2.5") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var editingMachine by remember { mutableStateOf<MachineEntity?>(null) }
    var editMachineName by remember { mutableStateOf("") }
    var editMuscleGroup by remember { mutableStateOf("") }
    var editSettingsNote by remember { mutableStateOf("") }
    var editIncrementKgText by remember { mutableStateOf("2.5") }
    var editErrorMessage by remember { mutableStateOf<String?>(null) }

    val errorRequiredFields = stringResource(R.string.error_required_fields)
    val errorMachineExists = stringResource(R.string.error_machine_exists)

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.catalog_title)) })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                machineName = ""
                muscleGroup = ""
                settingsNote = ""
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
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            editingMachine = machineEntity
                            editMachineName = machineEntity.name
                            editMuscleGroup = machineEntity.targetMuscleGroup
                            editSettingsNote = machineEntity.machineSettingsNote ?: ""
                            editIncrementKgText = machineEntity.defaultIncrementKg.toString()
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
                            text = stringResource(R.string.increment_format, machine.defaultIncrementKg.toString()),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
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
                        OutlinedTextField(
                            value = muscleGroup,
                            onValueChange = { muscleGroup = it },
                            label = { Text(stringResource(R.string.hint_muscle_group)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = settingsNote,
                            onValueChange = { settingsNote = it },
                            label = { Text(stringResource(R.string.hint_settings_note)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = incrementKgText,
                            onValueChange = { incrementKgText = it },
                            label = { Text(stringResource(R.string.hint_increment)) },
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
                            incrementKgText = incrementKgText
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
                        OutlinedTextField(
                            value = editMuscleGroup,
                            onValueChange = { editMuscleGroup = it },
                            label = { Text(stringResource(R.string.hint_muscle_group)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = editSettingsNote,
                            onValueChange = { editSettingsNote = it },
                            label = { Text(stringResource(R.string.hint_settings_note)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = editIncrementKgText,
                            onValueChange = { editIncrementKgText = it },
                            label = { Text(stringResource(R.string.hint_increment)) },
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
                            incrementKgText = editIncrementKgText
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
