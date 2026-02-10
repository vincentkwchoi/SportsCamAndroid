package com.sportscam.presentation.camera

import android.content.Context
import android.os.Environment
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraManager(
    private val context: Context
) {
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    fun startCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        analyzer: ImageAnalysis.Analyzer
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, analyzer)
                }

            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build()

            Log.d("AutoZoom", "Camera Selector: BACK_CAMERA requested")

            try {
                // Video Capture
                val recorder = Recorder.Builder()
                    .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
                    .build()
                videoCapture = VideoCapture.withOutput(recorder)

                cameraProvider?.unbindAll()
                camera = cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis,
                    videoCapture
                )

                // Observe Zoom State
                camera?.cameraInfo?.zoomState?.observe(lifecycleOwner) { zoomState ->
                    onZoomStateChanged?.invoke(zoomState.zoomRatio, zoomState.linearZoom)
                }

            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(context))
    }

    private var onZoomStateChanged: ((Float, Float) -> Unit)? = null

    fun setOnZoomStateChangedListener(listener: (Float, Float) -> Unit) {
        onZoomStateChanged = listener
    }

    fun startRecording(onVideoSaved: (String) -> Unit, onError: (String) -> Unit) {
        val videoCapture = this.videoCapture ?: return

        recording = videoCapture.output
            .prepareRecording(context, FileOutputOptions.Builder(createVideoFile()).build())
            .withAudioEnabled()
            .start(ContextCompat.getMainExecutor(context)) { recordEvent ->
                when(recordEvent) {
                    is VideoRecordEvent.Start -> {
                        // Recording started
                    }
                    is VideoRecordEvent.Finalize -> {
                        if (!recordEvent.hasError()) {
                            onVideoSaved(recordEvent.outputResults.outputUri.toString())
                        } else {
                            recording?.close()
                            recording = null
                            onError("Video capture failed: ${recordEvent.error}")
                        }
                    }
                }
            }
    }

    fun stopRecording() {
        recording?.stop()
        recording = null
    }

    private fun createVideoFile(): File {
        val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        val appDir = File(moviesDir, "SportsCam")
        if (!appDir.exists()) appDir.mkdirs()
        return File(appDir, "SportsCam_${System.currentTimeMillis()}.mp4")
    }

    fun setZoom(zoomRatio: Float) {
        camera?.cameraControl?.setZoomRatio(zoomRatio)
    }

    fun setLinearZoom(linearZoom: Float) {
        camera?.cameraControl?.setLinearZoom(linearZoom)
    }

    fun stopCamera() {
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
        stopRecording()
    }

    companion object {
        private const val TAG = "CameraManager"
    }
}
