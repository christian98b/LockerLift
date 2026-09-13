package com.lockerlift.mobile.ui.templates

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.lockerlift.core.database.entity.toDomainModel
import com.lockerlift.core.database.entity.toEntity
import com.lockerlift.core.model.WorkoutTemplate
import com.lockerlift.mobile.LockerLiftMobileApp
import com.lockerlift.mobile.R
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateListScreen(app: LockerLiftMobileApp) {
    val templateDao = remember { app.database.workoutTemplateDao() }
    val templatesState by templateDao.getAllActiveTemplatesWithMachinesFlow().collectAsState(initial = emptyList())
    val coroutineScope = rememberCoroutineScope()

    var showDialog by remember { mutableStateOf(false) }
    var templateName by remember { mutableStateOf("") }
    var templateDesc by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.templates_title)) })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                templateName = ""
                templateDesc = ""
                showDialog = true
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
                    modifier = Modifier.fillMaxWidth(),
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

        if (showDialog) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                title = { Text(stringResource(R.string.dialog_new_template_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = templateName,
                            onValueChange = { templateName = it },
                            label = { Text(stringResource(R.string.hint_template_name)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = templateDesc,
                            onValueChange = { templateDesc = it },
                            label = { Text(stringResource(R.string.hint_template_desc)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        if (templateName.isNotBlank()) {
                            coroutineScope.launch {
                                val newTemplate = WorkoutTemplate(
                                    name = templateName.trim(),
                                    description = templateDesc.takeIf { it.isNotBlank() }
                                )
                                templateDao.insertTemplate(newTemplate.toEntity())
                                showDialog = false
                            }
                        }
                    }) {
                        Text(stringResource(R.string.btn_create))
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
