package com.apexcore.app.freeze

import android.content.Context
import android.content.pm.ApplicationInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class FreezeFilterTest {

    private fun appInfo(
        pkg: String = "com.example.app",
        flags: Int = 0
    ): ApplicationInfo = ApplicationInfo().apply {
        packageName = pkg
        this.flags = flags
    }

    private val selfContext: Context = mock(Context::class.java).apply {
        `when`(packageName).thenReturn("com.apexcore.app")
    }

    @Test
    fun `excludes self package`() {
        val self = appInfo("com.apexcore.app")
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
            "com.apexcore.app",
            flags = ApplicationInfo.FLAG_SYSTEM
        )
        assertFalse(FreezeFilter.default(selfContext, selfSys))
    }
}
