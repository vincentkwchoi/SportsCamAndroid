package com.sportscam.domain

import android.util.Log
import android.util.Size
import com.sportscam.data.models.Track

class TargetZoomCalc(
    var targetSubjectHeightRatio: Float = 0.15f
) {
    private val smoother = SmoothingFilter(alpha = 0.2f)
    
    fun calculateError(subject: com.sportscam.data.models.Track, currentZoom: Float): Float {
        // 1. Smooth Input
        val smoothedHeight = smoother.filter(subject.bbox.height())
        
        // 2. Calculate Error (Target - Current)
        // iOS Vision receives the ZOOMED buffer, so smoothedHeight IS the height in crop.
        // Error = Target - HeightInCrop
        val error = targetSubjectHeightRatio - smoothedHeight
        Log.d("AutoZoom", "TargetCalc: H=$smoothedHeight, Target=$targetSubjectHeightRatio, Error=$error")
        return error
    }
    
    fun reset() {
        smoother.reset()
    }
}
