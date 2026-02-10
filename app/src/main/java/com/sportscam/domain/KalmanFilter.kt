package com.sportscam.domain

import android.graphics.RectF

/**
 * Kalman Filter for 8-state bounding box tracking: [cx, cy, a, h, vx, vy, va, vh]
 * Follows the logic in iOS Skicamera KalmanFilter.swift.
 */
class KalmanFilter(initialRect: RectF) {
    // State vector [cx, cy, ratio, h, vx, vy, vr, vh]
    private var state = FloatArray(8)
    // Simplified Covariance P (Diagonals)
    private var covariance = FloatArray(8)

    // Constants (Matching iOS precisely)
    private val stdWeightPosition = 1.0f / 20.0f
    private val stdWeightVelocity = 1.0f / 160.0f

    init {
        val h = initialRect.height()
        val w = initialRect.width()
        val cx = initialRect.centerX()
        val cy = initialRect.centerY()
        val safeH = maxOf(h, 0.0001f)
        val ratio = w / safeH

        // [cx, cy, ratio, h, 0, 0, 0, 0]
        state[0] = cx
        state[1] = cy
        state[2] = ratio
        state[3] = safeH
        // Velocities 4-7 are 0

        val std = floatArrayOf(
            2 * stdWeightPosition * safeH,
            2 * stdWeightPosition * safeH,
            1e-2f,
            2 * stdWeightPosition * safeH,
            10 * stdWeightVelocity * safeH,
            10 * stdWeightVelocity * safeH,
            1e-5f,
            10 * stdWeightVelocity * safeH
        )

        for (i in 0 until 8) {
            covariance[i] = std[i] * std[i]
        }
    }

    fun predict(): RectF {
        // x_new = x + v (Constant Velocity model)
        for (i in 0 until 4) {
            state[i] += state[i + 4]
        }

        // Q: Process Noise std based on current height
        val h = state[3]
        val stdQ = floatArrayOf(
            stdWeightPosition * h,
            stdWeightPosition * h,
            1e-2f,
            stdWeightPosition * h,
            stdWeightVelocity * h,
            stdWeightVelocity * h,
            1e-5f,
            stdWeightVelocity * h
        )

        // P = FPF^T + Q (Simplified diagonal approximation matching iOS)
        for (i in 0 until 8) {
            covariance[i] += stdQ[i] * stdQ[i]
        }

        return currentStateRect()
    }

    fun update(measurement: RectF) {
        val h = measurement.height()
        val w = measurement.width()
        val cx = measurement.centerX()
        val cy = measurement.centerY()
        val safeH = maxOf(h, 0.0001f)
        val ratio = w / safeH

        val z = floatArrayOf(cx, cy, ratio, safeH)

        // Simplified Update with fixed gain (Matching iOS v1 stability logic)
        // K = P * H^T * (HPH^T + R)^-1
        val gain = 0.4f

        for (i in 0 until 4) {
            val innovation = z[i] - state[i]
            state[i] += gain * innovation
            state[i + 4] += gain * innovation // Velocity update
        }
    }

    fun currentStateRect(): RectF {
        val cx = state[0]
        val cy = state[1]
        val ratio = state[2]
        val h = state[3]
        val w = h * ratio

        return RectF(
            cx - w / 2f,
            cy - h / 2f,
            cx + w / 2f,
            cy + h / 2f
        )
    }
}
