package com.sportscam.presentation.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sportscam.data.models.Detection
import com.sportscam.data.models.SportMode
import com.sportscam.data.models.Track

@Composable
fun DebugOverlay(
    detections: List<Detection>,
    activePlayers: List<Track> = emptyList(),
    fps: Float,
    analysisTimeMs: Float,
    currentZoom: Float,
    isAutoZoom: Boolean,
    averageHeight: Float,
    sportMode: SportMode,
    sourceSize: android.util.Size? = null,
    onModeToggle: (SportMode) -> Unit,
    onConfigClick: () -> Unit,
    onDisableDebug: () -> Unit
) {
    val textMeasurer = rememberTextMeasurer()

    Box(modifier = Modifier.fillMaxSize()) {
        
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasW = size.width
            val canvasH = size.height

            if (sourceSize != null && sourceSize.width > 0 && sourceSize.height > 0) {
                // IMPORTANT: detections are often based on a landscape buffer (e.g. 640x480)
                // but the screen is in portrait (e.g. 1080x2400).
                // CameraX ImageAnalysis rotates the frame, so we use the VM's calculated frameSize.
                val srcW = sourceSize.width.toFloat()
                val srcH = sourceSize.height.toFloat()
                
                // Calculate FIT_CENTER scaling to map model coordinates to UI pixels
                val scaleX = canvasW / srcW
                val scaleY = canvasH / srcH
                val scale = minOf(scaleX, scaleY)
                
                val offsetX = (canvasW - (srcW * scale)) / 2f
                val offsetY = (canvasH - (srcH * scale)) / 2f
                
                val viewW = srcW * scale
                val viewH = srcH * scale

                fun mapX(normX: Float) = (normX * viewW) + offsetX
                fun mapY(normY: Float) = (normY * viewH) + offsetY

                // Draw Raw Detections (Red)
                detections.forEach { det ->
                    val rect = det.bbox
                    drawRect(
                        color = Color.Red,
                        topLeft = Offset(mapX(rect.left), mapY(rect.top)),
                        size = Size((rect.right - rect.left) * viewW, (rect.bottom - rect.top) * viewH),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }

                // Draw Active Tracks (Green)
                activePlayers.forEach { track ->
                    val rect = track.bbox
                    val left = mapX(rect.left)
                    val top = mapY(rect.top)
                    
                    drawRect(
                        color = Color.Green,
                        topLeft = Offset(left, top),
                        size = Size((rect.right - rect.left) * viewW, (rect.bottom - rect.top) * viewH),
                        style = Stroke(width = 4.dp.toPx())
                    )
                    
                    drawText(
                        textMeasurer = textMeasurer,
                        text = "ID: ${track.id}",
                        topLeft = Offset(left, top - 40),
                        style = TextStyle(color = Color.Green, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    )
                }
            }
        }

        // Info Panel
        Column(
            modifier = Modifier
                .padding(top = 80.dp)
                .align(Alignment.TopCenter)
                .background(
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(12.dp)
                .width(240.dp)
        ) {
            Text(
                text = "AUTO ZOOM: ${if (isAutoZoom) "UNLOCKED" else "LOCKED"}",
                color = if (isAutoZoom) Color.Green else Color.Red,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
            
            Text(
                text = "Zoom: %.2fx | FPS: %.1f".format(currentZoom, fps),
                color = Color.White,
                fontSize = 12.sp
            )
            Text(
                text = "Analysis: %.1fms".format(analysisTimeMs),
                color = Color.Cyan,
                fontSize = 12.sp
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Button(
                onClick = { 
                     val nextMode = when(sportMode) {
                         SportMode.BASKETBALL -> SportMode.SKI
                         SportMode.SKI -> SportMode.HOCKEY
                         SportMode.HOCKEY -> SportMode.BASKETBALL
                     }
                     onModeToggle(nextMode)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF)),
                modifier = Modifier.fillMaxWidth().height(32.dp),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text("MODE: ${sportMode.name}", fontSize = 10.sp)
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Button(
                onClick = onConfigClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                modifier = Modifier.fillMaxWidth().height(32.dp),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text("CONFIGURE", fontSize = 10.sp)
            }
        }
    }
}
