package org.nigao.zhihuLite.video.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.JsonElement

@Serializable
data class VideoPlayInfo(
    @SerialName("za")
    val za: Za,
    @SerialName("video_play")
    val videoPlay: VideoPlay,
    @SerialName("ui_config")
    val uiConfig: UiConfig,
    @SerialName("template")
    val template: JsonElement? = null // Use JsonElement for unknown/nullable types
) {
    fun getPlayableUrl(): String? {
        val playList = videoPlay.playlist.mp4.sortedByDescending {
            it.size
        }
        return playList.first { it.url.isNotEmpty() }.url.first()
    }
}

@Serializable
data class Za(
    @SerialName("content_id")
    val contentId: String,
    @SerialName("content_type")
    val contentType: Int,
    @SerialName("content_token")
    val contentToken: String
)

@Serializable
data class VideoPlay(
    @SerialName("id")
    val id: String,
    @SerialName("default_cover")
    val defaultCover: String,
    @SerialName("is_paid")
    val isPaid: Boolean,
    @SerialName("is_trial")
    val isTrial: Boolean,
    @SerialName("svcs")
    val svcs: Svcs,
    @SerialName("play_count")
    val playCount: Long,
    @SerialName("meta")
    val meta: Meta,
    @SerialName("begin_frame")
    val beginFrame: BeginFrame,
    @SerialName("playlist")
    val playlist: VideoPlaylist
)

@Serializable
data class Svcs(
    @SerialName("value")
    val value: Int,
    @SerialName("reason")
    val reason: String
)

@Serializable
data class Meta(
    @SerialName("mime")
    val mime: String,
    @SerialName("duration")
    val duration: Double,
    @SerialName("resolution")
    val resolution: Resolution,
    @SerialName("hdr_type")
    val hdrType: String
)

@Serializable
data class Resolution(
    @SerialName("quality")
    val quality: String,
    @SerialName("width")
    val width: Int,
    @SerialName("height")
    val height: Int
)

@Serializable
data class BeginFrame(
    @SerialName("HD")
    val hd: String,
    @SerialName("SD")
    val sd: String
)

@Serializable
data class VideoPlaylist(
    @SerialName("mp3")
    val mp3: List<VideoItem> = emptyList(), // Use JsonElement for unknown array types
    @SerialName("mp4")
    val mp4: List<VideoItem> = emptyList()
)

@Serializable
data class VideoItem(
    @SerialName("key")
    val key: Int,
    @SerialName("name")
    val name: String,
    @SerialName("label")
    val label: String,
    @SerialName("type")
    val type: Int,
    @SerialName("quality")
    val quality: String,
    @SerialName("format")
    val format: String,
    @SerialName("codec")
    val codec: String,
    @SerialName("hdr_type")
    val hdrType: String,
    @SerialName("maxbitrate")
    val maxbitrate: Int,
    @SerialName("bitrate")
    val bitrate: Int,
    @SerialName("duration")
    val duration: Double,
    @SerialName("channels")
    val channels: Int,
    @SerialName("sample_rate")
    val sampleRate: Int,
    @SerialName("width")
    val width: Int,
    @SerialName("height")
    val height: Int,
    @SerialName("size")
    val size: Int,
    @SerialName("fps")
    val fps: Int,
    @SerialName("url")
    val url: List<String>
)

@Serializable
data class UiConfig(
    @SerialName("plugins")
    val plugins: List<JsonElement> = emptyList() // Use JsonElement for unknown array types
)