package com.lockerlift.mobile.ui.catalog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.lockerlift.core.database.entity.toDomainModel
import com.lockerlift.core.database.entity.toEntity
import com.lockerlift.core.model.Machine
import com.lockerlift.mobile.LockerLiftMobileApp
import com.lockerlift.mobile.R
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(app: LockerLiftMobileApp) {
    val machineDao = remember { app.database.machineDao() }
    val machinesState by machineDao.getAllMachinesFlow().collectAsState(initial = emptyList())
    val coroutineScope = rememberCoroutineScope()

    var showDialog by remember { mutableStateOf(false) }
    var machineName by remember { mutableStateOf("") }
    var muscleGroup by remember { mutableStateOf("") }
    var settingsNote by remember { mutableStateOf("") }
    var incrementKgText by remember { mutableStateOf("2.5") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

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
                showDialog = true
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
                    modifier = Modifier.fillMaxWidth(),
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

        if (showDialog) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
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
                            showDialog = false
                        }
                    }) {
                        Text(stringResource(R.string.btn_save))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDialog = false }) {
                        Text(stringResource(R.string.btn_cancel))
                    }
                }
            )
        }
    }
}
