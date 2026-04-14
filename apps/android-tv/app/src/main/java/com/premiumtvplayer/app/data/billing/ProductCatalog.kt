package com.premiumtvplayer.app.data.billing

/**
 * V1 product catalog — the client-side mirror of
 * `BILLING_PRODUCT_ID_SINGLE` / `BILLING_PRODUCT_ID_FAMILY` on the API.
 *
 * Ids MUST match the Google Play Console in-app products + the two
 * backend env vars (see `services/api/.env.example`). Keep in sync.
 */
enum class PremiumProduct(
    val productId: String,
    val displayName: String,
    val tagline: String,
    val bullets: List<String>,
) {
    Single(
        productId = "premium_player_single",
        displayName = "Lifetime · Single",
        tagline = "For one household member on one screen.",
        bullets = listOf(
            "One lifetime purchase — no recurring fees.",
            "1 active device.",
            "1 profile.",
            "All core features: live TV, VOD, EPG, Continue Watching.",
        ),
    ),
    Family(
        productId = "premium_player_family",
        displayName = "Lifetime · Family",
        tagline = "Premium across every TV in the house.",
        bullets = listOf(
            "One lifetime purchase — no recurring fees.",
            "Up to 5 active devices.",
            "Up to 5 profiles (kids-mode + PIN).",
            "Cloud sync: favorites, history, resume points.",
        ),
    ),
    ;

    companion object {
        fun fromId(productId: String): PremiumProduct? =
            entries.firstOrNull { it.productId == productId }
    }
}
