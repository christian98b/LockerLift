package com.lockerlift.mobile.ui.activities

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lockerlift.core.model.ActivityEntry
import com.lockerlift.core.model.ActivityFieldDefinition
import com.lockerlift.core.model.ActivityFieldType
import com.lockerlift.core.model.ActivityType
import com.lockerlift.mobile.R
import java.util.UUID

private enum class EditableFieldType(val modelType: ActivityFieldType, val labelRes: Int) {
    TEXT(ActivityFieldType.TEXT, R.string.activity_field_type_text),
    NUMBER(ActivityFieldType.NUMBER, R.string.activity_field_type_number)
}

/**
 * Dialog for logging a new activity entry (Issue #50): pick a type,
 * set the date, and fill in the type's custom fields.
 */
@Composable
fun LogActivityDialog(
    availableTypes: List<ActivityType>,
    onDismiss: () -> Unit,
    onSave: (ActivityEntry, String) -> Unit
) {
    var selectedTypeId by remember {
        mutableStateOf(availableTypes.firstOrNull()?.id ?: "")
    }
    var notes by remember { mutableStateOf("") }
    var typeMenuExpanded by remember { mutableStateOf(false) }
    val selectedType = availableTypes.firstOrNull { it.id == selectedTypeId }
    val fieldValues = remember(selectedTypeId) { mutableStateMapOf<String, String>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.activity_log_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (availableTypes.isEmpty()) {
                    Text(stringResource(R.string.activity_empty))
                } else {
                    Text(
                        text = stringResource(R.string.activity_type_label),
                        style = MaterialTheme.typography.labelMedium
                    )
                    ExposedDropdownMenuBox(
                        expanded = typeMenuExpanded,
                        onExpandedChange = { typeMenuExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedType?.name ?: "",
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(typeMenuExpanded)
                            }
                        )
                        ExposedDropdownMenu(
                            expanded = typeMenuExpanded,
                            onDismissRequest = { typeMenuExpanded = false }
                        ) {
                            availableTypes.forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type.name) },
                                    onClick = {
                                        selectedTypeId = type.id
                                        typeMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    selectedType?.fields?.forEach { field ->
                        OutlinedTextField(
                            value = fieldValues[field.id] ?: "",
                            onValueChange = { fieldValues[field.id] = it },
                            label = { Text(field.name) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text(stringResource(R.string.activity_notes_label)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    selectedType?.let { type ->
                        onSave(
                            ActivityEntry(
                                id = UUID.randomUUID().toString(),
                                activityTypeId = type.id,
                                date = System.currentTimeMillis(),
                                fieldValues = fieldValues.toMap(),
                                notes = notes.takeIf { it.isNotBlank() }
                            ),
                            type.name
                        )
                    }
                },
                enabled = selectedType != null
            ) { Text(stringResource(R.string.activity_btn_log)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) }
        }
    )
}

/**
 * Dialog for editing an existing activity entry (Issue #50).
 */
@Composable
fun EditActivityDialog(
    entry: ActivityEntry,
    type: ActivityType,
    onDismiss: () -> Unit,
    onSave: (ActivityEntry, String) -> Unit
) {
    var notes by remember { mutableStateOf(entry.notes ?: "") }
    val fieldValues = remember {
        val map = mutableStateMapOf<String, String>()
        entry.fieldValues.forEach { (k, v) -> map[k] = v }
        map
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.activity_edit_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(text = type.name, style = MaterialTheme.typography.titleMedium)
                type.fields.forEach { field ->
                    OutlinedTextField(
                        value = fieldValues[field.id] ?: "",
                        onValueChange = { fieldValues[field.id] = it },
                        label = { Text(field.name) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.activity_notes_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        entry.copy(
                            fieldValues = fieldValues.toMap(),
                            notes = notes.takeIf { it.isNotBlank() }
                        ),
                        type.name
                    )
                }
            ) { Text(stringResource(R.string.activity_btn_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) }
        }
    )
}

/**
 * Dialog for managing customizable activity types (Issue #50, owner
 * comment): create new types with user-defined fields, edit field lists,
 * delete custom types.
 */
@Composable
fun ManageActivityTypesDialog(
    types: List<ActivityType>,
    onDismiss: () -> Unit,
    onCreateType: (ActivityType) -> Unit,
    onRequestDeleteType: (ActivityType) -> Unit
) {
    var newTypeName by remember { mutableStateOf("") }
    var newTypeFields by remember { mutableStateOf(listOf<EditableField>()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.activity_manage_types_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                types.forEach { type ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(type.name, style = MaterialTheme.typography.titleSmall)
                            if (type.isBuiltin) {
                                Text(
                                    text = stringResource(R.string.activity_type_builtin_badge),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (!type.isBuiltin) {
                            IconButton(onClick = { onRequestDeleteType(type) }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(
                                        R.string.activity_delete_type_dialog_title
                                    ),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }

                Divider()

                OutlinedTextField(
                    value = newTypeName,
                    onValueChange = { newTypeName = it },
                    label = { Text(stringResource(R.string.activity_type_name_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                newTypeFields.forEachIndexed { index, field ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = field.name,
                            onValueChange = { newValue ->
                                newTypeFields = newTypeFields.toMutableList()
                                    .also { list -> list[index] = list[index].copy(name = newValue) }
                            },
                            label = { Text(stringResource(R.string.activity_field_name_hint)) },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        FilterChip(
                            selected = field.type == EditableFieldType.NUMBER,
                            onClick = {
                                newTypeFields = newTypeFields.toMutableList().also {
                                    it[index] = it[index].copy(
                                        type = if (field.type == EditableFieldType.NUMBER) EditableFieldType.TEXT else EditableFieldType.NUMBER
                                    )
                                }
                            },
                            label = {
                                Text(
                                    stringResource(
                                        if (field.type == EditableFieldType.NUMBER) R.string.activity_field_type_number
                                        else R.string.activity_field_type_text
                                    )
                                )
                            }
                        )
                        IconButton(onClick = {
                            newTypeFields = newTypeFields.toMutableList().also { it.removeAt(index) }
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = null)
                        }
                    }
                }
                TextButton(onClick = {
                    newTypeFields = newTypeFields + EditableField(name = "", type = EditableFieldType.TEXT)
                }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.activity_add_field))
                }
                Button(
                    onClick = {
                        if (newTypeName.isNotBlank()) {
                            onCreateType(
                                ActivityType(
                                    id = UUID.randomUUID().toString(),
                                    name = newTypeName.trim(),
                                    fields = newTypeFields
                                        .filter { it.name.isNotBlank() }
                                        .map { ActivityFieldDefinition(name = it.name.trim(), type = it.type.modelType) }
                                )
                            )
                            newTypeName = ""
                            newTypeFields = listOf()
                        }
                    },
                    enabled = newTypeName.isNotBlank()
                ) {
                    Text(stringResource(R.string.activity_create_type))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.activity_btn_save)) }
        }
    )
}

private data class EditableField(
    val name: String,
    val type: EditableFieldType
)
