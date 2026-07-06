package com.apexcore.app.freeze

interface FreezeBackend {
    val name: String
    val priority: Int
    suspend fun isReady(): Boolean
    suspend fun execute(op: FreezeOperation): FreezeOperation.Result
    suspend fun executeMany(ops: List<FreezeOperation>): List<FreezeOperation.Result> {
        return ops.map { execute(it) }
    }
}
