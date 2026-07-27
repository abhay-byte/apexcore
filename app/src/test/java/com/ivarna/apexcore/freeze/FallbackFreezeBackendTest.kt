package com.ivarna.apexcore.freeze

import android.app.ActivityManager
import android.content.Context
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class FallbackFreezeBackendTest {

    @Test
    fun `forceStop calls killBackgroundProcesses and returns Success`() = runTest {
        val context = mock(Context::class.java)
        val am = mock(ActivityManager::class.java)
        `when`(context.getSystemService(Context.ACTIVITY_SERVICE)).thenReturn(am)
        `when`(context.applicationContext).thenReturn(context)

        val backend = FallbackFreezeBackend(context)
        val result = backend.execute(FreezeOperation.ForceStop("com.example.app"))

        assertEquals(FreezeOperation.Result.Success, result)
        verify(am).killBackgroundProcesses("com.example.app")
    }

    @Test
    fun `disable returns unsupported failure`() = runTest {
        val context = mock(Context::class.java)
        val am = mock(ActivityManager::class.java)
        `when`(context.getSystemService(Context.ACTIVITY_SERVICE)).thenReturn(am)
        `when`(context.applicationContext).thenReturn(context)

        val backend = FallbackFreezeBackend(context)
        val result = backend.execute(FreezeOperation.Disable("com.example.app"))

        assertTrue(result is FreezeOperation.Result.Failure)
        assertEquals("unsupported-on-fallback", (result as FreezeOperation.Result.Failure).reason)
    }

    @Test
    fun `shell command returns unsupported failure`() = runTest {
        val context = mock(Context::class.java)
        val am = mock(ActivityManager::class.java)
        `when`(context.getSystemService(Context.ACTIVITY_SERVICE)).thenReturn(am)
        `when`(context.applicationContext).thenReturn(context)

        val backend = FallbackFreezeBackend(context)
        val result = backend.execute(FreezeOperation.ShellCommand("echo test"))

        assertTrue(result is FreezeOperation.Result.Failure)
        assertEquals("unsupported-on-fallback", (result as FreezeOperation.Result.Failure).reason)
    }
}
