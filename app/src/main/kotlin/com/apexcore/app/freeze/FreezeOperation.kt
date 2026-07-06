package com.apexcore.app.freeze

sealed class FreezeOperation(val pkg: String) {
    class ForceStop(pkg: String) : FreezeOperation(pkg)
    class Disable(pkg: String) : FreezeOperation(pkg)
    class Hide(pkg: String) : FreezeOperation(pkg)
    class Suspend(pkg: String) : FreezeOperation(pkg)
    class ShellCommand(command: String) : FreezeOperation(command)

    val name: String
        get() = when (this) {
            is ForceStop -> "force-stop"
            is Disable -> "disable"
            is Hide -> "hide"
            is Suspend -> "suspend"
            is ShellCommand -> pkg
        }

    sealed class Result {
        data object Success : Result()
        data class Failure(val reason: String) : Result() {
            val isSkipped: Boolean get() = this == SKIPPED_A11Y || this == SKIPPED_FALLBACK
        }

        companion object {
            val SKIPPED_A11Y = Failure("a11y-per-app-not-implemented")
            val SKIPPED_FALLBACK = Failure("fallback-neutered-on-modern-android")
        }
    }
}
