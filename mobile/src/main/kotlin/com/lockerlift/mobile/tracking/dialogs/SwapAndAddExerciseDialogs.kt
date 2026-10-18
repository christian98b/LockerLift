package com.lockerlift.mobile.tracking.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lockerlift.core.model.Machine
import com.lockerlift.core.model.ValidationResult
import com.lockerlift.core.model.WorkoutLogic
import com.lockerlift.mobile.R

/**
 * Dialog for swapping the machine of a station while preserving its logged
 * sets (menu entry "Swap exercise").
 */
@Composable
fun SwapExerciseDialog(
    machines: List<Machine>,
    onSwapMachine: (Machine) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_swap_exercise_title)) },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(machines) { _, m ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            onSwapMachine(m)
                            onDismiss()
                        }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(text = m.name, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                            Text(text = m.targetMuscleGroup, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    )
}

/**
 * Dialog for adding an exercise to the running session: either by picking
 * an existing machine or by creating a new machine on the fly.
 */
@Composable
fun AddExerciseDialog(
    machines: List<Machine>,
    onAppendMachine: (Machine) -> Unit,
    onCreateMachineAndAppend: (Machine) -> Unit,
    onDismiss: () -> Unit
) {
    var showNewMachineForm by remember { mutableStateOf(false) }
    var newMachineName by remember { mutableStateOf("") }
    var newMuscleGroup by remember { mutableStateOf("") }
    var newSettingsNote by remember { mutableStateOf("") }
    var newIncrement by remember { mutableStateOf("2.5") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (showNewMachineForm) R.string.dialog_new_machine_title else R.string.dialog_select_machine_title))
        },
        text = {
            if (showNewMachineForm) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newMachineName,
                        onValueChange = { newMachineName = it; errorMessage = null },
                        label = { Text(stringResource(R.string.hint_machine_name)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newMuscleGroup,
                        onValueChange = { newMuscleGroup = it; errorMessage = null },
                        label = { Text(stringResource(R.string.hint_muscle_group)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newSettingsNote,
                        onValueChange = { newSettingsNote = it },
                        label = { Text(stringResource(R.string.hint_settings_note)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newIncrement,
                        onValueChange = { newIncrement = it },
                        label = { Text(stringResource(R.string.hint_increment)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    val currentErrorMessage = errorMessage
                    if (currentErrorMessage != null) {
                        Text(text = currentErrorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(machines) { _, m ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                onAppendMachine(m)
                                onDismiss()
                            }
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(text = m.name, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                Text(text = m.targetMuscleGroup, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (showNewMachineForm) {
                Button(
                    onClick = {
                        val validation = WorkoutLogic.validateNewMachine(newMachineName, machines.map { it.name })
                        if (validation is ValidationResult.EmptyName) {
                            errorMessage = "Name cannot be empty"
                            return@Button
                        }
                        if (validation is ValidationResult.DuplicateName) {
                            errorMessage = "Machine already exists"
                            return@Button
                        }

                        onCreateMachineAndAppend(
                            Machine(
                                name = newMachineName.trim(),
                                targetMuscleGroup = newMuscleGroup.trim().ifEmpty { "General" },
                                machineSettingsNote = newSettingsNote.trim(),
                                defaultIncrementKg = newIncrement.toFloatOrNull() ?: 2.5f
                            )
                        )
                        onDismiss()
                    }
                ) {
                    Text(stringResource(R.string.btn_save))
                }
            } else {
                Button(onClick = { showNewMachineForm = true }) {
                    Text(stringResource(R.string.add_machine))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    )
}
