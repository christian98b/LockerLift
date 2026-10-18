package com.lockerlift.mobile.tracking.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.lockerlift.core.model.WeightConversion
import com.lockerlift.core.model.WeightUnit
import com.lockerlift.core.model.WorkoutLogic
import com.lockerlift.core.model.WorkoutSet
import com.lockerlift.mobile.R
import com.lockerlift.mobile.tracking.components.RepsStepperControls
import com.lockerlift.mobile.tracking.components.WeightStepperControls

/**
 * Dialog for editing an already logged set (weight, reps) or deleting it.
 */
@Composable
fun EditSetDialog(
    instanceId: String,
    machine: Machine?,
    sets: List<WorkoutSet>,
    setIndex: Int,
    onUpdateSet: (String, Int, Float, Int) -> Unit,
    onDeleteSet: (String, Int) -> Unit,
    onDismiss: () -> Unit
) {
    val setToEdit = sets.getOrNull(setIndex) ?: return
    val machineName = machine?.name ?: ""
    val editUnit = machine?.weightUnit ?: WeightUnit.KG
    val editUnitSuffix = WeightConversion.unitSuffix(editUnit)
    val increment = machine?.defaultIncrementKg ?: 2.5f

    var editWeightInput by remember(setToEdit) { mutableStateOf(WeightConversion.formatWeightForUnit(setToEdit.weightKg, editUnit)) }
    var editRepsInput by remember(setToEdit) { mutableStateOf(setToEdit.reps.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.edit_set_dialog_title, setToEdit.setNumber, machineName))
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Weight input & quick steppers
                Text(
                    text = stringResource(R.string.weight_label_unit_format, editUnitSuffix),
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                OutlinedTextField(
                    value = editWeightInput,
                    onValueChange = { editWeightInput = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                WeightStepperControls(
                    weightInput = editWeightInput,
                    incrementKg = increment,
                    unit = editUnit,
                    stackValuesKg = machine?.weightStackValuesKg ?: emptyList(),
                    onWeightChanged = { editWeightInput = it }
                )

                // Reps input & quick steppers
                Text(text = stringResource(R.string.reps_label), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                OutlinedTextField(
                    value = editRepsInput,
                    onValueChange = { editRepsInput = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                RepsStepperControls(
                    repsInput = editRepsInput,
                    onRepsChanged = { editRepsInput = it }
                )

                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(
                    onClick = {
                        onDeleteSet(instanceId, setIndex)
                        onDismiss()
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.btn_delete_set))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalWeight = WorkoutLogic.clampWeight(
                        WeightConversion.toKg(editWeightInput.toFloatOrNull() ?: 0f, editUnit)
                    )
                    val finalReps = WorkoutLogic.clampReps(editRepsInput.toIntOrNull() ?: 1)

                    onUpdateSet(instanceId, setIndex, finalWeight, finalReps)
                    onDismiss()
                }
            ) {
                Text(stringResource(R.string.btn_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    )
}
