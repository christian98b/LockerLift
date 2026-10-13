package com.lockerlift.wear

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
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
import com.lockerlift.core.database.state.ActiveSessionStateRepository
import com.lockerlift.wear.presentation.ActiveWorkoutScreen
import com.lockerlift.wear.presentation.WearSettingsScreen
import com.lockerlift.wear.presentation.licenses.WearLicensesScreen
import com.lockerlift.wear.theme.LockerLiftWearTheme

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
            LockerLiftWearTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    var activeWorkoutParams by remember { mutableStateOf<Pair<String?, String?>?>(null) }
                    var showSettings by remember { mutableStateOf(false) }
                    var showLicenses by remember { mutableStateOf(false) }

                    // Crash recovery: if an interrupted workout is durably
                    // persisted, re-enter it instead of the template picker.
                    LaunchedEffect(Unit) {
                        val snapshot = ActiveSessionStateRepository(app.database.activeSessionStateDao()).load()
                        if (snapshot != null) {
                            activeWorkoutParams = Pair(snapshot.templateId, snapshot.templateName)
                        }
                    }

                    DisposableEffect(activeWorkoutParams != null) {
                        if (activeWorkoutParams != null) {
                            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        }
                        onDispose {
                            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        }
                    }

                    val currentWorkoutParams = activeWorkoutParams
                    if (currentWorkoutParams != null) {
                        val (templateId, templateName) = currentWorkoutParams
                        ActiveWorkoutScreen(
                            app = app,
                            templateId = templateId,
                            templateName = templateName,
                            onFinishWorkout = {
                                activeWorkoutParams = null
                            }
                        )
                    } else if (showLicenses) {
                        WearLicensesScreen(
                            onNavigateBack = { showLicenses = false }
                        )
                    } else if (showSettings) {
                        WearSettingsScreen(
                            app = app,
                            onNavigateBack = { showSettings = false },
                            onOpenLicenses = { showLicenses = true }
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
                        style = MaterialTheme.typography.bodySmall,
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
