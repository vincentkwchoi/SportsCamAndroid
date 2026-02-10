package com.sportscam.presentation.viewmodels

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Size
import com.sportscam.data.models.CameraState
import com.sportscam.data.models.SportMode
import com.sportscam.domain.AutoZoomService
import com.sportscam.domain.BBoxActivityDetector
import com.sportscam.domain.AutoZoomResult
import com.sportscam.domain.ByteTrackTracker
import com.sportscam.domain.YOLODetector
import com.sportscam.domain.EfficientDetLiteRTDetector
import com.sportscam.data.models.AutoZoomConfig
import com.sportscam.data.models.Detection
import com.sportscam.presentation.camera.CameraManager
import com.sportscam.presentation.camera.ZoomHardwareActuator
import com.sportscam.domain.DebugImageSaver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CameraViewModel(
    private val context: Context // Ideally injected
) : ViewModel() {

    private val cameraManager = CameraManager(context)
    
    // Auto-Zoom Components - Restored for full functionality
    private val hardwareActuator = ZoomHardwareActuator(cameraManager)
    private val autoZoomService = AutoZoomService(
        detector = EfficientDetLiteRTDetector(context),
        tracker = ByteTrackTracker(),
        activityDetector = BBoxActivityDetector(),
        hardwareActuator = hardwareActuator
    )
    
    // State
    // Debug Utilities
    private val debugImageSaver = DebugImageSaver(context)

    private val _cameraState = MutableStateFlow(CameraState())
    val cameraState: StateFlow<CameraState> = _cameraState.asStateFlow()

    private val _config = MutableStateFlow(AutoZoomConfig.defaultFor(SportMode.SKI))
    val config: StateFlow<AutoZoomConfig> = _config.asStateFlow()

    private var frameCounter = 0L
    private var lastAnalysisTime = 0L
    private var lastDebugSaveTime = 0L
    var isAnalyzerActive = true
    
    init {
        // Sync service with initial mode (updates internal tracker/detector/etc)
        autoZoomService.setSportMode(_cameraState.value.sportMode)
    }
    
    fun startCamera(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        cameraManager.setOnZoomStateChangedListener { zoomRatio, _ ->
            _cameraState.update { 
                it.copy(zoomState = it.zoomState.copy(currentZoom = zoomRatio))
            }
        }
        cameraManager.startCamera(lifecycleOwner, previewView, analyzer)
    }

    private val analyzer = ImageAnalysis.Analyzer { imageProxy ->
        if (!isAnalyzerActive) {
            imageProxy.close()
            return@Analyzer
        }

        // Calculation of analysis FPS
        val currentTime = System.currentTimeMillis()
        if (lastAnalysisTime > 0) {
            val fps = 1000f / (currentTime - lastAnalysisTime)
            _cameraState.update { it.copy(fps = fps) }
        }
        lastAnalysisTime = currentTime

        processFrame(imageProxy)
        frameCounter++
    }

    private fun processFrame(imageProxy: ImageProxy) {
        val crop = imageProxy.cropRect
        Log.d("AutoZoom", "ImageProxy: Resolution=${imageProxy.width}x${imageProxy.height}, CropRect=[${crop.left}, ${crop.top}, ${crop.right}, ${crop.bottom}]")
        val startTime = System.currentTimeMillis()
        try {
            val bitmap = imageProxy.toBitmap()
            // Rotate Bitmap to match display orientation
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            val processedBitmap = if (rotationDegrees != 0) {
                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            } else {
                bitmap
            }

            // Update frame size for coordinates (swapping W/H if rotated 90/270)
            val frameSize = if (rotationDegrees % 180 != 0) {
                Size(imageProxy.height, imageProxy.width)
            } else {
                Size(imageProxy.width, imageProxy.height)
            }
            
            // Orchestrate full pipeline via AutoZoomService
            val result = autoZoomService.processFrame(
                bitmap = processedBitmap,
                frameNumber = frameCounter,
                frameSize = frameSize,
                sportMode = _cameraState.value.sportMode,
                currentZoom = _cameraState.value.zoomState.currentZoom
            )
            
            // Skip state update if frame was throttled (stale data remains in UI)
            if (!result.isProcessed) {
                return
            }

            // Periodic Visual Debug Logging (Once per second)
            val currentTimeMs = System.currentTimeMillis()
            if (result.isProcessed && (currentTimeMs - lastDebugSaveTime) >= 1000L) {
                Log.d("AutoZoom", "Triggering debug frame save for frame $frameCounter (Time elapsed: ${currentTimeMs - lastDebugSaveTime}ms)")
                lastDebugSaveTime = currentTimeMs
                debugImageSaver.saveFrame(
                    bitmap = processedBitmap,
                    detections = result.detections,
                    tracks = result.tracks,
                    activeTracks = result.activeTracks,
                    frameNumber = frameCounter
                )
            }

            // Calculate Metrics
            val executionTime = System.currentTimeMillis() - startTime
            val avgHeight = if (result.activeTracks.isNotEmpty()) {
                result.activeTracks.map { it.bbox.height() }.average().toFloat()
            } else {
                0f
            }
            
            // Update State for UI Overlay
            _cameraState.update { 
                it.copy(
                    detections = result.detections,
                    activePlayers = result.activeTracks,
                    analysisTimeMs = executionTime.toFloat(),
                    averageActiveHeight = avgHeight,
                    sourceSize = frameSize
                ) 
            }

        } catch (e: Exception) {
            Log.e("CameraViewModel", "Error analyzing frame", e)
        } finally {
            imageProxy.close()
        }
    }
    
    fun setSportMode(mode: SportMode) {
        _cameraState.update { it.copy(sportMode = mode) }
        val newConfig = AutoZoomConfig.defaultFor(mode)
        _config.value = newConfig
        autoZoomService.setSportMode(mode)
    }

    fun updateConfig(newConfig: AutoZoomConfig) {
        _config.value = newConfig
        autoZoomService.applyConfig(newConfig)
    }
    
    fun toggleDebugOverlay() {
        _cameraState.update { it.copy(showDebug = !it.showDebug) }
    }

    fun toggleRecording() {
        val isRecording = _cameraState.value.isRecording
        if (isRecording) {
            cameraManager.stopRecording()
            _cameraState.update { it.copy(isRecording = false) }
        } else {
            cameraManager.startRecording(
                onVideoSaved = { uri ->
                    Log.d("CameraViewModel", "Video saved: $uri")
                    // Could show toast or preview
                },
                onError = { error ->
                    Log.e("CameraViewModel", error)
                     _cameraState.update { it.copy(isRecording = false) }
                }
            )
            _cameraState.update { it.copy(isRecording = true) }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        cleanup()
    }
    
    fun cleanup() {
        cameraManager.stopCamera()
        autoZoomService.cleanup()
    }
}
