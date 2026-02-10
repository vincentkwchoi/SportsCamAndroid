package com.sportscam.domain

import android.util.Log
import android.util.Size
import com.sportscam.data.models.AutoZoomConfig
import com.sportscam.data.models.Detection
import com.sportscam.data.models.SportMode
import com.sportscam.presentation.camera.ZoomHardwareActuator

class AutoZoomService(
    private val throttler: FrameThrottler = FrameThrottler(interval = 1), // Step 1
    private val detector: EfficientDetLiteRTDetector, // Step 2 (Benchmark Winner)
    private val tracker: ByteTrackTracker, // Step 3
    private val selector: AthleteSelection = AthleteSelection(), // Step 4
    private val activityDetector: BBoxActivityDetector, // Step 5
    private val targetZoomCalc: TargetZoomCalc = TargetZoomCalc(), // Step 6
    private val hysteresisGate: HysteresisGate = HysteresisGate(), // Step 7
    private val pidController: PIDController = PIDController(), // Step 8
    private val zoomScaling: ZoomLogScaling = ZoomLogScaling(), // Step 9
    private val zoomConstraint: ZoomConstraint = ZoomConstraint(), // Step 10
    private val hardwareActuator: ZoomHardwareActuator // Step 11
) {
    private var currentZoom: Float = 1.0f

    /**
     * Consolidates the full 11-step pipeline from raw image to camera hardware actuation.
     */
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
        Log.d("AutoZoom", "Step 2: Detection found ${detections.size} subjects")

        // Step 3: Tracking
        val tracks = tracker.update(detections)

        // Filter active tracks via BBoxActivityDetector
        val activeTracks = tracks.filter { it.misses == 0 && activityDetector.isActive(it) }
        Log.d("AutoZoom", "Step 3: Tracking - Total=${tracks.size}, Active=${activeTracks.size}")

        // Step 4: Selection (Single Target)
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
    }
    
    fun applyConfig(config: AutoZoomConfig) {
        targetZoomCalc.targetSubjectHeightRatio = config.targetHeightRatio
        pidController.updateGains(config.kp, config.kd)
        zoomScaling.updateGain(config.kZoom)
        zoomConstraint.updateLimits(1.0f, config.maxZoom, config.rampRate)
        hardwareActuator.updateRampRate(config.rampRate)
        activityDetector.updateThreshold(config.shapeVarianceThreshold)
        hysteresisGate.updateThresholds(config.startThreshold, config.stopThreshold)
        tracker.updateConfig(config.trackBuffer, 0.3f)
        reset()
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
    }
}
