package com.sportscam.data.models

import android.util.Size

data class CameraState(
    val isRecording: Boolean = false,
    val recordingDuration: Long = 0L,  // Milliseconds
    val zoomState: ZoomState = ZoomState(),
    val detections: List<Detection> = emptyList(),
    val activePlayers: List<Track> = emptyList(),
    val fps: Float = 0f,
    val analysisTimeMs: Float = 0f,
    val averageActiveHeight: Float = 0f,
    val showDebug: Boolean = false,
    val sportMode: SportMode = SportMode.BASKETBALL,
    val sourceSize: Size? = null,
    val lastVideoUri: String? = null // Track the URI of the last recording
)
