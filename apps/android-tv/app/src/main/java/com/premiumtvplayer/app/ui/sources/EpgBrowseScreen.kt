package com.premiumtvplayer.app.ui.sources

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.Text
import com.premiumtvplayer.app.data.epg.EpgBrowseSnapshot
import com.premiumtvplayer.app.data.epg.EpgChannel
import com.premiumtvplayer.app.data.epg.EpgProgramme
import com.premiumtvplayer.app.ui.components.BootProgress
import com.premiumtvplayer.app.ui.components.ButtonVariant
import com.premiumtvplayer.app.ui.components.ChipStyle
import com.premiumtvplayer.app.ui.components.PremiumButton
import com.premiumtvplayer.app.ui.components.PremiumChip
import com.premiumtvplayer.app.ui.theme.LocalPremiumSpacing
import com.premiumtvplayer.app.ui.theme.PremiumColors
import com.premiumtvplayer.app.ui.theme.PremiumTvTheme
import com.premiumtvplayer.app.ui.theme.PremiumType
import java.time.Duration
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Programme guide for a single source. Channels render vertically, each
 * with a horizontal timeline of programme blocks. 30-minute blocks map
 * to a fixed per-minute width so different channels align visually on
 * the timeline.
 *
 * Run 15 fixture: [EpgRepository.browse] returns deterministic programme
 * blocks. Run 16 swaps the data source for the `/v1/epg/` endpoints
 * served by the EPG worker — the composable contract stays the same.
 */
@Composable
fun EpgBrowseScreen(
    onBack: () -> Unit,
    viewModel: EpgBrowseViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val spacing = LocalPremiumSpacing.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PremiumColors.BackgroundBase)
            .padding(spacing.pageGutter),
    ) {
        when (val s = state) {
            EpgUiState.Loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                BootProgress()
            }
            is EpgUiState.Error -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(spacing.l),
                modifier = Modifier.align(Alignment.Center),
            ) {
                Text(text = s.message, style = PremiumType.Body.copy(color = PremiumColors.DangerRed))
                PremiumButton(text = "Back", onClick = onBack, variant = ButtonVariant.Ghost)
            }
            is EpgUiState.Ready -> Grid(
                snapshot = s.snapshot,
                focusedProgrammeId = s.focusedProgrammeId,
                onBack = onBack,
                onFocusProgramme = viewModel::onFocusProgramme,
            )
        }
    }
}

private val MinuteWidth: Dp = 3.dp
private val ChannelColumnWidth: Dp = 220.dp
private val RowHeight: Dp = 84.dp

@Composable
private fun Grid(
    snapshot: EpgBrowseSnapshot,
    focusedProgrammeId: String?,
    onBack: () -> Unit,
    onFocusProgramme: (String?) -> Unit,
) {
    val spacing = LocalPremiumSpacing.current
    val vertical = rememberScrollState()

    Column(verticalArrangement = Arrangement.spacedBy(spacing.m)) {
        // Header row: title + back + overlay
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.m),
        ) {
            Text(
                text = "Programme Guide",
                style = PremiumType.DisplayLarge.copy(color = PremiumColors.OnSurfaceHigh),
                modifier = Modifier.weight(1f),
            )
            PremiumButton(text = "Back", onClick = onBack, variant = ButtonVariant.Ghost)
        }

        // Focused programme overlay (Bravia-style hero strip above the grid).
        val focused = focusedProgrammeId?.let { id ->
            snapshot.programmesByChannel.values.firstNotNullOfOrNull { list ->
                list.firstOrNull { it.id == id }
            }
        }
        FocusedProgrammeOverlay(focused)

        // Time axis.
        TimeAxis(snapshot = snapshot)

        Column(modifier = Modifier.verticalScroll(vertical)) {
            snapshot.channels.forEach { channel ->
                ChannelRow(
                    channel = channel,
                    programmes = snapshot.programmesByChannel[channel.id].orEmpty(),
                    windowStartMinutes = 0,
                    onFocusProgramme = onFocusProgramme,
                )
            }
        }
    }
}

@Composable
private fun TimeAxis(snapshot: EpgBrowseSnapshot) {
    val spacing = LocalPremiumSpacing.current
    val fmt = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val zoneId = remember { ZoneId.systemDefault() }
    val slotMinutes = 30L
    val totalMinutes = Duration.between(snapshot.windowStart, snapshot.windowEnd).toMinutes()
    val slotCount = (totalMinutes / slotMinutes).toInt()

    Row {
        Spacer(modifier = Modifier.width(ChannelColumnWidth))
        (0 until slotCount).forEach { slot ->
            val ts = snapshot.windowStart.plusSeconds(slot * slotMinutes * 60)
            val local = ts.atZone(zoneId).toLocalTime()
            Box(
                modifier = Modifier
                    .width(MinuteWidth * slotMinutes.toInt())
                    .padding(end = spacing.xxs),
            ) {
                Text(
                    text = fmt.format(local),
                    style = PremiumType.LabelSmall.copy(color = PremiumColors.OnSurfaceMuted),
                )
            }
        }
    }
}

