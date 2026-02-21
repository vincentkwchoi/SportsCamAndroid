package com.sportscam.domain

import kotlin.math.abs

class HysteresisGate(
    private var startThreshold: Float, // error required to START
    private var stopThreshold: Float   // error required to STOP
) {
    private var isZooming = false
    
    /**
     * Returns true if the PID controller should be active based on current relative error.
     */
    fun shouldUpdate(relativeError: Float): Boolean {
        val absError = abs(relativeError)
        
        return if (isZooming) {
            // If already zooming, keep going until error drops below stop threshold
            if (absError < stopThreshold) {
                isZooming = false
                false
            } else {
                true
            }
        } else {
            // If stable, wait until error exceeds start threshold to begin
            if (absError > startThreshold) {
                isZooming = true
                true
            } else {
                false
            }
        }
    }
    
    fun updateThresholds(start: Float, stop: Float) {
        this.startThreshold = start
        this.stopThreshold = stop
    }
    
    fun reset() {
        isZooming = false
    }
}
