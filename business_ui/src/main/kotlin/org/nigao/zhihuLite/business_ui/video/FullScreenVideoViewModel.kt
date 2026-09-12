package org.nigao.zhihuLite.business_ui.video

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.nigao.zhihuLite.business_logic.video.data.VideoPlayInfoApi
import org.nigao.zhihuLite.business_ui.AnswerWiring
import org.nigao.zhihuLite.business_ui.requireWiring
import org.nigao.zhihuLite.model.video.VideoPlayInfo

/** What the full-screen player can show. */
sealed interface FullScreenVideoUiState {

    data object Loading : FullScreenVideoUiState

    /**
     * [urls] are playback sources in preference order (720P H.264 first, see
     * [VideoPlayInfo.getPlayableUrls]); [aspectRatio] is width / height, so the surface can be laid
     * out full width while keeping the video's own shape.
     */
    data class Ready(val urls: List<String>, val aspectRatio: Float) : FullScreenVideoUiState

    /** Nothing playable came back, or the request failed. The screen offers a retry. */
    data object Failed : FullScreenVideoUiState
}

/**
 * Loads the playback sources for one video.
 *
 * The request is the one the inline player used to build: `/api/v4/video/play_info` with the answer
 * as `content_id`. A video that lives in a comment has no answer id, and the plate that renders it
 * refuses to offer playback in that case, so both ids are required here.
 */
class FullScreenVideoViewModel(
    private val answerId: String,
    private val videoId: String,
    private val api: VideoPlayInfoApi,
) : ViewModel() {

    private val _uiState = MutableStateFlow<FullScreenVideoUiState>(FullScreenVideoUiState.Loading)
    val uiState: StateFlow<FullScreenVideoUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        load()
    }

    fun retry() = load()

    private fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = FullScreenVideoUiState.Loading
            val info = try {
                api.getVideoPlayInfo(path = path(), body = body())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            val urls = info?.getPlayableUrls().orEmpty()
            _uiState.value = if (urls.isEmpty()) {
                FullScreenVideoUiState.Failed
            } else {
                FullScreenVideoUiState.Ready(urls = urls, aspectRatio = aspectRatioOf(info))
            }
        }
    }

    /** Request target; public so a test can assert exactly what goes on the wire. */
    fun path(): String = "/api/v4/video/play_info?r=$answerId$videoId"

    /** Request body; public for the same reason as [path]. */
    fun body(): String = Json.encodeToString(
        buildJsonObject {
            put("content_id", JsonPrimitive(answerId))
            put("content_type_str", JsonPrimitive("answer"))
            put("is_only_video", JsonPrimitive(true))
            put("scene_code", JsonPrimitive("answer_detail_web"))
            put("video_id", JsonPrimitive(videoId))
        },
    )

    /**
     * The video's own shape, from the play-info metadata.
     *
     * 16:9 only when the response does not carry a usable resolution: a portrait video laid out as
     * landscape would be wrong twice over — wrong box, and the wrong amount of screen used.
     */
    private fun aspectRatioOf(info: VideoPlayInfo?): Float {
        val resolution = info?.videoPlay?.meta?.resolution
        return if (resolution != null && resolution.height > 0 && resolution.width > 0) {
            resolution.width.toFloat() / resolution.height.toFloat()
        } else {
            DEFAULT_ASPECT_RATIO
        }
    }

    private companion object {
        const val DEFAULT_ASPECT_RATIO = 16f / 9f
    }
}

class FullScreenVideoViewModelFactory(
    private val answerId: String,
    private val videoId: String,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        if (!modelClass.isAssignableFrom(FullScreenVideoViewModel::class.java)) {
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
        // Same wiring the other screens resolve; the container provides the video api.
        val wiring = extras.requireWiring<AnswerWiring>()
        return FullScreenVideoViewModel(
            answerId = answerId,
            videoId = videoId,
            api = wiring.videoApi,
        ) as T
    }
}
