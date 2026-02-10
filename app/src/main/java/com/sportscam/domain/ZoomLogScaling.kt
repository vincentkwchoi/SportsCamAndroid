package com.sportscam.domain

class ZoomLogScaling(
    private var kZoom: Float = 1.0f
) {
    fun scale(linearVelocity: Float, currentZoom: Float): Float {
        // Logarithmic perception scaling
        // Velocity scales with current zoom level
        return linearVelocity * kZoom * currentZoom
    }
    
    fun updateGain(kZoom: Float) {
        this.kZoom = kZoom
    }
}
