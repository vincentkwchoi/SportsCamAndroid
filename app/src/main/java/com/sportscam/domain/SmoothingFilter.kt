package com.sportscam.domain

class SmoothingFilter(private val alpha: Float) {
    private var lastValue: Float? = null

    fun filter(newValue: Float): Float {
        val last = lastValue ?: newValue
        val filtered = alpha * newValue + (1 - alpha) * last
        lastValue = filtered
        return filtered
    }

    fun reset() {
        lastValue = null
    }
}
