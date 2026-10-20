package com.lockerlift.mobile.ui.plan

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lockerlift.core.database.entity.toDomainModel
import com.lockerlift.core.database.model.WorkoutPlanWithEntries
import com.lockerlift.core.model.ActivityType
import com.lockerlift.core.model.PlanEntry
import com.lockerlift.core.model.WorkoutPlan
import com.lockerlift.core.model.WorkoutTemplate
import com.lockerlift.mobile.R
import java.util.UUID
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/**
 * Training plan screen (Issue #51): manage the weekly rotation plan
 * (configurable multi-week cycle) and its scheduled days.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutPlanScreen(app: com.lockerlift.mobile.LockerLiftMobileApp) {
    val planViewModel: WorkoutPlanViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                WorkoutPlanViewModel(application = app, database = app.database)
            }
        }
    )

    val plansState by planViewModel.plans.collectAsState()
    val templatesState by planViewModel.templates.collectAsState()
    val activityTypesState by planViewModel.activityTypes.collectAsState()

    var showEditor by remember { mutableStateOf(false) }
    var editingPlan by remember { mutableStateOf<WorkoutPlanWithEntries?>(null) }
    var planToDelete by remember { mutableStateOf<WorkoutPlanWithEntries?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.plan_title)) }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showEditor = true }) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(R.string.plan_create)
                )
            }
        }
    ) { padding ->
        if (plansState.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.plan_empty),
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
                items(plansState.size, key = { plansState[it].plan.id }) { index ->
                    val item = plansState[index]
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.plan.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = stringResource(R.string.plan_cycle_weeks_label) +
                                            ": " + item.plan.cycleLengthWeeks,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row {
                                    IconButton(onClick = { editingPlan = item }) {
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = stringResource(R.string.history_edit_workout),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    IconButton(onClick = { planToDelete = item }) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = stringResource(R.string.btn_delete),
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditor || editingPlan != null) {
        PlanEditorDialog(
            initial = editingPlan,
            templates = templatesState,
            activityTypes = activityTypes,
            onDismiss = {
                showEditor = false
                editingPlan = null
            },
            onSave = { plan, entries ->
                planViewModel.savePlan(plan, entries)
                showEditor = false
                editingPlan = null
            }
        )
    }

    planToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { planToDelete = null },
            title = { Text(stringResource(R.string.plan_delete_dialog_title)) },
            text = { Text(stringResource(R.string.plan_delete_dialog_confirm)) },
            confirmButton = {
                Button(
                    onClick = {
                        planViewModel.deletePlan(target.plan.id)
                        planToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text(stringResource(R.string.btn_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { planToDelete = null }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }
}

/**
 * Editor dialog (Issue #51): plan name, rotation length, and for each
 * week of the cycle the 7 weekdays to assign a program.
 */
