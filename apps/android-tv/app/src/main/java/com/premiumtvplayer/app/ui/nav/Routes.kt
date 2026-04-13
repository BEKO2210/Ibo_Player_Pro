package com.premiumtvplayer.app.ui.nav

/** Stable, type-narrow string identifiers for the V1 nav graph. */
object Routes {
    const val Boot = "boot"
    const val Welcome = "welcome"
    const val Signup = "signup"
    const val Login = "login"
    const val TrialActivation = "trial-activation"
    const val ProfilePicker = "profile-picker"

    // ── Home (Run 14) ──────────────────────────────────────────────
    /** Nav argument key consumed by `HomeViewModel` via SavedStateHandle. */
    const val ProfileIdArg = "profileId"

    /** Pattern registered with NavHost. */
    const val HomePattern = "home?$ProfileIdArg={$ProfileIdArg}"

    /** Builder — use this at call sites to navigate into Home. */
    fun home(profileId: String? = null): String =
        if (profileId == null) "home" else "home?$ProfileIdArg=$profileId"

    // ── Sources (Run 15) ───────────────────────────────────────────
    const val SourceIdArg = "sourceId"

    const val Sources = "sources"
    const val AddSource = "sources/add"
    const val EpgBrowsePattern = "sources/{$SourceIdArg}/epg"

    fun epgBrowse(sourceId: String): String = "sources/$sourceId/epg"

    // ── Player (Run 16) ────────────────────────────────────────────
    const val ItemIdArg = "itemId"
    const val ItemTypeArg = "itemType"
    const val MediaUrlArg = "mediaUrl"
    const val ItemTitleArg = "itemTitle"

    /**
     * Player requires profileId + sourceId + itemId + itemType, plus a
     * pre-resolved mediaUrl + title. The mediaUrl is URL-encoded by the
     * caller.
     */
    const val PlayerPattern =
        "play/{$SourceIdArg}/{$ItemIdArg}/{$ItemTypeArg}?" +
            "$ProfileIdArg={$ProfileIdArg}&$MediaUrlArg={$MediaUrlArg}&$ItemTitleArg={$ItemTitleArg}"

    // ── Paywall (Run 17) ───────────────────────────────────────────
    const val Paywall = "paywall"

    // ── Diagnostics (Run 19) ───────────────────────────────────────
    const val Diagnostics = "diagnostics"

    // ── Parental controls (Run 18) ─────────────────────────────────
    const val ProfileManagement = "profiles/manage"
    const val DeviceManagement = "devices/manage"
    const val ProfileNameArg = "profileName"
    const val PinGatePattern = "profiles/{$ProfileIdArg}/pin-gate?$ProfileNameArg={$ProfileNameArg}"

    fun pinGate(profileId: String, profileName: String): String =
        "profiles/$profileId/pin-gate?$ProfileNameArg=${java.net.URLEncoder.encode(profileName, "UTF-8")}"

    fun play(
        profileId: String,
        sourceId: String,
        itemId: String,
        itemType: String,
        mediaUrl: String,
        title: String,
    ): String {
        val enc = fun(s: String): String = java.net.URLEncoder.encode(s, "UTF-8")
        return "play/$sourceId/${enc(itemId)}/$itemType" +
            "?$ProfileIdArg=$profileId" +
            "&$MediaUrlArg=${enc(mediaUrl)}" +
            "&$ItemTitleArg=${enc(title)}"
    }
}
