package com.ivarna.apexcore.tune

import org.junit.Assert.*
import org.junit.Test

class TuneRejectedPathsAbsentTest {

    @Test
    fun testCatalogExcludesAllForbiddenPaths() {
        val forbidden = listOf(
            "/dev/mali0",
            "/proc/ged",
            "sched_util_clamp_min",
            "sched_util_clamp_max",
            "msm_thermal",
            "throttling",
            "gpu_dvfs_enable",
            "gx_force_cpu_boost",
            "boost_gpu_enable"
        )

        for (node in TuneCatalog.allNodes) {
            for (f in forbidden) {
                assertFalse("Forbidden path $f found in ${node.path}", node.path.contains(f))
                node.availablePath?.let {
                    assertFalse("Forbidden path $f found in availablePath $it", it.contains(f))
                }
            }
        }
    }
}
