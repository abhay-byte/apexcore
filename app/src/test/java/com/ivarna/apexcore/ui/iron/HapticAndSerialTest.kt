package com.ivarna.apexcore.ui.iron

import android.content.Context
import android.provider.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class HapticGateTest {
    @Test
    fun `enforces 80ms floor`() {
        var now = 0L
        val g = HapticGate { now }
        assertTrue(g.allow())
        now = 50
        assertFalse("50ms later must be swallowed", g.allow())
        now = 90
        assertTrue("90ms later passes", g.allow())
    }
}

class SerialNumberTest {
    @Test
    fun `same id gives same serial`() {
        assertEquals(SerialNumber.hashOf("a1b2"), SerialNumber.hashOf("a1b2"))
        assertNotEquals(SerialNumber.hashOf("a1b2"), SerialNumber.hashOf("zzz"))
    }

    @Test
    fun `serial format matches XX-NNNN uppercase and non-empty`() {
        val formatRegex = Regex("^[A-Z]{2}-\\d{4}$")
        val ids = listOf("a1b2", "device_123", "apexcore_unit_test", "", "00000000")
        for (id in ids) {
            val serial = SerialNumber.hashOf(id)
            assertTrue("Serial should not be empty", serial.isNotEmpty())
            assertTrue("Serial $serial must match XX-NNNN format", formatRegex.matches(serial))
            assertEquals("Serial $serial must be uppercase", serial.uppercase(), serial)
        }
    }

    @Test
    fun `serial generate with context is deterministic`() {
        val context = mock(Context::class.java)
        val resolver = mock(android.content.ContentResolver::class.java)
        `when`(context.contentResolver).thenReturn(resolver)
        val s1 = SerialNumber.generate(context)
        val s2 = SerialNumber.generate(context)
        assertEquals(s1, s2)
        val formatRegex = Regex("^[A-Z]{2}-\\d{4}$")
        assertTrue(formatRegex.matches(s1))
    }
}
