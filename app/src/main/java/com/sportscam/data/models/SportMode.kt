package com.sportscam.data.models

enum class SportMode(
    val displayName: String,
    val trackBuffer: Int,          // Frames to keep lost tracks
    val minConfidence: Float,      // Minimum detection confidence
    val targetFrameRatio: Float,   // Target size as ratio of frame
    val movementThreshold: Float   // Activity detection threshold
) {
    BASKETBALL(
        displayName = "Basketball",
        trackBuffer = 150,         // Long occlusions (players overlap)
        minConfidence = 0.3f,
        targetFrameRatio = 0.33f,  // 1/3 of screen
        movementThreshold = 0.05f  // Moderate movement
    ),
    SKI(
        displayName = "Ski",
        trackBuffer = 30,          // Short occlusions (individual runs)
        minConfidence = 0.3f,
        targetFrameRatio = 0.33f,
        movementThreshold = 0.08f  // Higher threshold (background motion)
    ),
    HOCKEY(
        displayName = "Hockey",
        trackBuffer = 100,         // Medium occlusions (fast movement)
        minConfidence = 0.3f,
        targetFrameRatio = 0.33f,
        movementThreshold = 0.06f  // Fast movement
    )
}
