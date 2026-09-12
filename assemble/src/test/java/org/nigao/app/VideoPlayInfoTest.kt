package org.nigao.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nigao.zhihuLite.business_logic.zhihu.sharedJson
import org.nigao.zhihuLite.model.video.BeginFrame
import org.nigao.zhihuLite.model.video.Meta
import org.nigao.zhihuLite.model.video.Resolution
import org.nigao.zhihuLite.model.video.UiConfig
import org.nigao.zhihuLite.model.video.VideoItem
import org.nigao.zhihuLite.model.video.VideoPlay
import org.nigao.zhihuLite.model.video.VideoPlayInfo
import org.nigao.zhihuLite.model.video.VideoPlaylist
import org.nigao.zhihuLite.model.video.Za

class VideoPlayInfoTest {
    @Test
    fun nullableSvcsFromRealResponseShapeDecodes() {
        val response = """
            {
              "za": {
                "content_id": "",
                "content_type": 4,
                "content_token": "answer-1"
              },
              "video_play": {
                "id": "video-1",
                "default_cover": "https://example.com/cover.jpg",
                "is_paid": false,
                "is_trial": false,
                "svcs": null,
                "play_count": 0,
                "meta": {
                  "mime": "video/mp4",
                  "duration": 10.0,
                  "resolution": {
                    "quality": "HD",
                    "width": 720,
                    "height": 1280
                  },
                  "hdr_type": "SDR"
                },
                "begin_frame": {
                  "SD": "https://example.com/sd.jpg"
                },
                "playlist": {
                  "mp4": []
                }
              },
              "ui_config": {
                "plugins": []
              },
              "template": null
            }
        """.trimIndent()

        val result = sharedJson.decodeFromString<VideoPlayInfo>(response)

        assertNull(result.videoPlay.svcs)
        assertNull(result.videoPlay.beginFrame?.hd)
        assertEquals("https://example.com/sd.jpg", result.videoPlay.beginFrame?.sd)
        assertEquals("video-1", result.videoPlay.id)
    }

    @Test
    fun playableUrlsPrefer720pH264AndRetainLowerCostFallbacks() {
        val info = videoPlayInfo(
            listOf(
                videoItem(
                    quality = "FHD",
                    codec = "H264",
                    bitrate = 900.0,
                    size = 10_000_000L,
                    url = "https://example.com/h264-1080.mp4",
                ),
                videoItem(
                    quality = "HD",
                    codec = "H265",
                    bitrate = 320.0,
                    size = 3_600_000L,
                    url = "https://example.com/h265-720.mp4",
                ),
                videoItem(
                    quality = "SD",
                    codec = "H264",
                    bitrate = 300.0,
                    size = 3_000_000L,
                    url = "https://example.com/h264-480.mp4",
                ),
                videoItem(
                    quality = "HD",
                    codec = "H264",
                    bitrate = 500.0,
                    size = 5_700_000L,
                    url = "https://example.com/h264-720.mp4",
                ),
            )
        )

        assertEquals(
            listOf(
                "https://example.com/h264-720.mp4",
                "https://example.com/h264-480.mp4",
                "https://example.com/h265-720.mp4",
                "https://example.com/h264-1080.mp4",
            ),
            info.getPlayableUrls(),
        )
    }

    @Test
    fun emptyAndDuplicateUrlsAreRemoved() {
        val info = videoPlayInfo(
            listOf(
                videoItem(
                    quality = "HD",
                    codec = "H264",
                    url = "",
                ),
                videoItem(
                    quality = "HD",
                    codec = "H264",
                    url = "https://example.com/video.mp4",
                    additionalUrls = listOf("https://example.com/video.mp4", " "),
                ),
            )
        )

        assertEquals(
            listOf("https://example.com/video.mp4"),
            info.getPlayableUrls(),
        )
        assertTrue(info.getPlayableUrl()?.isNotBlank() == true)
        assertNull(videoPlayInfo(emptyList()).getPlayableUrl())
    }

