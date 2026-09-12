package org.nigao.zhihuLite.business_ui.video

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.nigao.zhihuLite.business_ui.R

/**
 * The video, full screen.
 *
 * Layout rule from the request: whatever the video's shape, it is displayed **full width with its own
 * aspect ratio, centred vertically** — so a landscape video letterboxes above and below, and a
 * portrait one fills the width and is centred (its box is taller than the screen, which is the price
 * of "full width" for a vertical video).
 *
 * Controls: a back button top-left, a draggable progress bar along the bottom, and a tap anywhere
 * else toggles pause/resume with a centred play control shown while paused.
 */
@Composable
fun FullScreenVideoScreen(
    answerId: String,
    videoId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: FullScreenVideoViewModel = viewModel(
        key = "$answerId:$videoId",
        factory = FullScreenVideoViewModelFactory(answerId = answerId, videoId = videoId),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playback = rememberVideoPlaybackUiState()
    val haptics = LocalHapticFeedback.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        when (val state = uiState) {
            FullScreenVideoUiState.Loading -> {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            FullScreenVideoUiState.Failed -> {
                VideoFailure(
                    onRetry = viewModel::retry,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            is FullScreenVideoUiState.Ready -> {
                VideoSurface(
                    urls = state.urls,
                    state = playback,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .aspectRatio(state.aspectRatio),
                    onPlaybackCompleted = playback::markCompleted,
                )

                // Tap layer: drawn above the video and below every control, so the back button and
                // the scrubber consume their own touches and everything else belongs to playback.
                // Tap toggles pause; holding plays at double speed until the finger lifts.
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                // Runs when the press starts and suspends until it ends, however it
                                // ends — so both the release after a hold and a cancelled gesture go
                                // through the same line.
                                onPress = {
                                    tryAwaitRelease()
                                    playback.setDoubleSpeed(false)
                                },
                                onLongPress = {
                                    playback.setDoubleSpeed(true)
                                    // A short buzz so the speed change is felt, not only seen.
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onTap = { playback.togglePlayPause() },
                            )
                        },
                )

                if (!playback.playWhenReady) {
                    Icon(
                        imageVector = Icons.Default.PlayCircleFilled,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(72.dp),
                    )
                }

                if (playback.showsDoubleSpeedHint) {
                    // Black plate, white content, top of the screen: the only thing that says why the
                    // video suddenly runs twice as fast.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(top = 12.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color.Black)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.FastForward,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.video_double_speed),
                            color = Color.White,
                            fontSize = 13.sp,
                        )
                    }
                }

                VideoProgressBar(
                    playback = playback,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }

        // Always reachable, including while loading or after a failure.
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 8.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                tint = Color.White,
            )
        }
    }
}

@Composable
private fun VideoFailure(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.padding(24.dp),
    ) {
        Text(
            text = stringResource(R.string.feed_load_failed),
            color = Color.White,
            textAlign = TextAlign.Center,
            fontSize = 16.sp,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = onRetry) {
            Text(text = stringResource(R.string.retry))
        }
    }
}

/**
 * Draggable progress bar plus elapsed/total time.
 *
 * Seeking happens when the drag ends rather than on every pixel: the position poller keeps writing
 * `positionMs` while a drag is in progress, so a live seek would fight the thumb.
 */
