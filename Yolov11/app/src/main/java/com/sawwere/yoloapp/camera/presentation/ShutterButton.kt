package com.sawwere.yoloapp.camera.presentation

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay


@Composable
fun ShutterButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Анимация масштаба при нажатии
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.8f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "ShutterButtonScale"
    )

    // Анимация прозрачности при нажатии
    val alphaPressed by animateFloatAsState(
        targetValue = if (isPressed) 0.7f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "ShutterButtonAlpha"
    )

    var flashEffect by remember { mutableStateOf(false) }


    if (isPressed) {
        LaunchedEffect(Unit) {
            flashEffect = true
            delay(100) // Длительность вспышки
            flashEffect = false
        }
    }

    Box(
        modifier = modifier
            .size(70.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = alphaPressed
            }
    ) {
        // Внешнее кольцо
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(
                    width = 3.dp,
                    color = Color.White.copy(alpha = if (flashEffect) 1f else 0.8f),
                    shape = CircleShape
                )
                .padding(6.dp)
                .clip(CircleShape)
                .background(Color.Transparent)
        )

        // Внутренний круг (кнопка)
        IconButton(
            onClick = onClick,
            interactionSource = interactionSource,
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
                .clip(CircleShape),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = Color.White.copy(
                    alpha = if (flashEffect) 0.9f else 1f
                )
            )
        ) {
            if (flashEffect) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = 0.6f))
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
                .border(
                    width = 1.5.dp,
                    color = Color.Black.copy(alpha = if (flashEffect) 0.1f else 0.3f),
                    shape = CircleShape
                )
        )
    }
}