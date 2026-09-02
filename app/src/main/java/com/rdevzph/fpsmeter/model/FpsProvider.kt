package com.rdevzph.fpsmeter.model

enum class FpsProvider(val label: String, val description: String) {
    CHOREOGRAPHER(
        label = "Choreographer",
        description = "Standard system frame callback. Low overhead, measures UI/display frame pace."
    ),
    SURFACE_FLINGER(
        label = "SurfaceFlinger",
        description = "Measures actual game rendering FPS via Shizuku. Supports Vulkan and OpenGL."
    );

    companion object {
        fun fromString(value: String?): FpsProvider {
            return entries.find { it.name.equals(value, ignoreCase = true) } ?: CHOREOGRAPHER
        }
    }
}

enum class GraphicsApi(val shortLabel: String, val fullLabel: String) {
    UNKNOWN("N/A", "Unknown"),
    OPENGL("GL", "OpenGL ES"),
    VULKAN("VK", "Vulkan")
}
