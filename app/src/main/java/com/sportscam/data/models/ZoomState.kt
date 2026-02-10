package com.sportscam.data.models

data class ZoomState(
    val currentZoom: Float = 1.0f,     // Current zoom ratio (1.0 - 5.0)
    val targetZoom: Float = 1.0f,      // Target zoom ratio
    val isAutoZoom: Boolean = true,    // Auto-zoom enabled
    val mode: ZoomMode = ZoomMode.AUTO
)

enum class ZoomMode {
    AUTO,       // Auto-zoom tracking
    MANUAL,     // Manual control
    LOCKED      // Zoom locked
}
