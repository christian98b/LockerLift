package com.lockerlift.mobile.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lockerlift.core.database.entity.toDomainModel
import com.lockerlift.mobile.LockerLiftMobileApp
import com.lockerlift.mobile.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(app: LockerLiftMobileApp) {
    val sessionDao = remember { app.database.workoutSessionDao() }
    val sessionsState by sessionDao.getAllSessionsWithDetailsFlow().collectAsState(initial = emptyList())
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.history_title)) })
        }
    ) { padding ->
        if (sessionsState.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.history_empty),
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
                items(sessionsState, key = { it.session.id }) { item ->
                    val session = item.session.toDomainModel()
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = item.template?.name ?: stringResource(R.string.workout_free),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = session.originDevice,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                text = dateFormat.format(Date(session.startTime)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(8.dp))
                            Divider()
                            Spacer(modifier = Modifier.height(8.dp))

                            val noSetsText = stringResource(R.string.history_no_sets)
                            item.machineInstances.forEach { instanceWithDetails ->
                                val machineName = instanceWithDetails.machine.name
                                val sets = instanceWithDetails.sets
                                val setsSummary = sets.joinToString(", ") { "${it.weightKg}kg × ${it.reps}" }

                                Text(
                                    text = "• $machineName: ${if (setsSummary.isNotBlank()) setsSummary else noSetsText}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
