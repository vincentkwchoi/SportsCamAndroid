package com.sportscam.presentation.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sportscam.data.models.AutoZoomConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoZoomConfigDialog(
    config: AutoZoomConfig,
    onDismiss: () -> Unit,
    onApply: (AutoZoomConfig) -> Unit,
    onReset: () -> Unit
) {
    var targetHeight by remember { mutableStateOf(config.targetHeightRatio) }
    var kp by remember { mutableStateOf(config.kp) }
    var kd by remember { mutableStateOf(config.kd) }
    var kZoom by remember { mutableStateOf(config.kZoom) }
    var maxZoom by remember { mutableStateOf(config.maxZoom) }
    var rampRate by remember { mutableStateOf(config.rampRate) }
    var trackBuffer by remember { mutableStateOf(config.trackBuffer.toFloat()) }
    var shapeVarianceThreshold by remember { mutableStateOf(config.shapeVarianceThreshold) }
    var startThreshold by remember { mutableStateOf(config.startThreshold) }
    var stopThreshold by remember { mutableStateOf(config.stopThreshold) }
    var minConfidence by remember { mutableStateOf(config.minConfidence) }
    var movementThreshold by remember { mutableStateOf(config.movementThreshold) }
    var panSmoothingAlpha by remember { mutableStateOf(config.panSmoothingAlpha) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Auto-Zoom Configuration") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Targeting", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                // Target Height
                ConfigSlider(
                    label = "Target Height Ratio: ${"%.2f".format(targetHeight)}",
                    value = targetHeight,
                    onValueChange = { targetHeight = it },
                    range = 0.05f..0.50f
                )

                Divider()
                Text("Dynamics (PID)", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                // PID Gains
                ConfigSlider(
                    label = "Kp (Speed): ${"%.1f".format(kp)}",
                    value = kp,
                    onValueChange = { kp = it },
                    range = 1f..15f
                )
                ConfigSlider(
                    label = "Kd (Damping): ${"%.1f".format(kd)}",
                    value = kd,
                    onValueChange = { kd = it },
                    range = 0.5f..10f
                )

                Divider()
                Text("Hardware & Log Scaling", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                // Scaling & Gain
                ConfigSlider(
                    label = "kZoom (Gain): ${"%.1f".format(kZoom)}",
                    value = kZoom,
                    onValueChange = { kZoom = it },
                    range = 1f..30f
                )

                // Constraints
                ConfigSlider(
                    label = "Max Zoom: ${"%.1f".format(maxZoom)}x",
                    value = maxZoom,
                    onValueChange = { maxZoom = it },
                    range = 1f..20f
                )
                ConfigSlider(
                    label = "Ramp Rate: ${"%.1f".format(rampRate)}",
                    value = rampRate,
                    onValueChange = { rampRate = it },
                    range = 0.5f..10f
                )

                Divider()
                Text("Hysteresis (Deadband)", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                ConfigSlider(
                    label = "Start Threshold: ${"%.2f".format(startThreshold)}",
                    value = startThreshold,
                    onValueChange = { startThreshold = it },
                    range = 0.01f..0.30f
                )
                ConfigSlider(
                    label = "Stop Threshold: ${"%.2f".format(stopThreshold)}",
                    value = stopThreshold,
                    onValueChange = { stopThreshold = it },
                    range = 0.01f..0.20f
                )

                Divider()
                Text("Tracking & Perception", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                // Buffer
                ConfigSlider(
                    label = "Track Persistence: ${trackBuffer.toInt()} frames",
                    value = trackBuffer,
                    onValueChange = { trackBuffer = it },
                    range = 1f..300f
                )
                ConfigSlider(
                    label = "Detection Confidence: ${"%.2f".format(minConfidence)}",
                    value = minConfidence,
                    onValueChange = { minConfidence = it },
                    range = 0.1f..0.9f
                )
                ConfigSlider(
                    label = "Activity Threshold: ${"%.4f".format(shapeVarianceThreshold)}",
                    value = shapeVarianceThreshold,
                    onValueChange = { shapeVarianceThreshold = it },
                    range = 0.0001f..0.01f
                )
                ConfigSlider(
                    label = "Movement Threshold: ${"%.2f".format(movementThreshold)}",
                    value = movementThreshold,
                    onValueChange = { movementThreshold = it },
                    range = 0.01f..0.20f
                )

                Divider()
                Text("Smoothing", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                ConfigSlider(
                    label = "Pan Smoothing Alpha: ${"%.2f".format(panSmoothingAlpha)}",
                    value = panSmoothingAlpha,
                    onValueChange = { panSmoothingAlpha = it },
                    range = 0.01f..1.0f
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onApply(config.copy(
                    targetHeightRatio = targetHeight,
                    kp = kp,
                    kd = kd,
                    kZoom = kZoom,
                    maxZoom = maxZoom,
                    rampRate = rampRate,
                    trackBuffer = trackBuffer.toInt(),
                    shapeVarianceThreshold = shapeVarianceThreshold,
                    startThreshold = startThreshold,
                    stopThreshold = stopThreshold,
                    minConfidence = minConfidence,
                    movementThreshold = movementThreshold,
                    panSmoothingAlpha = panSmoothingAlpha
                ))
            }) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onReset) {
                Text("Reset Defaults")
            }
        }
    )
}

@Composable
private fun ConfigSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>
) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelMedium)
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
