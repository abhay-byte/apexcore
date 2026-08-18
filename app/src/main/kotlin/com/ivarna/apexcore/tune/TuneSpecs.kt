package com.ivarna.apexcore.tune

data class TuneSpec(
    val id: TuneId,
    val category: TuneCategory,
    val title: String,
    val description: String,
    val kind: TuneControlKind,
    val slider: IntRange? = null,
    val defaultVal: String? = null
)

object TuneSpecs {

    val all: List<TuneSpec> = listOf(
        // 1. GPU (8 options)
        TuneSpec(
            id = TuneId.GPU_FLOOR,
            category = TuneCategory.GPU,
            title = "GPU frequency floor",
            description = "Raise minimum GPU clock during game",
            kind = TuneControlKind.SWITCH
        ),
        TuneSpec(
            id = TuneId.GPU_HOLD,
            category = TuneCategory.GPU,
            title = "GPU keep-awake",
            description = "Prevent GPU power collapse between frames",
            kind = TuneControlKind.SWITCH
        ),
        TuneSpec(
            id = TuneId.GPU_ADRENO,
            category = TuneCategory.GPU,
            title = "Adreno boost level",
            description = "Adreno GPU dynamic governor boost level",
            kind = TuneControlKind.SLIDER,
            slider = 0..3,
            defaultVal = "2"
        ),
        TuneSpec(
            id = TuneId.GPU_GOVERNOR,
            category = TuneCategory.GPU,
            title = "GPU governor",
            description = "Switch GPU governor out of powersave",
            kind = TuneControlKind.ENUM
        ),
        TuneSpec(
            id = TuneId.GPU_PWRLEVEL,
            category = TuneCategory.GPU,
            title = "GPU power floor",
            description = "Set minimum Adreno power level",
            kind = TuneControlKind.SLIDER,
            slider = 0..6,
            defaultVal = "0"
        ),
        TuneSpec(
            id = TuneId.GPU_GED_GAME,
            category = TuneCategory.GPU,
            title = "MediaTek game mode",
            description = "Enable MediaTek GED game engine boost",
            kind = TuneControlKind.SWITCH
        ),
        TuneSpec(
            id = TuneId.GPU_SAMSUNG_MIN,
            category = TuneCategory.GPU,
            title = "Samsung GPU min clock",
            description = "Set Samsung Exynos GPU minimum frequency",
            kind = TuneControlKind.SWITCH
        ),
        TuneSpec(
            id = TuneId.GPU_SIMPLE,
            category = TuneCategory.GPU,
            title = "Simple GPU ramp",
            description = "Enable Simple GPU governor ramp algorithm",
            kind = TuneControlKind.SWITCH
        ),

        // 2. CPU and scheduling (8 options)
        TuneSpec(
            id = TuneId.CPU_FLOOR,
            category = TuneCategory.CPU,
            title = "CPU frequency floor",
            description = "Raise minimum CPU frequency across all clusters",
            kind = TuneControlKind.SWITCH
        ),
        TuneSpec(
            id = TuneId.CPU_FLOOR_LITTLE,
            category = TuneCategory.CPU,
            title = "Little-cluster floor",
            description = "Raise efficiency cluster minimum frequency",
            kind = TuneControlKind.SWITCH
        ),
        TuneSpec(
            id = TuneId.CPU_FLOOR_BIG,
            category = TuneCategory.CPU,
            title = "Big-cluster floor",
            description = "Raise performance cluster minimum frequency",
            kind = TuneControlKind.SWITCH
        ),
        TuneSpec(
            id = TuneId.CPU_FLOOR_PRIME,
            category = TuneCategory.CPU,
            title = "Prime-cluster floor",
            description = "Raise prime/gold core minimum frequency",
            kind = TuneControlKind.SWITCH
        ),
        TuneSpec(
            id = TuneId.CPU_GOVERNOR,
            category = TuneCategory.CPU,
            title = "Leave powersave",
            description = "Switch powersave/conservative CPU governor to schedutil",
            kind = TuneControlKind.SWITCH
        ),
        TuneSpec(
            id = TuneId.CPU_UCLAMP,
            category = TuneCategory.CPU,
            title = "Top-app uclamp",
            description = "Set minimum CPU utilization clamp for foreground tasks",
            kind = TuneControlKind.SLIDER,
            slider = 0..100,
            defaultVal = "10"
        ),
        TuneSpec(
            id = TuneId.CPU_STUNE,
            category = TuneCategory.CPU,
            title = "Top-app schedtune",
            description = "Schedtune boost level for foreground tasks",
            kind = TuneControlKind.SLIDER,
            slider = 0..20,
            defaultVal = "10"
        ),
        TuneSpec(
            id = TuneId.CPU_STUNE_IDLE,
            category = TuneCategory.CPU,
            title = "Prefer idle for top-app",
            description = "Schedule foreground tasks on idle CPUs",
            kind = TuneControlKind.SWITCH
        ),

        // 3. Touch and input (7 options)
        TuneSpec(
            id = TuneId.INPUT_BOOST_EN,
            category = TuneCategory.INPUT,
            title = "Input boost",
            description = "Enable CPU boost on touch input",
            kind = TuneControlKind.SWITCH
        ),
        TuneSpec(
            id = TuneId.INPUT_BOOST_MS,
            category = TuneCategory.INPUT,
            title = "Input boost duration",
            description = "Touch boost duration in milliseconds",
            kind = TuneControlKind.SLIDER,
            slider = 40..128,
            defaultVal = "64"
        ),
        TuneSpec(
            id = TuneId.TOUCHBOOST,
            category = TuneCategory.INPUT,
            title = "Touch boost",
            description = "MSM performance touchboost module",
            kind = TuneControlKind.SWITCH
        ),
        TuneSpec(
            id = TuneId.CPUFREQ_BOOST,
            category = TuneCategory.INPUT,
            title = "CPU frequency boost",
            description = "Linux cpufreq boost driver toggle",
            kind = TuneControlKind.SWITCH
        ),
        TuneSpec(
            id = TuneId.DEVFREQ_BOOST,
            category = TuneCategory.INPUT,
            title = "Devfreq input duration",
            description = "Devfreq memory bus boost duration",
            kind = TuneControlKind.SLIDER,
            slider = 0..500,
            defaultVal = "100"
        ),
        TuneSpec(
            id = TuneId.SCHED_BOOST_INPUT,
            category = TuneCategory.INPUT,
            title = "Sched boost on input",
            description = "Raise CPU scheduling priority on touch event",
            kind = TuneControlKind.SWITCH
        ),
        TuneSpec(
            id = TuneId.SULTAN_INPUT,
            category = TuneCategory.INPUT,
            title = "Sultan input boost",
            description = "Sultanxda kernel CPU input boost module",
            kind = TuneControlKind.SWITCH
        ),

        // 4. Thermal (1 option)
        TuneSpec(
            id = TuneId.THERMAL_SCONFIG,
            category = TuneCategory.THERMAL,
            title = "Gaming thermal profile",
            description = "OEM thermal profile for gaming (sconfig 13)",
            kind = TuneControlKind.ENUM,
            defaultVal = "13"
        ),

        // 5. Memory (3 options)
        TuneSpec(
            id = TuneId.VM_SWAPPINESS,
            category = TuneCategory.MEMORY,
            title = "Lower swappiness",
            description = "Reduce memory swapping pressure during gameplay",
            kind = TuneControlKind.SLIDER,
            slider = 1..100,
            defaultVal = "30"
        ),
        TuneSpec(
            id = TuneId.VM_VFS_CACHE,
            category = TuneCategory.MEMORY,
            title = "Keep file cache",
            description = "Maintain VFS filesystem cache in RAM",
            kind = TuneControlKind.SLIDER,
            slider = 10..100,
            defaultVal = "50"
        ),
        TuneSpec(
            id = TuneId.VM_DIRTY_RATIO,
            category = TuneCategory.MEMORY,
            title = "Dirty ratio",
            description = "RAM threshold for flushing dirty file pages",
            kind = TuneControlKind.SLIDER,
            slider = 10..50,
            defaultVal = "20"
        ),

        // 6. Storage I/O (2 options)
        TuneSpec(
            id = TuneId.IO_SCHEDULER,
            category = TuneCategory.IO,
            title = "I/O scheduler",
            description = "Storage queue scheduler (mq-deadline / none)",
            kind = TuneControlKind.ENUM,
            defaultVal = "mq-deadline"
        ),
        TuneSpec(
            id = TuneId.IO_READAHEAD,
            category = TuneCategory.IO,
            title = "Read-ahead",
            description = "Disk storage read-ahead buffer in KB",
            kind = TuneControlKind.SLIDER,
            slider = 128..2048,
            defaultVal = "512"
        ),

        // 7. Display (2 options)
        TuneSpec(
            id = TuneId.DISPLAY_PEAK,
            category = TuneCategory.DISPLAY,
            title = "Peak refresh rate",
            description = "Lock display refresh rate to advertised peak",
            kind = TuneControlKind.ENUM
        ),
        TuneSpec(
            id = TuneId.DISPLAY_MIUI,
            category = TuneCategory.DISPLAY,
            title = "MIUI refresh mode",
            description = "Xiaomi/MIUI high refresh rate display mode",
            kind = TuneControlKind.ENUM
        ),

        // 8. Focus (3 options)
        TuneSpec(
            id = TuneId.FOCUS_DND,
            category = TuneCategory.FOCUS,
            title = "Do not disturb",
            description = "Silence notifications during game session",
            kind = TuneControlKind.SWITCH
        ),
        TuneSpec(
            id = TuneId.FOCUS_HEADSUP,
            category = TuneCategory.FOCUS,
            title = "Hide heads-up",
            description = "Suppress floating banner popups during game",
            kind = TuneControlKind.SWITCH
        ),
        TuneSpec(
            id = TuneId.FOCUS_IMMERSIVE,
            category = TuneCategory.FOCUS,
            title = "Immersive bars",
            description = "Auto-hide status and navigation bars in-game",
            kind = TuneControlKind.SWITCH
        ),

        // 9. Charging (1 option)
        TuneSpec(
            id = TuneId.CHARGE_BYPASS,
            category = TuneCategory.CHARGE,
            title = "Bypass charging in-game",
            description = "Pause battery charging while plugged in to reduce heat",
            kind = TuneControlKind.SWITCH
        ),

        // 10. Network (1 option)
        TuneSpec(
            id = TuneId.NET_TCP,
            category = TuneCategory.NETWORK,
            title = "TCP congestion",
            description = "TCP congestion algorithm (BBR / Westwood)",
            kind = TuneControlKind.ENUM,
            defaultVal = "bbr"
        )
    )

    val byId: Map<TuneId, TuneSpec> = all.associateBy { it.id }

    val byCategory: Map<TuneCategory, List<TuneSpec>> = all.groupBy { it.category }
}
