package com.ivarna.apexcore.tune

import com.ivarna.apexcore.fps.privilege.PrivilegeTier
import com.ivarna.apexcore.fps.privilege.ShellGateway
import com.ivarna.apexcore.fps.privilege.WriteResult
import com.ivarna.apexcore.fps.util.ShellResult

interface TuneShell {
    fun read(path: String, timeoutMs: Long = 120L): String?
    fun write(path: String, value: String, tier: PrivilegeTier, timeoutMs: Long = 400L): WriteResult
    fun write(
        path: String,
        value: String,
        tier: PrivilegeTier,
        timeoutMs: Long = 400L,
        verificationMode: VerificationMode
    ): WriteResult = write(path, value, tier, timeoutMs)
    fun exists(path: String, timeoutMs: Long = 120L): Boolean
    fun execute(command: String, tier: PrivilegeTier, timeoutMs: Long = 400L): ShellResult
}

class ShellGatewayTuneShell(
    private val shellGateway: ShellGateway,
    private val tierProvider: () -> PrivilegeTier?
) : TuneShell {

    override fun read(path: String, timeoutMs: Long): String? {
        val tier = tierProvider() ?: PrivilegeTier.STANDARD
        return shellGateway.readPathDirect(path, tier, timeoutMs)
    }

    override fun write(path: String, value: String, tier: PrivilegeTier, timeoutMs: Long): WriteResult {
        return shellGateway.writePath(path, value, tier, timeoutMs)
    }

    override fun write(
        path: String,
        value: String,
        tier: PrivilegeTier,
        timeoutMs: Long,
        verificationMode: VerificationMode
    ): WriteResult = shellGateway.writePath(path, value, tier, timeoutMs, verificationMode)

    override fun exists(path: String, timeoutMs: Long): Boolean {
        val tier = tierProvider() ?: PrivilegeTier.STANDARD
        return shellGateway.exists(path, tier, timeoutMs)
    }

    override fun execute(command: String, tier: PrivilegeTier, timeoutMs: Long): ShellResult {
        return shellGateway.execute(command, tier, timeoutMs)
    }
}
