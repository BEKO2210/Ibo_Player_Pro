package com.premiumtvplayer.app.data.parental

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgeFilterTest {

    @Test
    fun `null ageLimit allows everything`() {
        assertTrue(AgeFilter.isAllowed(null, "TV-MA"))
        assertTrue(AgeFilter.isAllowed(null, "PG-13"))
        assertTrue(AgeFilter.isAllowed(null, "totally-unknown"))
    }

    @Test
    fun `null or blank rating is allowed under any cap`() {
        assertTrue(AgeFilter.isAllowed(12, null))
        assertTrue(AgeFilter.isAllowed(0, ""))
    }

    @Test
    fun `MPAA PG-13 blocked by cap of 12, allowed by cap of 13`() {
        assertFalse(AgeFilter.isAllowed(12, "PG-13"))
        assertTrue(AgeFilter.isAllowed(13, "PG-13"))
    }

    @Test
    fun `TV-MA blocked for kids profile`() {
        assertFalse(AgeFilter.isAllowed(12, "TV-MA"))
        assertTrue(AgeFilter.isAllowed(18, "TV-MA"))
    }

    @Test
    fun `FSK ratings parsed correctly`() {
        assertEquals(12, AgeFilter.ratingToMinAge("FSK 12"))
        assertEquals(6, AgeFilter.ratingToMinAge("FSK6"))
        assertEquals(18, AgeFilter.ratingToMinAge("fsk-18"))
    }

    @Test
    fun `BBFC ratings`() {
        assertEquals(0, AgeFilter.ratingToMinAge("U"))
        assertEquals(12, AgeFilter.ratingToMinAge("12A"))
        assertEquals(15, AgeFilter.ratingToMinAge("15"))
        assertEquals(18, AgeFilter.ratingToMinAge("18"))
    }

    @Test
    fun `unknown rating returns null`() {
        assertNull(AgeFilter.ratingToMinAge("ABSOLUTE_GARBAGE"))
    }
}
