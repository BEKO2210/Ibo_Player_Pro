package com.premiumtvplayer.app.data.util

/**
 * A trivially-mockable wall clock. Production gets [SystemClock] via
 * Hilt; tests inject a fake that returns a controlled timestamp.
 *
 * We deliberately do not use `java.time.Clock` here — the JDK type is
 * heavy and forces every caller to handle Instant/Duration. For our
 * cache-TTL use case all we need is `currentTimeMillis()`.
 */
interface Clock {
    fun nowMillis(): Long
}

/** Backed by [java.lang.System.currentTimeMillis]. */
object SystemClock : Clock {
    override fun nowMillis(): Long = java.lang.System.currentTimeMillis()
}
