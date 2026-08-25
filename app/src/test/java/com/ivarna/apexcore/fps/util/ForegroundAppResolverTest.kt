package com.ivarna.apexcore.fps.util

import com.ivarna.apexcore.fps.privilege.PrivilegeMode
import com.ivarna.apexcore.fps.privilege.PrivilegePolicy
import com.ivarna.apexcore.fps.privilege.PrivilegeTier
import com.ivarna.apexcore.fps.privilege.ShellGateway
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

class ForegroundAppResolverTest {

    private lateinit var shellGateway: ShellGateway
    private lateinit var resolver: ForegroundAppResolver

    @Before
    fun setUp() {
        shellGateway = mock()
        whenever(shellGateway.currentPolicy()).thenReturn(PrivilegePolicy(PrivilegeMode.AUTO))
        whenever(shellGateway.execute(any<String>(), any<PrivilegeTier>(), any<Long>())).thenReturn(ShellResult("", -1))
        whenever(shellGateway.executeChain(any<String>(), any<List<PrivilegeTier>>())).thenReturn(ShellResult("", -1) to null)
        resolver = ForegroundAppResolver(shellGateway, null)
        resolver.clearTargetPackage()
    }

    @Test
    fun preferredPackage_winsOverSystemForeground() {
        resolver.preferredPackage = "com.game.foo"
        whenever(shellGateway.execute(any<String>(), eq(PrivilegeTier.STANDARD), any<Long>())).thenAnswer { inv ->
            val cmd = inv.arguments[0] as String
            if (cmd.contains("com.game.foo")) ShellResult("1234", 0) else ShellResult("", -1)
        }
        whenever(shellGateway.executeChain(any<String>(), any<List<PrivilegeTier>>())).thenReturn(ShellResult("mCurrentFocus=Window{... com.android.settings}", 0) to PrivilegeTier.STANDARD)
        val result = resolver.resolve()
        assertNotNull(result)
        assertEquals("com.game.foo", result!!.packageName)
    }

    @Test
    fun targetPackageChange_resolvesNewPid() {
        resolver.setTargetPackage("com.game.foo")
        whenever(shellGateway.execute(any<String>(), any<PrivilegeTier>(), any<Long>())).thenAnswer { inv ->
            val cmd = inv.arguments[0] as String
            when {
                cmd.contains("com.game.foo") -> ShellResult("111",0)
                cmd.contains("com.game.bar") -> ShellResult("222",0)
                else -> ShellResult("", -1)
            }
        }
        var result = resolver.resolve()
        assertEquals("com.game.foo", result!!.packageName)
        assertEquals(111, result.pid)
        resolver.setTargetPackage("com.game.bar")
        result = resolver.resolve()
        assertEquals("com.game.bar", result!!.packageName)
        assertEquals(222, result.pid)
    }

    @Test
    fun clearingPreferred_returnsToSystemForeground() {
        resolver.preferredPackage = "com.game.foo"
        whenever(shellGateway.execute(any<String>(), eq(PrivilegeTier.STANDARD), any<Long>())).thenAnswer { inv ->
            val cmd = inv.arguments[0] as String
            if (cmd.contains("com.game.foo")) ShellResult("123",0) else ShellResult("", -1)
        }
        var result = resolver.resolve()
        assertEquals("com.game.foo", result!!.packageName)
        resolver.clearTargetPackage()
        val windowOutput = "  mCurrentFocus=Window{abcd u0 com.other.app/com.other.app.MainActivity}"
        whenever(shellGateway.executeChain(any<String>(), any<List<PrivilegeTier>>())).thenAnswer { inv ->
            val cmd = inv.arguments[0] as String
            if (cmd.contains("dumpsys window")) ShellResult(windowOutput,0) to PrivilegeTier.STANDARD else ShellResult("", -1) to null
        }
        whenever(shellGateway.execute(any<String>(), any<PrivilegeTier>(), any<Long>())).thenAnswer { inv ->
            val cmd = inv.arguments[0] as String
            if (cmd.contains("com.other.app")) ShellResult("999",0) else ShellResult("", -1)
        }
        result = resolver.resolve()
        assertNotNull(result)
        assertEquals("com.other.app", result!!.packageName)
    }

    @Test
    fun isGameLikeSurface_cachesVerdict() {
        val sfOutput = "SurfaceView[com.game.foo/com.test.Activity]#0"
        whenever(shellGateway.executeChain(any<String>(), any<List<PrivilegeTier>>())).thenAnswer { inv ->
            val cmd = inv.arguments[0] as String
            if (cmd.contains("SurfaceFlinger --list")) ShellResult(sfOutput,0) to PrivilegeTier.STANDARD else ShellResult("", -1) to null
        }
        val first = resolver.isGameLikeSurface("com.game.foo")
        assertTrue(first)
        val second = resolver.isGameLikeSurface("com.game.foo")
        assertTrue(second)
        verify(shellGateway, atMost(1)).executeChain(argThat { contains("SurfaceFlinger --list") }, any<List<PrivilegeTier>>())
    }
}
