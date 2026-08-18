package com.ivarna.apexcore.tune

import org.junit.Assert.*
import org.junit.Test

class TuneCatalogTest {

    private val pathRegex = Regex("""^/(sys|dev|proc)/[A-Za-z0-9/_.:=-]+$""")

    @Test
    fun testAllPathsAreValidAndCharsetSafe() {
        for (node in TuneCatalog.allNodes) {
            assertTrue("Path ${node.path} should match safe sysfs/dev/proc regex", pathRegex.matches(node.path))
            node.availablePath?.let { avail ->
                assertTrue("AvailablePath $avail should match safe regex", pathRegex.matches(avail))
            }
        }
    }

    @Test
    fun testForbiddenPathsAreAbsent() {
        val forbiddenSubstrings = listOf(
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
            for (forbidden in forbiddenSubstrings) {
                assertFalse(
                    "Path ${node.path} must not contain forbidden pattern '$forbidden'",
                    node.path.contains(forbidden)
                )
                node.availablePath?.let { avail ->
                    assertFalse(
                        "Available path $avail must not contain forbidden pattern '$forbidden'",
                        avail.contains(forbidden)
                    )
                }
            }
        }
    }

    @Test
    fun testEveryTuneIdHasAtLeastOneCandidateNode() {
        for (id in TuneId.values()) {
            val nodes = TuneCatalog.nodesByTuneId[id]
            assertNotNull("TuneId $id must have catalog entries", nodes)
            assertTrue("TuneId $id must have at least one node", nodes!!.isNotEmpty())
        }
    }
}
