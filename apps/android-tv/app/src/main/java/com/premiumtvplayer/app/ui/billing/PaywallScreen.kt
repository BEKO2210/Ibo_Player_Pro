package com.premiumtvplayer.app.ui.billing

import android.app.Activity
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.premiumtvplayer.app.data.billing.BillingSku
import com.premiumtvplayer.app.data.billing.PremiumProduct
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
 * Two-plan paywall. Canonical Premium-TV layout:
 *
 *   hero title
 *     two side-by-side plan cards (Single · Family)
 *   restore + back buttons at the bottom
 *
 * Auto-dismisses when the server confirms the purchase / restore.
 */
@Composable
fun PaywallScreen(
    onPurchased: () -> Unit,
    onBack: () -> Unit,
    viewModel: PaywallViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state) {
        if (state is PaywallUiState.PurchaseSucceeded) onPurchased()
    }

    val spacing = LocalPremiumSpacing.current
    val background = Brush.radialGradient(
        colors = listOf(
            PremiumColors.AccentBlueDeep.copy(alpha = 0.25f),
            PremiumColors.BackgroundBase,
        ),
        center = Offset(x = 0f, y = 0f),
        radius = 2400f,
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .padding(horizontal = spacing.pageGutter, vertical = spacing.huge),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(spacing.l),
        ) {
            Header()
            when (val s = state) {
                PaywallUiState.Loading -> Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) { BootProgress() }
                is PaywallUiState.Ready -> PlanGrid(
                    skus = s.skus,
                    submitting = s.submitting,
                    errorMessage = s.errorMessage,
                    onPickPlan = { sku ->
                        val activity = context as? Activity
                        if (activity != null) viewModel.launchPurchase(activity, sku)
                    },
                    onRestore = viewModel::restore,
                    onBack = onBack,
                )
                is PaywallUiState.PurchaseSucceeded -> Text(
                    text = "You're in. Welcome to Premium.",
                    style = PremiumType.Headline.copy(color = PremiumColors.SuccessGreen),
                )
                is PaywallUiState.Error -> ErrorState(
                    message = s.message,
                    onRetry = viewModel::loadSkus,
                    onBack = onBack,
                )
            }
        }
    }
}

@Composable
private fun Header() {
    val spacing = LocalPremiumSpacing.current
    Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
            PremiumChip(label = "One-time", style = ChipStyle.Outline, accent = PremiumColors.AccentCyan)
            PremiumChip(label = "No subscription", style = ChipStyle.Outline, accent = PremiumColors.SuccessGreen)
        }
        Text(
            text = "Premium for life",
            style = PremiumType.DisplayLarge.copy(color = PremiumColors.OnSurfaceHigh),
        )
        Text(
            text = "Two lifetime plans. Pay once, keep it. Cancel the trial whenever " +
                "or let it roll into the plan that fits your household.",
            style = PremiumType.Body.copy(color = PremiumColors.OnSurface),
        )
    }
}

