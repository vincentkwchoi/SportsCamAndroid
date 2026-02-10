package com.sportscam.domain

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ModelBenchmarkTest {

    @Test
    fun benchmarkAllModels() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val testContext = InstrumentationRegistry.getInstrumentation().context
        
        val imageNames = listOf("one_basketball_player.jpg", "raw_capture.jpg")
        
        // Stubs for diagnostic
        val models = listOf(
            ModelConfig("MediaPipe (Stubbed)", "efficientdet-tflite-lite0-detection-metadata-v1.tflite", DetectorType.MEDIAPIPE_CPU),
            ModelConfig("YOLO (Stubbed)", "yolo26n_float16.tflite", DetectorType.LITERT_YOLO)
        )

        Log.d("ModelBenchmark", "=========================================================")
        Log.d("ModelBenchmark", "STARTING BENCHMARK ON PIXEL 10 (STUBBED FOR DIAGNOSTIC)")
        Log.d("ModelBenchmark", "=========================================================")

        models.forEach { config ->
            Log.d("ModelBenchmark", "RUNNING MODEL: ${config.name}")
            
            val detector: Any = try {
                when (config.type) {
                    DetectorType.MEDIAPIPE_CPU -> MediaPipeDetector(appContext, config.modelPath)
                    DetectorType.LITERT_EFFICIENTDET -> EfficientDetLiteRTDetector(appContext, config.modelPath)
                    DetectorType.LITERT_YOLO -> YOLODetector(appContext, config.modelPath)
                }
            } catch (e: Exception) {
                Log.e("ModelBenchmark", "FAILED to init ${config.name}", e)
                return@forEach
            }

            imageNames.forEach { imageName ->
                val inputStream = testContext.assets.open(imageName)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                
                // Warm-up run
                val detections = try {
                    when (config.type) {
                        DetectorType.MEDIAPIPE_CPU -> (detector as MediaPipeDetector).detect(bitmap)
                        DetectorType.LITERT_EFFICIENTDET -> (detector as EfficientDetLiteRTDetector).detect(bitmap)
                        DetectorType.LITERT_YOLO -> (detector as YOLODetector).detect(bitmap)
                    }
                } catch (e: Exception) {
                    Log.e("ModelBenchmark", "Warm-up failed for ${config.name}", e)
                    emptyList()
                }

                Log.d("ModelBenchmark", "  [$imageName] Detected: ${detections.size} subjects")

                val iterations = 5
                val latencies = mutableListOf<Long>()
                
                repeat(iterations) {
                    val start = System.currentTimeMillis()
                    when (config.type) {
                        DetectorType.MEDIAPIPE_CPU -> (detector as MediaPipeDetector).detect(bitmap)
                        DetectorType.LITERT_EFFICIENTDET -> (detector as EfficientDetLiteRTDetector).detect(bitmap)
                        DetectorType.LITERT_YOLO -> (detector as YOLODetector).detect(bitmap)
                    }
                    latencies.add(System.currentTimeMillis() - start)
                }

                val avgLatency = latencies.average()
                Log.d("ModelBenchmark", "  [$imageName] Avg Latency: ${avgLatency}ms")
            }
            
            // Clean up
            when (detector) {
                is MediaPipeDetector -> detector.close()
                is EfficientDetLiteRTDetector -> detector.close()
                is YOLODetector -> detector.close()
            }
            Log.d("ModelBenchmark", "---------------------------------------------------------")
        }
    }

    enum class DetectorType { MEDIAPIPE_CPU, LITERT_EFFICIENTDET, LITERT_YOLO }
    data class ModelConfig(val name: String, val modelPath: String, val type: DetectorType)
}
