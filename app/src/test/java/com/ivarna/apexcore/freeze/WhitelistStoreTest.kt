package com.ivarna.apexcore.freeze

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq

class WhitelistStoreTest {

    private val context: Context = mock(Context::class.java)
    private val prefs: SharedPreferences = mock(SharedPreferences::class.java)
    private val editor: SharedPreferences.Editor = mock(SharedPreferences.Editor::class.java)

    @Before
    fun setUp() {
        `when`(context.applicationContext).thenReturn(context)
        `when`(context.getSharedPreferences("apexcore_whitelist", Context.MODE_PRIVATE)).thenReturn(prefs)
        `when`(prefs.edit()).thenReturn(editor)
        `when`(editor.putStringSet(any(), any())).thenReturn(editor)
    }

    @Test
    fun `empty by default`() {
        `when`(prefs.getStringSet("pinned_packages", emptySet())).thenReturn(null)
        assertTrue(WhitelistStore.allPinned(context).isEmpty())
        assertFalse(WhitelistStore.isPinned(context, "com.example.app"))
    }

    @Test
    fun `isPinned true after setPinned`() {
        `when`(prefs.getStringSet("pinned_packages", emptySet())).thenReturn(null)
        WhitelistStore.setPinned(context, "com.example.app", true)

        val captor = argumentCaptor<Set<String>>()
        verify(editor).putStringSet(eq("pinned_packages"), captor.capture())
        assertEquals(setOf("com.example.app"), captor.firstValue)
    }

    @Test
    fun `setPinned false removes existing pin`() {
        `when`(prefs.getStringSet("pinned_packages", emptySet()))
            .thenReturn(setOf("com.example.app", "com.example.other"))
        WhitelistStore.setPinned(context, "com.example.app", false)

        val captor = argumentCaptor<Set<String>>()
        verify(editor).putStringSet(eq("pinned_packages"), captor.capture())
        assertEquals(setOf("com.example.other"), captor.firstValue)
    }

    @Test
    fun `allPinned returns stored set`() {
        `when`(prefs.getStringSet("pinned_packages", emptySet()))
            .thenReturn(setOf("com.example.app", "com.example.other"))
        assertEquals(setOf("com.example.app", "com.example.other"), WhitelistStore.allPinned(context))
    }

    @Test
    fun `pinning twice keeps single entry`() {
        `when`(prefs.getStringSet("pinned_packages", emptySet()))
            .thenReturn(setOf("com.example.app"))
        WhitelistStore.setPinned(context, "com.example.app", true)

        val captor = argumentCaptor<Set<String>>()
        verify(editor).putStringSet(eq("pinned_packages"), captor.capture())
        assertEquals(setOf("com.example.app"), captor.firstValue)
    }
}
