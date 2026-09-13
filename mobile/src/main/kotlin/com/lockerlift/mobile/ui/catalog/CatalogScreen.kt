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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lockerlift.core.database.entity.MachineEntity
import com.lockerlift.core.database.entity.toDomainModel
import com.lockerlift.core.database.entity.toEntity
import com.lockerlift.core.model.Machine
import com.lockerlift.core.sync.SyncPayloadSerializer
import com.lockerlift.core.sync.WearableDataLayerManager
import com.lockerlift.mobile.LockerLiftMobileApp
import com.lockerlift.mobile.R
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(app: LockerLiftMobileApp) {
    val context = LocalContext.current
    val machineDao = remember { app.database.machineDao() }
    val machinesState by machineDao.getAllMachinesFlow().collectAsState(initial = emptyList())
    val coroutineScope = rememberCoroutineScope()

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

    suspend fun syncCatalogToWear() {
        val allMachines = machineDao.getAllMachines()
        val catalogJson = SyncPayloadSerializer.encodeMachines(allMachines.map { it.toDomainModel() })
        WearableDataLayerManager(context).syncEquipmentCatalog(catalogJson)
    }

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
                        if (!machine.machineSettingsNote.isNullOrBlank()) {
                            Text(
                                text = stringResource(R.string.setup_format, machine.machineSettingsNote!!),
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
                        if (errorMessage != null) {
                            Text(text = errorMessage!!, color = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        if (machineName.isBlank() || muscleGroup.isBlank()) {
                            errorMessage = errorRequiredFields
                            return@Button
                        }
                        coroutineScope.launch {
                            val existing = machineDao.getMachineByName(machineName.trim())
                            if (existing != null) {
                                errorMessage = errorMachineExists
                                return@launch
                            }
                            val increment = incrementKgText.toFloatOrNull() ?: 2.5f
                            val newMachine = Machine(
                                name = machineName.trim(),
                                targetMuscleGroup = muscleGroup.trim(),
                                machineSettingsNote = settingsNote.takeIf { it.isNotBlank() },
                                defaultIncrementKg = increment
                            )
                            machineDao.insertMachine(newMachine.toEntity())
                            syncCatalogToWear()
                            showAddDialog = false
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
                        if (editErrorMessage != null) {
                            Text(text = editErrorMessage!!, color = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        if (editMachineName.isBlank() || editMuscleGroup.isBlank()) {
                            editErrorMessage = errorRequiredFields
                            return@Button
                        }
                        coroutineScope.launch {
                            val current = editingMachine ?: return@launch
                            val existing = machineDao.getMachineByName(editMachineName.trim())
                            if (existing != null && existing.id != current.id) {
                                editErrorMessage = errorMachineExists
                                return@launch
                            }
                            val increment = editIncrementKgText.toFloatOrNull() ?: 2.5f
                            val updated = current.copy(
                                name = editMachineName.trim(),
                                targetMuscleGroup = editMuscleGroup.trim(),
                                machineSettingsNote = editSettingsNote.takeIf { it.isNotBlank() },
                                defaultIncrementKg = increment,
                                updatedAt = System.currentTimeMillis()
                            )
                            machineDao.updateMachine(updated)
                            syncCatalogToWear()
                            editingMachine = null
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
