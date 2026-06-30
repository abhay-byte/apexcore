package com.apexcore.app.freeze

sealed class FreezeOperation(val pkg: String) {
    class ForceStop(pkg: String) : FreezeOperation(pkg)
    class Disable(pkg: String) : FreezeOperation(pkg)
    class Hide(pkg: String) : FreezeOperation(pkg)
    class Suspend(pkg: String) : FreezeOperation(pkg)

    val name: String
        get() = when (this) {
            is ForceStop -> "force-stop"
            is Disable -> "disable"
            is Hide -> "hide"
            is Suspend -> "suspend"
        }

    sealed class Result {
        data object Success : Result()
        data class Failure(val reason: String) : Result()
    }
}
