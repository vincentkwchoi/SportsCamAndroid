package com.sportscam.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel
import com.google.ai.edge.litert.TensorBuffer
import com.sportscam.data.models.Detection
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.util.ArrayList

class EfficientDetLiteRTDetector(
    private val context: Context,
    private val modelPath: String = "efficientdet-tflite-lite0-int8-v1.tflite"
) : ObjectDetector {
    private var compiledModel: CompiledModel? = null
    private var imageProcessor: ImageProcessor? = null
    
    private var inputBuffers: List<TensorBuffer>? = null
    private var outputBuffers: List<TensorBuffer>? = null
    
    // Configurable threshold
    override var confidenceThreshold: Float = 0.3f
    
    init {
        setupLiteRT()
    }

    private fun setupLiteRT() {
        try {
            Log.d("AutoZoom", "EfficientDetLiteRT: Initializing for $modelPath...")
            val options = CompiledModel.Options(Accelerator.NPU)
            compiledModel = CompiledModel.create(context.assets, modelPath, options)
            inputBuffers = compiledModel?.createInputBuffers()
            outputBuffers = compiledModel?.createOutputBuffers()
            
            imageProcessor = ImageProcessor.Builder()
                .add(ResizeOp(320, 320, ResizeOp.ResizeMethod.BILINEAR))
                .build()
                
            Log.d("AutoZoom", "EfficientDetLiteRT: Initialized successfully.")
        } catch (e: Exception) {
            Log.e("AutoZoom", "EfficientDetLiteRT: Initialization failed.", e)
        }
    }

    override fun detect(bitmap: Bitmap): List<Detection> {
        val model = compiledModel ?: return emptyList()
        val inputs = inputBuffers ?: return emptyList()
        val outputs = outputBuffers ?: return emptyList()
        
        var tensorImage = TensorImage(org.tensorflow.lite.DataType.UINT8)
        tensorImage.load(bitmap)
        tensorImage = imageProcessor?.process(tensorImage) ?: return emptyList()
        
        val inputBuffer = inputs[0]
        val buffer = tensorImage.buffer
        val inputArray = ByteArray(buffer.remaining())
        buffer.get(inputArray)
        inputBuffer.writeInt8(inputArray)
        
        model.run(inputs, outputs)
        
        val boxes = outputs[0].readFloat() 
        val classes = outputs[1].readFloat()
        val scores = outputs[2].readFloat()
        val count = outputs[3].readFloat()[0].toInt()
        
        val detections = ArrayList<Detection>()
        for (i in 0 until count) {
            val score = scores[i]
            val classId = classes[i].toInt()
            
            // Use the dynamic threshold
            if (score > confidenceThreshold && classId == 0) {
                detections.add(
                    Detection(
                        bbox = RectF(boxes[i * 4 + 1], boxes[i * 4 + 0], boxes[i * 4 + 3], boxes[i * 4 + 2]),
                        confidence = score,
                        classId = classId
                    )
                )
            }
        }
        return detections
    }

    override fun close() {
        compiledModel?.close()
    }
}
