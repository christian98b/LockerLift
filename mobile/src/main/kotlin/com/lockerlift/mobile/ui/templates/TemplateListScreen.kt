package com.lockerlift.mobile.ui.templates

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lockerlift.core.database.entity.MachineEntity
import com.lockerlift.core.database.entity.toDomainModel
import com.lockerlift.core.database.entity.toEntity
import com.lockerlift.core.database.model.WorkoutTemplateWithMachines
import com.lockerlift.core.model.WorkoutTemplate
import com.lockerlift.core.sync.SyncPayloadSerializer
import com.lockerlift.core.sync.WearableDataLayerManager
import com.lockerlift.core.sync.WorkoutTemplatePayload
import com.lockerlift.mobile.LockerLiftMobileApp
import com.lockerlift.mobile.R
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateListScreen(app: LockerLiftMobileApp) {
    val context = LocalContext.current
    val templateDao = remember { app.database.workoutTemplateDao() }
    val templatesState by templateDao.getAllActiveTemplatesWithMachinesFlow().collectAsState(initial = emptyList())
    val coroutineScope = rememberCoroutineScope()

    var showNewDialog by remember { mutableStateOf(false) }
    var newTemplateName by remember { mutableStateOf("") }
    var newTemplateDesc by remember { mutableStateOf("") }

    var editingTemplate by remember { mutableStateOf<WorkoutTemplateWithMachines?>(null) }
    var editTemplateName by remember { mutableStateOf("") }
    var editTemplateDesc by remember { mutableStateOf("") }
    var assignedMachines by remember { mutableStateOf<List<MachineEntity>>(emptyList()) }
    var showMachinePicker by remember { mutableStateOf(false) }

    LaunchedEffect(editingTemplate) {
        editingTemplate?.let { item ->
            editTemplateName = item.template.name
            editTemplateDesc = item.template.description ?: ""
            assignedMachines = templateDao.getMachinesForTemplateOrdered(item.template.id)
        }
    }

    suspend fun syncAllTemplatesToWear() {
        val allActive = templateDao.getAllActiveTemplatesWithMachinesFlow().first()
        val payloads = allActive.map { item ->
            val orderedMachineIds = templateDao.getCrossRefsForTemplate(item.template.id).map { it.machineId }
            WorkoutTemplatePayload(
                template = item.template.toDomainModel(),
                machineIdsInOrder = orderedMachineIds
            )
        }
        val json = SyncPayloadSerializer.encodeTemplates(payloads)
        WearableDataLayerManager(context).syncTemplates(json)
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.templates_title)) })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                newTemplateName = ""
                newTemplateDesc = ""
                showNewDialog = true
            }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_template))
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
            items(templatesState, key = { it.template.id }) { item ->
                val template = item.template.toDomainModel()
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { editingTemplate = item },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = template.name, style = MaterialTheme.typography.titleMedium)
                        if (!template.description.isNullOrBlank()) {
                            Text(
                                text = template.description!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.exercises_assigned_format, item.machines.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // Create New Template Dialog
        if (showNewDialog) {
            AlertDialog(
                onDismissRequest = { showNewDialog = false },
                title = { Text(stringResource(R.string.dialog_new_template_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = newTemplateName,
                            onValueChange = { newTemplateName = it },
                            label = { Text(stringResource(R.string.hint_template_name)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = newTemplateDesc,
                            onValueChange = { newTemplateDesc = it },
                            label = { Text(stringResource(R.string.hint_template_desc)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        if (newTemplateName.isNotBlank()) {
                            coroutineScope.launch {
                                val newTemplate = WorkoutTemplate(
                                    name = newTemplateName.trim(),
                                    description = newTemplateDesc.takeIf { it.isNotBlank() }
                                )
                                templateDao.insertTemplate(newTemplate.toEntity())
                                syncAllTemplatesToWear()
                                showNewDialog = false
                            }
                        }
                    }) {
                        Text(stringResource(R.string.btn_create))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showNewDialog = false }) {
                        Text(stringResource(R.string.btn_cancel))
                    }
                }
            )
        }

        // Edit Template Dialog (Reordering, Adding, Removing Exercises)
        if (editingTemplate != null) {
            AlertDialog(
                onDismissRequest = { editingTemplate = null },
                title = { Text(stringResource(R.string.dialog_edit_template_title)) },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 450.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = editTemplateName,
                            onValueChange = { editTemplateName = it },
                            label = { Text(stringResource(R.string.hint_template_name)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = editTemplateDesc,
                            onValueChange = { editTemplateDesc = it },
                            label = { Text(stringResource(R.string.hint_template_desc)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.section_assigned_exercises),
                            style = MaterialTheme.typography.titleMedium
                        )

                        if (assignedMachines.isEmpty()) {
                            Text(
                                text = stringResource(R.string.no_exercises_in_template),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            assignedMachines.forEachIndexed { index, machine ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = machine.name,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            Text(
                                                text = machine.targetMuscleGroup,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        // Move Up Action (AK 2.1.3)
                                        IconButton(
                                            onClick = {
                                                if (index > 0) {
                                                    val list = assignedMachines.toMutableList()
                                                    val item = list.removeAt(index)
                                                    list.add(index - 1, item)
                                                    assignedMachines = list
                                                }
                                            },
                                            enabled = index > 0
                                        ) {
                                            Icon(
                                                Icons.Default.ArrowUpward,
                                                contentDescription = stringResource(R.string.action_move_up)
                                            )
                                        }
                                        // Move Down Action (AK 2.1.3)
                                        IconButton(
                                            onClick = {
                                                if (index < assignedMachines.size - 1) {
                                                    val list = assignedMachines.toMutableList()
                                                    val item = list.removeAt(index)
                                                    list.add(index + 1, item)
                                                    assignedMachines = list
                                                }
                                            },
                                            enabled = index < assignedMachines.size - 1
                                        ) {
                                            Icon(
                                                Icons.Default.ArrowDownward,
                                                contentDescription = stringResource(R.string.action_move_down)
                                            )
                                        }
                                        // Remove Action (AK 2.2.2 - removes from template, leaves catalog intact)
                                        IconButton(
                                            onClick = {
                                                val list = assignedMachines.toMutableList()
                                                list.removeAt(index)
                                                assignedMachines = list
                                            }
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = stringResource(R.string.action_remove),
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Add Machine Picker Action (AK 2.2.1)
                        Button(
                            onClick = { showMachinePicker = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.btn_add_exercise))
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        if (editTemplateName.isNotBlank()) {
                            coroutineScope.launch {
                                val current = editingTemplate?.template ?: return@launch
                                val updated = current.copy(
                                    name = editTemplateName.trim(),
                                    description = editTemplateDesc.takeIf { it.isNotBlank() },
                                    updatedAt = System.currentTimeMillis()
                                )
                                templateDao.saveTemplateWithMachines(
                                    template = updated,
                                    machineIdsInOrder = assignedMachines.map { it.id }
                                )
                                syncAllTemplatesToWear()
                                editingTemplate = null
                            }
                        }
                    }) {
                        Text(stringResource(R.string.btn_save))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { editingTemplate = null }) {
                        Text(stringResource(R.string.btn_cancel))
                    }
                }
            )
        }

        // Machine Picker Dialog to add a machine from catalog to template
        if (showMachinePicker) {
            val catalogMachines by app.database.machineDao().getAllMachinesFlow().collectAsState(initial = emptyList())
            AlertDialog(
                onDismissRequest = { showMachinePicker = false },
                title = { Text(stringResource(R.string.dialog_select_machine_title)) },
                text = {
                    if (catalogMachines.isEmpty()) {
                        Text(stringResource(R.string.no_machines_in_catalog))
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(catalogMachines, key = { it.id }) { catMachine ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            assignedMachines = assignedMachines + catMachine
                                            showMachinePicker = false
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = catMachine.name,
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Text(
                                            text = catMachine.targetMuscleGroup,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showMachinePicker = false }) {
                        Text(stringResource(R.string.btn_cancel))
                    }
                }
            )
        }
    }
}
