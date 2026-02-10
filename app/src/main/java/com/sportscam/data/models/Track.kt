package com.sportscam.data.models

import android.graphics.RectF
import com.sportscam.domain.KalmanFilter

data class Track(
    val id: Int,               // Unique track ID
    val bbox: RectF,           // Current bounding box
    val confidence: Float,      // Track confidence
    val age: Int = 0,          // Frames since creation
    val hits: Int = 0,         // Successful matches
    val misses: Int = 0,       // Missed detections
    val isActive: Boolean = false,  // Movement detected
    val state: TrackState = TrackState.TENTATIVE,
    val kf: KalmanFilter? = null   // Predictive filter
)

enum class TrackState {
    TENTATIVE,  // New track, not confirmed
    CONFIRMED,  // Stable track
    LOST        // Track lost, in buffer
}
