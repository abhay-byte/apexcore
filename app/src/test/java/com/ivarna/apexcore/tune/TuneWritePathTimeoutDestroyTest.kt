package com.ivarna.apexcore.tune

import com.ivarna.apexcore.fps.privilege.PrivilegeModeStore
import com.ivarna.apexcore.fps.privilege.PrivilegeTier
import com.ivarna.apexcore.fps.privilege.ShellGateway
import com.ivarna.apexcore.fps.util.ShellExecutor
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.mock

class TuneWritePathTimeoutDestroyTest {

    @Test
    fun testShellExecutorTimesOutAndDestroysProcess() {
        val executor = ShellExecutor()
        // Run sleep command with short timeout
        val start = System.currentTimeMillis()
        val result = executor.execute("sleep 5", useRoot = false, timeoutMs = 200L)
        val elapsed = System.currentTimeMillis() - start

        assertFalse("Command should not report success on timeout", result.isSuccess)
        assertEquals("Timeout exitCode should be -1", -1, result.exitCode)
        assertTrue("Output should indicate timeout", result.output.contains("timeout"))
        assertTrue("Elapsed time should be close to timeout (around 200-500ms)", elapsed < 2000L)
    }

    @Test
    fun testShellGatewayRejectsBadPathOrValue() {
        val executor = ShellExecutor()
        val store = mock(PrivilegeModeStore::class.java)
        val gateway = ShellGateway(executor, store)

        // Invalid path (command injection attempt)
        val badPathResult = gateway.writePath("/sys/class/kgsl; rm -rf /", "1", PrivilegeTier.ROOT)
        assertFalse("Bad path must be rejected", badPathResult.ok)
        assertFalse("Bad path must not be verified", badPathResult.verified)

        // Invalid value
        val badValueResult = gateway.writePath("/sys/class/kgsl/min_freq", "100; id", PrivilegeTier.ROOT)
        assertFalse("Bad value must be rejected", badValueResult.ok)
        assertFalse("Bad value must not be verified", badValueResult.verified)
    }
}
