package org.nigao.zhihuLite.business_logic.video.data

import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.nigao.zhihuLite.business_logic.zhihu.ZhihuApi
import org.nigao.zhihuLite.business_logic.zhihu.sharedJson
import org.nigao.zhihuLite.model.video.VideoPlayInfo

interface VideoPlayInfoApi {
    suspend fun getVideoPlayInfo(path: String, body: String): VideoPlayInfo?
}

class VideoPlayInfoWebApi : VideoPlayInfoApi {
    override suspend fun getVideoPlayInfo(path: String, body: String): VideoPlayInfo? {
        return try {
            val response = ZhihuApi.request(path = path, method = "POST", body = body)
                ?: return null
            withContext(Dispatchers.Default) {
                sharedJson.decodeFromString<VideoPlayInfo>(response)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.e("Failed to load video play info for $path", e)
            null
        }
    }
}

val sharedVideoPlayInfoApi = VideoPlayInfoWebApi()
