package com.rdevzph.fpsmeter.model

enum class SurfaceFlingerMethod(val label: String, val description: String) {
    LATENCY(
        label = "Layer Latency",
        description = "Measures frame latency per layer. Falls back to TimeStats if unsupported."
    ),
    TIMESTATS(
        label = "TimeStats",
        description = "Reads compositor statistics. Recommended if Latency shows wrong FPS."
    );

    companion object {
        fun fromString(value: String?): SurfaceFlingerMethod {
            return entries.find { it.name.equals(value, ignoreCase = true) } ?: LATENCY
        }
    }
}

