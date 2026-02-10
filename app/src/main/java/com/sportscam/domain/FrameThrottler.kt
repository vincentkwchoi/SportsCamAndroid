package com.sportscam.domain

class FrameThrottler(
    private var interval: Int = 1
) {
    private var counter: Int = 0

    /**
     * Checks if the current frame should be processed and increments the internal counter.
     */
    fun shouldProcess(frameNumber: Long): Boolean {
        // iOS version uses internal counter, matching that for parity
        counter++
        return counter % interval == 0
    }
    
    /**
     * Returns the multiplier for Delta Time (dt) calculations.
     * E.g. Interval 3 -> Multiplier 3.0.
     */
    val intervalMultiplier: Float
        get() = interval.toFloat()

    fun setInterval(newInterval: Int) {
        if (newInterval > 0) {
            interval = newInterval
            reset()
        }
    }
    
    fun reset() {
        counter = 0
    }
}
