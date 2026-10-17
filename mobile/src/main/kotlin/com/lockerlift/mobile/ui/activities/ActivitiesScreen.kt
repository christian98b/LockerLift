package com.lockerlift.mobile.ui.activities

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lockerlift.core.database.LockerLiftDatabase
import com.lockerlift.core.database.entity.toDomainModel
import com.lockerlift.core.database.entity.toEntity
import com.lockerlift.core.database.model.ActivityEntryWithType
import com.lockerlift.core.model.ActivityEntry
import com.lockerlift.core.model.ActivityType
import com.lockerlift.mobile.R
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Activity journal screen (Issue #50): lists logged non-gym activities
 * alongside the workout history and offers logging, editing, deletion,
 * and management of customizable activity types.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivitiesScreen(
    database: LockerLiftDatabase,
    onExportToHealthConnect: suspend (ActivityEntry, String) -> Unit,
    onDeleteFromHealthConnect: suspend (String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val activityTypeDao = remember { database.activityTypeDao() }
    val activityEntryDao = remember { database.activityEntryDao() }

    val typesState by activityTypeDao.getAllActivityTypesFlow().collectAsState(initial = emptyList())
    val entriesState by activityEntryDao.getAllEntriesWithTypeFlow().collectAsState(initial = emptyList())

    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }

    var showLogDialog by remember { mutableStateOf(false) }
    var showManageDialog by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<Pair<ActivityEntryWithType, ActivityType>?>(null) }
    var entryToDelete by remember { mutableStateOf<ActivityEntryWithType?>(null) }
    var typeToDelete by remember { mutableStateOf<ActivityType?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.activity_manage_types_title)) },
                actions = {
                    IconButton(onClick = { showManageDialog = true }) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = stringResource(R.string.activity_manage_types_title)
                        )
                    }
                    IconButton(onClick = { showLogDialog = true }) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = stringResource(R.string.activity_log_title)
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (entriesState.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.activity_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(entriesState, key = { it.entry.id }) { item ->
                    val type = item.type?.toDomainModel()
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = type?.name ?: stringResource(R.string.activity_type_label),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = dateFormat.format(Date(item.entry.date)),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = {
                                        if (type != null) editingEntry = item to type
                                    }) {
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = stringResource(R.string.activity_edit_title),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    IconButton(onClick = { entryToDelete = item }) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = stringResource(R.string.activity_delete_dialog_title),
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                            type?.let { t ->
                                val values = ActivityEntry.valuesFromJsonSafe(item.entry.fieldValuesJson)
                                if (values.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    t.fields.forEach { field ->
                                        values[field.id]?.let { value ->
                                            Text(
                                                text = "• ${field.name}: $value",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                }
                            }
                            item.entry.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = notes,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontStyle = FontStyle.Italic,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // --- LOG ACTIVITY DIALOG ---
    if (showLogDialog) {
        LogActivityDialog(
            availableTypes = typesState.map { it.toDomainModel() },
            onDismiss = { showLogDialog = false },
            onSave = { entry, typeName ->
                coroutineScope.launch {
                    activityEntryDao.insertEntry(entry.toEntity())
                    onExportToHealthConnect(entry, typeName)
                    showLogDialog = false
                }
            }
        )
    }

    // --- EDIT ACTIVITY DIALOG ---
    editingEntry?.let { (item, type) ->
        EditActivityDialog(
            entry = item.entry.toDomainModel(),
            type = type,
            onDismiss = { editingEntry = null },
            onSave = { updatedEntry, typeName ->
                coroutineScope.launch {
                    activityEntryDao.updateEntry(updatedEntry.toEntity())
                    onExportToHealthConnect(updatedEntry, typeName)
                    editingEntry = null
                }
            }
        )
    }

    // --- DELETE ENTRY CONFIRMATION ---
    entryToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { entryToDelete = null },
            title = { Text(stringResource(R.string.activity_delete_dialog_title)) },
            text = { Text(stringResource(R.string.activity_delete_dialog_confirm)) },
            confirmButton = {
                Button(
                    onClick = {
                        val entryId = target.entry.id
                        coroutineScope.launch {
                            activityEntryDao.deleteEntryById(entryId)
                            onDeleteFromHealthConnect(entryId)
                            entryToDelete = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text(stringResource(R.string.btn_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { entryToDelete = null }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    // --- MANAGE TYPES DIALOG ---
    if (showManageDialog) {
        ManageActivityTypesDialog(
            types = typesState.map { it.toDomainModel() },
            onDismiss = { showManageDialog = false },
            onCreateType = { type ->
                coroutineScope.launch { activityTypeDao.insertActivityType(type.toEntity()) }
            },
            onRequestDeleteType = { type -> typeToDelete = type }
        )
    }

    // --- DELETE TYPE CONFIRMATION ---
    typeToDelete?.let { type ->
        AlertDialog(
            onDismissRequest = { typeToDelete = null },
            title = { Text(stringResource(R.string.activity_delete_type_dialog_title)) },
            text = { Text(stringResource(R.string.activity_delete_type_dialog_confirm)) },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            val entries = activityEntryDao.getEntriesByTypeId(type.id)
                            activityTypeDao.deleteActivityTypeById(type.id)
                            entries.forEach { onDeleteFromHealthConnect(it.id) }
                            typeToDelete = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text(stringResource(R.string.btn_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { typeToDelete = null }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }
}

private fun ActivityEntry.valuesFromJsonSafe(json: String): Map<String, String> =
    runCatching { ActivityEntry.valuesFromJson(json) }.getOrDefault(emptyMap())
