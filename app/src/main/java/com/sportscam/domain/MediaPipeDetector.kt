package com.sportscam.domain

import android.content.Context
import android.graphics.Bitmap
import com.sportscam.data.models.Detection

class MediaPipeDetector(
    private val context: Context,
    private val modelPath: String = "efficientdet_lite0.tflite"
) {
    fun detect(bitmap: Bitmap): List<Detection> {
        return emptyList()
    }

    fun close() {
    }
}
