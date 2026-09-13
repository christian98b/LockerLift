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

enum class MobileTab(val label: String) {
    CATALOG("Maschinen"),
    TEMPLATES("Vorlagen"),
    HISTORY("Historie"),
    TRACKING("Tracking")
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
                                label = { Text(MobileTab.CATALOG.label) }
                            )
                            NavigationBarItem(
                                selected = selectedTab == MobileTab.TEMPLATES,
                                onClick = { selectedTab = MobileTab.TEMPLATES },
                                icon = { Icon(Icons.Default.FormatListBulleted, contentDescription = null) },
                                label = { Text(MobileTab.TEMPLATES.label) }
                            )
                            NavigationBarItem(
                                selected = selectedTab == MobileTab.HISTORY,
                                onClick = { selectedTab = MobileTab.HISTORY },
                                icon = { Icon(Icons.Default.History, contentDescription = null) },
                                label = { Text(MobileTab.HISTORY.label) }
                            )
                            NavigationBarItem(
                                selected = selectedTab == MobileTab.TRACKING,
                                onClick = { selectedTab = MobileTab.TRACKING },
                                icon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                                label = { Text(MobileTab.TRACKING.label) }
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
