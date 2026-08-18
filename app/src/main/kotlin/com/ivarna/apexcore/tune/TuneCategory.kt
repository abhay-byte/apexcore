package com.ivarna.apexcore.tune

/**
 * 10 User-visible categories for Game Optimisation options.
 * Order matches apply priority:
 * GPU -> CPU -> Touch & Input -> Thermal -> Memory -> Storage I/O -> Display -> Focus -> Charging -> Network.
 */
enum class TuneCategory(val displayName: String) {
    GPU("GPU"),
    CPU("CPU and scheduling"),
    INPUT("Touch and input"),
    THERMAL("Thermal"),
    MEMORY("Memory"),
    IO("Storage I/O"),
    DISPLAY("Display"),
    FOCUS("Focus"),
    CHARGE("Charging"),
    NETWORK("Network")
}
