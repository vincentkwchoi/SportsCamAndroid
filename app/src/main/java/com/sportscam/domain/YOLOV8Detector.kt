package com.sportscam.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel
import com.google.ai.edge.litert.TensorBuffer
import com.sportscam.data.models.Detection
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.util.ArrayList

/**
 * Detector for YOLOv8 models exported to TFLite (LiteRT).
 * Optimized for Roboflow basketball player detection.
 */
class YOLOV8Detector(
    private val context: Context,
    private val modelPath: String,
    private val inputSize: Int = 640,
    private val numClasses: Int = 1,
    private val targetClassId: Int = 0
) : ObjectDetector {
    private var compiledModel: CompiledModel? = null
    private var imageProcessor: ImageProcessor? = null
    
    private var inputBuffers: List<TensorBuffer>? = null
    private var outputBuffers: List<TensorBuffer>? = null
    
    override var confidenceThreshold: Float = 0.3f
    var iouThreshold: Float = 0.45f
    
    init {
        setupLiteRT()
    }

    private fun setupLiteRT() {
        try {
            Log.d("AutoZoom", "YOLOV8Detector: Initializing for $modelPath...")
            val options = CompiledModel.Options(Accelerator.NPU)
            compiledModel = CompiledModel.create(context.assets, modelPath, options)
            inputBuffers = compiledModel?.createInputBuffers()
            outputBuffers = compiledModel?.createOutputBuffers()
            
            imageProcessor = ImageProcessor.Builder()
                .add(ResizeOp(inputSize, inputSize, ResizeOp.ResizeMethod.BILINEAR))
                .add(NormalizeOp(0f, 255f))
                .build()
                
            val outputShape = outputBuffers?.get(0)?.shape ?: intArrayOf()
            Log.d("AutoZoom", "YOLOV8Detector: Initialized. Output shape: ${outputShape.joinToString()}")
        } catch (e: Exception) {
            Log.e("AutoZoom", "YOLOV8Detector: Initialization failed.", e)
        }
    }

    override fun detect(bitmap: Bitmap): List<Detection> {
        val model = compiledModel ?: return emptyList()
        val inputs = inputBuffers ?: return emptyList()
        val outputs = outputBuffers ?: return emptyList()
        
        var tensorImage = TensorImage(org.tensorflow.lite.DataType.FLOAT32)
        tensorImage.load(bitmap)
        tensorImage = imageProcessor?.process(tensorImage) ?: return emptyList()
        
        val inputBuffer = inputs[0]
        val tensorFloatBuffer = tensorImage.buffer.asFloatBuffer()
        val inputArray = FloatArray(tensorFloatBuffer.remaining())
        tensorFloatBuffer.get(inputArray)
        inputBuffer.writeFloat(inputArray)
        
        model.run(inputs, outputs)
        
        val outputData = outputs[0].readFloat()
        val outputShape = outputs[0].shape
        
        // YOLOv8 output is typically [1, 4 + numClasses, 8400]
        // But some exports might be [1, 8400, 4 + numClasses]
        val isChannelsFirst = outputShape[1] == (4 + numClasses)
        val numBoxes = if (isChannelsFirst) outputShape[2] else outputShape[1]
        
        val candidates = ArrayList<Detection>()
        
        for (i in 0 until numBoxes) {
            val cx: Float
            val cy: Float
            val w: Float
            val h: Float
            val conf: Float
            
            if (isChannelsFirst) {
                cx = outputData[i]
                cy = outputData[i + numBoxes]
                w = outputData[i + numBoxes * 2]
                h = outputData[i + numBoxes * 3]
                conf = outputData[i + numBoxes * (4 + targetClassId)]
            } else {
                val offset = i * (4 + numClasses)
                cx = outputData[offset]
                cy = outputData[offset + 1]
                w = outputData[offset + 2]
                h = outputData[offset + 3]
                conf = outputData[offset + 4 + targetClassId]
            }
            
            if (conf > confidenceThreshold) {
                val left = (cx - w / 2f) / inputSize
                val top = (cy - h / 2f) / inputSize
                val right = (cx + w / 2f) / inputSize
                val bottom = (cy + h / 2f) / inputSize
                
                candidates.add(
                    Detection(
                        bbox = RectF(left, top, right, bottom),
                        confidence = conf,
                        classId = targetClassId
                    )
                )
            }
        }
        
        return applyNMS(candidates)
    }

    private fun applyNMS(detections: List<Detection>): List<Detection> {
        if (detections.isEmpty()) return emptyList()
        val sorted = detections.sortedByDescending { it.confidence }
        val result = ArrayList<Detection>()
        val skipped = BooleanArray(sorted.size)
        
        for (i in sorted.indices) {
            if (skipped[i]) continue
            val d1 = sorted[i]
            result.add(d1)
            for (j in i + 1 until sorted.size) {
                if (!skipped[j] && boxIou(d1.bbox, sorted[j].bbox) > iouThreshold) {
                    skipped[j] = true
                }
            }
        }
        return result
    }

    private fun boxIou(a: RectF, b: RectF): Float {
        val interL = maxOf(a.left, b.left)
        val interT = maxOf(a.top, b.top)
        val interR = minOf(a.right, b.right)
        val interB = minOf(a.bottom, b.bottom)
        if (interR <= interL || interB <= interT) return 0f
        val interArea = (interR - interL) * (interB - interT)
        val unionArea = (a.width() * a.height()) + (b.width() * b.height()) - interArea
        return interArea / unionArea
    }

    override fun close() {
        compiledModel?.close()
    }
}
