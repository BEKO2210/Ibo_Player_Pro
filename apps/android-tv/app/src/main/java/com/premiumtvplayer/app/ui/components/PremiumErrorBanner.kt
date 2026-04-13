package com.premiumtvplayer.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.premiumtvplayer.app.R
import com.premiumtvplayer.app.data.api.UserErrorMessage
import com.premiumtvplayer.app.ui.theme.LocalPremiumSpacing
import com.premiumtvplayer.app.ui.theme.PremiumColors
import com.premiumtvplayer.app.ui.theme.PremiumTvTheme
import com.premiumtvplayer.app.ui.theme.PremiumType

/**
 * Shared premium error banner. Every loading surface that can fail
 * should render this instead of a raw `Text(color = DangerRed)`.
 *
 * Resolves `UserErrorMessage` through `stringResource(...)` so the
 * active locale wins automatically. Accepts an optional retry button
 * slot.
 */
@Composable
fun PremiumErrorBanner(
    message: UserErrorMessage,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    val spacing = LocalPremiumSpacing.current
    val text = when (message) {
        is UserErrorMessage.Raw -> message.message.ifBlank { stringResource(R.string.error_generic) }
        is UserErrorMessage.Resource -> stringResource(message.resId)
        is UserErrorMessage.ResourceWithArg -> stringResource(message.resId, message.arg)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(PremiumColors.DangerRed.copy(alpha = 0.12f))
            .padding(horizontal = spacing.m, vertical = spacing.s),
        horizontalArrangement = Arrangement.spacedBy(spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = text,
                style = PremiumType.BodySmall.copy(color = PremiumColors.DangerRed),
            )
        }
        if (onRetry != null) {
            PremiumButton(
                text = stringResource(R.string.common_retry),
                onClick = onRetry,
                variant = ButtonVariant.Secondary,
            )
        }
    }
}

/** Convenience overload for call sites that still emit raw strings. */
@Composable
fun PremiumErrorBanner(
    raw: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    PremiumErrorBanner(UserErrorMessage.Raw(raw), modifier, onRetry)
}

@Preview(name = "PremiumErrorBanner · raw", widthDp = 720, heightDp = 160, showBackground = true, backgroundColor = 0xFF050608)
@Composable
private fun BannerRawPreview() {
    PremiumTvTheme {
        Box(modifier = Modifier.padding(LocalPremiumSpacing.current.l)) {
            PremiumErrorBanner(raw = "Couldn't reach the server.", onRetry = {})
        }
    }
}

@Preview(name = "PremiumErrorBanner · localized", widthDp = 720, heightDp = 160, showBackground = true, backgroundColor = 0xFF050608)
@Composable
private fun BannerLocalizedPreview() {
    PremiumTvTheme {
        Box(modifier = Modifier.padding(LocalPremiumSpacing.current.l)) {
            PremiumErrorBanner(
                message = UserErrorMessage.Resource(R.string.error_entitlement_required),
                onRetry = {},
            )
        }
    }
}
