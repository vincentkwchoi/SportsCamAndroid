package com.sportscam.domain

import android.graphics.RectF
import com.sportscam.data.models.Track
import kotlin.math.abs

class BBoxActivityDetector(
    private var shapeThreshold: Float = 0.002f, // Default matching iOS
    private val historySize: Int = 60           // 2 seconds at 30fps
) {
    private val minHistoryForDecision: Int = 10
    
    private data class BBoxFeatures(
        val width: Float,
        val height: Float,
        val area: Float,
        val aspectRatio: Float
    )

    // Map of Track ID -> List of features
    private val history = HashMap<Int, MutableList<BBoxFeatures>>()

    fun updateThreshold(threshold: Float) {
        this.shapeThreshold = threshold
    }

    /**
     * Determines if a track is "Active" (moving) based on bbox shape variance.
     */
    fun isActive(track: Track): Boolean {
        val features = extractFeatures(track.bbox)
        
        val trackHistory = history.getOrPut(track.id) { ArrayList() }
        trackHistory.add(features)
        
        if (trackHistory.size > historySize) {
            trackHistory.removeAt(0)
        }
        
        // Calculate activity score
        val varianceScore = calculateShapeVariance(track.id)
        return varianceScore > shapeThreshold
    }
    
    private fun extractFeatures(bbox: RectF): BBoxFeatures {
        val w = bbox.width()
        val h = bbox.height()
        val safeH = maxOf(h, 0.0001f)
        
        return BBoxFeatures(
            width = w,
            height = h,
            area = w * h,
            aspectRatio = w / safeH
        )
    }

    private fun calculateShapeVariance(trackId: Int): Float {
        val trackHistory = history[trackId] ?: return 1.0f // Assume active if no history
        
        if (trackHistory.size < minHistoryForDecision) {
            return 1.0f // Assume active initially
        }
        
        val aspectRatios = trackHistory.map { it.aspectRatio }
        val areas = trackHistory.map { it.area }
        
        val aspectVar = calculateVariance(aspectRatios)
        val areaVar = calculateVariance(areas)
        
        // Combine: (std(aspectRatio) + std(area)) / 2
        return (kotlin.math.sqrt(aspectVar) + kotlin.math.sqrt(areaVar)) / 2.0f
    }

    private fun calculateVariance(numbers: List<Float>): Float {
        if (numbers.isEmpty()) return 0f
        val mean = numbers.average().toFloat()
        return numbers.map { (it - mean) * (it - mean) }.average().toFloat()
    }

    fun cleanup(activeTrackIds: Set<Int>) {
        val iterator = history.entries.iterator()
        while (iterator.hasNext()) {
            if (!activeTrackIds.contains(iterator.next().key)) {
                iterator.remove()
            }
        }
    }
    
    fun reset() {
        history.clear()
    }
}
