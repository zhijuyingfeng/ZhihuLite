package org.nigao.zhihuLite.business_logic.share

import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import org.nigao.zhihuLite.business_logic.zhihu.sharedJson
import org.nigao.zhihuLite.business_logic.zhihu.ZhihuApi
import org.nigao.zhihuLite.model.share.ShareInfo

interface ShareInfoApi {
    suspend fun getAnswerShareInfo(answerId: String): ShareInfo?
}

class ShareInfoWebApi: ShareInfoApi {
    override suspend fun getAnswerShareInfo(answerId: String): ShareInfo? {
        try {
            val path = "/api/v4/answers/${answerId}?include=share_text"
            val response = ZhihuApi.request(path = path, method = "GET")
            if (response == null)  return null
            return sharedJson.decodeFromString<ShareInfo>(response)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.e("Failed to load share info for answer $answerId", e)
            return null
        }
    }

}

val sharedShareInfoApi = ShareInfoWebApi()
