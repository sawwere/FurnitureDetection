package com.sawwere.yoloapp

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.os.Build
import android.os.Bundle
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.sawwere.yoloapp.camera.presentation.CameraScreen
import com.sawwere.yoloapp.camera.presentation.CameraScreenViewModel
import com.sawwere.yoloapp.core.config.SaveConfig
import com.sawwere.yoloapp.core.detection.DetectionComponent
import com.sawwere.yoloapp.core.image.DrawImages
import com.sawwere.yoloapp.core.repository.MediaStoreRepository
import com.sawwere.yoloapp.core.system.VibrationComponent
import com.sawwere.yoloapp.ui.theme.YOLOAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity(), DetectionComponent.InstanceSegmentationListener {

    private lateinit var detectionComponent: DetectionComponent
    private lateinit var drawImages: DrawImages
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var vibrationComponent: VibrationComponent

    private var camera: Camera? = null
    private var segmentedBitmap: Bitmap? by mutableStateOf(null)
    private var originalBitmap: Bitmap? by mutableStateOf(null)


    private lateinit var vibrator : Vibrator

    private lateinit var viewModel : CameraScreenViewModel
    private val mediaStoreRepository = MediaStoreRepository()



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }

        enableEdgeToEdge()

        drawImages = DrawImages(applicationContext)
        cameraExecutor = Executors.newSingleThreadExecutor()
        viewModel = CameraScreenViewModel()

        detectionComponent = DetectionComponent(
            context = applicationContext,
            //modelPath = "yolo11n-seg_float16.tflite",
            modelPath = "furniture.tflite",
            //modelPath = "model_fp16.tflite",
            labelPath = null,
            instanceSegmentationListener = this,
            message = {
                Toast.makeText(applicationContext, it, Toast.LENGTH_SHORT).show()
            }
        )

        vibrationComponent = VibrationComponent(vibrator)

        setContent {
            YOLOAppTheme {
                CameraScreen(
                    viewModel = this.viewModel,
                    segmentedBitmap = segmentedBitmap,
                    onCaptureClick = {
                        saveCombinedImage()
                        vibrationComponent.triggerHapticFeedback()
                    }
                )
            }
        }

        checkPermission()
    }

    private fun checkPermission() = lifecycleScope.launch(Dispatchers.IO) {
        val isGranted = REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(
                applicationContext,
                it
            ) == PackageManager.PERMISSION_GRANTED
        }
        if (isGranted) {
            // Camera will be started in Compose when PreviewView is available
        } else {
            requestPermissionLauncher.launch(REQUIRED_PERMISSIONS)
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { map ->
        if (map.all { it.value }) {
            // Permissions granted, camera will start in Compose
        } else {
            Toast.makeText(baseContext, "Camera permission required", Toast.LENGTH_LONG).show()
        }
    }

    fun startCamera(previewView: PreviewView) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val aspectRatio = AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY

            val preview = Preview.Builder()
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setAspectRatioStrategy(
                            aspectRatio
                        ).build()
                )
                .build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setAspectRatioStrategy(
                            aspectRatio
                        ).build()
                )
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build().also {
                    it.setAnalyzer(cameraExecutor, ImageAnalyzer())
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageAnalyzer
                )
                viewModel.setupZoomState(camera!!)
            } catch (exc: Exception) {
                Log.e("CameraX", "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun saveCombinedImage() {
        val original = originalBitmap ?: run {
            Toast.makeText(this, getString(R.string.no_image), Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val bitmapToSave = if (segmentedBitmap != null) {
                    Bitmap.createBitmap(
                        original.width,
                        original.height,
                        Bitmap.Config.ARGB_8888
                    ).apply {
                        val canvas = Canvas(this)
                        canvas.drawBitmap(original, 0f, 0f, null)
                        canvas.drawBitmap(segmentedBitmap!!, 0f, 0f, null)
                    }
                } else {
                    original
                }

                mediaStoreRepository.saveToGallery(
                    context = applicationContext,
                    bitmap = bitmapToSave,
                    folderName = SaveConfig.folderName
                )

                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.image_saved),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Log.e("CameraX", "Error saving image: ${e.message}", e)
                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.error_saving, e.message ?: "Unknown error"),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    override fun onDetect(
        interfaceTime: Long,
        results: List<DetectionComponent.Detection>,
        preProcessTime: Long,
        postProcessTime: Long
    ) {
        this.viewModel.updateTimers(
            preProcessTime = preProcessTime,
            inferenceTime = interfaceTime,
            postProcessTime = postProcessTime
        )

        segmentedBitmap = if (results.isEmpty()) {
            null
        } else {
            drawImages(
                imageWidth = originalBitmap!!.width,
                imageHeight = originalBitmap!!.height,
                results = results
            )
        }


    }

    override fun onEmpty() {
        segmentedBitmap = null
    }

    override fun onError(error: String) {
        Toast.makeText(applicationContext, error, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        detectionComponent.close()
        cameraExecutor.shutdown()
    }

    inner class ImageAnalyzer : ImageAnalysis.Analyzer {
        override fun analyze(imageProxy: ImageProxy) {
            val bitmapBuffer = Bitmap.createBitmap(
                imageProxy.width,
                imageProxy.height,
                Bitmap.Config.ARGB_8888
            )

            imageProxy.use {
                bitmapBuffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer)
            }
            imageProxy.close()

            val matrix = Matrix().apply {
                postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
            }

            val rotatedBitmap = Bitmap.createBitmap(
                bitmapBuffer, 0, 0, bitmapBuffer.width, bitmapBuffer.height,
                matrix, true
            )

            originalBitmap = rotatedBitmap
            detectionComponent.invoke(rotatedBitmap)
        }
    }

    companion object {
        val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}