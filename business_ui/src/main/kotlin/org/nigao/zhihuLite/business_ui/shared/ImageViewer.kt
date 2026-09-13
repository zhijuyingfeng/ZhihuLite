package org.nigao.zhihuLite.business_ui.shared

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import org.nigao.zhihuLite.base_ui.noRippleClickable
import org.nigao.zhihuLite.business_ui.R
import org.nigao.zhihuLite.business_logic.share.ImageExportFailed
import org.nigao.zhihuLite.business_logic.share.ImageNeedsPermission
import org.nigao.zhihuLite.business_logic.share.ImageSaved
import org.nigao.zhihuLite.business_logic.share.downloadImage
import org.nigao.zhihuLite.business_logic.share.saveImageToPictures
import org.nigao.zhihuLite.business_logic.share.shareImage
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
    val context = LocalContext.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    // The image whose long-press menu is open, and where on the screen it was pressed.
    var menuUrl by remember { mutableStateOf<String?>(null) }
    var menuPosition by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    // A download is in flight: the menu is closed and this blocks a second action.
    var busy by remember { mutableStateOf(false) }
    // Set while the reader is being asked for the storage permission a pre-Android-10 save needs.
    var awaitingPermissionFor by remember { mutableStateOf<String?>(null) }

    fun showMessage(messageRes: Int) {
        Toast.makeText(context, messageRes, Toast.LENGTH_SHORT).show()
    }

    // The permission callback cannot call `save` directly: the launcher has to exist before it, and
    // `save` has to be able to launch it. The result is left here for the effect below instead.
    var saveAfterPermission by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val url = awaitingPermissionFor
        awaitingPermissionFor = null
        if (granted && url != null) {
            saveAfterPermission = url
        } else if (!granted) {
            showMessage(R.string.image_save_no_permission)
        }
    }

    fun save(url: String) {
        coroutineScope.launch {
            busy = true
            val bytes = downloadImage(url)
            busy = false
            if (bytes == null) {
                showMessage(R.string.image_download_failed)
                return@launch
            }
            when (val result = saveImageToPictures(context, url, bytes)) {
                is ImageSaved -> showMessage(R.string.image_saved_message)
                is ImageExportFailed -> showMessage(R.string.image_save_failed)
                ImageNeedsPermission -> {
                    awaitingPermissionFor = url
                    permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }
        }
    }

    fun share(url: String) {
        coroutineScope.launch {
            busy = true
            val bytes = downloadImage(url)
            busy = false
            if (bytes == null) {
                showMessage(R.string.image_download_failed)
                return@launch
            }
            if (!shareImage(context, url, bytes)) {
                showMessage(R.string.image_share_failed)
            }
        }
    }

    LaunchedEffect(saveAfterPermission) {
        val url = saveAfterPermission
        if (url != null) {
            saveAfterPermission = null
            // The permission arrived: finish the save the reader already asked for.
            save(url)
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        isZoomed = false
        menuUrl = null
    }

    Box(
        modifier = modifier.fillMaxSize()
            .background(color = Color.Black)
            .onSizeChanged { containerSize = it }
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
                },
                onLongPress = { position ->
                    menuPosition = position
                    menuUrl = imageUrls[page]
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
            PageIndicator(
                pagerState = pagerState,
                totalCount = imageUrls.size,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        if (busy) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        menuUrl?.let { url ->
            // Anywhere else closes the menu; drawn before it so the menu keeps its own taps.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .noRippleClickable { menuUrl = null },
            )
            ImageActionMenu(
                position = menuPosition,
                containerSize = containerSize,
                onShare = {
                    menuUrl = null
                    share(url)
                },
                onSave = {
                    menuUrl = null
                    save(url)
                },
                onDismiss = { menuUrl = null },
                modifier = Modifier.align(Alignment.TopStart),
            )
        }
    }
}

/**
 * The long-press menu: black, white text, at the point that was pressed.
 *
 * Anchored to the press rather than to the screen so it appears where the reader's finger is, and
 * clamped by an estimated menu size because the real one is only known after layout — a menu half off
 * the screen is worse than one nudged inward.
 */
@Composable
private fun ImageActionMenu(
    position: Offset,
    containerSize: IntSize,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val estimatedWidth = with(density) { 132.dp.toPx() }
    val estimatedHeight = with(density) { 96.dp.toPx() }
    val margin = with(density) { 12.dp.toPx() }

    val maxX = (containerSize.width - estimatedWidth - margin).coerceAtLeast(margin)
    val maxY = (containerSize.height - estimatedHeight - margin).coerceAtLeast(margin)
    val x = position.x.coerceIn(margin, maxX).toInt()
    val y = position.y.coerceIn(margin, maxY).toInt()

    Column(
        modifier = modifier
            .offset { IntOffset(x, y) }
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black)
            .padding(vertical = 4.dp),
    ) {
        ImageActionMenuItem(text = stringResource(R.string.image_menu_share), onClick = onShare)
        ImageActionMenuItem(text = stringResource(R.string.image_menu_save), onClick = onSave)
    }
}

@Composable
private fun ImageActionMenuItem(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        modifier = Modifier
            .fillMaxWidth()
            .noRippleClickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
    )
}

/**
 * Reads [androidx.compose.foundation.pager.PagerState.currentPage] inside its own
 * composable so a swipe only recomposes the counter instead of the whole viewer.
 */
@Composable
private fun PageIndicator(
    pagerState: PagerState,
    totalCount: Int,
    modifier: Modifier = Modifier,
) {
    Text(
        text = "${pagerState.currentPage + 1}/$totalCount",
        color = Color.White,
        fontSize = 14.sp,
        modifier = modifier
    )
}

@Composable
fun ZoomableImage(
    url: String,
    onDismiss: () -> Unit,
    onZoomChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onLongPress: (Offset) -> Unit = {},
) {
    val scale = remember { Animatable(1f) }
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    var contentSize by remember { mutableStateOf(IntSize.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val coroutineScope = rememberCoroutineScope()

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
                    onLongPress = onLongPress,
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