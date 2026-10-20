package com.lockerlift.mobile.tracking.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lockerlift.core.database.entity.ActivityTypeEntity
import com.lockerlift.core.database.entity.toDomainModel
import com.lockerlift.core.database.model.WorkoutPlanWithEntries
import com.lockerlift.core.model.WorkoutPlanLogic
import com.lockerlift.core.model.WorkoutTemplate
import com.lockerlift.mobile.R
import kotlinx.coroutines.flow.StateFlow

/**
 * "Up next today" card (Issue #51): resolves the planned program for the
 * current day from the first workout plan, if any. Receives the plan and
 * activity-type flows as parameters instead of reading the database from
 * inside a composable.
 */
@Composable
fun UpNextPlanCard(
    plans: StateFlow<List<WorkoutPlanWithEntries>>,
    activityTypes: StateFlow<List<ActivityTypeEntity>>,
    templates: List<WorkoutTemplate>
) {
    val plansState by plans.collectAsState()
    val activityTypesState by activityTypes.collectAsState()
    val now = remember { System.currentTimeMillis() }
    val plannedLabel: String? = remember(plansState, templates, activityTypesState, now) {
        val planWithEntries = plansState.firstOrNull() ?: return@remember null
        val plan = planWithEntries.plan.toDomainModel()
        val entry = WorkoutPlanLogic.entryFor(
            now,
            plan,
            planWithEntries.entries.map { it.toDomainModel() }
        ) ?: return@remember null
        entry.templateId?.let { id -> templates.firstOrNull { it.id == id }?.name }
            ?: entry.activityTypeId?.let { id ->
                activityTypesState.firstOrNull { it.id == id }?.name
            }
            ?: entry.label
    }
    val upNextText = plannedLabel ?: stringResource(R.string.plan_up_next_none)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.plan_up_next),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = upNextText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
