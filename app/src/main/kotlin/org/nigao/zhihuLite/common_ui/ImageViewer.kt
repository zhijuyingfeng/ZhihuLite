package org.nigao.zhihuLite.common_ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlin.math.min

@Composable
fun ImageViewer(
    imageUrls: List<String>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    initialPage: Int = 0,
) {
    var isZoomed by remember { mutableStateOf(false) }
    val pagerState = rememberPagerState(
        pageCount = { imageUrls.size },
        initialPage = initialPage
    )

    LaunchedEffect(pagerState.currentPage) {
        isZoomed = false
    }

    Box(
        modifier = modifier.fillMaxSize()
            .background(color = Color.Black)
    ) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !isZoomed,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            ZoomableImage(
                url = imageUrls[page],
                onDismiss = onDismiss,
                onZoomChange = { zoomed ->
                    if (pagerState.currentPage == page) {
                        isZoomed = zoomed
                    }
                }
            )
        }

        IconButton(
            onClick = onDismiss,
            modifier = Modifier.align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 16.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = Color.White.copy(alpha = 0.8f)
            )
        }
        Box(
            modifier = Modifier.align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .background(
                    color = Color.White.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(50)
                )
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                text = "${pagerState.currentPage + 1}/${imageUrls.size}",
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

@Composable
fun ZoomableImage(
    url: String,
    onDismiss: () -> Unit,
    onZoomChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val scale = remember { Animatable(1f) }
    val offsetX = remember { Animatable(1f) }
    val offsetY = remember { Animatable(1f) }
    var contentSize by remember { mutableStateOf(IntSize.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val coroutineScope = rememberCoroutineScope()

    val fitScale = if (contentSize.isValid() && containerSize.isValid()) {
        val widthRatio = containerSize.width.toFloat() / contentSize.width.toFloat()
        val heightRatio = containerSize.height.toFloat() / contentSize.height.toFloat()
        min(widthRatio, heightRatio)
    } else {
        null
    }

    val limitOffset = { targetOffset: Offset, targetScale: Float ->
        val fitScale = if (contentSize.isValid() && containerSize.isValid()) {
            val widthRatio = containerSize.width.toFloat() / contentSize.width.toFloat()
            val heightRatio = containerSize.height.toFloat() / contentSize.height.toFloat()
            min(widthRatio, heightRatio)
        } else {
            null
        }

        var newOffset = targetOffset

        fitScale?.let {
            val displayWidth = contentSize.width * fitScale * targetScale
            val displayHeight = contentSize.height * fitScale * targetScale

            val maxOffsetX = (displayWidth - containerSize.width).coerceAtLeast(0f) / 2f
            val maxOffsetY = (displayHeight - containerSize.height).coerceAtLeast(0f) / 2f

            newOffset = Offset(
                x = targetOffset.x.coerceIn(-maxOffsetX, maxOffsetX),
                y = targetOffset.y.coerceIn(-maxOffsetY, maxOffsetY)
            )
        }

        newOffset
    }

    LaunchedEffect(scale.value) {
        onZoomChange(scale.value != 1f)
    }

    Box(
        modifier = modifier.fillMaxSize()
            .onSizeChanged { containerSize = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { tapPosition ->
                        val targetScale = if (scale.value != 1f) { 1f } else { 2.5f }

                        coroutineScope.launch {
                            val tapOffsetFromCenter = tapPosition - Offset(containerSize.width / 2f, containerSize.height / 2f)
                            var targetOffset = Offset(
                                x = -tapOffsetFromCenter.x * targetScale,
                                y = -tapOffsetFromCenter.y * targetScale
                            )
                            targetOffset = limitOffset.invoke(targetOffset, targetScale)
                            launch {
                                scale.animateTo(targetScale, animationSpec = tween(300))
                            }
                            launch {
                                offsetX.animateTo(targetOffset.x, animationSpec = tween(300))
                            }
                            launch {
                                offsetY.animateTo(targetOffset.y, animationSpec = tween(300))
                            }
                        }
                    },
                    onTap = { onDismiss.invoke() }
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    do {
                        val event = awaitPointerEvent()
                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()
                        val centroid = event.calculateCentroid(useCurrent = false)

                        if (zoomChange != 1f || panChange != Offset.Zero) {
                            val currentScale = scale.value
                            val newScale = (currentScale * zoomChange).coerceIn(0.5f, 5f)

                            coroutineScope.launch {
                                scale.snapTo(newScale)
                            }

                            var newOffset = Offset.Zero
                            if (newScale > 1f) {
                                val effectiveZoomChange = if (currentScale != 0f) newScale / currentScale else 1f
                                val centroidOffset = centroid - Offset(containerSize.width / 2f, containerSize.height / 2f)

                                val rawOffset = Offset(offsetX.value, offsetY.value)
                                newOffset = rawOffset * effectiveZoomChange + centroidOffset * (1 - effectiveZoomChange) + panChange
                                newOffset = limitOffset(newOffset, newScale)
                            }

                            coroutineScope.launch {
                                offsetX.snapTo(newOffset.x)
                            }

                            coroutineScope.launch {
                                offsetY.snapTo(newOffset.y)
                            }

                            if (zoomChange != 1f || scale.value != 1f) {
                                event.changes.forEach { it.consume() }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                    if (scale.value < 1f) {
                        coroutineScope.launch {
                            scale.animateTo(1f, animationSpec = tween(300))
                        }
                        coroutineScope.launch {
                            offsetX.animateTo(0f, animationSpec = tween(300))
                        }

                        coroutineScope.launch {
                            offsetY.animateTo(0f, animationSpec = tween(300))
                        }
                    }
                }
            }
    ) {
        AsyncImage(
            model = url,
            contentDescription = null,
            onSuccess = { state ->
                val intrinsicSize = state.painter.intrinsicSize
                contentSize = IntSize(intrinsicSize.width.toInt(), intrinsicSize.height.toInt())
            },
            modifier = Modifier.fillMaxSize()
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    translationX = offsetX.value
                    translationY = offsetY.value
                }
        )
    }
}

private fun IntSize.isValid(): Boolean {
    return width > 0 && height > 0
}