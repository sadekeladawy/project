package com.island.app.ui.overlay

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.island.app.data.IslandSettings
import com.island.app.model.IslandDisplayMode
import com.island.app.model.IslandEvent
import com.island.app.service.CallCommand
import com.island.app.service.MediaCommand
import com.island.app.state.IslandUiState
import kotlin.math.abs

/**
 * Calculates how far downward (in dp) an expanded card glides so it sits cleanly
 * BELOW the physical front camera punch-hole and system status bar notifications,
 * leaving both the camera lens and status bar icons completely unobstructed.
 */
fun computeExpandedTopShiftDp(offsetYDp: Float, cutoutBottomDp: Float): Float {
    val safeClearanceBottom = maxOf(cutoutBottomDp, 34f)
    val targetTopY = maxOf(offsetYDp, safeClearanceBottom + 8f)
    return (targetTopY - offsetYDp).coerceIn(0f, 56f)
}

/**
 * Shared proportional target width & height calculation (in dp) used by both
 * IslandOverlayService (to size the fixed GPU surface once per mode transition)
 * and DynamicIslandOverlay. Widths are capped proportionally to the screen width
 * so compact cards never cover left/right status bar icons and expanded cards
 * never balloon excessively.
 */
fun computeTargetIslandSizeDp(
    uiState: IslandUiState,
    settings: IslandSettings,
    screenWidthDp: Float = 392f
): Pair<Float, Float> {
    val activeEvent = uiState.activeEvent
    val mode = if (activeEvent == null) IslandDisplayMode.IDLE else uiState.displayMode
    val safeScreenW = screenWidthDp.coerceAtLeast(320f)

    return when (mode) {
        IslandDisplayMode.IDLE -> Pair(
            settings.idleWidthDp.coerceIn(84f, minOf(180f, safeScreenW * 0.46f)),
            settings.idleHeightDp.coerceIn(24f, 40f)
        )
        IslandDisplayMode.COMPACT -> {
            // Restrict compact width to ~54% of screen width (max 214dp) so status bar
            // clock/notifications on the left and signal/battery on the right remain clear.
            val maxCompactW = minOf(safeScreenW * 0.54f, 214f).coerceAtLeast(settings.idleWidthDp + 34f)
            val desiredW = when (activeEvent) {
                is IslandEvent.MusicPlayback -> maxOf(settings.idleWidthDp + 64f, 194f)
                is IslandEvent.IncomingCall -> maxOf(settings.idleWidthDp + 76f, 210f)
                is IslandEvent.WifiConnected -> maxOf(settings.idleWidthDp + 72f, 206f)
                is IslandEvent.BluetoothChanged -> maxOf(settings.idleWidthDp + 74f, 208f)
                else -> maxOf(settings.idleWidthDp + 68f, 200f)
            }
            val w = minOf(maxCompactW, desiredW)
            val h = settings.idleHeightDp.coerceIn(32f, 38f)
            Pair(w, h)
        }
        IslandDisplayMode.EXPANDED -> {
            val w = minOf(safeScreenW - 44f, maxOf(settings.idleWidthDp * 2.2f, 276f))
                .coerceAtMost(308f)
            val h = when (activeEvent) {
                is IslandEvent.IncomingCall -> 94f
                is IslandEvent.MusicPlayback -> 152f
                else -> 74f
            }
            Pair(w, h)
        }
        IslandDisplayMode.FULL_EXPANDED -> {
            val w = minOf(safeScreenW - 32f, maxOf(settings.idleWidthDp * 2.35f, 292f))
                .coerceAtMost(324f)
            val h = when (activeEvent) {
                is IslandEvent.MusicPlayback -> 176f
                is IslandEvent.IncomingCall -> 116f
                else -> 98f
            }
            Pair(w, h)
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

    val screenWidthDp = LocalConfiguration.current.screenWidthDp.toFloat().coerceAtLeast(320f)
    val activeEvent = uiState.activeEvent
    val mode = if (activeEvent == null) IslandDisplayMode.IDLE else uiState.displayMode
    val isExpanded = mode == IslandDisplayMode.EXPANDED || mode == IslandDisplayMode.FULL_EXPANDED

    // In compact mode, reserve a center lens clearance when the capsule sits on the punch-hole.
    val overlapsCameraInCompact = !isExpanded && cutoutWidthDp > 0f && cutoutBottomDp > settings.offsetY + 2f
    val cameraSafeGap = if (overlapsCameraInCompact) {
        (cutoutWidthDp + 8f).coerceIn(32f, 58f).dp
    } else {
        0.dp
    }

    // In expanded modes, smoothly glide below the punch-hole & status bar so neither is obscured.
    val targetTopShift: Dp = if (isExpanded) {
        computeExpandedTopShiftDp(settings.offsetY, cutoutBottomDp).dp
    } else {
        0.dp
    }

    val (targetWidthFloat, targetHeightFloat) = computeTargetIslandSizeDp(
        uiState = uiState,
        settings = settings,
        screenWidthDp = screenWidthDp
    )
    val targetWidth = targetWidthFloat.dp
    val targetHeight = targetHeightFloat.dp
    val targetCorner: Dp = when (mode) {
        IslandDisplayMode.IDLE -> settings.cornerRadiusDp.dp
        IslandDisplayMode.COMPACT -> 20.dp
        IslandDisplayMode.EXPANDED -> 26.dp
        IslandDisplayMode.FULL_EXPANDED -> 30.dp
    }

    val speed = settings.animationSpeed.coerceIn(0.5f, 2.0f)
    val springDpSpec = remember(speed) {
        spring<Dp>(
            dampingRatio = 0.78f,
            stiffness = (420f * speed).coerceIn(220f, 960f)
        )
    }

    val animatedWidth by animateDpAsState(targetWidth, springDpSpec, label = "islandWidth")
    val animatedHeight by animateDpAsState(targetHeight, springDpSpec, label = "islandHeight")
    val animatedCorner by animateDpAsState(targetCorner, springDpSpec, label = "islandCorner")
    val animatedTopShift by animateDpAsState(targetTopShift, springDpSpec, label = "islandTopShift")

    // While dragging position/size sliders, track geometry 1:1 with zero spring lag
    val safeWidth = (if (isLiveAdjusting && mode == IslandDisplayMode.IDLE) targetWidth else animatedWidth)
        .coerceAtLeast(1.dp)
    val safeHeight = (if (isLiveAdjusting && mode == IslandDisplayMode.IDLE) targetHeight else animatedHeight)
        .coerceAtLeast(1.dp)
    val safeCorner = (if (isLiveAdjusting && mode == IslandDisplayMode.IDLE) targetCorner else animatedCorner)
        .coerceAtLeast(0.dp)
    val safeTopShift = if (isLiveAdjusting && !isExpanded) 0.dp else animatedTopShift.coerceAtLeast(0.dp)
    val shape = RoundedCornerShape(safeCorner)

    // Interactive expanded cards (Music & Incoming Call) handle their own button clicks without
    // collapsing on stray taps; users collapse via tap-outside, swipe-up, or the collapse button.
    val isInteractiveExpanded = isExpanded &&
        (activeEvent is IslandEvent.MusicPlayback || activeEvent is IslandEvent.IncomingCall)

    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    val currentOnSwipeUp by rememberUpdatedState(onSwipeUp)
    val currentMode by rememberUpdatedState(mode)

    val tapModifier = if (isInteractiveExpanded) {
        Modifier
    } else {
        Modifier.pointerInput(mode, activeEvent?.id) {
            detectTapGestures(
                onTap = { currentOnTap() },
                onLongPress = { currentOnLongPress() }
            )
        }
    }

    Box(
        modifier = modifier
            .zIndex(100f)
            .offset(y = safeTopShift)
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
            // Observe unconsumed vertical swipes in the Final pass so child media buttons
            // and the horizontal seekbar receive touches with zero latency or interference.
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
                    val startX = down.position.x
                    val startY = down.position.y
                    var collapsed = false
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Final)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        if (change.isConsumed || collapsed || currentMode == IslandDisplayMode.IDLE) {
                            continue
                        }
                        val dx = change.position.x - startX
                        val dy = change.position.y - startY
                        if (dy < -42f && abs(dy) > abs(dx) * 1.6f) {
                            collapsed = true
                            change.consume()
                            currentOnSwipeUp()
                            break
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = Pair(activeEvent, mode),
            transitionSpec = {
                fadeIn(tween(150)) togetherWith fadeOut(tween(100))
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
                    .padding(top = 5.dp, end = 8.dp)
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
