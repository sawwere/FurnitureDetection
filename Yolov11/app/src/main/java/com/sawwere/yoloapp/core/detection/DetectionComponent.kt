package com.sawwere.yoloapp.core.detection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.os.SystemClock
import android.util.Log
import com.sawwere.yoloapp.core.detection.MetaData.extractNamesFromLabelFile
import com.sawwere.yoloapp.core.detection.MetaData.extractNamesFromMetadata
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.CastOp
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import java.nio.ByteBuffer
import kotlin.math.min

class DetectionComponent(
    context: Context,
    modelPath: String,
    labelPath: String?,
    private val instanceSegmentationListener: InstanceSegmentationListener,
    message: (String) -> Unit
) {
    private var interpreter: Interpreter
    private var labels = mutableListOf<String>()

    private var tensorWidth = 0
    private var tensorHeight = 0
    private var numChannel = 0
    private var numElements = 0
    private var xPoints = 0
    private var yPoints = 0
    private var masksNum = 0

    private val imageProcessor = ImageProcessor.Builder()
        .add(NormalizeOp(INPUT_MEAN, INPUT_STANDARD_DEVIATION))
        .add(CastOp(INPUT_IMAGE_TYPE))
        .build()

    init {
        val options = Interpreter.Options()
        options.setNumThreads(4)

        val model = FileUtil.loadMappedFile(context, modelPath)
        interpreter = Interpreter(model, options)

        labels.addAll(extractNamesFromMetadata(model))
        labels = MetaData.extractNamesFromLabelFile(context, labelPath ?: "temp_meta.txt").toMutableList()
        if (labels.isEmpty()) {
            if (labelPath == null) {
                message("Model not contains metadata, provide LABELS_PATH in Constants.kt")
                labels.addAll(MetaData.TEMP_CLASSES)
            } else {
                labels.addAll(extractNamesFromLabelFile(context, labelPath))
            }
        }

        val inputShape = interpreter.getInputTensor(0)?.shape()
        val outputShape0 = interpreter.getOutputTensor(0)?.shape()
        val inputType = interpreter.getInputTensor(0).dataType()
        val outputType = interpreter.getOutputTensor(0).dataType()

        when (inputType) {
            DataType.FLOAT32 -> Log.d("Model", "Using FP32 input")
            DataType.UINT8 -> Log.d("Model", "Using UINT8 quantized input")
            else -> Log.e("Model", "Unsupported input type: $inputType")
        }
        when (outputType) {
            DataType.FLOAT32 -> Log.d("Model", "Using FP32 output")
            DataType.UINT8 -> Log.d("Model", "Using UINT8 quantized output")
            else -> Log.e("Model", "Unsupported output type: $inputType")
        }

        if (inputShape != null) {
            tensorWidth = inputShape[1]
            tensorHeight = inputShape[2]

            // If in case input shape is in format of [1, 3, ..., ...]
            if (inputShape[1] == 3) {
                tensorWidth = inputShape[2]
                tensorHeight = inputShape[3]
            }
        }

        if (outputShape0 != null) {
            numChannel = outputShape0[1]
            numElements = outputShape0[2]
//            xPoints = outputShape0[1]
//            yPoints = outputShape0[2]
//            masksNum = outputShape0[3]
        }


//
//        if (outputShape1 != null) {
//            if (outputShape1[1] == 32) {
//                masksNum = outputShape1[1]
//                xPoints = outputShape1[2]
//                yPoints = outputShape1[3]
//            } else {
//
//            }
//        }
    }

    fun close() {
        interpreter.close()
    }

    fun invoke(frame: Bitmap) {

        var preProcessTime = SystemClock.uptimeMillis()

        val imageBuffer = preProcess(frame)
//
        preProcessTime = SystemClock.uptimeMillis() - preProcessTime
//
        var interfaceTime = SystemClock.uptimeMillis()

        val outputBuffer = HashMap<Int, Any>()
        outputBuffer[0] = Array(1) { Array(300) { FloatArray(6) } }

        interpreter.runForMultipleInputsOutputs(imageBuffer, outputBuffer)
//
        interfaceTime = SystemClock.uptimeMillis() - interfaceTime
//
        var postProcessTime = SystemClock.uptimeMillis()

        val segmentationResults = processOutput(
            imageWidth = frame.width,
            imageHeight = frame.height,
            output = outputBuffer[0] as Array<Array<FloatArray>>
        )

        postProcessTime = SystemClock.uptimeMillis() - postProcessTime

        instanceSegmentationListener.onDetect(
            preProcessTime = preProcessTime,
            interfaceTime = interfaceTime,
            postProcessTime = postProcessTime,
            results = segmentationResults
        )
    }

    private fun processOutput(
        output: Array<Array<FloatArray>>,
        imageWidth: Int,
        imageHeight: Int,
    ): List<Detection> {
        val detections = mutableListOf<Detection>()
        val batch = output[0]

        for (element in batch) {
            val confidence = element[4]
            if (confidence< 0.01) {
                continue
            }
            val classId = element[5].toInt()
            val className = labels.getOrNull(classId) ?: "unknown"
            if (confidence < CONFIDENCE_THRESHOLD) {
                continue
            }


            val left = element[0] * imageWidth
            val top = element[1] * imageHeight
            val right = element[2] * imageWidth
            val bottom = element[3] * imageHeight

            detections.add(
                Detection(
                    classId = element[5].toInt(),
                    className = className,
                    confidence = confidence,
                    bbox = RectF(left, top, right, bottom)
                )
            )
        }
        return detections
    }

    private fun preProcess(frame: Bitmap): Array<ByteBuffer> {
        val resizedBitmap = Bitmap.createScaledBitmap(frame, tensorWidth, tensorHeight, false)
        val tensorImage = TensorImage(INPUT_IMAGE_TYPE)
        tensorImage.load(resizedBitmap)
        val processedImage = imageProcessor.process(tensorImage)
        return arrayOf(processedImage.buffer)
    }

    interface InstanceSegmentationListener {
        fun onError(error: String)
        fun onEmpty()

        fun onDetect(
            interfaceTime: Long,
            results: List<Detection>,
            preProcessTime: Long,
            postProcessTime: Long
        )
    }

    companion object {
        private const val INPUT_MEAN = 128f
        private const val INPUT_STANDARD_DEVIATION = 128f
        private val INPUT_IMAGE_TYPE = DataType.FLOAT32
        private val OUTPUT_IMAGE_TYPE = DataType.FLOAT32
        private const val CONFIDENCE_THRESHOLD = 0.7F
    }

    data class Detection(
        val classId: Int,
        val className: String,
        val confidence: Float,
        val bbox: RectF // [left, top, right, bottom] в пикселях
    )
}