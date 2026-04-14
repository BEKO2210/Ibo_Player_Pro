package com.premiumtvplayer.app.data.parental

/**
 * Age-gating helper. Pure — no Compose, no DI.
 *
 * Backend profiles carry an optional `ageLimit` (Int 0..21). Content
 * items carry an optional `rating` string (e.g. "PG-13", "TV-MA").
 * When a kids profile is active, any content rated above the profile's
 * cap is locked with a PIN prompt.
 *
 * This implementation handles the most common rating vocabularies:
 *  - `PG`, `PG-13`, `R`, `NC-17`                     (MPAA)
 *  - `TV-Y`, `TV-Y7`, `TV-G`, `TV-PG`, `TV-14`, `TV-MA`  (US TV)
 *  - `U`, `PG`, `12`, `12A`, `15`, `18`              (BBFC)
 *  - `FSK 0` / `FSK 6` / `FSK 12` / `FSK 16` / `FSK 18` (DE)
 *  - bare numbers ("12", "16")
 *
 * Unknown ratings default to "allowed" — the safer default for an
 * adult profile; kids profiles force an explicit numeric rating via
 * source validation (a later polish pass).
 */
object AgeFilter {

    /**
     * @param profileAgeLimit `null` means no cap (adult default).
     * @param rating raw rating string from the item's EPG/VOD metadata
     * @return true if the item is allowed under this profile's cap
     */
    fun isAllowed(profileAgeLimit: Int?, rating: String?): Boolean {
        if (profileAgeLimit == null) return true
        if (rating.isNullOrBlank()) return true
        val minAge = ratingToMinAge(rating) ?: return true
        return minAge <= profileAgeLimit
    }

    /**
     * Translate a rating string to the minimum viewer age it implies.
     * Returns null when the rating is unknown.
     */
    fun ratingToMinAge(rating: String): Int? {
        val norm = rating.trim().uppercase().replace("_", "-")

        // FSK <n>  — German. "FSK 12" or "FSK12"
        fskRegex.matchEntire(norm)?.let { return it.groupValues[1].toInt() }

        // Bare numeric ("12", "16", "18")
        bareNumberRegex.matchEntire(norm)?.let { return it.value.toInt() }

        return when (norm) {
            // MPAA
            "G" -> 0
            "PG" -> 8
            "PG-13" -> 13
            "R" -> 17
            "NC-17" -> 18
            // US TV
            "TV-Y" -> 0
            "TV-Y7", "TV-Y7-FV" -> 7
            "TV-G" -> 0
            "TV-PG" -> 10
            "TV-14" -> 14
            "TV-MA" -> 17
            // BBFC
            "U" -> 0
            "12A", "12" -> 12
            "15" -> 15
            "18" -> 18
            else -> null
        }
    }

    private val fskRegex = Regex("FSK[\\s-]*([0-9]+)")
    private val bareNumberRegex = Regex("[0-9]+")
}
