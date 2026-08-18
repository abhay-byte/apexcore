package com.ivarna.apexcore.tune

import com.ivarna.apexcore.fps.privilege.PrivilegeTier
import com.ivarna.apexcore.fps.privilege.ShellGateway
import com.ivarna.apexcore.fps.privilege.WriteResult

interface TuneShell {
    fun read(path: String, timeoutMs: Long = 120L): String?
    fun write(path: String, value: String, tier: PrivilegeTier, timeoutMs: Long = 400L): WriteResult
    fun exists(path: String, timeoutMs: Long = 120L): Boolean
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

    override fun exists(path: String, timeoutMs: Long): Boolean {
        val tier = tierProvider() ?: PrivilegeTier.STANDARD
        return shellGateway.exists(path, tier, timeoutMs)
    }
}
