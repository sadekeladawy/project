package com.island.app.ui.overlay

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.island.app.data.IslandSettings
import com.island.app.model.AppTransitionDirection
import com.island.app.model.AppTransitionEvent
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AppExitTransitionOverlay(
    transition: AppTransitionEvent,
    settings: IslandSettings,
    onReachedIsland: () -> Unit,
    onFinished: () -> Unit
) {
    val density = LocalDensity.current
    val targetOffsetXPx = with(density) { settings.offsetX.dp.toPx() }
    val targetOffsetYPx = with(density) { (settings.offsetY + 4f).dp.toPx() }

    // 1.0x speed = ~540ms; 0.5x = slower, 2.0x = faster
    val speed = settings.animationSpeed.coerceIn(0.5f, 2.0f)
    val baseDurationMs = (540f / speed).toInt().coerceIn(260, 1100)
    val springStiffness = (290f * speed * speed).coerceIn(120f, 900f)

    val isExit = transition.direction == AppTransitionDirection.EXIT_TO_ISLAND

    val scale = remember(transition.id) { Animatable(if (isExit) 1f else 0.11f) }
    val translateX = remember(transition.id) { Animatable(if (isExit) 0f else targetOffsetXPx) }
    val translateY = remember(transition.id) { Animatable(if (isExit) 0f else targetOffsetYPx) }
    val cornerDp = remember(transition.id) { Animatable(if (isExit) 24f else 180f) }
    val alpha = remember(transition.id) { Animatable(if (isExit) 0.98f else 0.96f) }

    LaunchedEffect(transition.id) {
        if (isExit) {
            coroutineScope {
                launch {
                    scale.animateTo(
                        targetValue = 0.11f,
                        animationSpec = spring(
                            dampingRatio = 0.78f,
                            stiffness = springStiffness
                        )
                    )
                }
                launch {
                    translateX.animateTo(
                        targetValue = targetOffsetXPx,
                        animationSpec = spring(
                            dampingRatio = 0.82f,
                            stiffness = springStiffness
                        )
                    )
                }
                launch {
                    translateY.animateTo(
                        targetValue = targetOffsetYPx,
                        animationSpec = spring(
                            dampingRatio = 0.82f,
                            stiffness = springStiffness
                        )
                    )
                }
                launch {
                    cornerDp.animateTo(
                        targetValue = 180f,
                        animationSpec = tween(
                            durationMillis = (baseDurationMs * 0.85f).toInt(),
                            easing = FastOutSlowInEasing
                        )
                    )
                }
                launch {
                    delay((baseDurationMs * 0.62f).toLong())
                    onReachedIsland()
                    alpha.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(
                            durationMillis = (baseDurationMs * 0.38f).toInt(),
                            easing = FastOutSlowInEasing
                        )
                    )
                }
            }
            onFinished()
        } else {
            // Reverse animation: expand out of the island into a full-screen card and fade away
            coroutineScope {
                launch {
                    scale.animateTo(
                        targetValue = 1f,
                        animationSpec = spring(
                            dampingRatio = 0.80f,
                            stiffness = springStiffness
                        )
                    )
                }
                launch {
                    translateX.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(
                            dampingRatio = 0.82f,
                            stiffness = springStiffness
                        )
                    )
                }
                launch {
                    translateY.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(
                            dampingRatio = 0.82f,
                            stiffness = springStiffness
                        )
                    )
                }
                launch {
                    cornerDp.animateTo(
                        targetValue = 24f,
                        animationSpec = tween(
                            durationMillis = (baseDurationMs * 0.8f).toInt(),
                            easing = FastOutSlowInEasing
                        )
                    )
                }
                launch {
                    delay((baseDurationMs * 0.55f).toLong())
                    alpha.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(
                            durationMillis = (baseDurationMs * 0.45f).toInt(),
                            easing = FastOutSlowInEasing
                        )
                    )
                }
            }
            onFinished()
        }
    }

    val dominant = remember(transition.dominantColorArgb) { Color(transition.dominantColorArgb) }
    val validArt = remember(transition.albumArt) {
        val bmp = transition.albumArt
        if (bmp != null && !bmp.isRecycled) runCatching { bmp.asImageBitmap() }.getOrNull() else null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                transformOrigin = TransformOrigin(0.5f, 0f)
                scaleX = scale.value.coerceAtLeast(0.05f)
                scaleY = scale.value.coerceAtLeast(0.05f)
                translationX = translateX.value
                translationY = translateY.value
                this.alpha = alpha.value.coerceIn(0f, 1f)
                shape = RoundedCornerShape(cornerDp.value.coerceAtLeast(0f).dp)
                clip = true
            }
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        dominant,
                        dominant.copy(alpha = 0.92f),
                        Color(0xFF0A0C12)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        if (validArt != null) {
            Image(
                bitmap = validArt,
                contentDescription = transition.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(164.dp)
                    .clip(RoundedCornerShape(28.dp))
            )
        } else {
            Box(
                modifier = Modifier
                    .size(144.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = transition.title,
                    tint = Color.White,
                    modifier = Modifier.size(64.dp)
                )
            }
        }
    }
}
