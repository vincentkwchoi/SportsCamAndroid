package com.sportscam.domain

import com.sportscam.data.models.Detection
import com.sportscam.data.models.Track

data class AutoZoomResult(
    val detections: List<Detection> = emptyList(),
    val tracks: List<Track> = emptyList(),
    val activeTracks: List<Track> = emptyList(),
    val isProcessed: Boolean = false
)
