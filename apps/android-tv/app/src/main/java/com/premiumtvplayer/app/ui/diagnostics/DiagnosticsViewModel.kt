package com.premiumtvplayer.app.ui.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.premiumtvplayer.app.BuildConfig
import com.premiumtvplayer.app.data.diagnostics.ErrorLogBuffer
import com.premiumtvplayer.app.data.diagnostics.HealthClient
import com.premiumtvplayer.app.data.diagnostics.HealthSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DiagnosticsInfo(
    val versionName: String,
    val versionCode: Int,
    val apiBaseUrl: String,
    val firebaseProjectId: String,
    val firebaseApplicationId: String,
)

sealed interface DiagnosticsUiState {
    data object Loading : DiagnosticsUiState
    data class Ready(
        val info: DiagnosticsInfo,
        val health: HealthSnapshot?,
        val errors: List<ErrorLogBuffer.Entry>,
        val refreshing: Boolean = false,
    ) : DiagnosticsUiState
}

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    private val healthClient: HealthClient,
    private val errorLog: ErrorLogBuffer,
) : ViewModel() {

    private val info = DiagnosticsInfo(
        versionName = BuildConfig.VERSION_NAME,
        versionCode = BuildConfig.VERSION_CODE,
        apiBaseUrl = BuildConfig.API_BASE_URL,
        firebaseProjectId = BuildConfig.FIREBASE_PROJECT_ID,
        firebaseApplicationId = BuildConfig.FIREBASE_APPLICATION_ID,
    )

    private val _uiState = MutableStateFlow<DiagnosticsUiState>(DiagnosticsUiState.Loading)
    val uiState: StateFlow<DiagnosticsUiState> = _uiState.asStateFlow()

    init {
        // Observe the error log and update the Ready state live.
        errorLog.entries
            .onEach { entries ->
                val current = _uiState.value
                if (current is DiagnosticsUiState.Ready) {
                    _uiState.value = current.copy(errors = entries)
                }
            }
            .launchIn(viewModelScope)

        refresh()
    }

    fun refresh() {
        val current = _uiState.value
        val ready = (current as? DiagnosticsUiState.Ready)?.copy(refreshing = true)
        _uiState.value = ready ?: DiagnosticsUiState.Loading
        viewModelScope.launch {
            val snapshot = runCatching { healthClient.fetch() }.getOrElse {
                HealthSnapshot(
                    ok = false,
                    status = null,
                    database = null,
                    redis = null,
                    service = null,
                    rawBody = it.message ?: "",
                )
            }
            _uiState.value = DiagnosticsUiState.Ready(
                info = info,
                health = snapshot,
                errors = errorLog.entries.value,
                refreshing = false,
            )
        }
    }
}
