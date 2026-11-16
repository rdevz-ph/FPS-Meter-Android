package com.rdevzph.fpsmeter.model

enum class RamUnit(val label: String, val description: String) {
    GB(
        label = "GB",
        description = "Gigabytes"
    ),
    MB(
        label = "MB",
        description = "Megabytes"
    );

    companion object {
        fun fromString(value: String?): RamUnit {
            return entries.find { it.name.equals(value, ignoreCase = true) } ?: GB
        }
    }
}
