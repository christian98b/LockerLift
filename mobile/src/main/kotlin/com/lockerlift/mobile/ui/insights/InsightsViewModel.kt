package com.lockerlift.mobile.ui.insights

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lockerlift.core.database.LockerLiftDatabase
import com.lockerlift.core.database.model.WorkoutSessionWithDetails
import com.lockerlift.core.model.InsightsEngine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * State holder for the insights dashboard (Issue #56): exposes the workout
 * history as pre-mapped session snapshots instead of letting the composable
 * query the session DAO directly.
 */
class InsightsViewModel(
    application: Application,
    database: LockerLiftDatabase
) : AndroidViewModel(application) {

    val snapshots: StateFlow<List<InsightsEngine.SessionSnapshot>> =
        database.workoutSessionDao()
            .getAllSessionsWithDetailsFlow()
            .map { sessions: List<WorkoutSessionWithDetails> -> InsightsMapper.toSnapshots(sessions) }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}
