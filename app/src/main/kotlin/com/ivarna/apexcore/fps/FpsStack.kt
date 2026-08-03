package com.ivarna.apexcore.fps

import android.content.Context
import com.ivarna.apexcore.fps.privilege.PrivilegeMode
import com.ivarna.apexcore.fps.privilege.PrivilegeModeStore
import com.ivarna.apexcore.fps.privilege.ShellGateway
import com.ivarna.apexcore.fps.source.CpuDataSource
import com.ivarna.apexcore.fps.source.DmaFenceFpsDataSource
import com.ivarna.apexcore.fps.source.FpsDaemonManager
import com.ivarna.apexcore.fps.source.GfxinfoFpsDataSource
import com.ivarna.apexcore.fps.source.SurfaceFlingerFpsDataSource
import com.ivarna.apexcore.fps.util.ForegroundAppResolver
import com.ivarna.apexcore.fps.util.GpuVendor
import com.ivarna.apexcore.fps.util.GpuVendorDetector
import com.ivarna.apexcore.fps.util.ShellExecutor

/**
 * Manual composition root for the FPS stack (no Hilt in ApexCore).
 * Privilege mode tracks the top-bar Root / Shizuku preference.
 *
 * Methods + fallbacks mirror factualstats:
 * Root/Shizuku/Standard via [ShellGateway] chains on dumpsys + /proc paths.
 */
class FpsStack private constructor(
    val repository: FpsRepository,
    val cpuDataSource: CpuDataSource,
    val shellExecutor: ShellExecutor,
    val privilegeModeStore: PrivilegeModeStore,
    val shellGateway: ShellGateway
) {
    fun syncPreferredBackend(pref: String?) {
        privilegeModeStore.syncFromPreferredBackend(pref)
        // Daemon is root-only — stop it when mode forbids root
        val mode = privilegeModeStore.mode.value
        if (mode == PrivilegeMode.SHIZUKU || mode == PrivilegeMode.STANDARD) {
            repository.stopDaemon()
        }
    }

    fun gpuVendor(): GpuVendor = GpuVendorDetector.detect(shellExecutor)

    fun privilegeLabel(): String = privilegeModeStore.label()

    companion object {
        @Volatile
        private var instance: FpsStack? = null

        fun get(context: Context): FpsStack {
            instance?.let { return it }
            return synchronized(this) {
                instance ?: create(context.applicationContext).also { instance = it }
            }
        }

        private fun create(context: Context): FpsStack {
            val shellExecutor = ShellExecutor()
            val privilegeModeStore = PrivilegeModeStore(context)
            val shellGateway = ShellGateway(shellExecutor, privilegeModeStore)
            val foregroundAppResolver = ForegroundAppResolver(shellExecutor)
            val dma = DmaFenceFpsDataSource(context)
            val sf = SurfaceFlingerFpsDataSource(shellGateway, foregroundAppResolver)
            val gfx = GfxinfoFpsDataSource(shellGateway, foregroundAppResolver)
            val cpu = CpuDataSource(shellGateway)
            val daemon = FpsDaemonManager(context, shellGateway)
            privilegeModeStore.addOnModeChangedListener {
                // Invalidate root daemon when leaving ROOT/AUTO with root
                if (privilegeModeStore.mode.value.let {
                        it == PrivilegeMode.SHIZUKU || it == PrivilegeMode.STANDARD
                    }
                ) {
                    daemon.stop()
                }
            }
            val repo = FpsRepositoryImpl(dma, sf, gfx, foregroundAppResolver, daemon)
            return FpsStack(repo, cpu, shellExecutor, privilegeModeStore, shellGateway)
        }
    }
}
