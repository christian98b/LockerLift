package com.lockerlift.mobile.tracking

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lockerlift.mobile.LockerLiftMobileApp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileTrackingScreen(app: LockerLiftMobileApp) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Mobiles Workout Tracking") })
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "LockerLift Spind-Modus",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Lass dein Handy sicher im Spind und tracke deine Einheit autark auf deiner Wear OS Smartwatch!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
