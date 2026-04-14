package com.premiumtvplayer.app.ui.player

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import com.premiumtvplayer.app.ui.components.BootProgress
import com.premiumtvplayer.app.ui.components.ButtonVariant
import com.premiumtvplayer.app.ui.components.ChipStyle
import com.premiumtvplayer.app.ui.components.PremiumButton
import com.premiumtvplayer.app.ui.components.PremiumChip
import com.premiumtvplayer.app.ui.theme.LocalPremiumSpacing
import com.premiumtvplayer.app.ui.theme.PremiumColors
import com.premiumtvplayer.app.ui.theme.PremiumTvTheme
import com.premiumtvplayer.app.ui.theme.PremiumType

/**
 * Full-bleed playback screen. Hosts a Media3 ExoPlayer via AndroidView
 * and reports progress / state changes into PlayerViewModel so the
 * heartbeat + session lifecycle stays in sync with the server.
 *
 * Stream URL resolution in Run 16: the client uses a `mediaUrl` passed
 * via nav args. A proper server-side resolver endpoint that decrypts
 * source credentials and signs temporary playback URLs belongs to a
 * later security hardening pass — for V1 the media URL travels through
 * the nav graph alongside the itemId.
 */
@Composable
fun PlayerScreen(
    mediaUrl: String,
    itemTitle: String,
    onExit: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val player = remember(context) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(mediaUrl))
            prepare()
            playWhenReady = true
        }
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                viewModel.onPlayingStateChanged(isPlaying)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> viewModel.onBuffering()
                    Player.STATE_ENDED -> viewModel.stop(completed = true)
                    else -> Unit
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                viewModel.onError(error.message ?: "Playback error")
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    // Pump progress into the ViewModel on a 1s UI cadence (the server
    // heartbeat runs on a 10s cadence inside the VM; the 1s UI cadence
    // keeps the progress bar smooth).
    LaunchedEffect(player) {
        while (true) {
            val positionMs = player.currentPosition
            val durationMs = player.duration.takeIf { it > 0 }
            viewModel.onProgress(
                positionSeconds = (positionMs / 1000L).toInt(),
                durationSeconds = durationMs?.let { (it / 1000L).toInt() },
            )
            kotlinx.coroutines.delay(1000L)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PremiumColors.BackgroundBase),
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = false
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Bottom scrim + custom controls — our own premium overlay,
        // not the stock Media3 PlayerView UI.
        PlayerOverlay(
            state = state,
            itemTitle = itemTitle,
            onPlayPause = {
                if (player.isPlaying) player.pause() else player.play()
            },
            onSeek = { delta ->
                player.seekTo((player.currentPosition + delta).coerceAtLeast(0L))
            },
            onExit = {
                viewModel.stop()
                onExit()
            },
        )
    }
}

@Composable
private fun PlayerOverlay(
    state: PlayerUiState,
    itemTitle: String,
    onPlayPause: () -> Unit,
    onSeek: (deltaMs: Long) -> Unit,
    onExit: () -> Unit,
) {
    val spacing = LocalPremiumSpacing.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0.0f to androidx.compose.ui.graphics.Color.Transparent,
                    0.72f to androidx.compose.ui.graphics.Color.Transparent,
                    1.0f to PremiumColors.BackgroundBase.copy(alpha = 0.9f),
                ),
            ),
        verticalArrangement = Arrangement.Bottom,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.pageGutter, vertical = spacing.l),
            horizontalArrangement = Arrangement.spacedBy(spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = itemTitle,
                    style = PremiumType.Headline.copy(color = PremiumColors.OnSurfaceHigh),
                    maxLines = 1,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    PremiumChip(
                        label = state.chipLabel(),
                        style = ChipStyle.Outline,
                        accent = state.chipAccent(),
                    )
                    state.positionLabel()?.let { label ->
                        PremiumChip(
                            label = label,
                            style = ChipStyle.Outline,
                            accent = PremiumColors.OnSurfaceMuted,
                        )
                    }
                }
            }

            when (state) {
                is PlayerUiState.Starting -> BootProgress()
                is PlayerUiState.Buffering -> BootProgress()
                else -> {
                    PremiumButton(text = "-10s", onClick = { onSeek(-10_000L) }, variant = ButtonVariant.Ghost)
                    PremiumButton(
                        text = if (state is PlayerUiState.Playing) "Pause" else "Play",
                        onClick = onPlayPause,
                    )
                    PremiumButton(text = "+10s", onClick = { onSeek(10_000L) }, variant = ButtonVariant.Ghost)
                    PremiumButton(text = "Exit", onClick = onExit, variant = ButtonVariant.Secondary)
                }
            }
        }
    }

    if (state is PlayerUiState.Error) {
        ErrorOverlay(message = state.message, onExit = onExit)
    }
}

@Composable
private fun ErrorOverlay(message: String, onExit: () -> Unit) {
    val spacing = LocalPremiumSpacing.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PremiumColors.BackgroundBase.copy(alpha = 0.9f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(PremiumColors.SurfaceFloating)
                .padding(spacing.xl),
            verticalArrangement = Arrangement.spacedBy(spacing.m),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Playback error",
                style = PremiumType.TitleLarge.copy(color = PremiumColors.OnSurfaceHigh),
            )
            Text(
                text = message,
                style = PremiumType.Body.copy(color = PremiumColors.DangerRed),
            )
            Spacer(modifier = Modifier.height(spacing.s))
            PremiumButton(text = "Back", onClick = onExit)
        }
    }
}

private fun PlayerUiState.chipLabel(): String = when (this) {
    PlayerUiState.Starting -> "Starting"
    is PlayerUiState.Buffering -> "Buffering"
    is PlayerUiState.Playing -> "Playing"
    is PlayerUiState.Paused -> "Paused"
    is PlayerUiState.Stopped -> "Stopped"
    is PlayerUiState.Error -> "Error"
}

private fun PlayerUiState.chipAccent() = when (this) {
    is PlayerUiState.Playing -> PremiumColors.SuccessGreen
    is PlayerUiState.Error -> PremiumColors.DangerRed
    PlayerUiState.Starting, is PlayerUiState.Buffering -> PremiumColors.AccentCyan
    else -> PremiumColors.OnSurfaceMuted
}

private fun PlayerUiState.positionLabel(): String? {
    fun format(pos: Int, dur: Int?): String = if (dur == null) {
        formatSeconds(pos)
    } else {
        "${formatSeconds(pos)} / ${formatSeconds(dur)}"
    }
    return when (this) {
        is PlayerUiState.Playing -> format(positionSeconds, durationSeconds)
        is PlayerUiState.Paused -> format(positionSeconds, durationSeconds)
        is PlayerUiState.Buffering -> format(positionSeconds, null)
        else -> null
    }
}

private fun formatSeconds(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@Preview(name = "Player · playing overlay", widthDp = 1280, heightDp = 720, showBackground = true, backgroundColor = 0xFF050608)
@Composable
private fun PlayerOverlayPreview() {
    PremiumTvTheme {
        PlayerOverlay(
            state = PlayerUiState.Playing(
                session = com.premiumtvplayer.app.data.api.PlaybackSessionDto(
                    id = "sess1",
                    profileId = "p1",
                    sourceId = "s1",
                    itemId = "vod1",
                    itemType = "vod",
                    state = "playing",
                    latestPositionSeconds = 420,
                    sessionStartedAt = "2026-04-13T20:00:00.000Z",
                ),
                positionSeconds = 420,
                durationSeconds = 7200,
            ),
            itemTitle = "Tonight's Feature",
            onPlayPause = {},
            onSeek = {},
            onExit = {},
        )
    }
}
