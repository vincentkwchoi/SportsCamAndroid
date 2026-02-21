package com.sportscam.data.models

enum class SportMode(val displayName: String) {
    BASKETBALL("Basketball"),
    SKI("Ski"),
    HOCKEY("Hockey")
}

enum class SelectionStrategy {
    SINGLE_SUBJECT,
    GROUP
}

data class AutoZoomConfig(
    val targetHeightRatio: Float,
    val kp: Float,
    val kd: Float,
    val kZoom: Float,
    val maxZoom: Float,
    val rampRate: Float,
    val shapeVarianceThreshold: Float,
    val startThreshold: Float,
    val stopThreshold: Float,
    val trackBuffer: Int,
    val minConfidence: Float,
    val movementThreshold: Float,
    val selectionStrategy: SelectionStrategy,
    val panSmoothingAlpha: Float = 0.2f,
    val analysisInterval: Int = 1,
    val iouThreshold: Float = 0.45f,
    val minHistoryDecision: Int = 10,
    val activityHistorySize: Int = 60
) {
    companion object {
        fun defaultFor(mode: SportMode) = when(mode) {
            SportMode.SKI -> AutoZoomConfig(
                targetHeightRatio = 0.15f,
                kp = 5.0f,
                kd = 2.5f,
                kZoom = 10.0f,
                maxZoom = 20.0f,
                rampRate = 2.0f,
                shapeVarianceThreshold = 0.002f,
                startThreshold = 0.15f,
                stopThreshold = 0.05f,
                trackBuffer = 30,
                minConfidence = 0.3f,
                movementThreshold = 0.08f,
                selectionStrategy = SelectionStrategy.SINGLE_SUBJECT,
                panSmoothingAlpha = 0.2f,
                analysisInterval = 1,
                iouThreshold = 0.45f
            )
            SportMode.BASKETBALL -> AutoZoomConfig(
                targetHeightRatio = 0.33f,
                kp = 2.0f,      
                kd = 1.0f,      
                kZoom = 8.0f,   
                maxZoom = 10.0f, 
                rampRate = 0.25f, 
                shapeVarianceThreshold = 0.002f,
                startThreshold = 0.25f,
                stopThreshold = 0.08f, 
                trackBuffer = 30,
                minConfidence = 0.3f,
                movementThreshold = 0.05f,
                selectionStrategy = SelectionStrategy.GROUP,
                panSmoothingAlpha = 0.4f,
                analysisInterval = 1,
                iouThreshold = 0.45f
            )
            SportMode.HOCKEY -> AutoZoomConfig(
                targetHeightRatio = 0.33f,
                kp = 2.0f,      
                kd = 1.0f,      
                kZoom = 8.0f,   
                maxZoom = 10.0f, 
                rampRate = 0.25f, 
                shapeVarianceThreshold = 0.002f,
                startThreshold = 0.25f,
                stopThreshold = 0.08f, 
                trackBuffer = 30,
                minConfidence = 0.3f,
                movementThreshold = 0.05f,
                selectionStrategy = SelectionStrategy.GROUP,
                panSmoothingAlpha = 0.4f,
                analysisInterval = 1,
                iouThreshold = 0.45f
            )
        }
    }
}
