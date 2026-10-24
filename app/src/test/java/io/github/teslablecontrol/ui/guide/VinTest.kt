package io.github.teslablecontrol.ui.guide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VinTest {
    @Test
    fun recognisesValidVins() {
        assertTrue(isValidVin("5YJ3E1EA1PF000001"))
        assertFalse(isValidVin("5YJ3E1EA1PF00000"))
        // I, O and Q are never used in a VIN.
        assertFalse(isValidVin("5YJ3E1EA1PF00000O"))
    }

    @Test
    fun findsTheVinInCopiedText() {
        assertEquals("5YJ3E1EA1PF000001", findVin("5YJ3E1EA1PF000001"))
        assertEquals("5YJ3E1EA1PF000001", findVin("  VIN: 5yj3e1ea1pf000001\n"))
        assertEquals("LRW3E7FS2RC000123", findVin("My car (LRW3E7FS2RC000123) is parked"))
        assertNull(findVin("Hello"))
        // Longer runs of letters and digits aren't a VIN.
        assertNull(findVin("5YJ3E1EA1PF0000012"))
    }
}
