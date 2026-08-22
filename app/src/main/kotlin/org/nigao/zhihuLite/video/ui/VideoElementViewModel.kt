package org.nigao.zhihuLite.video.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.nigao.zhihuLite.h5Parser.HtmlNode
import org.nigao.zhihuLite.video.model.VideoPlayInfo
import org.nigao.zhihuLite.video.network.VideoPlayInfoApi

sealed interface VideoPlayInfoState {
    data object Initialized : VideoPlayInfoState
    data object Loading : VideoPlayInfoState
    data object Failed : VideoPlayInfoState
    data class Success(val videoPlayInfo: VideoPlayInfo) : VideoPlayInfoState
}

class VideoElementViewModel(
    private val answerId: String?,
    private val element: HtmlNode.Element,
    private val api: VideoPlayInfoApi
): ViewModel() {
    private val _playInfoState =
        MutableStateFlow<VideoPlayInfoState>(VideoPlayInfoState.Initialized)
    val playInfoState: StateFlow<VideoPlayInfoState> = _playInfoState.asStateFlow()

    private var loadJob: Job? = null

    fun getPlayInfo(forceRefresh: Boolean = false) {
        if (!forceRefresh &&
            (_playInfoState.value is VideoPlayInfoState.Loading ||
                _playInfoState.value is VideoPlayInfoState.Success)
        ) {
            return
        }

        val videoId = element.attributes["data-lens-id"]?.takeIf(String::isNotBlank)
        val contentId = answerId?.takeIf(String::isNotBlank)
        if (videoId == null || contentId == null) {
            _playInfoState.value = VideoPlayInfoState.Failed
            return
        }

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _playInfoState.value = VideoPlayInfoState.Loading

            val path = "/api/v4/video/play_info?r=$contentId$videoId"
            val jsonObject = buildJsonObject {
                put("content_id", JsonPrimitive(contentId))
                put("content_type_str", JsonPrimitive("answer"))
                put("is_only_video", JsonPrimitive(true))
                put("scene_code", JsonPrimitive("answer_detail_web"))
                put("video_id", JsonPrimitive(videoId))
            }

            val videoPlayInfo = api.getVideoPlayInfo(
                path = path,
                body = Json.encodeToString(jsonObject),
            )
            _playInfoState.value = if (videoPlayInfo == null) {
                VideoPlayInfoState.Failed
            } else {
                VideoPlayInfoState.Success(videoPlayInfo)
            }
        }
    }
}