@Composable
private fun PlanGrid(
    skus: List<BillingSku>,
    submitting: Boolean,
    errorMessage: String?,
    onPickPlan: (BillingSku) -> Unit,
    onRestore: () -> Unit,
    onBack: () -> Unit,
) {
    val spacing = LocalPremiumSpacing.current
    Column(verticalArrangement = Arrangement.spacedBy(spacing.l)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.l),
        ) {
            skus.forEach { sku ->
                PlanCard(
                    sku = sku,
                    submitting = submitting,
                    highlighted = sku.product == PremiumProduct.Family,
                    onPick = { onPickPlan(sku) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (errorMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(PremiumColors.DangerRed.copy(alpha = 0.12f))
                    .padding(horizontal = spacing.m, vertical = spacing.s),
            ) {
                Text(
                    text = errorMessage,
                    style = PremiumType.BodySmall.copy(color = PremiumColors.DangerRed),
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PremiumButton(text = "Restore Purchases", onClick = onRestore, variant = ButtonVariant.Secondary, enabled = !submitting)
            PremiumButton(text = "Not Now", onClick = onBack, variant = ButtonVariant.Ghost, enabled = !submitting)
        }
    }
}

@Composable
private fun PlanCard(
    sku: BillingSku,
    submitting: Boolean,
    highlighted: Boolean,
    onPick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalPremiumSpacing.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(16.dp)
    val borderColor = when {
        focused -> PremiumColors.FocusAccent
        highlighted -> PremiumColors.AccentCyan
        else -> PremiumColors.SurfaceHigh
    }
    val background = if (highlighted) PremiumColors.SurfaceFloating else PremiumColors.SurfaceElevated

    Column(
        modifier = modifier
            .clip(shape)
            .background(background)
            .border(width = if (focused || highlighted) 2.dp else 1.dp, color = borderColor, shape = shape)
            .clickable(interactionSource = interaction, indication = null, enabled = !submitting, onClick = onPick)
            .padding(spacing.xl),
        verticalArrangement = Arrangement.spacedBy(spacing.m),
    ) {
        if (highlighted) {
            PremiumChip(label = "Recommended", accent = PremiumColors.AccentCyan)
        }
        Text(
            text = sku.product.displayName,
            style = PremiumType.TitleLarge.copy(color = PremiumColors.OnSurfaceHigh),
        )
        Text(
            text = sku.product.tagline,
            style = PremiumType.BodySmall.copy(color = PremiumColors.OnSurfaceMuted),
        )
        Spacer(modifier = Modifier.height(spacing.xs))
        Text(
            text = sku.formattedPrice,
            style = PremiumType.DisplayHero.copy(color = PremiumColors.OnSurfaceHigh),
        )
        Spacer(modifier = Modifier.height(spacing.xs))
        sku.product.bullets.forEach { bullet ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.s),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .width(6.dp)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(PremiumColors.AccentCyan),
                )
                Text(
                    text = bullet,
                    style = PremiumType.Body.copy(color = PremiumColors.OnSurface),
                )
            }
        }
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit, onBack: () -> Unit) {
    val spacing = LocalPremiumSpacing.current
    Column(verticalArrangement = Arrangement.spacedBy(spacing.m)) {
        Text(
            text = message,
            style = PremiumType.Body.copy(color = PremiumColors.DangerRed),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.m)) {
            PremiumButton(text = "Retry", onClick = onRetry)
            PremiumButton(text = "Back", onClick = onBack, variant = ButtonVariant.Ghost)
        }
    }
}

@Preview(name = "PaywallScreen · mocked skus", widthDp = 1280, heightDp = 720, showBackground = true, backgroundColor = 0xFF050608)
@Composable
private fun PaywallScreenPreview() {
    // Play Billing's ProductDetails can't be instantiated outside the
    // SDK, so the preview renders the header + a stub plan grid using
    // the catalog copy only.
    PremiumTvTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PremiumColors.BackgroundBase)
                .padding(LocalPremiumSpacing.current.pageGutter),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(LocalPremiumSpacing.current.l)) {
                Header()
                // Simplified preview card rendering — no real ProductDetails.
                Row(horizontalArrangement = Arrangement.spacedBy(LocalPremiumSpacing.current.l)) {
                    PremiumProduct.entries.forEach { product ->
                        Column(
                            modifier = Modifier
                                .widthIn(max = 420.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (product == PremiumProduct.Family) PremiumColors.SurfaceFloating
                                    else PremiumColors.SurfaceElevated,
                                )
                                .border(
                                    width = if (product == PremiumProduct.Family) 2.dp else 1.dp,
                                    color = if (product == PremiumProduct.Family) PremiumColors.AccentCyan
                                    else PremiumColors.SurfaceHigh,
                                    shape = RoundedCornerShape(16.dp),
                                )
                                .padding(LocalPremiumSpacing.current.xl),
                            verticalArrangement = Arrangement.spacedBy(LocalPremiumSpacing.current.s),
                        ) {
                            Text(
                                text = product.displayName,
                                style = PremiumType.TitleLarge.copy(color = PremiumColors.OnSurfaceHigh),
                            )
                            Text(
                                text = product.tagline,
                                style = PremiumType.BodySmall.copy(color = PremiumColors.OnSurfaceMuted),
                            )
                            Text(
                                text = if (product == PremiumProduct.Family) "€49.99" else "€24.99",
                                style = PremiumType.DisplayHero.copy(color = PremiumColors.OnSurfaceHigh),
                            )
                        }
                    }
                }
            }
        }
    }
}
