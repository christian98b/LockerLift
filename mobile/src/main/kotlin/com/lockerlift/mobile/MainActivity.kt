package com.lockerlift.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.lockerlift.mobile.tracking.MobileTrackingScreen
import com.lockerlift.mobile.ui.catalog.CatalogScreen
import com.lockerlift.mobile.ui.history.HistoryScreen
import com.lockerlift.mobile.ui.templates.TemplateListScreen

import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource

enum class MobileTab(@StringRes val labelRes: Int) {
    CATALOG(R.string.tab_catalog),
    TEMPLATES(R.string.tab_templates),
    HISTORY(R.string.tab_history),
    TRACKING(R.string.tab_tracking)
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as LockerLiftMobileApp

        setContent {
            MaterialTheme {
                var selectedTab by remember { mutableStateOf(MobileTab.CATALOG) }

                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = selectedTab == MobileTab.CATALOG,
                                onClick = { selectedTab = MobileTab.CATALOG },
                                icon = { Icon(Icons.Default.FitnessCenter, contentDescription = null) },
                                label = { Text(stringResource(MobileTab.CATALOG.labelRes)) }
                            )
                            NavigationBarItem(
                                selected = selectedTab == MobileTab.TEMPLATES,
                                onClick = { selectedTab = MobileTab.TEMPLATES },
                                icon = { Icon(Icons.Default.FormatListBulleted, contentDescription = null) },
                                label = { Text(stringResource(MobileTab.TEMPLATES.labelRes)) }
                            )
                            NavigationBarItem(
                                selected = selectedTab == MobileTab.HISTORY,
                                onClick = { selectedTab = MobileTab.HISTORY },
                                icon = { Icon(Icons.Default.History, contentDescription = null) },
                                label = { Text(stringResource(MobileTab.HISTORY.labelRes)) }
                            )
                            NavigationBarItem(
                                selected = selectedTab == MobileTab.TRACKING,
                                onClick = { selectedTab = MobileTab.TRACKING },
                                icon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                                label = { Text(stringResource(MobileTab.TRACKING.labelRes)) }
                            )
                        }
                    }
                ) { innerPadding ->
                    Surface(modifier = Modifier.padding(innerPadding)) {
                        when (selectedTab) {
                            MobileTab.CATALOG -> CatalogScreen(app)
                            MobileTab.TEMPLATES -> TemplateListScreen(app)
                            MobileTab.HISTORY -> HistoryScreen(app)
                            MobileTab.TRACKING -> MobileTrackingScreen(app)
                        }
                    }
                }
            }
        }
    }
}
