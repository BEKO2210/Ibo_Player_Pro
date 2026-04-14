package com.premiumtvplayer.app.ui.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.premiumtvplayer.app.data.api.ApiErrorCopy
import com.premiumtvplayer.app.data.api.ApiException
import com.premiumtvplayer.app.data.api.PlaybackSessionDto
import com.premiumtvplayer.app.data.playback.PlaybackItemType
import com.premiumtvplayer.app.data.playback.PlaybackRepository
import com.premiumtvplayer.app.data.playback.PlaybackStateValue
import com.premiumtvplayer.app.ui.nav.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Playback UI state. `session` is non-null once `/v1/playback/start` has
 * returned; we track the full lifecycle locally and mirror it into the
 * backend on a 10-second heartbeat cadence.
 */
sealed interface PlayerUiState {
    data object Starting : PlayerUiState
    data class Playing(val session: PlaybackSessionDto, val positionSeconds: Int, val durationSeconds: Int?) : PlayerUiState
    data class Paused(val session: PlaybackSessionDto, val positionSeconds: Int, val durationSeconds: Int?) : PlayerUiState
    data class Buffering(val session: PlaybackSessionDto, val positionSeconds: Int) : PlayerUiState
    data class Stopped(val session: PlaybackSessionDto, val finalPositionSeconds: Int) : PlayerUiState
    data class Error(val message: String) : PlayerUiState
}

/** Reads the heartbeat cadence so tests can override it. */
interface PlayerClock {
    val heartbeatIntervalMs: Long
    suspend fun sleep(millis: Long)
}

object DefaultPlayerClock : PlayerClock {
    override val heartbeatIntervalMs: Long = 10_000L
    override suspend fun sleep(millis: Long) = delay(millis)
}

@HiltViewModel
class PlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val playback: PlaybackRepository,
) : ViewModel() {

    private val profileId: String = requireNotNull(savedStateHandle[Routes.ProfileIdArg]) {
        "PlayerScreen requires a profileId nav argument"
    }
    private val sourceId: String = requireNotNull(savedStateHandle[Routes.SourceIdArg]) {
        "PlayerScreen requires a sourceId nav argument"
    }
    private val itemId: String = requireNotNull(savedStateHandle[Routes.ItemIdArg]) {
        "PlayerScreen requires an itemId nav argument"
    }
    private val itemTypeRaw: String = requireNotNull(savedStateHandle[Routes.ItemTypeArg]) {
        "PlayerScreen requires an itemType nav argument"
    }
    private val itemType: PlaybackItemType = when (itemTypeRaw) {
        "live" -> PlaybackItemType.Live
        "vod" -> PlaybackItemType.Vod
        "series_episode" -> PlaybackItemType.SeriesEpisode
        else -> PlaybackItemType.Vod
    }

    private val _uiState = MutableStateFlow<PlayerUiState>(PlayerUiState.Starting)
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var heartbeatJob: Job? = null
    private var currentSession: PlaybackSessionDto? = null
    private var currentPositionSeconds: Int = 0
    private var currentDurationSeconds: Int? = null

    /** Override-able for tests; production uses the 10s cadence. */
    var clock: PlayerClock = DefaultPlayerClock

    init {
        startSession()
    }

    private fun startSession() {
        viewModelScope.launch {
            try {
                val session = playback.start(
                    profileId = profileId,
                    sourceId = sourceId,
                    itemId = itemId,
                    itemType = itemType,
                )
                currentSession = session
                _uiState.value = PlayerUiState.Buffering(session, positionSeconds = 0)
            } catch (t: Throwable) {
                _uiState.value = PlayerUiState.Error(mapError(t, "Could not start playback."))
            }
        }
    }

    /** Called by the ExoPlayer listener each time position updates. */
    fun onProgress(positionSeconds: Int, durationSeconds: Int?) {
        currentPositionSeconds = positionSeconds
        currentDurationSeconds = durationSeconds
        val session = currentSession ?: return
        _uiState.value = when (val s = _uiState.value) {
            is PlayerUiState.Playing -> s.copy(positionSeconds = positionSeconds, durationSeconds = durationSeconds)
            is PlayerUiState.Paused -> s.copy(positionSeconds = positionSeconds, durationSeconds = durationSeconds)
            is PlayerUiState.Buffering -> PlayerUiState.Playing(session, positionSeconds, durationSeconds)
            else -> s
        }
        ensureHeartbeatRunning()
    }

    fun onPlayingStateChanged(playing: Boolean) {
        val session = currentSession ?: return
        _uiState.value = if (playing) {
            PlayerUiState.Playing(session, currentPositionSeconds, currentDurationSeconds)
        } else {
            PlayerUiState.Paused(session, currentPositionSeconds, currentDurationSeconds)
        }
        // Heartbeat starts on first onProgress (when we have a real position).
    }

    fun onBuffering() {
        val session = currentSession ?: return
        _uiState.value = PlayerUiState.Buffering(session, currentPositionSeconds)
    }

    fun onError(message: String) {
        _uiState.value = PlayerUiState.Error(message)
        heartbeatJob?.cancel()
    }

    /** Called from the screen when the user exits (back press / done). */
    fun stop(completed: Boolean = false) {
        val session = currentSession ?: return
        heartbeatJob?.cancel()
        viewModelScope.launch {
            runCatching {
                playback.stop(
                    sessionId = session.id,
                    finalPositionSeconds = currentPositionSeconds,
                    durationSeconds = currentDurationSeconds,
                    completed = completed,
                )
            }
            _uiState.value = PlayerUiState.Stopped(session, currentPositionSeconds)
        }
    }

    private fun ensureHeartbeatRunning() {
        if (heartbeatJob?.isActive == true) return
        heartbeatJob = viewModelScope.launch {
            heartbeatLoop(this)
        }
    }

    private suspend fun heartbeatLoop(scope: CoroutineScope) {
        while (scope.isActive()) {
            clock.sleep(clock.heartbeatIntervalMs)
            val session = currentSession ?: return
            val snapshot = _uiState.value
            val state = when (snapshot) {
                is PlayerUiState.Playing -> PlaybackStateValue.Playing
                is PlayerUiState.Paused -> PlaybackStateValue.Paused
                is PlayerUiState.Buffering -> PlaybackStateValue.Buffering
                else -> return
            }
            runCatching {
                playback.heartbeat(
                    sessionId = session.id,
                    positionSeconds = currentPositionSeconds,
                    state = state,
                    durationSeconds = currentDurationSeconds,
                )
            }
        }
    }

    override fun onCleared() {
        heartbeatJob?.cancel()
        super.onCleared()
    }

    private fun mapError(t: Throwable, fallback: String): String =
        (t as? ApiException.Server)?.let { ApiErrorCopy.forCode(it.code, it.message) }
            ?: t.message ?: fallback
}

/** `isActive` extension so we can keep the loop helper API small. */
private fun CoroutineScope.isActive(): Boolean = coroutineContext[Job]?.isActive ?: true
