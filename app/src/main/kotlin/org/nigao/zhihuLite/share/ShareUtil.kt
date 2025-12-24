package org.nigao.zhihuLite.share

import android.content.Context
import android.content.Intent


suspend fun shareAnswer(answerId: String, context: Context) {
    val shareInfo = sharedShareInfoApi.getAnswerShareInfo(answerId = answerId)

    shareInfo?.let {
        shareText(context = context, text = shareInfo.shareText)
    }
}

fun shareText(context: Context, text: String) {
    val shareIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, text)
        type = "text/plain"
    }
    context.startActivity(
        Intent.createChooser(shareIntent, "Share via")
    )
}