package com.lockerlift.mobile.tracking.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lockerlift.core.model.WeightConversion
import com.lockerlift.core.model.WeightStack
import com.lockerlift.core.model.WeightUnit
import com.lockerlift.core.model.WorkoutLogic

/**
 * Quick weight stepper buttons below the weight input. On a configured
 * weight stack scale (Issue #48) the steppers jump to printed pin positions,
 * otherwise they apply the machine's increment deltas.
 */
@Composable
fun WeightStepperControls(
    weightInput: String,
    incrementKg: Float,
    unit: WeightUnit,
    stackValuesKg: List<Float> = emptyList(),
    onWeightChanged: (String) -> Unit
) {
    val hasStackScale = stackValuesKg.isNotEmpty()
    val currentKg = WeightConversion.toKg(weightInput.toFloatOrNull() ?: 0f, unit)
    val minusSteps = if (hasStackScale) {
        (1..3).mapNotNull { step ->
            WeightStack.nextScaleValue(currentKg, stackValuesKg, up = false, steps = step)
        }
    } else {
        WorkoutLogic.calculateWeightSteppers(incrementKg).first
    }
    val plusSteps = if (hasStackScale) {
        (1..3).mapNotNull { step ->
            WeightStack.nextScaleValue(currentKg, stackValuesKg, up = true, steps = step)
        }
    } else {
        WorkoutLogic.calculateWeightSteppers(incrementKg).second
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Minus row: e.g. -5, -2.5, -1.25 (or printed scale values)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            minusSteps.forEach { target ->
                OutlinedButton(
                    onClick = {
                        if (hasStackScale) {
                            onWeightChanged(WeightConversion.formatWeightForUnit(target, unit))
                        } else {
                            val newW = WorkoutLogic.clampWeight(currentKg + target)
                            onWeightChanged(WeightConversion.formatWeightForUnit(newW, unit))
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = if (hasStackScale) {
                            WeightConversion.formatWeightForUnit(target, unit)
                        } else {
                            WorkoutLogic.formatDelta(target)
                        },
                        fontSize = 13.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip
                    )
                }
            }
        }
        // Plus row: e.g. +1.25, +2.5, +5 (or printed scale values)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            plusSteps.forEach { target ->
                OutlinedButton(
                    onClick = {
                        if (hasStackScale) {
                            onWeightChanged(WeightConversion.formatWeightForUnit(target, unit))
                        } else {
                            val newW = WorkoutLogic.clampWeight(currentKg + target)
                            onWeightChanged(WeightConversion.formatWeightForUnit(newW, unit))
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = if (hasStackScale) {
                            WeightConversion.formatWeightForUnit(target, unit)
                        } else {
                            WorkoutLogic.formatDelta(target)
                        },
                        fontSize = 13.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip
                    )
                }
            }
        }
    }
}

/**
 * Quick rep stepper buttons (-5, -1, +1, +5) below the reps input.
 */
@Composable
fun RepsStepperControls(
    repsInput: String,
    onRepsChanged: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(-5, -1, 1, 5).forEach { delta ->
            OutlinedButton(
                onClick = {
                    val current = repsInput.toIntOrNull() ?: 0
                    val newR = WorkoutLogic.clampReps(current + delta)
                    onRepsChanged(newR.toString())
                },
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = if (delta > 0) "+$delta" else "$delta",
                    fontSize = 13.sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
