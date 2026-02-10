package com.sportscam.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Environment
import android.util.Log
import com.sportscam.data.models.Detection
import com.sportscam.data.models.Track
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Utility to save image frames with bounding boxes drawn on them for debugging.
 */
class DebugImageSaver(private val context: Context) {

    fun saveFrame(
        bitmap: Bitmap,
        detections: List<Detection>,
        tracks: List<Track>,
        activeTracks: List<Track>,
        frameNumber: Long
    ) {
        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        
        // Save raw clean image for debugging
        saveToDisk(bitmap, frameNumber, isRaw = true)
        
        val canvas = Canvas(mutableBitmap)
        val w = mutableBitmap.width.toFloat()
        val h = mutableBitmap.height.toFloat()

        // Paints
        val redPaint = Paint().apply {
            color = Color.RED
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        val greenPaint = Paint().apply {
            color = Color.GREEN
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }
        val textPaint = Paint().apply {
            color = Color.YELLOW
            textSize = 24f
            isFakeBoldText = true
        }

        // Draw Raw Detections (Red)
        detections.forEach { det ->
            val rect = det.bbox
            canvas.drawRect(
                rect.left * w, rect.top * h,
                rect.right * w, rect.bottom * h,
                redPaint
            )
        }

        // Draw Tracks (Green if active/confirmed)
        tracks.forEach { track ->
            val rect = track.bbox
            canvas.drawRect(
                rect.left * w, rect.top * h,
                rect.right * w, rect.bottom * h,
                greenPaint
            )
            canvas.drawText(
                "ID:${track.id}",
                rect.left * w,
                (rect.top * h) - 5f,
                textPaint
            )
        }

        saveToDisk(mutableBitmap, frameNumber)
    }

    private fun saveToDisk(bitmap: Bitmap, frameNumber: Long, isRaw: Boolean = false) {
        val timestamp = SimpleDateFormat("HHmmss", Locale.getDefault()).format(Date())
        val suffix = if (isRaw) "_RAW" else "_ANNOTATED"
        val fileName = "Debug_F${frameNumber}_$timestamp$suffix.jpg"
        
        Log.d("AutoZoom", "Attempting to save debug frame $frameNumber to disk...")
        try {
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "SportsCam_Debug")
            if (!dir.exists()) {
                val created = dir.mkdirs()
                Log.d("AutoZoom", "Created debug directory: $created at ${dir.absolutePath}")
            }
            
            val file = File(dir, fileName)
            FileOutputStream(file).use { out ->
                val compressed = bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                Log.d("AutoZoom", "Compression successful: $compressed")
            }
            Log.d("AutoZoom", "SUCCESS: Saved debug frame to: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e("AutoZoom", "ERROR: Failed to save debug frame", e)
        }
    }
}