@Composable
private fun PlanEditorDialog(
    initial: WorkoutPlanWithEntries?,
    templates: List<WorkoutTemplate>,
    activityTypes: List<ActivityType>,
    onDismiss: () -> Unit,
    onSave: (WorkoutPlan, List<PlanEntry>) -> Unit
) {
    var name by remember { mutableStateOf(initial?.plan?.name ?: "") }
    var cycleWeeks by remember { mutableStateOf(initial?.plan?.cycleLengthWeeks ?: 1) }
    // (weekIndex, dayOfWeek) -> draft entry
    var drafts by remember {
        val initialDrafts: Map<Pair<Int, Int>, PlanEntry> =
            initial?.entries?.associateBy({ it.weekIndex to it.dayOfWeek }, { it.toDomainModel() })
                ?: emptyMap()
        mutableStateOf(initialDrafts)
    }
    var pickDay by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    val dayNames = DayOfWeek.entries.associateWith {
        it.getDisplayName(TextStyle.SHORT, Locale.getDefault())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.plan_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.plan_name_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.plan_cycle_weeks_label),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(onClick = { if (cycleWeeks > 1) cycleWeeks-- }) { Text("−") }
                    Text(
                        text = "$cycleWeeks",
                        modifier = Modifier.padding(horizontal = 12.dp),
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedButton(onClick = { if (cycleWeeks < 8) cycleWeeks++ }) { Text("+") }
                }
                for (week in 0 until cycleWeeks) {
                    Text(
                        text = stringResource(R.string.plan_week_label, week + 1),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    for (day in 1..7) {
                        val assigned = drafts[week to day]
                        val label = assigned?.let { entry ->
                            entry.templateId?.let { id -> templates.firstOrNull { it.id == id }?.name }
                                ?: entry.activityTypeId?.let { id ->
                                    activityTypes.firstOrNull { it.id == id }?.name
                                }
                                ?: entry.label
                        }
                        OutlinedButton(
                            onClick = { pickDay = week to day },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "${dayNames[DayOfWeek.of(day)]}: " +
                                    (label ?: stringResource(R.string.plan_day_empty))
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val plan = WorkoutPlan(
                        id = initial?.plan?.id ?: UUID.randomUUID().toString(),
                        name = name.trim(),
                        cycleLengthWeeks = cycleWeeks,
                        startDate = initial?.plan?.startDate ?: System.currentTimeMillis(),
                        createdAt = initial?.plan?.createdAt ?: System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                    val entries = drafts.values
                        .filter { it.weekIndex < cycleWeeks }
                        .map { it.copy(planId = plan.id) }
                    onSave(plan, entries)
                },
                enabled = name.isNotBlank() && cycleWeeks >= 1
            ) { Text(stringResource(R.string.plan_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) }
        }
    )

    pickDay?.let { picked ->
        val week = picked.first
        val day = picked.second
        DayAssignmentDialog(
            week = week,
            day = day,
            dayName = dayNames[DayOfWeek.of(day)] ?: "",
            templates = templates,
            activityTypes = activityTypes,
            current = drafts[week to day],
            onDismiss = { pickDay = null },
            onSelected = { entry ->
                drafts = drafts.toMutableMap().also { map ->
                    if (entry == null) {
                        map.remove(week to day)
                    } else {
                        map[week to day] = entry
                    }
                }
                pickDay = null
            }
        )
    }
}

/**
 * Picker for one planned day (Issue #51): gym template, activity type,
 * free-text label, or clear.
 */
@Composable
private fun DayAssignmentDialog(
    week: Int,
    day: Int,
    dayName: String,
    templates: List<WorkoutTemplate>,
    activityTypes: List<ActivityType>,
    current: PlanEntry?,
    onDismiss: () -> Unit,
    onSelected: (PlanEntry?) -> Unit
) {
    var customLabel by remember { mutableStateOf(current?.label ?: "") }

    fun newEntry(templateId: String? = null, activityTypeId: String? = null, label: String? = null) =
        PlanEntry(
            id = current?.id ?: UUID.randomUUID().toString(),
            planId = current?.planId ?: "",
            weekIndex = week,
            dayOfWeek = day,
            templateId = templateId,
            activityTypeId = activityTypeId,
            label = label
        )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(dayName) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (templates.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.tab_templates),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    templates.forEach { template ->
                        TextButton(onClick = {
                            onSelected(newEntry(templateId = template.id))
                        }) { Text(template.name) }
                    }
                }
                if (activityTypes.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.tab_activities),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    activityTypes.forEach { type ->
                        TextButton(onClick = {
                            onSelected(newEntry(activityTypeId = type.id))
                        }) { Text(type.name) }
                    }
                }
                Text(
                    text = stringResource(R.string.hint_settings_note),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                OutlinedTextField(
                    value = customLabel,
                    onValueChange = { customLabel = it },
                    label = { Text(stringResource(R.string.plan_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(
                    onClick = { onSelected(newEntry(label = customLabel.takeIf { it.isNotBlank() })) },
                    enabled = customLabel.isNotBlank()
                ) { Text(stringResource(R.string.btn_save)) }
                TextButton(onClick = { onSelected(null) }) {
                    Text(
                        stringResource(R.string.btn_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) }
        }
    )
}
