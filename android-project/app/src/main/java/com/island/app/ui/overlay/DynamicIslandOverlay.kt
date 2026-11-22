package com.island.app.ui.overlay

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.island.app.data.IslandSettings
import com.island.app.model.IslandDisplayMode
import com.island.app.model.IslandEvent
import com.island.app.service.CallCommand
import com.island.app.service.MediaCommand
import com.island.app.state.IslandUiState

/**
 * Calculates how much vertical clearance (dp) an expanded card needs at the top
 * so its text and album art sit cleanly below the front camera cutout / status bar strip.
 */
fun computeCameraTopInsetDp(offsetYDp: Float, cutoutBottomDp: Float): Float {
    if (cutoutBottomDp <= 0f) return 0f
    val overlap = cutoutBottomDp - offsetYDp
    return if (overlap > 2f) (overlap + 8f).coerceIn(10f, 48f) else 0f
}

/**
 * Shared target width & height calculation (in dp) used by both IslandOverlayService
 * (to size the WindowManager surface once per transition) and DynamicIslandOverlay.
 */
fun computeTargetIslandSizeDp(
    uiState: IslandUiState,
    settings: IslandSettings,
    cameraTopInsetDp: Float
): Pair<Float, Float> {
    val activeEvent = uiState.activeEvent
    val mode = if (activeEvent == null) IslandDisplayMode.IDLE else uiState.displayMode

    return when (mode) {
        IslandDisplayMode.IDLE -> Pair(settings.idleWidthDp, settings.idleHeightDp)
        IslandDisplayMode.COMPACT -> {
            val w = when (activeEvent) {
                is IslandEvent.MusicPlayback -> 236f
                is IslandEvent.IncomingCall -> 286f
                is IslandEvent.WifiConnected -> 278f
                is IslandEvent.BluetoothChanged -> 282f
                is IslandEvent.ChargingStarted -> 270f
                is IslandEvent.LowBattery -> 270f
                else -> 260f
            }
            val h = maxOf(settings.idleHeightDp, 40f)
            Pair(w, h)
        }
        IslandDisplayMode.EXPANDED -> {
            val baseH = when (activeEvent) {
                is IslandEvent.IncomingCall -> 118f
                is IslandEvent.MusicPlayback -> 208f
                else -> 92f
            }
            Pair(348f, baseH + cameraTopInsetDp)
        }
        IslandDisplayMode.FULL_EXPANDED -> {
            val baseH = when (activeEvent) {
                is IslandEvent.MusicPlayback -> 236f
                is IslandEvent.IncomingCall -> 150f
                else -> 136f
            }
            Pair(364f, baseH + cameraTopInsetDp)
        }
    }
}

