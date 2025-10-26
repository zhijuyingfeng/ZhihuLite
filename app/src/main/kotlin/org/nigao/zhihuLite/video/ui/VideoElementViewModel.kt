package org.nigao.zhihuLite.video.ui

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.nigao.zhihuLite.h5Parser.HtmlNode
import org.nigao.zhihuLite.model.FeedItem
import org.nigao.zhihuLite.video.model.VideoPlayInfo
import org.nigao.zhihuLite.video.network.VideoPlayInfoApi

sealed class VideoPlayInfoState {
    object INITIALIZED: VideoPlayInfoState()
    object LOADING: VideoPlayInfoState()
    object FAILED: VideoPlayInfoState()
    class SUCCESS(val videoPlayInfo: VideoPlayInfo) : VideoPlayInfoState()
}

class VideoElementViewModel (
    val feedItem: FeedItem,
    val element: HtmlNode.Element,
    private val api: VideoPlayInfoApi
): ViewModel() {
    val playInfoState: StateFlow<VideoPlayInfoState>
        get() = _playInfoState
    private var _playInfoState: MutableStateFlow<VideoPlayInfoState> = MutableStateFlow(VideoPlayInfoState.INITIALIZED)

    suspend fun getPlayInfo() {
        val videoId = element.attributes["data-lens-id"]
        val answerId = feedItem.target?.id

        require(videoId?.isNotBlank() == true)
        require(answerId?.isNotBlank() == true)

        val path = "/api/v4/video/play_info?r=$answerId$videoId"
        val jsonObject = buildJsonObject {
            put("content_id", JsonPrimitive(answerId))
            put("content_type_str", JsonPrimitive("answer"))
            put("is_only_video", JsonPrimitive(true))
            put("scene_code", JsonPrimitive("answer_detail_web"))
            put("video_id", JsonPrimitive(videoId))
        }

        _playInfoState.value = VideoPlayInfoState.LOADING
        val videoPlayInfo = api.getVideoPlayInfo(path, Json.encodeToString(jsonObject))
        _playInfoState.value = if (videoPlayInfo == null) {
            VideoPlayInfoState.FAILED
        } else {
            VideoPlayInfoState.SUCCESS(videoPlayInfo)
        }
    }
}