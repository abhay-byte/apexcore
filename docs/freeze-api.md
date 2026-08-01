# ApexCore — Freeze API

Public surface of the freeze framework. Internal `freeze.*` package
hides implementation; `FreezeFramework` is the only entry point used by
the UI.

## `FreezeFramework` (object)

```kotlin
object FreezeFramework {
    val activeBackend: StateFlow<FreezeBackend?>        // observable
    val lastResult: StateFlow<FreezeResult?>            // observable
    suspend fun detect(): FreezeBackend                 // re-resolve
    suspend fun freezeAll(
        filter: (ApplicationInfo) -> Boolean = { FreezeFilter.default(context, it) }
    ): FreezeResult
    suspend fun forceStopOne(pkg: String): FreezeOperation.Result
    suspend fun isReady(): Boolean                      // true if privileged backend active
}
```

### `freezeAll(filter)`

Iterates installed apps, calls `filter` on each `ApplicationInfo`,
applies the active backend's `ForceStop` to the survivors. Returns
`FreezeResult(killed, failed, skipped, durationMs, backend)`.

Default filter excludes:

- Self (`context.packageName`)
- Pure-system apps (`FLAG_SYSTEM && !FLAG_UPDATED_SYSTEM_APP`)
- Apps with `FLAG_STOPPED` already (nothing to kill)

### `freezeOne(pkg)`, `forceStopOne(pkg)`

Single-target variants. `freezeOne` may escalate to `Disable` if backend
supports it (T5 scope). `forceStopOne` always uses `am force-stop`.

### `isReady()`

`true` if any backend (including fallback) responded. `false` only on
PackageManager crash.

---

## `FreezeBackend` (interface)

```kotlin
sealed interface FreezeBackend {
    val name: String                                  // "Shizuku", "Root", ...
    val priority: Int                                 // 0=best, 99=fallback
    suspend fun isReady(): Boolean
    suspend fun execute(op: FreezeOperation): FreezeOperation.Result
}
```

Implementations: `ShizukuFreezeBackend`, `RootFreezeBackend`,
`AccessibilityFreezeBackend`, `FallbackFreezeBackend`.

---

## `FreezeOperation` (sealed)

```kotlin
sealed class FreezeOperation(val pkg: String) {
    class ForceStop(pkg: String) : FreezeOperation(pkg)
    class Disable(pkg: String)   : FreezeOperation(pkg)   // T5
    class Hide(pkg: String)      : FreezeOperation(pkg)   // T5
    class Suspend(pkg: String)   : FreezeOperation(pkg)   // T5

    sealed class Result {
        data object Success : Result()
        data class Failure(val reason: String) : Result() {
            val isSkipped: Boolean      // true if a11y stub (SKIPPED_A11Y)
        }
        companion object {
            val SKIPPED_A11Y = Failure("a11y-per-app-not-implemented")
        }
    }
}
```

---

## `FreezeResult`

```kotlin
data class FreezeResult(
    val killed: Int,
    val failed: Int,
    val skipped: Int,
    val durationMs: Long,
    val backend: String,         // human-readable, for status footer
    val totalMemMb: Long,        // from /proc/meminfo MemTotal
    val beforeAvailMb: Long,     // MemAvailable before freeze
    val afterAvailMb: Long,      // MemAvailable after freeze
    val swapTotalMb: Long,       // SwapTotal
    val swapFreeMb: Long         // SwapFree
) {
    val freedMb: Long get() = (afterAvailMb - beforeAvailMb).coerceAtLeast(0)
}
```

---

## `FreezeReceiver`

```kotlin
class FreezeReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == "com.ivarna.apexcore.action.FREEZE_ALL") {
            val pending = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                FreezeFramework.freezeAll()
                pending.finish()
            }
        }
    }
}
```

Manifest:

```xml
<receiver android:name=".freeze.FreezeReceiver" android:exported="true">
    <intent-filter>
        <action android:name="com.ivarna.apexcore.action.FREEZE_ALL" />
    </intent-filter>
</receiver>
```

External invocation:

```bash
adb shell am start -a com.ivarna.apexcore.action.FREEZE_ALL
```

(Tasker / Automate / MacroDroid can also call this.)
