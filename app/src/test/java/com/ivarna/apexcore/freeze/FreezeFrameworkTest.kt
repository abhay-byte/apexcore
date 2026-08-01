package com.ivarna.apexcore.freeze

import android.content.Context
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

class FreezeFrameworkTest {

    private var previousResolver: FreezeBackendResolver? = null

    @Before
    fun setUp() {
        previousResolver = FreezeFramework.resolver()
        FreezeFramework.setResolverForTest(FreezeBackendResolver(emptyList()))
    }

    @After
    fun tearDown() {
        FreezeFramework.setResolverForTest(previousResolver)
    }

    @Test
    fun `isReady false when no elevated backend`() = runTest {
        assertFalse(FreezeFramework.isReady())
    }

    @Test
    fun `freezeAll returns blocked result when no elevated backend ready`() = runTest {
        val result = FreezeFramework.freezeAll(mock(Context::class.java))
        assertEquals("blocked", result.backend)
        assertEquals(0, result.killed)
        assertEquals(0, result.failed)
        assertEquals(0, result.skipped)
    }
}
