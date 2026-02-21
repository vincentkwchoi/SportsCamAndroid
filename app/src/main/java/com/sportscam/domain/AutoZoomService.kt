package com.sportscam.domain

import android.util.Log
import android.util.Size
import com.sportscam.data.models.AutoZoomConfig
import com.sportscam.data.models.Detection
import com.sportscam.data.models.SportMode
import com.sportscam.data.models.SelectionStrategy
import com.sportscam.presentation.camera.ZoomHardwareActuator

class AutoZoomService(
    private val throttler: FrameThrottler = FrameThrottler(interval = 1),
    private var detector: ObjectDetector,
    private val tracker: ByteTrackTracker,
    private var selector: AthleteSelector = SingleSubjectSelector(),
    private val activityDetector: BBoxActivityDetector,
    private val targetZoomCalc: TargetZoomCalc = TargetZoomCalc(),
    private val hysteresisGate: HysteresisGate = HysteresisGate(startThreshold = 0.15f, stopThreshold = 0.05f),
    private val pidController: PIDController = PIDController(kp = 1.0f, kd = 0.0f),
    private val zoomScaling: ZoomLogScaling = ZoomLogScaling(),
    private val zoomConstraint: ZoomConstraint = ZoomConstraint(),
    private val hardwareActuator: ZoomHardwareActuator
) {
    private var currentZoom: Float = 1.0f
    private var currentSportMode: SportMode? = null

    fun processFrame(
        bitmap: android.graphics.Bitmap, 
        frameNumber: Long, 
        frameSize: Size, 
        sportMode: SportMode, 
        currentZoom: Float
    ): AutoZoomResult {
        this.currentZoom = currentZoom
        
        // Step 1: Throttling
        if (!throttler.shouldProcess(frameNumber)) {
            return AutoZoomResult()
        }

        // Step 2: Detection
        val detections = detector.detect(bitmap)

        // Step 3: Tracking
        val tracks = tracker.update(detections)

        // Filter active tracks via BBoxActivityDetector
        val activeTracks = tracks.filter { it.misses == 0 && activityDetector.isActive(it) }

        // Step 4: Selection (Using Strategy set via config)
        val target = selector.selectTarget(activeTracks)
        
        if (target == null) {
            return AutoZoomResult(
                detections = detections,
                tracks = tracks,
                activeTracks = activeTracks,
                isProcessed = true
            )
        }
        
        // Step 5: dt calculation
        val dt = (1.0f / 30.0f) * throttler.intervalMultiplier
        
        // Step 6: Target Calc
        val zoomError = targetZoomCalc.calculateError(target, currentZoom)
        val targetH = targetZoomCalc.targetSubjectHeightRatio
        val relativeError = if (targetH > 0) (zoomError / targetH) else 0f
        
        // Step 7: Hysteresis
        val shouldZoom = hysteresisGate.shouldUpdate(relativeError)
        
        if (shouldZoom) {
            // Step 8: PID Controller
            val currentTime = System.currentTimeMillis()
            val velocity = pidController.calculate(zoomError, currentTime)
            
            // Step 9: Log Scaling
            val logVelocity = zoomScaling.scale(velocity, currentZoom)
            
            // Step 10: Constraints & Integration
            var nextZoom = currentZoom + (logVelocity * dt)
            nextZoom = zoomConstraint.constrain(nextZoom, currentZoom, dt)
            
            // Step 11: Hardware Apply
            if (kotlin.math.abs(nextZoom - currentZoom) > 0.001f) {
                hardwareActuator.applyZoom(nextZoom)
                this.currentZoom = nextZoom
            }
        } else {
            pidController.reset()
        }
        
        return AutoZoomResult(
            detections = detections,
            tracks = tracks,
            activeTracks = listOf(target),
            isProcessed = true
        )
    }
    
    fun setSportMode(mode: SportMode) {
        val defaultConfig = AutoZoomConfig.defaultFor(mode)
        applyConfig(defaultConfig)
        currentSportMode = mode
    }

    fun updateDetector(newDetector: ObjectDetector) {
        detector.close()
        detector = newDetector
    }
    
    fun applyConfig(config: AutoZoomConfig) {
        // Only swap strategy if it actually changed, to avoid unnecessary resets
        val newStrategy = when (config.selectionStrategy) {
            SelectionStrategy.SINGLE_SUBJECT -> SingleSubjectSelector()
            SelectionStrategy.GROUP -> GroupSelector()
        }
        
        if (selector::class != newStrategy::class) {
            selector = newStrategy
            reset() // Full reset required when strategy architecture changes
        }

        // SEAMLESS UPDATES: No reset() called below this line
        throttler.setInterval(config.analysisInterval)
        detector.confidenceThreshold = config.minConfidence
        targetZoomCalc.targetSubjectHeightRatio = config.targetHeightRatio
        pidController.updateGains(config.kp, config.kd)
        zoomScaling.updateGain(config.kZoom)
        zoomConstraint.updateLimits(1.0f, config.maxZoom, config.rampRate)
        hardwareActuator.updateRampRate(config.rampRate)
        activityDetector.updateThreshold(config.shapeVarianceThreshold)
        hysteresisGate.updateThresholds(config.startThreshold, config.stopThreshold)
        tracker.updateConfig(config.trackBuffer, config.minConfidence)
        tracker.matchThresh = config.iouThreshold
    }
    
    fun cleanup() {
        detector.close()
        reset()
    }
    
    fun reset() {
        pidController.reset()
        hysteresisGate.reset()
        tracker.reset()
        activityDetector.reset()
        selector.reset()
    }
}
