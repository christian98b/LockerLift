package com.lockerlift.wear

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
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
import com.lockerlift.wear.presentation.WearSettingsScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        val app = application as LockerLiftWearApp

        setContent {
            MaterialTheme {
                var activeWorkoutParams by remember { mutableStateOf<Pair<String?, String?>?>(null) }
                var showSettings by remember { mutableStateOf(false) }

                DisposableEffect(activeWorkoutParams != null) {
                    if (activeWorkoutParams != null) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                    onDispose {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }

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
                } else if (showSettings) {
                    WearSettingsScreen(
                        app = app,
                        onNavigateBack = { showSettings = false }
                    )
                } else {
                    TemplateSelectionScreen(
                        app = app,
                        onSelectTemplate = { id, name ->
                            activeWorkoutParams = Pair(id, name)
                        },
                        onOpenSettings = {
                            showSettings = true
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
    onSelectTemplate: (String?, String?) -> Unit,
    onOpenSettings: () -> Unit = {}
) {
    val templateDao = remember { app.database.workoutTemplateDao() }
    val templatesState by templateDao.getAllActiveTemplatesWithMachinesFlow().collectAsState(initial = emptyList())
    val listState = rememberScalingLazyListState()
    val freeWorkoutTitle = androidx.compose.ui.res.stringResource(R.string.workout_free)

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
                onClick = { onSelectTemplate(null, freeWorkoutTitle) },
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .padding(vertical = 4.dp)
            ) {
                Text(freeWorkoutTitle)
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
                        text = androidx.compose.ui.res.stringResource(R.string.exercises_count_format, item.machines.size),
                        style = MaterialTheme.typography.bodyExtraSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Button(
                onClick = onOpenSettings,
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .padding(vertical = 4.dp)
            ) {
                Text(androidx.compose.ui.res.stringResource(R.string.settings_title))
            }
        }
    }
}
