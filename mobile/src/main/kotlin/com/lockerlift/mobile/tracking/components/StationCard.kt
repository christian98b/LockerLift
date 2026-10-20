package com.lockerlift.mobile.tracking.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lockerlift.core.database.entity.WorkoutSetEntity
import com.lockerlift.core.database.logic.WorkoutTrackingLogic
import com.lockerlift.core.model.Machine
import com.lockerlift.core.model.SessionMachineInstance
import com.lockerlift.core.model.WeightConversion
import com.lockerlift.core.model.WeightDirection
import com.lockerlift.core.model.WeightStack
import com.lockerlift.core.model.WeightUnit
import com.lockerlift.core.model.WorkoutLogic
import com.lockerlift.core.model.WorkoutSet
import com.lockerlift.mobile.R

/**
 * Card of one station in the active workout: header with the 3-dots menu,
 * progression suggestion banner, completed sets table, add-set button and
 * the next-station suggestion.
 */
@Composable
fun StationCard(
    index: Int,
    instance: SessionMachineInstance,
    machine: Machine?,
    machines: List<Machine>,
    sessionInstances: List<SessionMachineInstance>,
    loggedSets: List<WorkoutSet>,
    setsByInstance: Map<String, List<WorkoutSet>>,
    historicalSetsProvider: suspend (String) -> List<WorkoutSetEntity>,
    onAddSet: (Int) -> Unit,
    onEditSet: (String, Int) -> Unit,
    onSwapExercise: (Int) -> Unit,
    onEditSettings: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onRemove: (Int) -> Unit,
    onJumpToStation: (Int) -> Unit
) {
    val machineName = machine?.name ?: stringResource(R.string.hint_machine_name)
    val muscleGroup = machine?.targetMuscleGroup ?: ""

    var expandedMenu by remember { mutableStateOf(false) }

    // Query historical sets for this machine
    var historicalSets by remember(instance.machineId) { mutableStateOf<List<WorkoutSetEntity>>(emptyList()) }
    LaunchedEffect(instance.machineId) {
        historicalSets = historicalSetsProvider(instance.machineId)
    }
    val lastHistSets = remember(historicalSets) { WorkoutTrackingLogic.extractLastSessionSets(historicalSets) }

    // Double progression proposal for next set
    val lastSet = loggedSets.lastOrNull()
    val showProgressionSuggestion = lastSet != null && WorkoutLogic.isProgressionProposed(lastSet.reps)
    val unit = machine?.weightUnit ?: WeightUnit.KG
    val unitSuffix = WeightConversion.unitSuffix(unit)
    val increment = machine?.defaultIncrementKg ?: 2.5f
    val weightDirection = machine?.weightDirection ?: WeightDirection.NORMAL
    val stackValuesKg = machine?.weightStackValuesKg ?: emptyList()
    val overloadedWeight = if (lastSet != null) (
        WeightStack.nextProgressionWeight(lastSet.weightKg, stackValuesKg, weightDirection)
            ?: WorkoutLogic.calculateNextWeight(lastSet.weightKg, increment, shouldProgress = true, weightDirection = weightDirection)
        ) else 0f

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // --- 1. Header: Name, Muscle, Note, 3-dots Menu ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${index + 1}. $machineName",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (muscleGroup.isNotBlank()) {
                            Text(
                                text = muscleGroup,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        val noteText = instance.customSettingsNote ?: ""
                        if (noteText.isNotBlank()) {
                            Text(
                                text = "• ⚙️ $noteText",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // 3-dots Menu
                Box {
                    IconButton(onClick = { expandedMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                    }
                    DropdownMenu(
                        expanded = expandedMenu,
                        onDismissRequest = { expandedMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_swap_exercise)) },
                            onClick = {
                                onSwapExercise(index)
                                expandedMenu = false
                            },
                            leadingIcon = { Icon(Icons.Default.SwapHoriz, null) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_edit_settings)) },
                            onClick = {
                                onEditSettings(index)
                                expandedMenu = false
                            },
                            leadingIcon = { Icon(Icons.Default.Settings, null) }
                        )
                        if (index > 0) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_move_up)) },
                                onClick = {
                                    onMove(index, index - 1)
                                    expandedMenu = false
                                },
                                leadingIcon = { Icon(Icons.Default.ArrowUpward, null) }
                            )
                        }
                        if (index < sessionInstances.size - 1) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_move_down)) },
                                onClick = {
                                    onMove(index, index + 1)
                                    expandedMenu = false
                                },
                                leadingIcon = { Icon(Icons.Default.ArrowDownward, null) }
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_remove_exercise), color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                onRemove(index)
                                expandedMenu = false
                            },
                            leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
                        )
                    }
                }
            }

            // Progression suggestion banner
            if (showProgressionSuggestion) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = stringResource(
                                R.string.progression_suggestion_format,
                                WeightConversion.formatWeightForUnit(overloadedWeight, unit) + " " + unitSuffix
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // --- 2. Set Table Header ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.table_header_set),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(36.dp)
                )
                Text(
                    text = stringResource(R.string.table_header_prev),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = stringResource(R.string.table_header_weight),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(68.dp)
                )
                Text(
                    text = stringResource(R.string.table_header_reps),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(44.dp)
                )
                Text(
                    text = stringResource(R.string.table_header_status),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(72.dp)
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

            // --- 3. Completed Sets Table Rows ---
            loggedSets.forEachIndexed { setIdx, set ->
                val prevSet = lastHistSets.getOrNull(setIdx)
                val prevText = if (prevSet != null) {
                    "${WeightConversion.formatWeightForUnit(prevSet.weightKg, unit)} $unitSuffix × ${prevSet.reps}"
                } else "—"

                Surface(
                    onClick = { onEditSet(instance.id, setIdx) },
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${setIdx + 1}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(36.dp)
                        )
                        Text(
                            text = prevText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${WeightConversion.formatWeightForUnit(set.weightKg, unit)} $unitSuffix",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.width(68.dp)
                        )
                        Text(
                            text = "${set.reps}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.width(44.dp)
                        )
                        // Status badge with checkmark (Clickable to edit)
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.extraSmall,
                            modifier = Modifier.width(72.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 3.dp, horizontal = 4.dp)
                            ) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(13.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = stringResource(R.string.set_status_logged),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }

            // --- 4. Add Set Button ---
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = { onAddSet(index) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.btn_add_set))
            }

            // --- 5. Next Station Suggestion ---
            if (loggedSets.isNotEmpty()) {
                val nextStationIndex = WorkoutLogic.findNextUnfinishedStationIndex(index, sessionInstances, setsByInstance)
                if (nextStationIndex != null && nextStationIndex in sessionInstances.indices) {
                    val nextInst = sessionInstances[nextStationIndex]
                    val nextMachine = machines.find { it.id == nextInst.machineId }
                    val nextMachineName = nextMachine?.name ?: "Station ${nextStationIndex + 1}"
                    Spacer(modifier = Modifier.height(10.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                        ),
                        onClick = { onJumpToStation(nextStationIndex) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.next_exercise_suggestion, nextMachineName),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            Text(
                                text = stringResource(R.string.btn_jump_to_exercise),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
