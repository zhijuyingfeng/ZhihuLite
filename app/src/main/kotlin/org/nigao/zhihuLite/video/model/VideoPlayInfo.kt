package org.nigao.zhihuLite.video.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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
    val template: JsonElement? = null
) {
    /**
     * Returns playback sources in preference order. A 720P H.264 source is preferred because it
     * is broadly hardware-decodable and avoids the startup/buffering cost of always choosing the
     * largest file. Remaining qualities/codecs are retained as automatic fallbacks.
     */
    fun getPlayableUrls(): List<String> {
        return videoPlay.playlist.mp4
            .asSequence()
            .filter { item ->
                item.format.equals("mp4", ignoreCase = true) &&
                    item.url.any { it.isNotBlank() }
            }
            .sortedWith(
                compareBy<VideoItem>(
                    { it.playbackPreference },
                    { it.bitrate.takeIf { bitrate -> bitrate > 0 } ?: Int.MAX_VALUE },
                    { it.size.takeIf { size -> size > 0 } ?: Int.MAX_VALUE },
                )
            )
            .flatMap { it.url.asSequence() }
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
            .toList()
    }

    fun getPlayableUrl(): String? {
        return getPlayableUrls().firstOrNull()
    }
}

private val VideoItem.playbackPreference: Int
    get() {
        val normalizedCodec = codec.uppercase()
        val normalizedQuality = quality.uppercase()
        val isH264 = normalizedCodec == "H264" || normalizedCodec.contains("AVC")
        val isH265 = normalizedCodec == "H265" || normalizedCodec.contains("HEVC")

        return when {
            isH264 && normalizedQuality == "HD" -> 0
            isH264 && normalizedQuality == "SD" -> 1
            isH265 && normalizedQuality == "HD" -> 2
            isH265 && normalizedQuality == "SD" -> 3
            isH264 && normalizedQuality == "FHD" -> 4
            isH265 && normalizedQuality == "FHD" -> 5
            normalizedQuality == "HD" -> 6
            normalizedQuality == "SD" -> 7
            normalizedQuality == "FHD" -> 8
            else -> 9
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
    val svcs: Svcs? = null,
    @SerialName("play_count")
    val playCount: Long,
    @SerialName("meta")
    val meta: Meta,
    @SerialName("begin_frame")
    val beginFrame: BeginFrame? = null,
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
    val hd: String? = null,
    @SerialName("SD")
    val sd: String? = null
)

@Serializable
data class VideoPlaylist(
    @SerialName("mp3")
    val mp3: List<VideoItem> = emptyList(),
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
    val plugins: List<JsonElement> = emptyList()
)
