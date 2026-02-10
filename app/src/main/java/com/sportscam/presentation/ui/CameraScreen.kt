package com.sportscam.presentation.ui

import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
                sourceSize = state.sourceSize, // CRITICAL for correct mapping
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
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            SportModeSelector(
                currentMode = state.sportMode,
                onModeSelected = { viewModel.setSportMode(it) },
                modifier = Modifier.align(Alignment.TopCenter)
            )
            
            androidx.compose.material3.Button(
                onClick = { viewModel.toggleRecording() },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 30.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = if (state.isRecording) androidx.compose.ui.graphics.Color.Red else androidx.compose.ui.graphics.Color.White
                )
            ) {
                Text(
                    text = if (state.isRecording) "STOP" else "RECORD",
                    color = if (state.isRecording) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color.Black
                )
            }
        }
    }
    
    DisposableEffect(Unit) {
        onDispose {
            viewModel.cleanup()
        }
    }
}
