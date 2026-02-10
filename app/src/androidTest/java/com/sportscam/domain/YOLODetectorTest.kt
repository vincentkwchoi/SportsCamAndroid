package com.sportscam.domain

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Environment
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class YOLODetectorTest {

    @Test
    fun testMultiImageDetection() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val detector = YOLODetector(appContext)
        val imageNames = listOf("one_basketball_player.jpg", "raw_capture.jpg")

        imageNames.forEach { imageName ->
            Log.d("YOLODetectorTest", "-------------------------------------------")
            Log.d("YOLODetectorTest", "TESTING IMAGE: $imageName")
            
            // 1. Load test image from assets
            val testContext = InstrumentationRegistry.getInstrumentation().context
            val assetManager = testContext.assets
            val inputStream = assetManager.open(imageName)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            assertNotNull("$imageName should not be null", bitmap)

            // 2. Run detection
            Log.d("YOLODetectorTest", "Starting detection on ${bitmap.width}x${bitmap.height} image")
            val detections = detector.detect(bitmap)
            
            // 3. Log results
            Log.d("YOLODetectorTest", "Found ${detections.size} detections for $imageName")
            detections.forEachIndexed { _, det ->
                Log.d("YOLODetectorTest", String.format(
                    "[RAW_DETECTION][%s] class:%d, conf:%.4f, L:%.4f, T:%.4f, R:%.4f, B:%.4f",
                    imageName, det.classId, det.confidence, det.bbox.left, det.bbox.top, det.bbox.right, det.bbox.bottom
                ))
            }

            // 4. Save annotated image
            val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(mutableBitmap)
            val paint = Paint().apply {
                color = Color.RED
                style = Paint.Style.STROKE
                strokeWidth = 5f
            }
            val textPaint = Paint().apply {
                color = Color.RED
                textSize = 40f
                isFakeBoldText = true
            }

            detections.forEach { det ->
                val rect = det.bbox
                canvas.drawRect(
                    rect.left * mutableBitmap.width,
                    rect.top * mutableBitmap.height,
                    rect.right * mutableBitmap.width,
                    rect.bottom * mutableBitmap.height,
                    paint
                )
                canvas.drawText(
                    "ID:${det.classId} ${String.format("%.2f", det.confidence)}",
                    rect.left * mutableBitmap.width,
                    (rect.top * mutableBitmap.height) - 10f,
                    textPaint
                )
            }
            saveBitmapToDisk(mutableBitmap, "${imageName}_detected.jpg")
        }
    }

    private fun saveBitmapToDisk(bitmap: Bitmap, fileName: String) {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(appContext.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "SportsCam_Test")
        if (!dir.exists()) dir.mkdirs()
        
        val file = File(dir, fileName)
        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            Log.d("YOLODetectorTest", "Saved test result to: ${file.absolutePath}")
            println("Saved test result to: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e("YOLODetectorTest", "Failed to save test image", e)
        }
    }
}