    private fun videoPlayInfo(items: List<VideoItem>): VideoPlayInfo {
        return VideoPlayInfo(
            za = Za(
                contentId = "answer-1",
                contentType = 4,
                contentToken = "answer-1",
            ),
            videoPlay = VideoPlay(
                id = "video-1",
                defaultCover = "https://example.com/cover.jpg",
                isPaid = false,
                isTrial = false,
                playCount = 0,
                meta = Meta(
                    mime = "video/mp4",
                    duration = 10.0,
                    resolution = Resolution(
                        quality = "HD",
                        width = 720,
                        height = 1280,
                    ),
                    hdrType = "SDR",
                ),
                beginFrame = BeginFrame(
                    hd = "https://example.com/hd.jpg",
                    sd = "https://example.com/sd.jpg",
                ),
                playlist = VideoPlaylist(mp4 = items),
            ),
            uiConfig = UiConfig(),
        )
    }

    /**
     * The real `/api/v4/video/play_info` response (only the URLs are shortened), i.e. the payload
     * that broke playback: the server sends `bitrate` as a fractional number (`301.201`) while the
     * model declared it as `Int`, so the whole response failed to decode and the player went to its
     * failed state. Every field type below was taken from this capture.
     */
    @Test
    fun realResponseWithFractionalBitrateDecodes() {
        val result = sharedJson.decodeFromString<VideoPlayInfo>(REAL_PLAY_INFO_RESPONSE)

        assertEquals(3, result.videoPlay.playlist.mp4.size)
        assertEquals(301.201, result.videoPlay.playlist.mp4[0].bitrate, 0.0001)

        val urls = result.getPlayableUrls()
        assertTrue(
            "expected the 720P H.264 source first, got $urls",
            urls.first().contains("/HD/"),
        )
    }

    private fun videoItem(
        quality: String,
        codec: String,
        url: String,
        bitrate: Double = 500.0,
        size: Long = 5_000_000L,
        additionalUrls: List<String> = emptyList(),
    ): VideoItem {
        return VideoItem(
            key = 1,
            name = quality,
            label = quality,
            type = 0,
            quality = quality,
            format = "mp4",
            codec = codec,
            hdrType = "SDR",
            maxbitrate = bitrate,
            bitrate = bitrate,
            duration = 10.0,
            channels = 2,
            sampleRate = 48_000,
            width = if (quality == "SD") 480 else 720,
            height = if (quality == "FHD") 1920 else 1280,
            size = size,
            fps = 24,
            url = listOf(url) + additionalUrls,
        )
    }
}

internal const val REAL_PLAY_INFO_RESPONSE = """{"za":{"content_id":"","content_type":4,"content_token":"2082236587618865396"},"video_play":{"id":"2082235184292938255","default_cover":"https://pic.example/cover.jpg","is_paid":false,"is_trial":false,"svcs":null,"play_count":0,"meta":{"mime":"video/mp4","duration":405.185,"resolution":{"quality":"FHD","width":1920,"height":1080},"hdr_type":"SDR"},"begin_frame":{"FHD":"https://pic.example/frame-fhd.jpg","HD":"https://pic.example/frame-hd.jpg","SD":"https://pic.example/frame-sd.jpg"},"playlist":{"mp4":[{"key":20011,"name":"480P","label":"标清 480P","type":0,"quality":"SD","format":"mp4","codec":"H264","hdr_type":"SDR","maxbitrate":0,"bitrate":301.201,"duration":405.185,"channels":2,"sample_rate":44100,"width":848,"height":476,"size":15255302,"fps":25,"url":["https://vdn.example/SD/source-0.mp4"]},{"key":20012,"name":"720P","label":"高清 720P","type":0,"quality":"HD","format":"mp4","codec":"H264","hdr_type":"SDR","maxbitrate":0,"bitrate":412.465,"duration":405.185,"channels":2,"sample_rate":44100,"width":1280,"height":720,"size":20890611,"fps":25,"url":["https://vdn.example/HD/source-1.mp4"]},{"key":20013,"name":"1080P","label":"超清 1080P","type":0,"quality":"FHD","format":"mp4","codec":"H264","hdr_type":"SDR","maxbitrate":0,"bitrate":664.239,"duration":405.185,"channels":2,"sample_rate":44100,"width":1920,"height":1080,"size":33642467,"fps":25,"url":["https://vdn.example/FHD/source-2.mp4"]}]}},"ui_config":{"plugins":[]},"template":null}"""
