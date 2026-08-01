package com.ivarna.apexcore.freeze

interface FreezeBackend {
    val name: String
    val priority: Int
    suspend fun isReady(): Boolean
    suspend fun execute(op: FreezeOperation): FreezeOperation.Result
    suspend fun executeMany(ops: List<FreezeOperation>): List<FreezeOperation.Result> {
        return ops.map { execute(it) }
    }
    suspend fun executeWithOutput(cmd: String): String {
        return ""
    }

    /** Clears cached readiness state; called when the resolver invalidates. */
    fun invalidate() {}
}
