package com.sportscam.domain

class ZoomConstraint(
    private var minZoom: Float = 1.0f,
    private var maxZoom: Float = 3.0f,
    private var maxVelocity: Float = 2.0f  // Max zoom change per second (ramp rate)
) {
    fun constrain(targetZoom: Float, currentZoom: Float, dt: Float): Float {
        // 1. Rate Limiting (Motion Sickness Prevention)
        val maxChange = maxVelocity * dt
        
        // Clamp the change per step
        // If system wants to jump 1.0 -> 2.0 in 0.01s (speed 100/s), clamp to 0.02 (speed 2/s)
        var desiredChange = targetZoom - currentZoom
        desiredChange = desiredChange.coerceIn(-maxChange, maxChange)
        
        val newZoom = currentZoom + desiredChange
        
        // 2. Hard Range Limiting
        return newZoom.coerceIn(minZoom, maxZoom)
    }
    
    fun updateLimits(minZoom: Float, maxZoom: Float, maxVelocity: Float) {
        this.minZoom = minZoom
        this.maxZoom = maxZoom
        this.maxVelocity = maxVelocity // Ramp Rate
    }
}
