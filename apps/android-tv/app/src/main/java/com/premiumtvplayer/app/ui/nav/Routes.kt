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
}
