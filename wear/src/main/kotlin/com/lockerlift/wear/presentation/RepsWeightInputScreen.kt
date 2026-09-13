package com.lockerlift.wear.presentation

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.*
import com.lockerlift.core.model.Machine
import com.lockerlift.core.model.SetType
import com.lockerlift.core.model.WorkoutSet
import com.lockerlift.wear.R
import com.lockerlift.wear.logic.WearWorkoutLogic
import kotlinx.coroutines.launch

@Composable
fun RepsWeightInputScreen(
    machine: Machine,
    setNumber: Int,
    lastWeight: Float = 60f,
    lastReps: Int = 10,
    cadence: String? = machine.defaultCadence,
    historicalPerformanceText: String? = null,
    onSaveSet: (WorkoutSet) -> Unit,
    onCancel: () -> Unit
) {
    var weight by remember(lastWeight) { mutableFloatStateOf(lastWeight) }
    var reps by remember(lastReps) { mutableIntStateOf(lastReps) }
    val increment = machine.defaultIncrementKg
    val isProgressionProposed = WearWorkoutLogic.isProgressionProposed(reps)

    val listState = rememberScalingLazyListState()
    val focusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    ScalingLazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .onRotaryScrollEvent {
                coroutineScope.launch {
                    listState.scrollBy(it.verticalScrollPixels)
                }
                true
            }
            .focusRequester(focusRequester)
            .focusable(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Text(
                text = stringResource(R.string.set_header_format, machine.name, setNumber),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (!historicalPerformanceText.isNullOrBlank()) {
            item {
                Card(
                    onClick = {},
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .padding(vertical = 2.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    Text(
                        text = historicalPerformanceText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(4.dp)
                    )
                }
            }
        }

        if (cadence != null) {
            item {
                Text(
                    text = stringResource(R.string.tempo_format, cadence),
                    style = MaterialTheme.typography.bodyExtraSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (isProgressionProposed) {
            item {
                Card(
                    onClick = {
                        weight = WearWorkoutLogic.calculateNextWeight(weight, increment, true)
                    },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Text(
                        text = stringResource(
                            R.string.progression_suggestion_format,
                            WearWorkoutLogic.formatWeight(increment)
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Button(
                    onClick = { if (weight > increment) weight -= increment },
                    modifier = Modifier.size(36.dp)
                ) {
                    Text("-", fontSize = 18.sp)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${WearWorkoutLogic.formatWeight(weight)} kg",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { weight += increment },
                    modifier = Modifier.size(36.dp)
                ) {
                    Text("+", fontSize = 18.sp)
                }
            }
        }

        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Button(
                    onClick = { if (reps > 1) reps -= 1 },
                    modifier = Modifier.size(36.dp)
                ) {
                    Text("-", fontSize = 18.sp)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.reps_count_format, reps),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { reps += 1 },
                    modifier = Modifier.size(36.dp)
                ) {
                    Text("+", fontSize = 18.sp)
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(6.dp))
            Button(
                onClick = {
                    val completedSet = WorkoutSet(
                        sessionMachineId = "", // To be linked by caller
                        setNumber = setNumber,
                        reps = reps,
                        weightKg = weight,
                        cadence = cadence,
                        setType = SetType.NORMAL
                    )
                    onSaveSet(completedSet)
                },
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                Text(stringResource(R.string.btn_finish_set))
            }
        }

        item {
            CompactButton(
                onClick = onCancel,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    }
}
