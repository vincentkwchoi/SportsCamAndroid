package com.sportscam.domain

import android.graphics.Bitmap
import com.sportscam.data.models.Detection

interface ObjectDetector {
    fun detect(bitmap: Bitmap): List<Detection>
    fun close()
    var confidenceThreshold: Float
}
