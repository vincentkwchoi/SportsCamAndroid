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
import com.sportscam.domain.EfficientDetLiteRTDetector
import com.sportscam.domain.YOLOV8Detector
import com.sportscam.domain.ObjectDetector
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
    
    // Auto-Zoom Components
    private val hardwareActuator = ZoomHardwareActuator(cameraManager)
    
    // Start with EfficientDet for general use
    private val initialDetector: ObjectDetector = EfficientDetLiteRTDetector(context)
    
    private val autoZoomService = AutoZoomService(
        detector = initialDetector,
        tracker = ByteTrackTracker(),
        activityDetector = BBoxActivityDetector(),
        hardwareActuator = hardwareActuator
    )
    
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
        // Sync service with initial mode
        setSportMode(_cameraState.value.sportMode)
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
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val startTime = System.currentTimeMillis()
        try {
            val bitmap = imageProxy.toBitmap()
            
            val processedBitmap = if (rotationDegrees != 0) {
                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            } else {
                bitmap
            }

            val frameSize = if (rotationDegrees % 180 != 0) {
                Size(imageProxy.height, imageProxy.width)
            } else {
                Size(imageProxy.width, imageProxy.height)
            }
            
            val result = autoZoomService.processFrame(
                bitmap = processedBitmap,
                frameNumber = frameCounter,
                frameSize = frameSize,
                sportMode = _cameraState.value.sportMode,
                currentZoom = _cameraState.value.zoomState.currentZoom
            )
            
            if (!result.isProcessed) {
                return
            }

            val currentTimeMs = System.currentTimeMillis()
            if (result.isProcessed && (currentTimeMs - lastDebugSaveTime) >= 1000L) {
                lastDebugSaveTime = currentTimeMs
                debugImageSaver.saveFrame(
                    bitmap = processedBitmap,
                    detections = result.detections,
                    tracks = result.tracks,
                    activeTracks = result.activeTracks,
                    frameNumber = frameCounter
                )
            }

            val executionTime = System.currentTimeMillis() - startTime
            val avgHeight = if (result.activeTracks.isNotEmpty()) {
                result.activeTracks.map { it.bbox.height() }.average().toFloat()
            } else {
                0f
            }
            
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
        
        // Switch detector if needed for specialized sports
        val newDetector: ObjectDetector = when (mode) {
            SportMode.BASKETBALL -> {
                // Specialized Roboflow Basketball Player Detection Model
                YOLOV8Detector(context, "basketball_player_yolov8.tflite", numClasses = 1)
            }
            else -> {
                // Standard person detection for other sports
                EfficientDetLiteRTDetector(context)
            }
        }
        
        autoZoomService.updateDetector(newDetector)

        val newConfig = AutoZoomConfig.defaultFor(mode)
        _config.value = newConfig
        autoZoomService.setSportMode(mode)
    }

    fun updateConfig(newConfig: AutoZoomConfig) {
        _config.value = newConfig
        autoZoomService.applyConfig(newConfig)
    }

    fun updateCameraOrientation(rotation: Int) {
        cameraManager.updateAnalyzerRotation(rotation)
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
                    _cameraState.update { it.copy(lastVideoUri = uri) }
                },
                onError = { error ->
                    Log.e("CameraViewModel", error)
                     _cameraState.update { it.copy(isRecording = false) }
                }
            )
            _cameraState.update { it.copy(isRecording = true) }
        }
    }

    fun deleteLastVideo() {
        val uri = _cameraState.value.lastVideoUri ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val success = cameraManager.deleteVideo(uri)
            if (success) {
                _cameraState.update { it.copy(lastVideoUri = null) }
                Log.d("CameraViewModel", "Last video deleted successfully")
            }
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
