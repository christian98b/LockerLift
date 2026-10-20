package com.lockerlift.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.lockerlift.mobile.tracking.MobileTrackingScreen
import com.lockerlift.mobile.ui.activities.ActivitiesScreen
import com.lockerlift.mobile.ui.catalog.CatalogScreen
import com.lockerlift.mobile.ui.history.HistoryScreen
import com.lockerlift.mobile.ui.insights.InsightsScreen
import com.lockerlift.mobile.ui.licenses.OpenSourceLicensesScreen
import com.lockerlift.mobile.ui.plan.WorkoutPlanScreen
import com.lockerlift.mobile.ui.settings.SettingsScreen
import com.lockerlift.mobile.ui.templates.TemplateListScreen
import com.lockerlift.mobile.ui.theme.LockerLiftTheme

enum class MobileTab(@StringRes val labelRes: Int) {
    CATALOG(R.string.tab_catalog),
    TEMPLATES(R.string.tab_templates),
    PLAN(R.string.plan_title),
    HISTORY(R.string.tab_history),
    TRACKING(R.string.tab_tracking)
}

private enum class HistorySubTab(@StringRes val labelRes: Int) {
    SESSIONS(R.string.tab_history),
    ACTIVITIES(R.string.tab_activities),
    INSIGHTS(R.string.tab_insights)
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = application as LockerLiftMobileApp

        setContent {
            LockerLiftTheme {
                val context = LocalContext.current
                var selectedTab by remember { mutableStateOf(MobileTab.CATALOG) }
                var historySubTab by remember { mutableStateOf(HistorySubTab.SESSIONS) }
                var showSettings by remember { mutableStateOf(false) }
                var showLicenses by remember { mutableStateOf(false) }

                BackHandler(enabled = showSettings || showLicenses) {
                    if (showLicenses) showLicenses = false else showSettings = false
                }

                Scaffold(
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = selectedTab == MobileTab.CATALOG,
                                onClick = { showSettings = false; selectedTab = MobileTab.CATALOG },
                                icon = { Icon(Icons.Default.FitnessCenter, contentDescription = null) },
                                label = { Text(stringResource(MobileTab.CATALOG.labelRes)) }
                            )
                            NavigationBarItem(
                                selected = selectedTab == MobileTab.TEMPLATES,
                                onClick = { showSettings = false; selectedTab = MobileTab.TEMPLATES },
                                icon = { Icon(Icons.Default.FormatListBulleted, contentDescription = null) },
                                label = { Text(stringResource(MobileTab.TEMPLATES.labelRes)) }
                            )
                            NavigationBarItem(
                                selected = selectedTab == MobileTab.PLAN,
                                onClick = { showSettings = false; selectedTab = MobileTab.PLAN },
                                icon = { Icon(Icons.Default.EventNote, contentDescription = null) },
                                label = { Text(stringResource(MobileTab.PLAN.labelRes)) }
                            )
                            NavigationBarItem(
                                selected = selectedTab == MobileTab.HISTORY,
                                onClick = { showSettings = false; selectedTab = MobileTab.HISTORY },
                                icon = { Icon(Icons.Default.History, contentDescription = null) },
                                label = { Text(stringResource(MobileTab.HISTORY.labelRes)) }
                            )
                            NavigationBarItem(
                                selected = selectedTab == MobileTab.TRACKING,
                                onClick = { showSettings = false; selectedTab = MobileTab.TRACKING },
                                icon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                                label = { Text(stringResource(MobileTab.TRACKING.labelRes)) }
                            )
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = innerPadding.calculateBottomPadding())
                            .consumeWindowInsets(PaddingValues(bottom = innerPadding.calculateBottomPadding()))
                    ) {
                        when {
                            showLicenses -> OpenSourceLicensesScreen(
                                onNavigateBack = { showLicenses = false }
                            )
                            showSettings -> SettingsScreen(
                                app = app,
                                onOpenLicenses = { showLicenses = true }
                            )
                            selectedTab == MobileTab.CATALOG -> CatalogScreen(app)
                            selectedTab == MobileTab.TEMPLATES -> TemplateListScreen(app)
                            selectedTab == MobileTab.PLAN -> WorkoutPlanScreen(app)
                            selectedTab == MobileTab.TRACKING -> MobileTrackingScreen(app)
                            selectedTab == MobileTab.HISTORY -> Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .windowInsetsPadding(WindowInsets.statusBars)
                                    .consumeWindowInsets(WindowInsets.statusBars)
                            ) {
                                TabRow(selectedTabIndex = historySubTab.ordinal) {
                                    HistorySubTab.entries.forEach { subTab ->
                                        Tab(
                                            selected = historySubTab == subTab,
                                            onClick = { historySubTab = subTab },
                                            text = { Text(stringResource(subTab.labelRes)) }
                                        )
                                    }
                                }
                                when (historySubTab) {
                                    HistorySubTab.SESSIONS -> HistoryScreen(app)
                                    HistorySubTab.ACTIVITIES -> ActivitiesScreen(
                                        app = app,
                                        onExportToHealthConnect = { entry, typeName ->
                                            val manager = com.lockerlift.core.healthconnect.HealthConnectManager(context)
                                            if (manager.isAvailable() && manager.hasPermissions()) {
                                                manager.exportActivityEntry(entry, typeName)
                                            }
                                        },
                                        onDeleteFromHealthConnect = { entryId ->
                                            val manager = com.lockerlift.core.healthconnect.HealthConnectManager(context)
                                            if (manager.isAvailable() && manager.hasPermissions()) {
                                                manager.deleteActivityEntry(entryId)
                                            }
                                        }
                                    )
                                    HistorySubTab.INSIGHTS -> InsightsScreen(app)
                                }
                            }
                        }
                        if (!showSettings && selectedTab != MobileTab.TRACKING) {
                            IconButton(
                                onClick = { showSettings = true },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .statusBarsPadding()
                            ) {
                                Icon(
                                    Icons.Default.Settings,
                                    contentDescription = stringResource(R.string.tab_settings)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
