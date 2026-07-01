package com.apexcore.app.freeze

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FreezeBackendResolverTest {

    private class TestBackend(
        override val name: String,
        override val priority: Int,
        private val ready: Boolean = false
    ) : FreezeBackend {
        override suspend fun isReady(): Boolean = ready
        override suspend fun execute(op: FreezeOperation): FreezeOperation.Result =
            FreezeOperation.Result.Success
    }

    @Test
    fun `detect returns highest priority ready backend - Shizuku first`() = runTest {
        val shizuku = TestBackend("Shizuku", 0, ready = true)
        val root = TestBackend("Root", 1)
        val a11y = TestBackend("Accessibility", 2)
        val fallback = TestBackend("cached only", 99)

        val resolver = FreezeBackendResolver(listOf(shizuku, root, a11y, fallback))
        assertEquals("Shizuku", resolver.detect().name)
    }

    @Test
    fun `detect falls through to Root when Shizuku unavailable`() = runTest {
        val shizuku = TestBackend("Shizuku", 0)
        val root = TestBackend("Root", 1, ready = true)
        val a11y = TestBackend("Accessibility", 2)
        val fallback = TestBackend("cached only", 99)

        val resolver = FreezeBackendResolver(listOf(shizuku, root, a11y, fallback))
        assertEquals("Root", resolver.detect().name)
    }

    @Test
    fun `detect falls through to fallback when no elevated backend ready`() = runTest {
        val shizuku = TestBackend("Shizuku", 0)
        val root = TestBackend("Root", 1)
        val a11y = TestBackend("Accessibility", 2)
        val fallback = TestBackend("cached only", 99, ready = true)

        val resolver = FreezeBackendResolver(listOf(shizuku, root, a11y, fallback))
        assertEquals("cached only", resolver.detect().name)
    }

    @Test
    fun `detect caches result and does not re-probe`() = runTest {
        var probeCount = 0
        val shizuku = object : FreezeBackend {
            override val name = "Shizuku"
            override val priority = 0
            override suspend fun isReady(): Boolean {
                probeCount++
                return true
            }
            override suspend fun execute(op: FreezeOperation): FreezeOperation.Result =
                FreezeOperation.Result.Success
        }
        val fallback = TestBackend("cached only", 99, ready = true)

        val resolver = FreezeBackendResolver(listOf(shizuku, fallback))
        assertEquals("Shizuku", resolver.detect().name)
        assertEquals("Shizuku", resolver.detect().name)
        assertEquals(1, probeCount)
    }

    @Test
    fun `invalidate clears cache and forces re-detect`() = runTest {
        var probeCount = 0
        val shizuku = object : FreezeBackend {
            override val name = "Shizuku"
            override val priority = 0
            override suspend fun isReady(): Boolean {
                probeCount++
                return probeCount == 1 // only ready on first probe
            }
            override suspend fun execute(op: FreezeOperation): FreezeOperation.Result =
                FreezeOperation.Result.Success
        }
        val fallback = TestBackend("cached only", 99, ready = true)

        val resolver = FreezeBackendResolver(listOf(shizuku, fallback))
        assertEquals("Shizuku", resolver.detect().name)
        resolver.invalidate()
        assertEquals("cached only", resolver.detect().name)
        assertEquals(2, probeCount)
    }

    @Test
    fun `candidates ordered by priority ascending`() {
        val resolver = FreezeBackendResolver(
            listOf(
                TestBackend("Shizuku", 0),
                TestBackend("Root", 1),
                TestBackend("Accessibility", 2),
                TestBackend("cached only", 99)
            )
        )
        val priorities = resolver.candidates.map { it.priority }
        assertEquals(listOf(0, 1, 2, 99), priorities)
    }

    @Test
    fun `backend that throws is skipped`() = runTest {
        val throws = object : FreezeBackend {
            override val name = "Throws"
            override val priority = 0
            override suspend fun isReady(): Boolean = throw RuntimeException("crash")
            override suspend fun execute(op: FreezeOperation): FreezeOperation.Result =
                FreezeOperation.Result.Success
        }
        val fallback = TestBackend("cached only", 99, ready = true)

        val resolver = FreezeBackendResolver(listOf(throws, fallback))
        assertEquals("cached only", resolver.detect().name)
    }
}
