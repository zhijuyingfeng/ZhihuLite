package org.nigao.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.nigao.zhihuLite.business_logic.video.data.VideoPlayInfoApi
import org.nigao.zhihuLite.business_logic.zhihu.sharedJson
import org.nigao.zhihuLite.business_ui.video.FullScreenVideoUiState
import org.nigao.zhihuLite.business_ui.video.FullScreenVideoViewModel
import org.nigao.zhihuLite.business_ui.video.formatPlaybackTime
import org.nigao.zhihuLite.model.video.Meta
import org.nigao.zhihuLite.model.video.Resolution
import org.nigao.zhihuLite.model.video.UiConfig
import org.nigao.zhihuLite.model.video.VideoItem
import org.nigao.zhihuLite.model.video.VideoPlay
import org.nigao.zhihuLite.model.video.VideoPlayInfo
import org.nigao.zhihuLite.model.video.VideoPlaylist
import org.nigao.zhihuLite.model.video.Za
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Records the request and answers with a fixed payload (or nothing). */
private class RecordingVideoApi(private val info: VideoPlayInfo?) : VideoPlayInfoApi {
    var lastPath: String? = null
    var lastBody: String? = null
    var calls = 0

    override suspend fun getVideoPlayInfo(path: String, body: String): VideoPlayInfo? {
        calls++
        lastPath = path
        lastBody = body
        return info
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class FullScreenVideoViewModelTest {

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(api: VideoPlayInfoApi) = FullScreenVideoViewModel(
        answerId = "answer-1",
        videoId = "video-9",
        api = api,
    )

    @Test
    fun `loads playable urls and reports the video's own aspect ratio`() = runBlocking {
        // A portrait video: the screen has to lay it out by its own shape, not as 16:9.
        val api = RecordingVideoApi(videoPlayInfo(width = 720, height = 1280, quality = "HD", codec = "H264"))

        val state = withTimeoutOrNull(5_000) { viewModel(api).uiState.first { it is FullScreenVideoUiState.Ready } }

        state as FullScreenVideoUiState.Ready
        assertEquals(listOf("https://cdn.example/hd.mp4"), state.urls)
        assertEquals(720f / 1280f, state.aspectRatio, 0.001f)
        assertEquals("/api/v4/video/play_info?r=answer-1video-9", api.lastPath)
        assertTrue("the request must send both ids: ${api.lastBody}", api.lastBody!!.contains("\"video_id\":\"video-9\""))
        assertTrue(api.lastBody!!.contains("\"content_id\":\"answer-1\""))
    }

    @Test
    fun `falls back to 16 by 9 when the response carries no resolution`() = runBlocking {
        val api = RecordingVideoApi(videoPlayInfo(width = 0, height = 0, quality = "HD", codec = "H264"))

        val state = withTimeoutOrNull(5_000) { viewModel(api).uiState.first { it is FullScreenVideoUiState.Ready } }

        assertEquals(16f / 9f, (state as FullScreenVideoUiState.Ready).aspectRatio, 0.001f)
    }

    @Test
    fun `lays the player out by the resolution of the captured response`() = runBlocking {
        // The real endpoint's payload (see VideoPlayInfoTest): a 1920x1080 landscape video, so the
        // full-width plate is 16:9 — the ratio comes from the data, not from a constant.
        val info = sharedJson.decodeFromString<VideoPlayInfo>(REAL_PLAY_INFO_RESPONSE)

        val state = withTimeoutOrNull(5_000) {
            viewModel(RecordingVideoApi(info)).uiState.first { it is FullScreenVideoUiState.Ready }
        }

        state as FullScreenVideoUiState.Ready
        assertEquals(1920f / 1080f, state.aspectRatio, 0.001f)
        assertEquals(3, state.urls.size)
    }

    @Test
    fun `reports failure when nothing playable comes back`() = runBlocking {
        val api = RecordingVideoApi(null)

        val state = withTimeoutOrNull(5_000) { viewModel(api).uiState.first { it is FullScreenVideoUiState.Failed } }

        assertTrue(state is FullScreenVideoUiState.Failed)
    }

    @Test
    fun `retry asks the api again`() = runBlocking {
        val api = RecordingVideoApi(null)
        val viewModel = viewModel(api)
        withTimeoutOrNull(5_000) { viewModel.uiState.first { it is FullScreenVideoUiState.Failed } }

        viewModel.retry()
        withTimeoutOrNull(5_000) { viewModel.uiState.first { it is FullScreenVideoUiState.Failed } }

        assertEquals(2, api.calls)
    }

    @Test
    fun `formats the progress label`() {
        assertEquals("0:00", formatPlaybackTime(0))
        assertEquals("0:07", formatPlaybackTime(7_400))
        assertEquals("2:03", formatPlaybackTime(123_000))
        assertEquals("1:01:01", formatPlaybackTime(3_661_000))
        assertEquals("0:00", formatPlaybackTime(-5))
    }

    /** One mp4 source with the given shape; the endpoint's own fields otherwise. */
    private fun videoPlayInfo(width: Int, height: Int, quality: String, codec: String): VideoPlayInfo =
        VideoPlayInfo(
            za = Za(contentId = "", contentType = 4, contentToken = "answer-1"),
            videoPlay = VideoPlay(
                id = "video-9",
                defaultCover = "https://pic.example/cover.jpg",
                isPaid = false,
                isTrial = false,
                playCount = 0,
                meta = Meta(
                    mime = "video/mp4",
                    duration = 10.0,
                    resolution = Resolution(quality = quality, width = width, height = height),
                    hdrType = "SDR",
                ),
                playlist = VideoPlaylist(
                    mp4 = listOf(
                        VideoItem(
                            key = 1,
                            name = "720P",
                            label = "高清 720P",
                            type = 0,
                            quality = quality,
                            format = "mp4",
                            codec = codec,
                            hdrType = "SDR",
                            maxbitrate = 0.0,
                            bitrate = 412.465,
                            duration = 10.0,
                            channels = 2,
                            sampleRate = 48_000,
                            width = width,
                            height = height,
                            size = 1_000_000L,
                            fps = 25,
                            url = listOf("https://cdn.example/hd.mp4"),
                        ),
                    ),
                ),
            ),
            uiConfig = UiConfig(),
        )
}
