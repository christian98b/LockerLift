package com.lockerlift.mobile.tracking.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lockerlift.core.database.entity.WorkoutSetEntity
import com.lockerlift.core.database.logic.WorkoutTrackingLogic
import com.lockerlift.core.model.Machine
import com.lockerlift.core.model.SessionMachineInstance
import com.lockerlift.core.model.SetType
import com.lockerlift.core.model.WeightConversion
import com.lockerlift.core.model.WeightDirection
import com.lockerlift.core.model.WeightStack
import com.lockerlift.core.model.WeightUnit
import com.lockerlift.core.model.WorkoutLogic
import com.lockerlift.core.model.WorkoutSet
import com.lockerlift.mobile.R
import com.lockerlift.mobile.tracking.components.RepsStepperControls
import com.lockerlift.mobile.tracking.components.WeightStepperControls

/**
 * Dialog for logging the next set of a station, including the double
 * progression proposal and weight/reps quick steppers.
 */
@Composable
fun LogSetDialog(
    instance: SessionMachineInstance,
    machine: Machine?,
    machines: List<Machine>,
    existingSets: List<WorkoutSet>,
    historicalSetsProvider: suspend (String) -> List<WorkoutSetEntity>,
    restTimerSeconds: Int,
    onLogSet: (String, WorkoutSet) -> Unit,
    onRestTimerStarted: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val machineName = machine?.name ?: ""
    val nextSetNumber = existingSets.size + 1

    var historicalSets by remember { mutableStateOf<List<WorkoutSetEntity>>(emptyList()) }
    LaunchedEffect(instance.machineId) {
        historicalSets = historicalSetsProvider(instance.machineId)
    }

    val (prefillWeight, prefillReps) = remember(instance.id, existingSets.size) {
        WorkoutTrackingLogic.determinePrefillWeightAndReps(
            currentSets = existingSets,
            historicalSets = historicalSets,
            defaultWeight = 60f,
            defaultReps = 10
        )
    }

    val unit = machine?.weightUnit ?: WeightUnit.KG
    val unitSuffix = WeightConversion.unitSuffix(unit)
    var weightInput by remember(prefillWeight) { mutableStateOf(WeightConversion.formatWeightForUnit(prefillWeight, unit)) }
    var repsInput by remember(prefillReps) { mutableStateOf(prefillReps.toString()) }

    val currentWeight = weightInput.toFloatOrNull() ?: 0f
    val currentReps = repsInput.toIntOrNull() ?: 0
    val increment = machine?.defaultIncrementKg ?: 2.5f
    val weightDirection = machine?.weightDirection ?: WeightDirection.NORMAL
    val stackValuesKg = machine?.weightStackValuesKg ?: emptyList()
    val showProgressionSuggestion = WorkoutLogic.isProgressionProposed(currentReps)
    val overloadedWeight = WeightConversion.toKg(currentWeight, unit).let { currentKg ->
        WeightStack.nextProgressionWeight(currentKg, stackValuesKg, weightDirection)
            ?: WorkoutLogic.calculateNextWeight(currentKg, increment, shouldProgress = true, weightDirection = weightDirection)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.log_set_dialog_title, nextSetNumber, machineName))
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Double progression proposal banner
                if (showProgressionSuggestion) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = stringResource(
                                    R.string.progression_suggestion_format,
                                    WeightConversion.formatWeightForUnit(overloadedWeight, unit) + " " + unitSuffix
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Button(
                                onClick = {
                                    weightInput = WeightConversion.formatWeightForUnit(overloadedWeight, unit)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                            ) {
                                Text(
                                    text = stringResource(
                                        R.string.btn_apply_overload,
                                        WeightConversion.formatWeightForUnit(increment, unit)
                                    )
                                )
                            }
                        }
                    }
                }

                // Weight Input & quick buttons
                Text(
                    text = stringResource(R.string.weight_label_unit_format, unitSuffix),
                    fontWeight = FontWeight.Bold
                )
                OutlinedTextField(
                    value = weightInput,
                    onValueChange = { weightInput = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                WeightStepperControls(
                    weightInput = weightInput,
                    incrementKg = increment,
                    unit = unit,
                    stackValuesKg = stackValuesKg,
                    onWeightChanged = { weightInput = it }
                )

                // Reps Input & quick buttons
                Text(text = stringResource(R.string.reps_label), fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = repsInput,
                    onValueChange = { repsInput = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                RepsStepperControls(
                    repsInput = repsInput,
                    onRepsChanged = { repsInput = it }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalWeight = WorkoutLogic.clampWeight(
                        WeightConversion.toKg(weightInput.toFloatOrNull() ?: 0f, unit)
                    )
                    val finalReps = WorkoutLogic.clampReps(repsInput.toIntOrNull() ?: 1)

                    val newSet = WorkoutSet(
                        sessionMachineId = instance.id,
                        setNumber = nextSetNumber,
                        reps = finalReps,
                        weightKg = finalWeight,
                        cadence = machine?.defaultCadence ?: "2-0-2-0",
                        setType = SetType.NORMAL
                    )

                    onLogSet(instance.id, newSet)
                    onRestTimerStarted(restTimerSeconds)
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
