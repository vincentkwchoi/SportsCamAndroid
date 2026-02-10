package com.sportscam.domain

class PIDController(
    private var kp: Float = 6.0f,      // Proportional gain
    private var kd: Float = 3.0f       // Derivative gain
) {
    private var lastError: Float = 0f
    private var lastTime: Long = 0L
    private var active: Boolean = false
    
    fun calculate(error: Float, currentTime: Long): Float {
        // First run or reset
        if (!active || lastTime == 0L) {
            lastError = error
            lastTime = currentTime
            active = true
            return 0f
        }
        
        val dt = (currentTime - lastTime) / 1000f  // Convert ms to seconds
        
        // Prevent Divide by Zero or huge jumps if paused
        if (dt <= 0 || dt > 1.0f) {
            lastTime = currentTime
            return 0f
        }
        
        val derivative = (error - lastError) / dt
        val velocity = (kp * error) + (kd * derivative)
        
        lastError = error
        lastTime = currentTime
        
        return velocity
    }
    
    fun updateGains(kp: Float, kd: Float) {
        this.kp = kp
        this.kd = kd
    }
    
    fun reset() {
        lastError = 0f
        lastTime = 0L
        active = false
    }
}
