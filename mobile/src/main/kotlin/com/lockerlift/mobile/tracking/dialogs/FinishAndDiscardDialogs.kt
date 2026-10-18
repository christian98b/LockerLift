package com.lockerlift.mobile.tracking.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lockerlift.core.model.SessionMachineInstance
import com.lockerlift.core.model.WorkoutSet
import com.lockerlift.mobile.R

/**
 * Finish-workout dialog: optional notes, total set count and the
 * consolidate-template prompt when the session deviated from the template.
 */
@Composable
fun FinishWorkoutDialog(
    instances: List<SessionMachineInstance>,
    setsByInstance: Map<String, List<WorkoutSet>>,
    hasVariations: Boolean,
    onFinish: (Boolean, String) -> Unit,
    onDismiss: () -> Unit
) {
    var workoutNotes by remember { mutableStateOf("") }
    var consolidateTemplate by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.finish_workout_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val totalSets = setsByInstance.values.sumOf { it.size }
                Text(text = stringResource(R.string.sets_completed_format, totalSets))

                OutlinedTextField(
                    value = workoutNotes,
                    onValueChange = { workoutNotes = it },
                    label = { Text(stringResource(R.string.workout_notes_hint)) },
                    modifier = Modifier.fillMaxWidth()
                )

                if (hasVariations) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = consolidateTemplate,
                            onCheckedChange = { consolidateTemplate = it }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.consolidate_template_title),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onFinish(consolidateTemplate, workoutNotes) }
            ) {
                Text(stringResource(R.string.btn_save_and_finish))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    )
}

/**
 * Confirmation dialog before discarding the active workout without saving.
 */
@Composable
fun DiscardWorkoutDialog(
    onDiscard: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.discard_workout)) },
        text = { Text(stringResource(R.string.discard_workout_confirm)) },
        confirmButton = {
            Button(
                onClick = {
                    onDiscard()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text(stringResource(R.string.discard_workout))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    )
}
