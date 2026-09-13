package com.lockerlift.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.*
import com.lockerlift.core.database.entity.toDomainModel
import com.lockerlift.wear.presentation.ActiveWorkoutScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as LockerLiftWearApp

        setContent {
            MaterialTheme {
                var activeWorkoutParams by remember { mutableStateOf<Pair<String?, String?>?>(null) }

                if (activeWorkoutParams != null) {
                    val (templateId, templateName) = activeWorkoutParams!!
                    ActiveWorkoutScreen(
                        app = app,
                        templateId = templateId,
                        templateName = templateName,
                        onFinishWorkout = {
                            activeWorkoutParams = null
                        }
                    )
                } else {
                    TemplateSelectionScreen(
                        app = app,
                        onSelectTemplate = { id, name ->
                            activeWorkoutParams = Pair(id, name)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun TemplateSelectionScreen(
    app: LockerLiftWearApp,
    onSelectTemplate: (String?, String?) -> Unit
) {
    val templateDao = remember { app.database.workoutTemplateDao() }
    val templatesState by templateDao.getAllActiveTemplatesWithMachinesFlow().collectAsState(initial = emptyList())
    val listState = rememberScalingLazyListState()

    ScalingLazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Text(
                text = "LockerLift",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        item {
            Button(
                onClick = { onSelectTemplate(null, "Freies Training") },
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .padding(vertical = 4.dp)
            ) {
                Text("Freies Training")
            }
        }

        items(templatesState, key = { it.template.id }) { item ->
            val template = item.template.toDomainModel()
            Card(
                onClick = { onSelectTemplate(template.id, template.name) },
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .padding(vertical = 4.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(text = template.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "${item.machines.size} Übungen",
                        style = MaterialTheme.typography.bodyExtraSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
