package com.premiumtvplayer.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.premiumtvplayer.app.R
import com.premiumtvplayer.app.ui.theme.PremiumTvTheme

/**
 * Brand mark — renders `assets/logo/logo-no_background.png` (mirrored
 * into `res/drawable/brand_logo.png`).
 *
 * Three canonical sizes covering every place the logo appears in V1:
 *   - [BrandLogoSize.Splash]   — boot screen / cinematic intro
 *   - [BrandLogoSize.Hero]     — home-screen header chip
 *   - [BrandLogoSize.Inline]   — settings, about, footer
 *
 * The image already has a transparent background, so it composites
 * cleanly over any premium dark surface without an extra circle/plate.
 */
enum class BrandLogoSize(val dimension: Dp) {
    Splash(160.dp),
    Hero(96.dp),
    Inline(48.dp),
}

@Composable
fun BrandLogo(
    modifier: Modifier = Modifier,
    size: BrandLogoSize = BrandLogoSize.Hero,
    colorFilter: ColorFilter? = null,
) {
    Image(
        painter = painterResource(id = R.drawable.brand_logo),
        contentDescription = "Premium TV Player",
        modifier = modifier.size(size.dimension),
        contentScale = ContentScale.Fit,
        colorFilter = colorFilter,
    )
}

@Preview(name = "BrandLogo · Splash", widthDp = 240, heightDp = 240, showBackground = true, backgroundColor = 0xFF050608)
@Composable
private fun BrandLogoSplashPreview() {
    PremiumTvTheme { BrandLogo(size = BrandLogoSize.Splash) }
}

@Preview(name = "BrandLogo · Hero", widthDp = 160, heightDp = 160, showBackground = true, backgroundColor = 0xFF050608)
@Composable
private fun BrandLogoHeroPreview() {
    PremiumTvTheme { BrandLogo(size = BrandLogoSize.Hero) }
}

@Preview(name = "BrandLogo · Inline", widthDp = 96, heightDp = 96, showBackground = true, backgroundColor = 0xFF050608)
@Composable
private fun BrandLogoInlinePreview() {
    PremiumTvTheme { BrandLogo(size = BrandLogoSize.Inline) }
}
