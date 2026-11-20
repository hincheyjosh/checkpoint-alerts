package com.checkpointalerts.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.checkpointalerts.app.data.AlertScope
import com.checkpointalerts.app.data.Checkpoint
import com.checkpointalerts.app.data.CheckpointRepository
import com.checkpointalerts.app.data.SettingsStore
import com.checkpointalerts.app.data.TnCounties
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class UiState(
    val checkpoints: List<Checkpoint> = emptyList(),
    val isRefreshing: Boolean = false,
    val lastUpdatedMillis: Long = 0L,
    val refreshError: Boolean = false,
    val scope: AlertScope = AlertScope.WHOLE_STATE,
    val homeCounty: String = "",
    val showAll: Boolean = false,
    val notificationGranted: Boolean = false,
    val notificationsEnabled: Boolean = true
)

class CheckpointViewModel(application: Application) : AndroidViewModel(application) {

    private val settings = SettingsStore(application)
    private val repo = CheckpointRepository(application, settings)

    private val _showAll = MutableStateFlow(false)
    private val _notificationGranted = MutableStateFlow(false)
    private val _notificationsEnabled = MutableStateFlow(settings.notificationsEnabled)
    private val _scope = MutableStateFlow(settings.scope)
    private val _homeCounty = MutableStateFlow(settings.homeCounty)

    // combine() only has overloads up to 5 flows in this coroutines version,
    // so the 6th flow joins through the 2-flow extension below.
    val uiState: StateFlow<UiState> = combine(
        repo.state, _showAll, _notificationGranted, _scope, _homeCounty
    ) { repoState, showAll, granted, scope, homeCounty ->
        val visible = if (showAll) {
            repoState.checkpoints
        } else {
            repoState.checkpoints.filter { inScope(it, scope, homeCounty) }
        }
        UiState(
            checkpoints = visible,
            isRefreshing = repoState.isRefreshing,
            lastUpdatedMillis = repoState.lastUpdatedMillis,
            refreshError = repoState.refreshError,
            scope = scope,
            homeCounty = homeCounty,
            showAll = showAll,
            notificationGranted = granted
        )
    }.combine(_notificationsEnabled) { state, enabled ->
        state.copy(notificationsEnabled = enabled)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    init {
        repo.loadCache()
        viewModelScope.launch { repo.refresh() }
    }

    fun refresh() {
        viewModelScope.launch { repo.refresh() }
    }

    fun setScope(scope: AlertScope) {
        settings.scope = scope
        _scope.value = scope
    }

    fun setHomeCounty(county: String) {
        settings.homeCounty = county
        _homeCounty.value = county
    }

    fun setShowAll(showAll: Boolean) {
        _showAll.value = showAll
    }

    fun setNotificationGranted(granted: Boolean) {
        _notificationGranted.value = granted
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        settings.notificationsEnabled = enabled
        _notificationsEnabled.value = enabled
    }

    private fun inScope(checkpoint: Checkpoint, scope: AlertScope, homeCounty: String): Boolean {
        if (scope != AlertScope.NEARBY) return true
        if (homeCounty.isBlank()) return true
        return TnCounties.scopeCounties(homeCounty).contains(checkpoint.county)
    }
}
