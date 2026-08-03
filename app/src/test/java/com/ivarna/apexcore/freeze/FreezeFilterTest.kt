package com.ivarna.apexcore.freeze

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any

class FreezeFilterTest {

    private fun appInfo(
        pkg: String = "com.example.app",
        flags: Int = 0
    ): ApplicationInfo = ApplicationInfo().apply {
        packageName = pkg
        this.flags = flags
    }

    private val selfContext: Context = mock(Context::class.java)
    private val prefs: SharedPreferences = mock(SharedPreferences::class.java)

    @Before
    fun setUp() {
        `when`(selfContext.packageName).thenReturn("com.ivarna.apexcore")
        `when`(selfContext.applicationContext).thenReturn(selfContext)
        `when`(selfContext.getSharedPreferences("apexcore_whitelist", Context.MODE_PRIVATE)).thenReturn(prefs)
        `when`(prefs.getStringSet("pinned_packages", emptySet())).thenReturn(null)
        val editor: SharedPreferences.Editor = mock(SharedPreferences.Editor::class.java)
        `when`(prefs.edit()).thenReturn(editor)
        `when`(editor.putStringSet(any(), any())).thenReturn(editor)
    }

    @Test
    fun `excludes self package`() {
        val self = appInfo("com.ivarna.apexcore")
        assertFalse(FreezeFilter.default(selfContext, self))
    }

    @Test
    fun `includes user app`() {
        val user = appInfo("com.example.game")
        assertTrue(FreezeFilter.default(selfContext, user))
    }

    @Test
    fun `excludes pure system app`() {
        val sys = appInfo(
            "com.android.settings",
            flags = ApplicationInfo.FLAG_SYSTEM
        )
        assertFalse(FreezeFilter.default(selfContext, sys))
    }

    @Test
    fun `includes updated system app`() {
        val updated = appInfo(
            "com.android.chrome",
            flags = ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
        )
        assertTrue(FreezeFilter.default(selfContext, updated))
    }

    @Test
    fun `excludes already stopped app`() {
        val stopped = appInfo(
            "com.example.idle",
            flags = ApplicationInfo.FLAG_STOPPED
        )
        assertFalse(FreezeFilter.default(selfContext, stopped))
    }

    @Test
    fun `excludes stopped system app`() {
        val stoppedSys = appInfo(
            "com.android.phone",
            flags = ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_STOPPED
        )
        assertFalse(FreezeFilter.default(selfContext, stoppedSys))
    }

    @Test
    fun `excludes self even with system flag`() {
        val selfSys = appInfo(
            "com.ivarna.apexcore",
            flags = ApplicationInfo.FLAG_SYSTEM
        )
        assertFalse(FreezeFilter.default(selfContext, selfSys))
    }

    @Test
    fun `excludes pinned user app`() {
        val pinned = appInfo("com.example.pinned")
        `when`(prefs.getStringSet("pinned_packages", emptySet())).thenReturn(setOf("com.example.pinned"))
        assertFalse(FreezeFilter.default(selfContext, pinned))
    }

    @Test
    fun `includes unpinned user app`() {
        val user = appInfo("com.example.unpinned")
        `when`(prefs.getStringSet("pinned_packages", emptySet())).thenReturn(setOf("com.example.other"))
        assertTrue(FreezeFilter.default(selfContext, user))
    }

    @Test
    fun `excludes protect package set`() {
        val game = appInfo("com.example.game")
        assertFalse(
            FreezeFilter.shouldFreeze(
                selfContext,
                game,
                protectPackages = setOf("com.example.game")
            )
        )
    }

    @Test
    fun `excludes process suffix of protected package`() {
        val push = appInfo("com.example.game:push")
        assertFalse(
            FreezeFilter.shouldFreeze(
                selfContext,
                push,
                protectPackages = setOf("com.example.game")
            )
        )
    }

    @Test
    fun `excludes always-protect systemui`() {
        val sysui = appInfo("com.android.systemui", flags = 0)
        assertFalse(FreezeFilter.default(selfContext, sysui))
    }
}
