package com.sportscam.data.models

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
    val trackBuffer: Int
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
                trackBuffer = 30
            )
            SportMode.BASKETBALL -> AutoZoomConfig(
                targetHeightRatio = 0.33f,
                kp = 6.0f,
                kd = 3.0f,
                kZoom = 15.0f,
                maxZoom = 5.0f,
                rampRate = 4.0f,
                shapeVarianceThreshold = 0.002f,
                startThreshold = 0.10f,
                stopThreshold = 0.05f,
                trackBuffer = 150
            )
            SportMode.HOCKEY -> AutoZoomConfig(
                targetHeightRatio = 0.33f,
                kp = 6.5f,
                kd = 3.2f,
                kZoom = 12.0f,
                maxZoom = 5.0f,
                rampRate = 3.5f,
                shapeVarianceThreshold = 0.002f,
                startThreshold = 0.10f,
                stopThreshold = 0.05f,
                trackBuffer = 100
            )
        }
    }
}
