package com.sportscam.presentation.ui

import android.view.OrientationEventListener
import android.view.Surface
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.sportscam.presentation.viewmodels.CameraViewModel

import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf

@Composable
fun CameraScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    val viewModel = remember { CameraViewModel(context) }
    val state by viewModel.cameraState.collectAsState()

    var isCameraStarted by remember { androidx.compose.runtime.mutableStateOf(false) }
    var tapCount by remember { mutableIntStateOf(0) }
    var lastTapTime by remember { mutableLongStateOf(0L) }
    var showConfigDialog by remember { androidx.compose.runtime.mutableStateOf(false) }
    val config by viewModel.config.collectAsState()

    // Handle orientation changes for AI detection robustness
    DisposableEffect(context) {
        val orientationEventListener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                
                // Convert degrees (0-359) to Surface rotation constants
                val rotation = when (orientation) {
                    in 45 until 135 -> Surface.ROTATION_270
                    in 135 until 225 -> Surface.ROTATION_180
                    in 225 until 315 -> Surface.ROTATION_90
                    else -> Surface.ROTATION_0
                }
                // This informs the CameraX analyzer to rotate its output 
                // to match how the user is holding the phone.
                viewModel.updateCameraOrientation(rotation)
            }
        }
        orientationEventListener.enable()
        onDispose {
            orientationEventListener.disable()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    scaleType = PreviewView.ScaleType.FIT_CENTER
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { previewView ->
                if (!isCameraStarted) {
                    viewModel.startCamera(lifecycleOwner, previewView)
                    isCameraStarted = true
                }
            }
        )
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            val now = System.currentTimeMillis()
                            if (now - lastTapTime < 500) {
                                tapCount++
                            } else {
                                tapCount = 1
                            }
                            lastTapTime = now
                            
                            if (tapCount == 5) {
                                viewModel.toggleDebugOverlay()
                                tapCount = 0
                            }
                        }
                    )
                }
        )
        
        if (state.showDebug) {
            DebugOverlay(
                detections = state.detections,
                activePlayers = state.activePlayers,
                fps = state.fps,
                analysisTimeMs = state.analysisTimeMs,
                currentZoom = state.zoomState.currentZoom,
                isAutoZoom = state.zoomState.isAutoZoom,
                averageHeight = state.averageActiveHeight,
                sportMode = state.sportMode,
                sourceSize = state.sourceSize,
                onModeToggle = { viewModel.setSportMode(it) },
                onConfigClick = { showConfigDialog = true },
                onDisableDebug = { viewModel.toggleDebugOverlay() }
            )
        }
        
        if (showConfigDialog) {
            AutoZoomConfigDialog(
                config = config,
                onDismiss = { showConfigDialog = false },
                onApply = { 
                    viewModel.updateConfig(it)
                    showConfigDialog = false
                },
                onReset = {
                    viewModel.setSportMode(state.sportMode)
                }
            )
        }
        
        // UI Controls Layer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(16.dp)
        ) {
            SportModeSelector(
                currentMode = state.sportMode,
                onModeSelected = { viewModel.setSportMode(it) },
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 24.dp)
            )
            
            // Bottom Controls Row
            Row(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (state.lastVideoUri != null && !state.isRecording) {
                    androidx.compose.material3.Button(
                        onClick = { viewModel.deleteLastVideo() },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = Color.Gray.copy(alpha = 0.8f)
                        )
                    ) {
                        Text("DELETE LAST", color = Color.White)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                }

                androidx.compose.material3.Button(
                    onClick = { viewModel.toggleRecording() },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = if (state.isRecording) Color.Red else Color.White
                    )
                ) {
                    Text(
                        text = if (state.isRecording) "STOP" else "RECORD",
                        color = if (state.isRecording) Color.White else Color.Black
                    )
                }
            }
        }
    }
    
    DisposableEffect(Unit) {
        onDispose {
            viewModel.cleanup()
        }
    }
}
