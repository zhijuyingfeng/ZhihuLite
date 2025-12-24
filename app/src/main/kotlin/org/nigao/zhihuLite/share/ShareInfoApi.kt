package org.nigao.zhihuLite.share

import io.github.aakira.napier.Napier
import org.nigao.zhihuLite.network.sharedJson
import org.nigao.zhihuLite.web.WebUtil

interface ShareInfoApi {
    suspend fun getAnswerShareInfo(answerId: String): ShareInfo?
}

class ShareInfoWebApi: ShareInfoApi {
    override suspend fun getAnswerShareInfo(answerId: String): ShareInfo? {
        try {
            val path = "/api/v4/answers/${answerId}?include=share_text"
            val response = WebUtil.request(path = path, method = "GET")
            Napier.i("getShareInfo: $response")
            if (response == null)  return null
            return sharedJson.decodeFromString<ShareInfo>(response)
        }  catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

}

val sharedShareInfoApi = ShareInfoWebApi()