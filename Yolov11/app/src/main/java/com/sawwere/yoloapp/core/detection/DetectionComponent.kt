package com.sawwere.yoloapp.core.detection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.os.SystemClock
import android.util.Log
import com.sawwere.yoloapp.core.detection.MetaData.extractNamesFromMetadata
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.CastOp
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import java.nio.ByteBuffer

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
//        if (labels.isEmpty()) {
//            if (labelPath == null) {
//                message("Model not contains metadata, provide LABELS_PATH in Constants.kt")
//                labels.addAll(MetaData.TEMP_CLASSES)
//            } else {
//                labels.addAll(extractNamesFromLabelFile(context, labelPath))
//            }
//        }

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
//        if (tensorWidth == 0 || tensorHeight == 0
//            || numChannel == 0 || numElements == 0
//            || xPoints == 0 || yPoints == 0 || masksNum == 0) {
//            instanceSegmentationListener.onError("Interpreter not initialized properly")
//            return
//        }

        var preProcessTime = SystemClock.uptimeMillis()

        val imageBuffer = preProcess(frame)

//        val finalDetections = applyNMS(rawDetections)
//
//        finalDetections.forEach { detection ->
//            val rect = scaleBoundingBox(detection, frame.width, frame.height)
//        }

//        val coordinatesBuffer = TensorBuffer.createFixedSize(
//            intArrayOf(1 , numChannel, numElements),
//            OUTPUT_IMAGE_TYPE
//        )
//
//        val maskProtoBuffer = TensorBuffer.createFixedSize(
//            intArrayOf(1, xPoints, yPoints, masksNum),
//            OUTPUT_IMAGE_TYPE
//        )
////
//        val outputBuffer = mapOf<Int, Any>(
//            0 to coordinatesBuffer.buffer.rewind(),
//            1 to maskProtoBuffer.buffer.rewind()
//        )
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
//
//        val bestBoxes = bestBox(coordinatesBuffer.floatArray) ?: run {
//            instanceSegmentationListener.onEmpty()
//            return
//        }
//
//        val maskProto = reshapeMaskOutput(maskProtoBuffer.floatArray)
//
//        val segmentationResults = bestBoxes.map {
//            SegmentationResult(
//                box = it,
//                mask = getFinalMask(frame.width, frame.height, it, maskProto)
//            )
//        }

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

            if (confidence < CONFIDENCE_THRESHOLD) continue

            val left = element[0] * imageWidth
            val top = element[1] * imageHeight
            val right = element[2] * imageWidth
            val bottom = element[3] * imageHeight

            detections.add(
                Detection(
                    classId = element[5].toInt(),
                    confidence = confidence,
                    bbox = RectF(left, top, right, bottom)
                )
            )
        }
        return detections
    }

//    fun applyNMS(detections: List<Detection>): List<Detection> {
//        val sorted = detections.sortedByDescending { it.confidence }
//        val selected = mutableListOf<Detection>()
//
//        for (detection in sorted) {
//            var overlap = false
//            for (selectedDetection in selected) {
//                if (calculateIoU(
//                        detection, selectedDetection
//                ) > IOU_THRESHOLD) {
//                    overlap = true
//                    break
//                }
//            }
//            if (!overlap) selected.add(detection)
//        }
//        return selected
//    }
//
//    fun scaleBoundingBox(detection: Detection, imageWidth: Int, imageHeight: Int): RectF {
//        val x = detection.x * imageWidth
//        val y = detection.y * imageHeight
//        val width = detection.width * imageWidth
//        val height = detection.height * imageHeight
//        return RectF(
//            x - width / 2,  // left
//            y - height / 2, // top
//            x + width / 2,  // right
//            y + height / 2  // bottom
//        )
//    }

//    private fun bestBox(array: FloatArray) : List<Output0>? {
//
//        val output0List = mutableListOf<Output0>()
//
//        for (c in 0 until numElements) {
//            var maxConf = CONFIDENCE_THRESHOLD
//            var maxIdx = -1
//            var currentInd = 4
//            var arrayIdx = c + numElements * currentInd
//
//            while (currentInd < (numChannel - masksNum)){
//                if (array[arrayIdx] > maxConf) {
//                    maxConf = array[arrayIdx]
//                    maxIdx = currentInd - 4
//                }
//                currentInd++
//                arrayIdx += numElements
//            }
//
//            if (maxConf > CONFIDENCE_THRESHOLD) {
//                val clsName = labels[maxIdx]
//                val cx = array[c] // 0
//                val cy = array[c + numElements] // 1
//                val w = array[c + numElements * 2]
//                val h = array[c + numElements * 3]
//                val x1 = cx - (w/2F)
//                val y1 = cy - (h/2F)
//                val x2 = cx + (w/2F)
//                val y2 = cy + (h/2F)
//                if (x1 < 0F || x1 > 1F) continue
//                if (y1 < 0F || y1 > 1F) continue
//                if (x2 < 0F || x2 > 1F) continue
//                if (y2 < 0F || y2 > 1F) continue
//
//                val maskWeight = mutableListOf<Float>()
//                while (currentInd < numChannel){
//                    maskWeight.add(array[arrayIdx])
//                    currentInd++
//                    arrayIdx += numElements
//                }
//
//                output0List.add(
//                    Output0(
//                        x1 = x1, y1 = y1, x2 = x2, y2 = y2,
//                        cx = cx, cy = cy, w = w, h = h,
//                        cnf = maxConf, cls = maxIdx, clsName = clsName,
//                        maskWeight = maskWeight
//                    )
//                )
//            }
//        }
//
//        if (output0List.isEmpty()) return null
//
//        return applyNMS(output0List)
//    }

//    private fun applyNMS(output0List: List<Output0>) : MutableList<Output0> {
//        val sortedBoxes = output0List.sortedByDescending { it.cnf }.toMutableList()
//        val selectedBoxes = mutableListOf<Output0>()
//
//        while(sortedBoxes.isNotEmpty()) {
//            val first = sortedBoxes.first()
//            selectedBoxes.add(first)
//            sortedBoxes.remove(first)
//
//            val iterator = sortedBoxes.iterator()
//            while (iterator.hasNext()) {
//                val nextBox = iterator.next()
//                val iou = calculateIoU(first, nextBox)
//                if (iou >= IOU_THRESHOLD) {
//                    iterator.remove()
//                }
//            }
//        }
//
//        return selectedBoxes
//    }

    private fun calculateIoU(box1: Detection, box2: Detection): Float {
        val x1 = maxOf(box1.bbox.left, box2.bbox.left)
        val y1 = maxOf(box1.bbox.top, box2.bbox.top)
        val x2 = minOf(box1.bbox.right, box2.bbox.right)
        val y2 = minOf(box1.bbox.bottom, box2.bbox.bottom)
        val intersectionArea = maxOf(0F, x2 - x1) * maxOf(0F, y2 - y1)
        val box1Area = box1.bbox.width() * box1.bbox.height()
        val box2Area = box2.bbox.width() * box2.bbox.height()
        return intersectionArea / (box1Area + box2Area - intersectionArea)
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
        private const val INPUT_MEAN = 0f
        private const val INPUT_STANDARD_DEVIATION = 255f
        private val INPUT_IMAGE_TYPE = DataType.FLOAT32
        private val OUTPUT_IMAGE_TYPE = DataType.FLOAT32
        private const val CONFIDENCE_THRESHOLD = 0.8F
        private const val IOU_THRESHOLD = 0.5F
    }

    data class Detection(
        val classId: Int,
        val confidence: Float,
        val bbox: RectF // [left, top, right, bottom] в пикселях
    )
}