package com.lockerlift.mobile.ui.templates

import androidx.compose.foundation.background
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
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lockerlift.core.database.entity.MachineEntity
import com.lockerlift.core.database.entity.toDomainModel
import com.lockerlift.core.database.model.WorkoutTemplateWithMachines
import com.lockerlift.core.model.WorkoutTemplate
import com.lockerlift.core.sync.PullRefreshSyncResult
import com.lockerlift.core.sync.SyncPayloadSerializer
import com.lockerlift.core.sync.SyncPullRefreshCoordinator
import com.lockerlift.core.sync.WearableDataLayerManager
import com.lockerlift.core.sync.WorkoutTemplatePayload
import com.lockerlift.mobile.LockerLiftMobileApp
import com.lockerlift.mobile.R
import com.lockerlift.mobile.sync.MobileMasterDataSync
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateListScreen(app: LockerLiftMobileApp) {
    val context = LocalContext.current
    val templateListViewModel: TemplateListViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                TemplateListViewModel(application = app, database = app.database)
            }
        }
    )
    val templatesState by templateListViewModel.templates.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val dataLayerManager = remember { WearableDataLayerManager(context) }
    val syncCoordinator = remember { SyncPullRefreshCoordinator(dataLayerManager) }

    var newTemplateName by remember { mutableStateOf("") }
    var newTemplateDesc by remember { mutableStateOf("") }
    var showNewDialog by remember { mutableStateOf(false) }

    var editingTemplate by remember { mutableStateOf<WorkoutTemplateWithMachines?>(null) }
    var editTemplateName by remember { mutableStateOf("") }
    var editTemplateDesc by remember { mutableStateOf("") }
    var showMachinePicker by remember { mutableStateOf(false) }

    var isRefreshing by remember { mutableStateOf(false) }
    var deleteTemplateCandidate by remember { mutableStateOf<WorkoutTemplateWithMachines?>(null) }
    var infoMessage by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val assignedMachines by templateListViewModel.assignedMachines.collectAsState()
    val catalogMachines by templateListViewModel.catalogMachines.collectAsState()

    LaunchedEffect(editingTemplate) {
        editingTemplate?.let { item ->
            editTemplateName = item.template.name
            editTemplateDesc = item.template.description ?: ""
            templateListViewModel.loadAssignedMachines(item.template.id)
        }
    }

    suspend fun syncAllTemplatesToWear() {
        MobileMasterDataSync.pushAllMasterData(context, app.database, dataLayerManager)
    }

    fun triggerRefresh() {
        if (isRefreshing) return
        isRefreshing = true
        coroutineScope.launch {
            val result = syncCoordinator.executeSync(
                syncMasterDataAction = {
                    syncAllTemplatesToWear()
                }
            )
            isRefreshing = false
            when (result) {
                is PullRefreshSyncResult.Success -> {
                    val msg = if (result.itemsSyncedCount == 1) {
                        context.getString(R.string.sync_success_single)
                    } else {
                        context.getString(R.string.sync_success_format, result.itemsSyncedCount)
                    }
                    snackbarHostState.showSnackbar(msg)
                }
                is PullRefreshSyncResult.UpToDate -> {
                    snackbarHostState.showSnackbar(context.getString(R.string.sync_up_to_date))
                }
                is PullRefreshSyncResult.WatchUnreachable -> {
                    snackbarHostState.showSnackbar(context.getString(R.string.sync_watch_unreachable))
                }
                is PullRefreshSyncResult.Error -> {
                    snackbarHostState.showSnackbar(context.getString(R.string.sync_error_format, result.message))
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { triggerRefresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(templatesState, key = { it.template.id }) { item ->
                    val template = item.template.toDomainModel()
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            if (value == SwipeToDismissBoxValue.EndToStart) {
                                deleteTemplateCandidate = item
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
                            .clickable { editingTemplate = item },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(text = template.name, style = MaterialTheme.typography.titleMedium)
                            val templateDescription = template.description
                            if (!templateDescription.isNullOrBlank()) {
                                Text(
                                    text = templateDescription,
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
            }
        }

        LaunchedEffect(infoMessage) {
            infoMessage?.let {
                snackbarHostState.showSnackbar(it)
                infoMessage = null
            }
        }

        // Delete Template Confirmation Dialog (Issue #40)
        val templateToDelete = deleteTemplateCandidate
        if (templateToDelete != null) {
            AlertDialog(
                onDismissRequest = { deleteTemplateCandidate = null },
                title = { Text(stringResource(R.string.dialog_delete_template_title)) },
                text = { Text(stringResource(R.string.dialog_delete_template_confirm, templateToDelete.template.name)) },
                confirmButton = {
                    Button(
                        onClick = {
                            val target = deleteTemplateCandidate
                            deleteTemplateCandidate = null
                            if (target != null) {
                                templateListViewModel.deleteTemplate(
                                    target.template.id,
                                    target.template.name
                                ) { result ->
                                    infoMessage = when (result) {
                                        is TemplateDeleteResult.Deleted ->
                                            context.getString(R.string.template_deleted, result.templateName)
                                        is TemplateDeleteResult.BlockedActiveWorkout ->
                                            context.getString(R.string.template_delete_blocked_active_workout)
                                    }
                                }
                            }
                        }
                    ) {
                        Text(stringResource(R.string.btn_delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deleteTemplateCandidate = null }) {
                        Text(stringResource(R.string.btn_cancel))
                    }
                }
            )
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
                            templateListViewModel.createTemplate(
                                name = newTemplateName,
                                description = newTemplateDesc
                            )
                            showNewDialog = false
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
                                                    templateListViewModel.setAssignedMachines(
                                                        moveItem(assignedMachines, index, index - 1)
                                                    )
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
                                                    templateListViewModel.setAssignedMachines(
                                                        moveItem(assignedMachines, index, index + 1)
                                                    )
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
                                                templateListViewModel.setAssignedMachines(
                                                    assignedMachines.filterIndexed { i, _ -> i != index }
                                                )
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
                            val current = editingTemplate?.template
                            if (current != null) {
                                templateListViewModel.saveTemplate(
                                    current = current,
                                    name = editTemplateName,
                                    description = editTemplateDesc,
                                    machineIdsInOrder = assignedMachines.map { it.id }
                                )
                            }
                            editingTemplate = null
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
                                            templateListViewModel.addAssignedMachine(catMachine)
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

private fun moveItem(list: List<MachineEntity>, fromIndex: Int, toIndex: Int): List<MachineEntity> {
    val mutable = list.toMutableList()
    val item = mutable.removeAt(fromIndex)
    mutable.add(toIndex, item)
    return mutable
}
