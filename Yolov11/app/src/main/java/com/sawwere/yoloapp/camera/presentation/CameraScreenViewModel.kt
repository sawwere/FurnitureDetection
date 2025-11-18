package com.sawwere.yoloapp.camera.presentation

import androidx.camera.core.Camera
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class CameraScreenViewModel(

) {
    private lateinit var camera: Camera

    private val _preProcessTime = MutableStateFlow(0L)
    val preProcessTime = _preProcessTime.asStateFlow()

    private val _inferenceTime = MutableStateFlow(0L)
    val inferenceTime = _inferenceTime.asStateFlow()

    private val _postProcessTime = MutableStateFlow(0L)
    val postProcessTime = _postProcessTime.asStateFlow()

    private val _zoomProgress = MutableStateFlow(0f)
    val zoomProgress = _zoomProgress.asStateFlow()

    private var minZoomRatio = 1f
    private var maxZoomRatio = 1f


    fun updateTimers(
        preProcessTime: Long,
        inferenceTime: Long,
        postProcessTime: Long,
    ) {
        _inferenceTime.update { inferenceTime }
        _preProcessTime.update { preProcessTime }
        _postProcessTime.update { postProcessTime }
    }

    fun setupZoomState(camera: Camera) {
        this.camera = camera

        val zoomState = camera.cameraInfo.zoomState.value
        zoomState?.let {
            minZoomRatio = zoomState.minZoomRatio
            maxZoomRatio = zoomState.maxZoomRatio
            _zoomProgress.update { calculateZoomProgress(zoomState.zoomRatio) }
        }
    }

    fun updateCameraZoom(newZoomValue: Float) {
        _zoomProgress.update { newZoomValue }
        camera.let { cam ->
            val newZoomRatio = minZoomRatio + (_zoomProgress.value / 10f) * (maxZoomRatio - minZoomRatio)
            cam.cameraControl.setZoomRatio(newZoomRatio)
        }
    }

    fun handlePinchZoom(scaleFactor: Float) {
        camera.let { cam ->
            val zoomState = cam.cameraInfo.zoomState.value ?: return
            val currentZoom = zoomState.zoomRatio
            val newZoom = currentZoom * scaleFactor

            // Ограничиваем зум минимальным/максимальным значением
            val clampedZoom = newZoom.coerceIn(minZoomRatio, maxZoomRatio)

            // Обновляем состояние зума
            cam.cameraControl.setZoomRatio(clampedZoom)
            _zoomProgress.update { calculateZoomProgress(clampedZoom) }
        }
    }

    private fun calculateZoomProgress(zoomRatio: Float): Float {
        return ((zoomRatio - minZoomRatio) / (maxZoomRatio - minZoomRatio)) * 10f
    }

}