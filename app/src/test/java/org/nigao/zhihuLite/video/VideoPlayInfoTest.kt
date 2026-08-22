package org.nigao.zhihuLite.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nigao.zhihuLite.network.sharedJson
import org.nigao.zhihuLite.video.model.BeginFrame
import org.nigao.zhihuLite.video.model.Meta
import org.nigao.zhihuLite.video.model.Resolution
import org.nigao.zhihuLite.video.model.UiConfig
import org.nigao.zhihuLite.video.model.VideoItem
import org.nigao.zhihuLite.video.model.VideoPlay
import org.nigao.zhihuLite.video.model.VideoPlayInfo
import org.nigao.zhihuLite.video.model.VideoPlaylist
import org.nigao.zhihuLite.video.model.Za

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
                    bitrate = 900,
                    size = 10_000_000,
                    url = "https://example.com/h264-1080.mp4",
                ),
                videoItem(
                    quality = "HD",
                    codec = "H265",
                    bitrate = 320,
                    size = 3_600_000,
                    url = "https://example.com/h265-720.mp4",
                ),
                videoItem(
                    quality = "SD",
                    codec = "H264",
                    bitrate = 300,
                    size = 3_000_000,
                    url = "https://example.com/h264-480.mp4",
                ),
                videoItem(
                    quality = "HD",
                    codec = "H264",
                    bitrate = 500,
                    size = 5_700_000,
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

    private fun videoItem(
        quality: String,
        codec: String,
        url: String,
        bitrate: Int = 500,
        size: Int = 5_000_000,
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
