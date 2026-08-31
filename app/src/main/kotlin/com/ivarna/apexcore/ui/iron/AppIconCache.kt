package com.ivarna.apexcore.ui.iron

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Central cache for app icons loaded from PackageManager.
 * Avoids repeated binder calls (getApplicationIcon) on the main thread during
 * scroll/animations which was causing jank on Games + Pin sheet.
 */
object AppIconCache {
    private val cache = LruCache<String, Drawable>(128)
    private val inFlight = ConcurrentHashMap<String, Mutex>()

    suspend fun get(context: Context, pkg: String): Drawable? =
        load(context.packageManager, pkg)

    fun getIfCached(pkg: String): Drawable? = synchronized(cache) { cache[pkg] }

    fun put(pkg: String, d: Drawable) = synchronized(cache) { cache.put(pkg, d) }

    /**
     * Warm the cache off the main thread when a Games / All Apps detail is selected.
     * Safe to call repeatedly; no-ops when already cached.
     */
    suspend fun prefetch(pm: PackageManager, packageName: String) {
        if (getIfCached(packageName) != null) return
        load(pm, packageName)
    }

    suspend fun prefetch(context: Context, packageName: String) {
        prefetch(context.packageManager, packageName)
    }

    private suspend fun load(pm: PackageManager, pkg: String): Drawable? {
        synchronized(cache) { cache[pkg] }?.let { return it }
        val gate = inFlight.getOrPut(pkg) { Mutex() }
        return try {
            gate.withLock {
                synchronized(cache) { cache[pkg] }?.let { return@withLock it }
                withContext(Dispatchers.IO) {
                    try {
                        val d = pm.getApplicationIcon(pkg)
                        synchronized(cache) { cache.put(pkg, d) }
                        d
                    } catch (_: Throwable) {
                        null
                    }
                }
            }
        } finally {
            inFlight.remove(pkg, gate)
        }
    }
}

/**
 * Async app icon that loads via IO dispatcher and caches.
 * Falls back to placeholder if not ready yet to keep composition cheap.
 */
@Composable
fun AsyncAppIcon(pkg: String, contentDescription: String?, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var drawable by remember(pkg) { mutableStateOf(AppIconCache.getIfCached(pkg)) }

    if (drawable == null) {
        LaunchedEffect(pkg) {
            val d = AppIconCache.get(ctx, pkg)
            if (d != null) drawable = d
        }
    }

    drawable?.let { d ->
        val painter = com.google.accompanist.drawablepainter.rememberDrawablePainter(d)
        Image(
            painter = painter,
            contentDescription = contentDescription,
            modifier = modifier.fillMaxSize()
        )
    }
}
