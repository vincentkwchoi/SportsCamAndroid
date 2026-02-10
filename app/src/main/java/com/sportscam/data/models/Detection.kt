package com.sportscam.data.models

import android.graphics.RectF

data class Detection(
    val bbox: RectF,           // Bounding box (normalized 0-1)
    val confidence: Float,      // Detection confidence 0-1
    val classId: Int,          // Object class ID (0 = person)
    val trackId: Int = -1      // Assigned by tracker
)