// `ExperimentalMaterial3Api` is needed for the Slider overload that takes `thumb`/`track` slots —
// the stable one cannot draw a dot thumb or a two-tone grey track.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoProgressBar(
    playback: VideoPlaybackUiState,
    modifier: Modifier = Modifier,
) {
    var scrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }

    val durationMs = playback.durationMs
    val shownPositionMs = if (scrubbing) {
        (scrubFraction * durationMs).toLong()
    } else {
        playback.positionMs
    }
    val fraction = if (durationMs > 0) {
        (shownPositionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            // No strip behind the bar: the track and the dot carry themselves over the picture, and
            // the reader asked for no extra background here.
            .navigationBarsPadding()
            .padding(horizontal = 12.dp),
    ) {
        Slider(
            value = fraction,
            // The visible bar is 4dp, so the interactive node is given its own height instead of
            // being measured from the dot: the whole strip above the labels is then draggable.
            modifier = Modifier.fillMaxWidth().height(ProgressTouchHeight),
            onValueChange = { fractionValue ->
                scrubbing = true
                scrubFraction = fractionValue
            },
            onValueChangeFinished = {
                playback.requestSeekTo((scrubFraction * durationMs).toLong())
                // A scrub in the middle of a paused video plays on from where the reader left it; a
                // finished video stays finished (see `resumeAfterSeek`).
                playback.resumeAfterSeek()
                scrubbing = false
            },
            enabled = durationMs > 0,
            // A plain dot as the thumb, and a two-tone track: the remainder in light grey, the part
            // already played in a grey brighter than it, so the played fraction reads at a glance on
            // top of the dark strip. Material's own thumb is a vertical bar and its track colours are
            // the theme's primary, which is why both slots are replaced rather than tinted.
            thumb = {
                // A dark ring drawn *behind* the dot rather than a blur behind it: `shadow` on a
                // shape this flat only offsets a faint smudge downwards (measured: magenta 234 to
                // 211), which is not enough to keep a near-white dot readable on a bright frame.
                //
                // The slot fills the slider's height, with the dot centred inside it. Material
                // measures the thumb with the layout's constraints (so its slot is at least
                // `TrackHeight`, 16dp) but the track with `minHeight = 0` (so that slot is 7dp here),
                // then places each slot at `(max(heights) - ownHeight) / 2`. A thumb whose content
                // had its own smaller height was therefore top-aligned in a taller slot and sat
                // 14px above the bar — measured. Filling the height makes both contents centre on
                // the same line, whatever the dot's size.
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxHeight()
                        .background(Color.Transparent)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(ProgressThumbSize + ProgressOutlineWidth * 2)
                            .background(ProgressOutlineColor, CircleShape),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(ProgressThumbSize)
                                .background(Color.White, CircleShape),
                        )
                    }
                }
            },
            track = { sliderState ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ProgressTrackHeight + ProgressOutlineWidth * 2)
                        .background(ProgressOutlineColor, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            // Inset by the outline width so the ring wraps the *ends* too. Without
                            // the horizontal inset the bar reached the outline's own edge, so the
                            // ring was two straight bands above and below with square-cut ends —
                            // which is exactly how it read.
                            .padding(horizontal = ProgressOutlineWidth)
                            .height(ProgressTrackHeight)
                            // Clipped, because the played segment below is a plain rectangle: without
                            // this its square corner paints over the track's rounded left end.
                            .clip(CircleShape)
                            .background(ProgressTrackColor, CircleShape),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(sliderState.value.coerceIn(0f, 1f))
                                .fillMaxHeight()
                                .background(ProgressPlayedColor),
                        )
                    }
                }
            },
        )
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            // The interactive area above is deliberately tall, which left the labels floating far
            // below the bar; `offset` moves them visually without giving the height back. The
            // horizontal padding matches where the bar actually starts and ends — Material insets
            // the track by half the thumb's width at each end, so without it the labels sit outside
            // the bar by exactly that much.
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ProgressBarEndInset)
                .offset(y = ProgressLabelsOffset),
        ) {
            Text(
                text = formatPlaybackTime(shownPositionMs),
                color = Color.White,
                fontSize = 12.sp,
                style = ProgressTimeShadow,
            )
            Text(
                text = formatPlaybackTime(durationMs),
                color = Color.White,
                fontSize = 12.sp,
                style = ProgressTimeShadow,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

/**
 * A soft shadow under the time labels.
 *
 * The bar has no background of its own any more, so white digits need something to sit on when the
 * frame behind them is bright. A blurred black shadow keeps them readable without bringing a band
 * back — the shadow hugs each glyph instead of covering the picture.
 */
private val ProgressTimeShadow = TextStyle(
    shadow = Shadow(
        color = Color.Black.copy(alpha = 0.7f),
        offset = Offset(1.5f, 1.5f),
        blurRadius = 6f,
    ),
)

/**
 * The dark ring around the track and the dot, for the same reason the time labels have a shadow: the
 * bar sits on the picture, and near-white marks vanish on a bright frame. Drawn as an outline rather
 * than an elevation shadow because the shapes are flat — a 4dp bar has nothing for a shadow to fall
 * on.
 */
private val ProgressOutlineColor = Color.Black.copy(alpha = 0.6f)

private val ProgressOutlineWidth = 1.5.dp

/**
 * How far the time labels sit above their layout position, to close the gap to the bar.
 *
 * The fraction is from asking for whole screen pixels: 22dp plus another 2px, and this screen is
 * 3.25px per dp. Expressed in dp so it scales with density on other devices.
 */
private val ProgressLabelsOffset = (-22.6).dp


/**
 * The bar the reader has not reached yet. Darkened twice (#BDBDBD -> #A0A0A0 -> #8A8A8A) so the
 * played part (#EDEDED) reads as the brighter one at a glance.
 */
private val ProgressTrackColor = Color(0xFF8A8A8A)

/** The part already played: a grey brighter than [ProgressTrackColor]. */
private val ProgressPlayedColor = Color(0xFFEDEDED)

/**
 * How thick the bar is. It has come down 4dp -> 3.4dp (2px) -> 3dp, so the 1.5dp outline around it
 * is now half its height.
 */
private val ProgressTrackHeight = 3.dp

/**
 * The bar is thin on purpose; the area that accepts a drag is not.
 *
 * 72dp = the previous 56dp plus 8dp above and below, so a finger landing off the 4dp track still
 * catches the drag. (Material's own minimum is 48dp; this is deliberately larger.)
 */
private val ProgressTouchHeight = 72.dp

/**
 * The current position: a white dot, not Material's vertical bar.
 *
 * 7.2dp because the last adjustment was "2px" more radius on a 3.25px-per-dp screen (0.6dp).
 */
private val ProgressThumbSize = 7.2.dp
/**
 * How far Material insets the track from the slider's own edges: half the thumb, so the thumb's
 * centre can reach either end. The labels are padded by the same amount to line up with the bar.
 */
private val ProgressBarEndInset = (ProgressThumbSize + ProgressOutlineWidth * 2) / 2

/** `m:ss`, or `h:mm:ss` once the video is over an hour long. Public for the app module's suite. */
fun formatPlaybackTime(millis: Long): String {
    val safeMillis = millis.coerceAtLeast(0)
    val totalSeconds = safeMillis / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
