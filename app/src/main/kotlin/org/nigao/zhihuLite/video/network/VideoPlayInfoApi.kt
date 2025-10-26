package org.nigao.zhihuLite.video.network

import io.github.aakira.napier.Napier
import kotlinx.serialization.json.Json
import org.nigao.zhihuLite.video.model.VideoPlayInfo
import org.nigao.zhihuLite.web.WebUtil

interface VideoPlayInfoApi {
    suspend fun getVideoPlayInfo(path: String, body: String): VideoPlayInfo?
}

class VideoPlayInfoWebApi: VideoPlayInfoApi {
    override suspend fun getVideoPlayInfo(path: String, body: String): VideoPlayInfo? {
        try {
            val response = WebUtil.request(path = path, method = "POST", body = body)
            Napier.i("getVideoPlayInfo: $response")
            val json = Json {
                ignoreUnknownKeys = true
            }
            if (response == null)  return null
            return json.decodeFromString<VideoPlayInfo>(response)
        }  catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}