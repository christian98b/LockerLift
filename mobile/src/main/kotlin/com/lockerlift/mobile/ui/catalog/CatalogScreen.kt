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
import com.lockerlift.core.database.entity.toDomainModel
import com.lockerlift.core.database.entity.toEntity
import com.lockerlift.core.model.Machine
import com.lockerlift.mobile.LockerLiftMobileApp
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

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Maschinenkatalog") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                machineName = ""
                muscleGroup = ""
                settingsNote = ""
                errorMessage = null
                showDialog = true
            }) {
                Icon(Icons.Default.Add, contentDescription = "Maschine hinzufügen")
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
                            text = "Muskelgruppe: ${machine.targetMuscleGroup}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!machine.machineSettingsNote.isNullOrBlank()) {
                            Text(
                                text = "Setup: ${machine.machineSettingsNote}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = "Schrittweite: ${machine.defaultIncrementKg} kg",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        if (showDialog) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                title = { Text("Neue Maschine anlegen") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = machineName,
                            onValueChange = { machineName = it },
                            label = { Text("Name (z.B. Latzug)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = muscleGroup,
                            onValueChange = { muscleGroup = it },
                            label = { Text("Zielmuskelgruppe (z.B. Rücken)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = settingsNote,
                            onValueChange = { settingsNote = it },
                            label = { Text("Geräteeinstellungen (optional)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = incrementKgText,
                            onValueChange = { incrementKgText = it },
                            label = { Text("Schrittweite in kg (z.B. 2.5)") },
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
                            errorMessage = "Name und Muskelgruppe sind Pflichtfelder."
                            return@Button
                        }
                        coroutineScope.launch {
                            val existing = machineDao.getMachineByName(machineName.trim())
                            if (existing != null) {
                                errorMessage = "Eine Maschine mit diesem Namen existiert bereits."
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
                        Text("Speichern")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDialog = false }) {
                        Text("Abbrechen")
                    }
                }
            )
        }
    }
}