@Composable
private fun ChannelRow(
    channel: EpgChannel,
    programmes: List<EpgProgramme>,
    @Suppress("SameParameterValue") windowStartMinutes: Int,
    onFocusProgramme: (String?) -> Unit,
) {
    val spacing = LocalPremiumSpacing.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(RowHeight + spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Channel gutter on the left — non-focusable, always visible.
        Column(
            modifier = Modifier
                .width(ChannelColumnWidth)
                .padding(end = spacing.s),
        ) {
            Text(
                text = channel.displayName,
                style = PremiumType.Label.copy(color = PremiumColors.OnSurface),
                maxLines = 1,
            )
            Text(
                text = "ch ${channel.id.takeLast(3)}",
                style = PremiumType.LabelSmall.copy(color = PremiumColors.OnSurfaceDim),
                maxLines = 1,
            )
        }
        // Programmes row — horizontal TvLazyRow so the D-pad keeps working.
        TvLazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.xxs)) {
            items(programmes) { programme ->
                ProgrammeBlock(
                    programme = programme,
                    onFocus = onFocusProgramme,
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(spacing.xxs))
}

@Composable
private fun ProgrammeBlock(
    programme: EpgProgramme,
    onFocus: (String?) -> Unit,
) {
    val spacing = LocalPremiumSpacing.current
    val minutes = Duration.between(programme.startsAt, programme.endsAt).toMinutes().coerceAtLeast(15L)
    val width = MinuteWidth * minutes.toInt()
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(10.dp)

    Box(
        modifier = Modifier
            .width(width)
            .height(RowHeight)
            .clip(shape)
            .background(
                if (focused) PremiumColors.SurfaceFloating else PremiumColors.SurfaceElevated,
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) PremiumColors.FocusAccent else PremiumColors.SurfaceHigh,
                shape = shape,
            )
            .clickable(interactionSource = interaction, indication = null) { onFocus(programme.id) }
            .padding(horizontal = spacing.s, vertical = spacing.xs),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
            Text(
                text = programme.title,
                style = PremiumType.Label.copy(color = PremiumColors.OnSurfaceHigh),
                maxLines = 1,
            )
            programme.category?.let {
                Text(
                    text = it.uppercase(),
                    style = PremiumType.LabelSmall.copy(color = PremiumColors.AccentCyan),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun FocusedProgrammeOverlay(programme: EpgProgramme?) {
    val spacing = LocalPremiumSpacing.current
    val shape = RoundedCornerShape(16.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (programme == null) PremiumColors.SurfaceBase
                else PremiumColors.SurfaceFloating,
            )
            .padding(spacing.l),
        verticalArrangement = Arrangement.spacedBy(spacing.s),
    ) {
        if (programme == null) {
            Text(
                text = "Focus any programme block to see details.",
                style = PremiumType.Body.copy(color = PremiumColors.OnSurfaceMuted),
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
                PremiumChip(
                    label = programme.category ?: "Programme",
                    style = ChipStyle.Outline,
                    accent = PremiumColors.AccentCyan,
                )
                PremiumChip(
                    label = timeRangeFor(programme),
                    style = ChipStyle.Outline,
                    accent = PremiumColors.OnSurfaceMuted,
                )
            }
            Text(
                text = programme.title,
                style = PremiumType.Headline.copy(color = PremiumColors.OnSurfaceHigh),
                maxLines = 1,
            )
            programme.subtitle?.let {
                Text(
                    text = it,
                    style = PremiumType.Body.copy(color = PremiumColors.OnSurface),
                    maxLines = 1,
                )
            }
            programme.description?.let {
                Text(
                    text = it,
                    style = PremiumType.BodySmall.copy(color = PremiumColors.OnSurfaceMuted),
                    maxLines = 2,
                )
            }
        }
    }
}

private fun timeRangeFor(programme: EpgProgramme): String {
    val fmt = DateTimeFormatter.ofPattern("HH:mm")
    val zone = ZoneId.systemDefault()
    return "${fmt.format(programme.startsAt.atZone(zone))}–${fmt.format(programme.endsAt.atZone(zone))}"
}

// Used indirectly via ColorFilter defaults — keeps the import from getting
// flagged as unused since the screen uses a mix of token + Color literals in
// Previews only.
@Suppress("unused")
private val ignoredColorHint: Color = Color.Transparent

@Preview(name = "EpgBrowse · overlay", widthDp = 1280, heightDp = 720, showBackground = true, backgroundColor = 0xFF050608)
@Composable
private fun EpgBrowseOverlayPreview() {
    PremiumTvTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PremiumColors.BackgroundBase)
                .padding(LocalPremiumSpacing.current.pageGutter),
        ) {
            Column(
                modifier = Modifier.widthIn(max = 1080.dp),
                verticalArrangement = Arrangement.spacedBy(LocalPremiumSpacing.current.m),
            ) {
                FocusedProgrammeOverlay(
                    programme = EpgProgramme(
                        id = "p1",
                        channelId = "ch1",
                        sourceId = "s1",
                        title = "Cinematic Feature",
                        subtitle = "Episode 3",
                        description = "Premium-tier placeholder programme.",
                        category = "Movies",
                        startsAt = java.time.Instant.parse("2026-04-13T20:00:00Z"),
                        endsAt = java.time.Instant.parse("2026-04-13T21:30:00Z"),
                    ),
                )
            }
        }
    }
}
