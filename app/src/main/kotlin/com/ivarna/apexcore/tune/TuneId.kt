package com.ivarna.apexcore.tune

/**
 * Normative inventory of 36 game-tuning options across 10 categories.
 * Defined in docs/plans/T12-tune-options.md.
 */
enum class TuneId {
    // 1. GPU (8 options)
    GPU_FLOOR,
    GPU_HOLD,
    GPU_ADRENO,
    GPU_GOVERNOR,
    GPU_PWRLEVEL,
    GPU_GED_GAME,
    GPU_SAMSUNG_MIN,
    GPU_SIMPLE,

    // 2. CPU and scheduling (8 options)
    CPU_FLOOR,
    CPU_FLOOR_LITTLE,
    CPU_FLOOR_BIG,
    CPU_FLOOR_PRIME,
    CPU_GOVERNOR,
    CPU_UCLAMP,
    CPU_STUNE,
    CPU_STUNE_IDLE,

    // 3. Touch and input (7 options)
    INPUT_BOOST_EN,
    INPUT_BOOST_MS,
    TOUCHBOOST,
    CPUFREQ_BOOST,
    DEVFREQ_BOOST,
    SCHED_BOOST_INPUT,
    SULTAN_INPUT,

    // 4. Thermal (1 option)
    THERMAL_SCONFIG,

    // 5. Memory (3 options)
    VM_SWAPPINESS,
    VM_VFS_CACHE,
    VM_DIRTY_RATIO,

    // 6. Storage I/O (2 options)
    IO_SCHEDULER,
    IO_READAHEAD,

    // 7. Display (2 options)
    DISPLAY_PEAK,
    DISPLAY_MIUI,

    // 8. Focus (3 options)
    FOCUS_DND,
    FOCUS_HEADSUP,
    FOCUS_IMMERSIVE,

    // 9. Charging (1 option)
    CHARGE_BYPASS,

    // 10. Network (1 option)
    NET_TCP
}
