package com.sawwere.yoloapp.camera.presentation

import android.graphics.Bitmap
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.sawwere.yoloapp.MainActivity
import com.sawwere.yoloapp.R


@Composable
fun CameraScreen(
    viewModel: CameraScreenViewModel,
    segmentedBitmap: Bitmap?,
    onCaptureClick: () -> Unit
) {
    val context = LocalContext.current

    val preProcessTime = viewModel.preProcessTime.collectAsState()
    val inferenceTime = viewModel.inferenceTime.collectAsState()
    val postProcessTime= viewModel.postProcessTime.collectAsState()
    val zoomProgress = viewModel.zoomProgress.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Camera preview with overlay
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(16.dp)
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        viewModel.handlePinchZoom(zoom)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            var previewView: PreviewView? by remember { mutableStateOf(null) }

            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        previewView = this
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
            )

            segmentedBitmap?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Segmentation Result",
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(3f / 4f)
                )
            }

            LaunchedEffect(previewView) {
                if (previewView != null) {
                    (context as? MainActivity)?.startCamera(previewView!!)
                }
            }
        }

        SpeedInfoPanel(
            preProcessTime = preProcessTime.value,
            inferenceTime = inferenceTime.value,
            postProcessTime = postProcessTime.value
        )

        Slider(
            value = zoomProgress.value,
            onValueChange = { newProgress ->
                        viewModel.updateCameraZoom(newProgress)
                    },
            valueRange = 0f..10f,
            steps = 9,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF6200EE),
                activeTrackColor = Color(0xFF6200EE),
                inactiveTrackColor = Color(0xFF6200EE).copy(alpha = 0.24f)
            )
        )

        // Добавлен отступ для системной панели навигации
        Spacer(modifier = Modifier
            .height(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
        )

        // В функции CameraScreen замените Button на:
        ShutterButton(
            onClick = onCaptureClick,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 18.dp)
        )

        // Дополнительный отступ для безопасности
        Spacer(modifier = Modifier
            .height(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
        )
    }
}

@Composable
fun SpeedInfoPanel(
    preProcessTime: Long,
    inferenceTime: Long,
    postProcessTime: Long
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.speed_info),
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = Color.White
        )

        SpeedInfoRow(
            label = stringResource(R.string.preprocess_label),
            value = preProcessTime.toString()
        )
        SpeedInfoRow(
            label = stringResource(R.string.inference_label),
            value = inferenceTime.toString()
        )
        SpeedInfoRow(
            label = stringResource(R.string.postprocess_label),
            value = postProcessTime.toString()
        )
    }
}

@Composable
fun SpeedInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Text(
            text = label,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = value,
            color = Color.White
        )
    }
}