package org.nigao.zhihuLite.business_ui.video

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.nigao.zhihuLite.business_logic.answer.HtmlNode
import org.nigao.zhihuLite.business_logic.video.data.sharedVideoPlayInfoApi

class VideoElementViewModelFactory(
    val answerId: String?,
    val element: HtmlNode.Element
):ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (!modelClass.isAssignableFrom(VideoElementViewModel::class.java)) {
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }

        return VideoElementViewModel(
            answerId = answerId,
            element = element,
            api = sharedVideoPlayInfoApi
        ) as T
    }
}