@Composable
fun DynamicIslandOverlay(
    uiState: IslandUiState,
    settings: IslandSettings,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onSwipeUp: () -> Unit,
    onMediaCommand: (MediaCommand) -> Unit,
    onCallCommand: (CallCommand) -> Unit,
    modifier: Modifier = Modifier,
    cutoutWidthDp: Float = 0f,
    cutoutBottomDp: Float = 0f,
    isLiveAdjusting: Boolean = false
) {
    val isHidden = !settings.masterEnabled ||
        (settings.hideInFullscreen && uiState.isFullscreenHidden)

    val visibilityAlpha by animateFloatAsState(
        targetValue = if (isHidden) 0f else 1f,
        animationSpec = tween(durationMillis = 180),
        label = "islandVisibilityAlpha"
    )
    val visibilityScale by animateFloatAsState(
        targetValue = if (isHidden) 0.78f else 1f,
        animationSpec = spring(dampingRatio = 0.76f, stiffness = Spring.StiffnessMediumLow),
        label = "islandVisibilityScale"
    )

    if (visibilityAlpha <= 0.01f && isHidden) {
        return
    }

    val activeEvent = uiState.activeEvent
    val mode = if (activeEvent == null) IslandDisplayMode.IDLE else uiState.displayMode

    val cameraTopInsetFloat = computeCameraTopInsetDp(settings.offsetY, cutoutBottomDp)
    val cameraTopInset = cameraTopInsetFloat.dp

    // In compact mode, reserve a center camera gap whenever the capsule overlaps the front camera
    val overlapsCamera = cutoutWidthDp > 0f && cameraTopInsetFloat > 0f
    val cameraSafeGap = if (overlapsCamera) {
        (cutoutWidthDp + 14f).coerceIn(44f, 92f).dp
    } else {
        0.dp
    }

    val (targetWidthFloat, targetHeightFloat) = computeTargetIslandSizeDp(
        uiState = uiState,
        settings = settings,
        cameraTopInsetDp = cameraTopInsetFloat
    )
    val targetWidth = targetWidthFloat.dp
    val targetHeight = targetHeightFloat.dp
    val targetCorner: Dp = when (mode) {
        IslandDisplayMode.IDLE -> settings.cornerRadiusDp.dp
        IslandDisplayMode.COMPACT -> 24.dp
        IslandDisplayMode.EXPANDED -> 32.dp
        IslandDisplayMode.FULL_EXPANDED -> 36.dp
    }

    val speed = settings.animationSpeed.coerceIn(0.5f, 2.0f)
    val springDpSpec = remember(speed) {
        spring<Dp>(
            dampingRatio = 0.76f,
            stiffness = (380f * speed).coerceIn(200f, 900f)
        )
    }

    val animatedWidth by animateDpAsState(targetWidth, springDpSpec, label = "islandWidth")
    val animatedHeight by animateDpAsState(targetHeight, springDpSpec, label = "islandHeight")
    val animatedCorner by animateDpAsState(targetCorner, springDpSpec, label = "islandCorner")

    // While dragging position/size sliders, track geometry 1:1 with zero spring lag
    val safeWidth = (if (isLiveAdjusting && mode == IslandDisplayMode.IDLE) targetWidth else animatedWidth)
        .coerceAtLeast(1.dp)
    val safeHeight = (if (isLiveAdjusting && mode == IslandDisplayMode.IDLE) targetHeight else animatedHeight)
        .coerceAtLeast(1.dp)
    val safeCorner = (if (isLiveAdjusting && mode == IslandDisplayMode.IDLE) targetCorner else animatedCorner)
        .coerceAtLeast(0.dp)
    val shape = RoundedCornerShape(safeCorner)

    // Interactive expanded cards (Music & Incoming Call) handle their own button clicks without
    // collapsing on stray taps; users collapse via tap-outside, swipe-up, or the collapse button.
    val isInteractiveExpanded = (mode == IslandDisplayMode.EXPANDED || mode == IslandDisplayMode.FULL_EXPANDED) &&
        (activeEvent is IslandEvent.MusicPlayback || activeEvent is IslandEvent.IncomingCall)

    val tapModifier = if (isInteractiveExpanded) {
        Modifier
    } else {
        Modifier.pointerInput(mode, activeEvent?.id) {
            detectTapGestures(
                onTap = { onTap() },
                onLongPress = { onLongPress() }
            )
        }
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                alpha = visibilityAlpha.coerceIn(0f, 1f)
                scaleX = visibilityScale.coerceAtLeast(0.1f)
                scaleY = visibilityScale.coerceAtLeast(0.1f)
            }
            .width(safeWidth)
            .height(safeHeight)
            .clip(shape)
            .background(Color.Black)
            .border(width = 0.75.dp, color = Color(0x26FFFFFF), shape = shape)
            .then(tapModifier)
            .pointerInput(mode) {
                var cumulativeDrag = 0f
                var triggered = false
                detectVerticalDragGestures(
                    onDragStart = {
                        cumulativeDrag = 0f
                        triggered = false
                    },
                    onVerticalDrag = { _, dragAmount ->
                        if (triggered) return@detectVerticalDragGestures
                        cumulativeDrag += dragAmount
                        if (cumulativeDrag < -28f && mode != IslandDisplayMode.IDLE) {
                            triggered = true
                            onSwipeUp()
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = Pair(activeEvent, mode),
            transitionSpec = {
                (fadeIn(tween(180)) + scaleIn(
                    initialScale = 0.92f,
                    animationSpec = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMedium)
                )) togetherWith (fadeOut(tween(110)) + scaleOut(targetScale = 0.95f))
            },
            contentKey = { (evt, m) -> "${evt?.type?.name ?: "IDLE"}_${evt?.id ?: "none"}_${m.name}" },
            label = "islandContentTransition"
        ) { (event, displayMode) ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (event == null || displayMode == IslandDisplayMode.IDLE) {
                    IdleCapsuleContent()
                } else {
                    EventCardContent(
                        event = event,
                        displayMode = displayMode,
                        cameraSafeGapDp = cameraSafeGap,
                        cameraTopInsetDp = cameraTopInset,
                        onCollapse = onSwipeUp,
                        onMediaCommand = onMediaCommand,
                        onCallCommand = onCallCommand
                    )
                }
            }
        }

        if (uiState.queuedCount > 0 && mode != IslandDisplayMode.IDLE) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 6.dp, end = 10.dp)
                    .background(Color(0x33FFFFFF), RoundedCornerShape(8.dp))
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            ) {
                Text(
                    text = "+${uiState.queuedCount}",
                    color = Color(0xFFD1D5DB),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun IdleCapsuleContent() {
    Box(modifier = Modifier.fillMaxSize())
}
