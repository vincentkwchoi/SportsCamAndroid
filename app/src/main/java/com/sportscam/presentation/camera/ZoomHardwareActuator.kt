package com.sportscam.presentation.camera

import com.sportscam.presentation.camera.CameraManager

/**
 * Wraps CameraManager zoom calls to provide a clean interface for the Algorithm.
 * Allows switching between CameraManager implementation or mock for testing.
 */
class ZoomHardwareActuator(
    private val cameraManager: CameraManager
) {
    private var rampRate: Float = 4.0f
    
    fun applyZoom(targetZoom: Float) {
        // Log.d("ZoomHW", "Hardware setting zoom to: $targetZoom")
        // In the future this might handle linear zoom conversion or smoothing
        // For now direct pass-through
        cameraManager.setZoom(targetZoom)
    }
    
    fun updateRampRate(rate: Float) {
        this.rampRate = rate
        // Note: Ramp rate is enforced in ZoomConstraint, not here usually, 
        // unless using hardware ramping APIs.
    }
}
