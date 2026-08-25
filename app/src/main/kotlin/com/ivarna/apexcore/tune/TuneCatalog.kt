package com.ivarna.apexcore.tune

/**
 * Clean-room sysfs and settings catalog for all 39 TuneId options.
 * Defined in docs/plans/T12-real-game-optimisation.md and docs/plans/T12-tune-options.md.
 *
 * SAFETY INVARIANT: Never contains /dev/mali0, /proc/ged, sched_util_clamp_min,
 * msm_thermal/enabled, throttling, or GED force-max paths.
 */
object TuneCatalog {

    val allNodes: List<TuneNode> = listOf(
        // 1. GPU_FLOOR (groupId = gpu_min)
        TuneNode(
            path = "/sys/class/kgsl/kgsl-3d0/devfreq/min_freq",
            id = TuneId.GPU_FLOOR,
            vendor = TuneVendor.ADRENO,
            privilege = TunePrivilege.SHELL_OK,
            valueKind = TuneValueKind.FREQ_HZ,
            availablePath = "/sys/class/kgsl/kgsl-3d0/gpu_available_frequencies",
            groupId = "gpu_min"
        ),
        TuneNode(
            path = "/sys/class/kgsl/kgsl-3d0/min_gpuclk",
            id = TuneId.GPU_FLOOR,
            vendor = TuneVendor.ADRENO,
            privilege = TunePrivilege.ROOT_ONLY,
            valueKind = TuneValueKind.FREQ_HZ,
            availablePath = "/sys/class/kgsl/kgsl-3d0/gpu_available_frequencies",
            groupId = "gpu_min"
        ),
        TuneNode(
            path = "/sys/class/kgsl/kgsl-3d0/min_clock_mhz",
            id = TuneId.GPU_FLOOR,
            vendor = TuneVendor.ADRENO,
            privilege = TunePrivilege.SHELL_OK,
            valueKind = TuneValueKind.FREQ_MHZ,
            availablePath = "/sys/class/kgsl/kgsl-3d0/freq_table_mhz",
            groupId = "gpu_min"
        ),
        TuneNode(
            path = "/sys/class/devfreq/13000000.mali/min_freq",
            id = TuneId.GPU_FLOOR,
            vendor = TuneVendor.MALI,
            privilege = TunePrivilege.ROOT_ONLY,
            valueKind = TuneValueKind.FREQ_HZ,
            availablePath = "/sys/class/devfreq/13000000.mali/available_frequencies",
            groupId = "gpu_min"
        ),
        TuneNode(
            path = "/sys/class/devfreq/mali0/min_freq",
            id = TuneId.GPU_FLOOR,
            vendor = TuneVendor.MALI,
            privilege = TunePrivilege.ROOT_ONLY,
            valueKind = TuneValueKind.FREQ_HZ,
            availablePath = "/sys/class/devfreq/mali0/available_frequencies",
            groupId = "gpu_min"
        ),
        TuneNode(
            path = "/sys/class/devfreq/gpu/min_freq",
            id = TuneId.GPU_FLOOR,
            vendor = TuneVendor.MALI,
            privilege = TunePrivilege.ROOT_ONLY,
            valueKind = TuneValueKind.FREQ_HZ,
            availablePath = "/sys/class/devfreq/gpu/available_frequencies",
            groupId = "gpu_min"
        ),
        TuneNode(
            path = "/sys/class/misc/mali0/device/devfreq/13000000.mali/min_freq",
            id = TuneId.GPU_FLOOR,
            vendor = TuneVendor.MALI,
            privilege = TunePrivilege.ROOT_ONLY,
            valueKind = TuneValueKind.FREQ_HZ,
            availablePath = "/sys/class/misc/mali0/device/devfreq/13000000.mali/available_frequencies",
            groupId = "gpu_min"
        ),
        TuneNode(
            path = "/sys/kernel/gpu/gpu_min_clock",
            id = TuneId.GPU_FLOOR,
            vendor = TuneVendor.SAMSUNG,
            privilege = TunePrivilege.ROOT_ONLY,
            valueKind = TuneValueKind.FREQ_MHZ,
            availablePath = "/sys/kernel/gpu/gpu_available_clocks",
            groupId = "gpu_min"
        ),

        // 2. GPU_HOLD
        TuneNode(
            path = "/sys/class/kgsl/kgsl-3d0/force_clk_on",
            id = TuneId.GPU_HOLD,
            vendor = TuneVendor.ADRENO,
            privilege = TunePrivilege.ROOT_ONLY,
            valueKind = TuneValueKind.RAW,
            groupId = "kgsl_force_clk"
        ),
        TuneNode(
            path = "/sys/class/kgsl/kgsl-3d0/force_bus_on",
            id = TuneId.GPU_HOLD,
            vendor = TuneVendor.ADRENO,
            privilege = TunePrivilege.ROOT_ONLY,
            valueKind = TuneValueKind.RAW,
            groupId = "kgsl_force_bus"
        ),
        TuneNode(
            path = "/sys/class/kgsl/kgsl-3d0/force_rail_on",
            id = TuneId.GPU_HOLD,
            vendor = TuneVendor.ADRENO,
            privilege = TunePrivilege.ROOT_ONLY,
            valueKind = TuneValueKind.RAW,
            groupId = "kgsl_force_rail"
        ),
        TuneNode(
            path = "/sys/class/kgsl/kgsl-3d0/idle_timer",
            id = TuneId.GPU_HOLD,
            vendor = TuneVendor.ADRENO,
            privilege = TunePrivilege.ROOT_ONLY,
            valueKind = TuneValueKind.RAW,
            groupId = "kgsl_idle_timer"
        ),

        // 3. GPU_ADRENO
        TuneNode(
            path = "/sys/class/kgsl/kgsl-3d0/devfreq/adrenoboost",
            id = TuneId.GPU_ADRENO,
            vendor = TuneVendor.ADRENO,
            privilege = TunePrivilege.ROOT_ONLY,
            valueKind = TuneValueKind.RAW,
            groupId = "adreno_boost"
        ),
        TuneNode(
            path = "/sys/class/kgsl/kgsl-3d0/adrenoboost",
            id = TuneId.GPU_ADRENO,
            vendor = TuneVendor.ADRENO,
            privilege = TunePrivilege.ROOT_ONLY,
            valueKind = TuneValueKind.RAW,
            groupId = "adreno_boost"
        ),

        // 4. GPU_GOVERNOR
        TuneNode(
            path = "/sys/class/kgsl/kgsl-3d0/devfreq/governor",
            id = TuneId.GPU_GOVERNOR,
            vendor = TuneVendor.ADRENO,
            privilege = TunePrivilege.ROOT_ONLY,
            valueKind = TuneValueKind.ENUM,
            availablePath = "/sys/class/kgsl/kgsl-3d0/devfreq/available_governors",
            groupId = "gpu_gov"
        ),
        TuneNode(
            path = "/sys/kernel/gpu/gpu_governor",
            id = TuneId.GPU_GOVERNOR,
            vendor = TuneVendor.SAMSUNG,
            privilege = TunePrivilege.ROOT_ONLY,
            valueKind = TuneValueKind.ENUM,
            availablePath = "/sys/kernel/gpu/gpu_available_governors",
            groupId = "gpu_gov"
        ),
        TuneNode(
            path = "/sys/class/kgsl/kgsl-3d0/devfreq/max_freq",
            id = TuneId.GPU_LOCK_MAX,
            vendor = TuneVendor.ADRENO,
            privilege = TunePrivilege.ROOT_ONLY,
            valueKind = TuneValueKind.FREQ_HZ,
            availablePath = "/sys/class/kgsl/kgsl-3d0/gpu_available_frequencies",
            groupId = "gpu_lock_max",
            probeStrategy = ProbeStrategy.READ_METADATA_ONLY
        ),

        // 5. GPU_PWRLEVEL (Extra power floor, not a freq floor)
        TuneNode(
            path = "/sys/class/kgsl/kgsl-3d0/min_pwrlevel",
            id = TuneId.GPU_PWRLEVEL,
            vendor = TuneVendor.ADRENO,
            privilege = TunePrivilege.ROOT_ONLY,
            valueKind = TuneValueKind.PWRLEVEL,
            groupId = "gpu_pwr_extra"
        ),
        TuneNode(
            path = "/sys/class/kgsl/kgsl-3d0/default_pwrlevel",
            id = TuneId.GPU_PWRLEVEL,
            vendor = TuneVendor.ADRENO,
            privilege = TunePrivilege.ROOT_ONLY,
            valueKind = TuneValueKind.PWRLEVEL,
            groupId = "gpu_pwr_extra"
        ),

        // 6. GPU_GED_GAME
        TuneNode(
            path = "/sys/module/ged/parameters/gx_game_mode",
            id = TuneId.GPU_GED_GAME,
            vendor = TuneVendor.MALI,
            privilege = TunePrivilege.ROOT_ONLY,
            valueKind = TuneValueKind.RAW,
            groupId = "ged_game_mode"
        ),

        // 7. GPU_SAMSUNG_MIN
        TuneNode(
            path = "/sys/kernel/gpu/gpu_min_clock",
            id = TuneId.GPU_SAMSUNG_MIN,
            vendor = TuneVendor.SAMSUNG,
            privilege = TunePrivilege.ROOT_ONLY,
            valueKind = TuneValueKind.FREQ_MHZ,
            availablePath = "/sys/kernel/gpu/gpu_available_clocks",
            groupId = "samsung_gpu_min"
        ),

        // 8. GPU_SIMPLE
        TuneNode(
            path = "/sys/module/simple_gpu_algorithm/parameters/simple_gpu_activate",
            id = TuneId.GPU_SIMPLE,
            vendor = TuneVendor.GENERIC,
            privilege = TunePrivilege.ROOT_ONLY,
            valueKind = TuneValueKind.RAW,
            groupId = "simple_gpu"
        ),
        TuneNode(
            path = "/sys/module/simple_ondemand/parameters/simple_gpu_activate",
            id = TuneId.GPU_SIMPLE,
            vendor = TuneVendor.GENERIC,
            privilege = TunePrivilege.ROOT_ONLY,
            valueKind = TuneValueKind.RAW,
            groupId = "simple_gpu"
        ),

        // 9. CPU_FLOOR
        TuneNode(
            path = "/sys/devices/system/cpu/cpufreq/policy0/scaling_min_freq",
            id = TuneId.CPU_FLOOR,
            vendor = TuneVendor.GENERIC,
            privilege = TunePrivilege.SHELL_OK,
            valueKind = TuneValueKind.FREQ_KHZ,
            availablePath = "/sys/devices/system/cpu/cpufreq/policy0/scaling_available_frequencies",
            groupId = "cpu_min_policy0"
        ),
        TuneNode(
            path = "/sys/devices/system/cpu/cpufreq/policy1/scaling_min_freq",
            id = TuneId.CPU_FLOOR,
            vendor = TuneVendor.GENERIC,
            privilege = TunePrivilege.SHELL_OK,
            valueKind = TuneValueKind.FREQ_KHZ,
            availablePath = "/sys/devices/system/cpu/cpufreq/policy1/scaling_available_frequencies",
            groupId = "cpu_min_policy1"
        ),
        TuneNode(
            path = "/sys/devices/system/cpu/cpufreq/policy2/scaling_min_freq",
            id = TuneId.CPU_FLOOR,
            vendor = TuneVendor.GENERIC,
            privilege = TunePrivilege.SHELL_OK,
            valueKind = TuneValueKind.FREQ_KHZ,
            availablePath = "/sys/devices/system/cpu/cpufreq/policy2/scaling_available_frequencies",
            groupId = "cpu_min_policy2"
        ),
        TuneNode(
            path = "/sys/devices/system/cpu/cpufreq/policy3/scaling_min_freq",
            id = TuneId.CPU_FLOOR,
            vendor = TuneVendor.GENERIC,
            privilege = TunePrivilege.SHELL_OK,
            valueKind = TuneValueKind.FREQ_KHZ,
            availablePath = "/sys/devices/system/cpu/cpufreq/policy3/scaling_available_frequencies",
            groupId = "cpu_min_policy3"
        ),
        TuneNode(
            path = "/sys/devices/system/cpu/cpufreq/policy4/scaling_min_freq",
            id = TuneId.CPU_FLOOR,
            vendor = TuneVendor.GENERIC,
            privilege = TunePrivilege.SHELL_OK,
            valueKind = TuneValueKind.FREQ_KHZ,
            availablePath = "/sys/devices/system/cpu/cpufreq/policy4/scaling_available_frequencies",
            groupId = "cpu_min_policy4"
        ),
        TuneNode(
            path = "/sys/devices/system/cpu/cpufreq/policy5/scaling_min_freq",
            id = TuneId.CPU_FLOOR,
            vendor = TuneVendor.GENERIC,
            privilege = TunePrivilege.SHELL_OK,
            valueKind = TuneValueKind.FREQ_KHZ,
            availablePath = "/sys/devices/system/cpu/cpufreq/policy5/scaling_available_frequencies",
            groupId = "cpu_min_policy5"
        ),
        TuneNode(
            path = "/sys/devices/system/cpu/cpufreq/policy6/scaling_min_freq",
            id = TuneId.CPU_FLOOR,
            vendor = TuneVendor.GENERIC,
            privilege = TunePrivilege.SHELL_OK,
            valueKind = TuneValueKind.FREQ_KHZ,
            availablePath = "/sys/devices/system/cpu/cpufreq/policy6/scaling_available_frequencies",
            groupId = "cpu_min_policy6"
        ),
        TuneNode(
            path = "/sys/devices/system/cpu/cpufreq/policy7/scaling_min_freq",
            id = TuneId.CPU_FLOOR,
            vendor = TuneVendor.GENERIC,
            privilege = TunePrivilege.SHELL_OK,
            valueKind = TuneValueKind.FREQ_KHZ,
            availablePath = "/sys/devices/system/cpu/cpufreq/policy7/scaling_available_frequencies",
            groupId = "cpu_min_policy7"
        ),

        // 10. CPU_FLOOR_LITTLE
        TuneNode(
            path = "/sys/devices/system/cpu/cpufreq/policy0/scaling_min_freq",
            id = TuneId.CPU_FLOOR_LITTLE,
            vendor = TuneVendor.GENERIC,
            privilege = TunePrivilege.SHELL_OK,
            valueKind = TuneValueKind.FREQ_KHZ,
            availablePath = "/sys/devices/system/cpu/cpufreq/policy0/scaling_available_frequencies",
            groupId = "cpu_min_little"
        ),

        // 11. CPU_FLOOR_BIG
        TuneNode(
            path = "/sys/devices/system/cpu/cpufreq/policy4/scaling_min_freq",
            id = TuneId.CPU_FLOOR_BIG,
            vendor = TuneVendor.GENERIC,
            privilege = TunePrivilege.SHELL_OK,
            valueKind = TuneValueKind.FREQ_KHZ,
            availablePath = "/sys/devices/system/cpu/cpufreq/policy4/scaling_available_frequencies",
            groupId = "cpu_min_big"
        ),

        // 12. CPU_FLOOR_PRIME
        TuneNode(
            path = "/sys/devices/system/cpu/cpufreq/policy7/scaling_min_freq",
            id = TuneId.CPU_FLOOR_PRIME,
            vendor = TuneVendor.GENERIC,
            privilege = TunePrivilege.SHELL_OK,
            valueKind = TuneValueKind.FREQ_KHZ,
            availablePath = "/sys/devices/system/cpu/cpufreq/policy7/scaling_available_frequencies",
            groupId = "cpu_min_prime"
        ),

        // 13. CPU_GOVERNOR is discovered from every live policy at runtime.
        // Keep one metadata marker so the catalog remains total without
        // encoding a policy0/policy4/policy7 topology assumption.
        TuneNode(
            path = "/sys/devices/system/cpu/cpufreq",
            id = TuneId.CPU_GOVERNOR,
            vendor = TuneVendor.GENERIC,
            privilege = TunePrivilege.SHELL_OK,
            valueKind = TuneValueKind.RAW,
            groupId = "cpu_gov_discovery",
            probeStrategy = ProbeStrategy.READ_METADATA_ONLY
        ),
        TuneNode(
            path = "/sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq",
            id = TuneId.CPU_LOCK_MAX,
            vendor = TuneVendor.GENERIC,
            privilege = TunePrivilege.ROOT_ONLY,
            valueKind = TuneValueKind.FREQ_KHZ,
            availablePath = "/sys/devices/system/cpu/cpufreq/policy0/cpuinfo_max_freq",
            groupId = "cpu_lock_max",
            probeStrategy = ProbeStrategy.READ_METADATA_ONLY
        ),

        // 14. CPU_UCLAMP (cgroup top-app only, NOT root cgroup, NOT sysctl)
        TuneNode(
            path = "/dev/cpuctl/top-app/cpu.uclamp.min",
            id = TuneId.CPU_UCLAMP,
            vendor = TuneVendor.GENERIC,
            privilege = TunePrivilege.ROOT_ONLY,
            valueKind = TuneValueKind.RAW,
            groupId = "uclamp_top"
        ),

        // 15. CPU_STUNE
        TuneNode(
            path = "/dev/stune/top-app/schedtune.boost",
            id = TuneId.CPU_STUNE,
            vendor = TuneVendor.GENERIC,
            privilege = TunePrivilege.ROOT_ONLY,
            valueKind = TuneValueKind.RAW,
            groupId = "stune_top"
        ),

        // 16. CPU_STUNE_IDLE
        TuneNode(
            path = "/dev/stune/top-app/schedtune.prefer_idle",
            id = TuneId.CPU_STUNE_IDLE,
            vendor = TuneVendor.GENERIC,
            privilege = TunePrivilege.ROOT_ONLY,
            valueKind = TuneValueKind.RAW,
            groupId = "stune_idle"
        ),

        // 17. INPUT_BOOST_EN
        TuneNode(
            path = "/sys/module/cpu_boost/parameters/input_boost_enabled",
            id = TuneId.INPUT_BOOST_EN,
            vendor = TuneVendor.GENERIC,
            privilege = TunePrivilege.ROOT_ONLY,
            valueKind = TuneValueKind.ENUM,
            groupId = "cpu_boost_en"
        ),
        TuneNode(
            path = "/sys/module/cpu_boost/parameters/cpuboost_enable",
            id = TuneId.INPUT_BOOST_EN,
            vendor = TuneVendor.GENERIC,
            privilege = TunePrivilege.ROOT_ONLY,
            valueKind = TuneValueKind.ENUM,
            groupId = "cpu_boost_en"
        ),
        TuneNode(
            path = "/sys/module/cpu_boost/parameters/cpu_boost",
            id = TuneId.INPUT_BOOST_EN,
            vendor = TuneVendor.GENERIC,
            privilege = TunePrivilege.ROOT_ONLY,
            valueKind = TuneValueKind.ENUM,
            groupId = "cpu_boost_en"
        ),

        // 18. INPUT_BOOST_MS
        TuneNode(
            path = "/sys/module/cpu_boost/parameters/input_boost_ms",
            id = TuneId.INPUT_BOOST_MS,
            vendor = TuneVendor.GENERIC,
            privilege = TunePrivilege.ROOT_ONLY,
            valueKind = TuneValueKind.RAW,
            groupId = "input_boost_ms"
        ),
        TuneNode(
            path = "/sys/devices/system/cpu/cpu_boost/input_boost_ms",
            id = TuneId.INPUT_BOOST_MS,
            vendor = TuneVendor.GENERIC,
            privilege = TunePrivilege.ROOT_ONLY,
            valueKind = TuneValueKind.RAW,
            groupId = "input_boost_ms"
        ),
        TuneNode(
            path = "/sys/module/cpu_boost/parameters/boost_ms",
            id = TuneId.INPUT_BOOST_MS,
            vendor = TuneVendor.GENERIC,
            privilege = TunePrivilege.ROOT_ONLY,
            valueKind = TuneValueKind.RAW,
            groupId = "input_boost_ms"
        ),

        // 19. TOUCHBOOST
        TuneNode(
            path = "/sys/module/msm_performance/parameters/touchboost",
            id = TuneId.TOUCHBOOST,
            vendor = TuneVendor.GENERIC,
            privilege = TunePrivilege.ROOT_ONLY,
            valueKind = TuneValueKind.RAW,
            groupId = "touchboost"
        ),

        // 20. CPUFREQ_BOOST
        TuneNode(
            path = "/sys/devices/system/cpu/cpufreq/boost",
            id = TuneId.CPUFREQ_BOOST,
            vendor = TuneVendor.GENERIC,
            privilege = TunePrivilege.SHELL_OK,
            valueKind = TuneValueKind.RAW,
            groupId = "cpufreq_boost"
        ),

        // 21. DEVFREQ_BOOST
        TuneNode(
            path = "/sys/module/devfreq_boost/parameters/input_boost_duration",
            id = TuneId.DEVFREQ_BOOST,
            vendor = TuneVendor.GENERIC,
            privilege = TunePrivilege.ROOT_ONLY,
            valueKind = TuneValueKind.RAW,
            groupId = "devfreq_ib_ms"
        ),

        // 22. SCHED_BOOST_INPUT
        TuneNode(
            path = "/sys/devices/system/cpu/cpu_boost/sched_boost_on_input",
            id = TuneId.SCHED_BOOST_INPUT,
            vendor = TuneVendor.GENERIC,
            privilege = TunePrivilege.ROOT_ONLY,
            valueKind = TuneValueKind.RAW,
            groupId = "sched_boost_in"
        ),
        TuneNode(
            path = "/sys/module/cpu_boost/parameters/sched_boost_on_input",
            id = TuneId.SCHED_BOOST_INPUT,
            vendor = TuneVendor.GENERIC,
            privilege = TunePrivilege.ROOT_ONLY,
            valueKind = TuneValueKind.RAW,
            groupId = "sched_boost_in"
        ),

        // 23. SULTAN_INPUT
        TuneNode(
            path = "/sys/kernel/cpu_input_boost/enabled",
            id = TuneId.SULTAN_INPUT,
            vendor = TuneVendor.GENERIC,
            privilege = TunePrivilege.ROOT_ONLY,
            valueKind = TuneValueKind.RAW,
            groupId = "sultan_ib"
        ),
        TuneNode(
            path = "/sys/module/cpu_input_boost/parameters/input_boost_duration",
            id = TuneId.SULTAN_INPUT,
            vendor = TuneVendor.GENERIC,
            privilege = TunePrivilege.ROOT_ONLY,
            valueKind = TuneValueKind.RAW,
            groupId = "sultan_ib_ms"
        ),

        // 24. THERMAL_SCONFIG
        TuneNode(
            path = "/sys/class/thermal/thermal_message/sconfig",
            id = TuneId.THERMAL_SCONFIG,
            vendor = TuneVendor.GENERIC,
            privilege = TunePrivilege.ROOT_ONLY,
            valueKind = TuneValueKind.RAW,
            groupId = "sconfig"
        ),

        // 25. VM_SWAPPINESS
        TuneNode(
            path = "/proc/sys/vm/swappiness",
            id = TuneId.VM_SWAPPINESS,
            vendor = TuneVendor.GENERIC,
            privilege = TunePrivilege.ROOT_ONLY,
            valueKind = TuneValueKind.RAW,
            groupId = "vm_swappiness"
        ),

        // 26. VM_VFS_CACHE
        TuneNode(
            path = "/proc/sys/vm/vfs_cache_pressure",
            id = TuneId.VM_VFS_CACHE,
            vendor = TuneVendor.GENERIC,
            privilege = TunePrivilege.ROOT_ONLY,
            valueKind = TuneValueKind.RAW,
            groupId = "vm_vfs_cache"
        ),

        // 27. VM_DIRTY_RATIO
        TuneNode(
            path = "/proc/sys/vm/dirty_ratio",
            id = TuneId.VM_DIRTY_RATIO,
            vendor = TuneVendor.GENERIC,
            privilege = TunePrivilege.ROOT_ONLY,
            valueKind = TuneValueKind.RAW,
            groupId = "vm_dirty_ratio"
        ),

        // 28. IO_SCHEDULER
        TuneNode(
            path = "/sys/block/sda/queue/scheduler",
            id = TuneId.IO_SCHEDULER,
            vendor = TuneVendor.GENERIC,
            privilege = TunePrivilege.ROOT_ONLY,
            valueKind = TuneValueKind.ENUM,
            groupId = "io_scheduler"
        ),
        TuneNode(
            path = "/sys/block/sdb/queue/scheduler",
            id = TuneId.IO_SCHEDULER,
            vendor = TuneVendor.GENERIC,
            privilege = TunePrivilege.ROOT_ONLY,
            valueKind = TuneValueKind.ENUM,
            groupId = "io_scheduler"
        ),
        TuneNode(
            path = "/sys/block/mmcblk0/queue/scheduler",
            id = TuneId.IO_SCHEDULER,
            vendor = TuneVendor.GENERIC,
            privilege = TunePrivilege.ROOT_ONLY,
            valueKind = TuneValueKind.ENUM,
            groupId = "io_scheduler"
        ),
        TuneNode(
            path = "/sys/block/dm-0/queue/scheduler",
            id = TuneId.IO_SCHEDULER,
            vendor = TuneVendor.GENERIC,
            privilege = TunePrivilege.ROOT_ONLY,
            valueKind = TuneValueKind.ENUM,
            groupId = "io_scheduler"
        ),

        // 29. IO_READAHEAD
        TuneNode(
            path = "/sys/block/sda/queue/read_ahead_kb",
            id = TuneId.IO_READAHEAD,
            vendor = TuneVendor.GENERIC,
            privilege = TunePrivilege.ROOT_ONLY,
            valueKind = TuneValueKind.RAW,
            groupId = "io_readahead"
        ),
        TuneNode(
            path = "/sys/block/sdb/queue/read_ahead_kb",
            id = TuneId.IO_READAHEAD,
            vendor = TuneVendor.GENERIC,
            privilege = TunePrivilege.ROOT_ONLY,
            valueKind = TuneValueKind.RAW,
            groupId = "io_readahead"
        ),
        TuneNode(
            path = "/sys/block/mmcblk0/queue/read_ahead_kb",
            id = TuneId.IO_READAHEAD,
            vendor = TuneVendor.GENERIC,
            privilege = TunePrivilege.ROOT_ONLY,
            valueKind = TuneValueKind.RAW,
            groupId = "io_readahead"
        ),

        // 30. DISPLAY_PEAK (Settings API - virtual node placeholder)
        TuneNode(
            path = "/sys/devices/virtual/display/peak_refresh_rate",
            id = TuneId.DISPLAY_PEAK,
            vendor = TuneVendor.GENERIC,
            privilege = TunePrivilege.SHELL_OK,
            valueKind = TuneValueKind.ENUM,
            groupId = "display_peak"
        ),

        // 31. DISPLAY_MIUI (Settings API - virtual node placeholder)
        TuneNode(
            path = "/sys/devices/virtual/display/miui_refresh_rate",
            id = TuneId.DISPLAY_MIUI,
            vendor = TuneVendor.GENERIC,
            privilege = TunePrivilege.SHELL_OK,
            valueKind = TuneValueKind.ENUM,
            groupId = "display_miui"
        ),

        // 32. GAME_MODE_PERFORMANCE (command action; capability is game-specific)
        TuneNode(
            path = "/sys/devices/virtual/game_mode/performance",
            id = TuneId.GAME_MODE_PERFORMANCE,
            vendor = TuneVendor.GENERIC,
            privilege = TunePrivilege.SHELL_OK,
            valueKind = TuneValueKind.ENUM,
            groupId = "game_mode_performance",
            probeStrategy = ProbeStrategy.COMMAND_QUERY
        ),

        // 33. FOCUS_DND (Settings/NotificationManager API)
        TuneNode(
            path = "/sys/devices/virtual/focus/dnd",
            id = TuneId.FOCUS_DND,
            vendor = TuneVendor.GENERIC,
            privilege = TunePrivilege.SHELL_OK,
            valueKind = TuneValueKind.ENUM,
            groupId = "focus_dnd"
        ),

        // 33. FOCUS_HEADSUP (Settings API)
        TuneNode(
            path = "/sys/devices/virtual/focus/heads_up",
            id = TuneId.FOCUS_HEADSUP,
            vendor = TuneVendor.GENERIC,
            privilege = TunePrivilege.SHELL_OK,
            valueKind = TuneValueKind.ENUM,
            groupId = "focus_headsup"
        ),

        // 34. FOCUS_IMMERSIVE (Settings API)
        TuneNode(
            path = "/sys/devices/virtual/focus/immersive",
            id = TuneId.FOCUS_IMMERSIVE,
            vendor = TuneVendor.GENERIC,
            privilege = TunePrivilege.SHELL_OK,
            valueKind = TuneValueKind.ENUM,
            groupId = "focus_immersive"
        ),

        // 35. CHARGE_BYPASS
        TuneNode(
            path = "/sys/class/qcom-battery/bypass_charging_enable",
            id = TuneId.CHARGE_BYPASS,
            vendor = TuneVendor.GENERIC,
            privilege = TunePrivilege.ROOT_ONLY,
            valueKind = TuneValueKind.RAW,
            groupId = "charge_bypass"
        ),
        TuneNode(
            path = "/sys/class/power_supply/battery/input_suspend",
            id = TuneId.CHARGE_BYPASS,
            vendor = TuneVendor.GENERIC,
            privilege = TunePrivilege.ROOT_ONLY,
            valueKind = TuneValueKind.RAW,
            groupId = "charge_bypass"
        ),
        TuneNode(
            path = "/sys/class/power_supply/battery/charging_enabled",
            id = TuneId.CHARGE_BYPASS,
            vendor = TuneVendor.GENERIC,
            privilege = TunePrivilege.ROOT_ONLY,
            valueKind = TuneValueKind.RAW,
            groupId = "charge_bypass"
        ),

        // 36. NET_TCP
        TuneNode(
            path = "/proc/sys/net/ipv4/tcp_congestion_control",
            id = TuneId.NET_TCP,
            vendor = TuneVendor.GENERIC,
            privilege = TunePrivilege.ROOT_ONLY,
            valueKind = TuneValueKind.ENUM,
            availablePath = "/proc/sys/net/ipv4/tcp_available_congestion_control",
            groupId = "net_tcp"
        )
    )

    val nodesByTuneId: Map<TuneId, List<TuneNode>> = allNodes.groupBy { it.id }

    /** Returns first representative candidate node for Phase 1 probe. */
    fun phase1Candidates(): List<TuneNode> {
        return TuneId.values().mapNotNull { id ->
            nodesByTuneId[id]?.firstOrNull()
        }
    }

    /** Discover available cpufreq policy directories without cluster assumptions. */
    fun discoverPolicies(shell: TuneShell): List<String> {
        return shell.execute(
            "find /sys/devices/system/cpu/cpufreq -mindepth 1 -maxdepth 1 -type d -name 'policy*' -print 2>/dev/null | sort -V",
            com.ivarna.apexcore.fps.privilege.PrivilegeTier.STANDARD,
            250L
        ).output.lineSequence().mapNotNull {
            it.trim().takeIf { path -> path.matches(Regex("/sys/devices/system/cpu/cpufreq/policy[0-9]+")) }
                ?.substringAfterLast('/')
        }.distinct().toList()
    }
}
