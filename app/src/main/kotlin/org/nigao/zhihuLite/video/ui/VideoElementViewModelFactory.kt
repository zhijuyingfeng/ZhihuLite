package org.nigao.zhihuLite.video.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.nigao.zhihuLite.h5Parser.HtmlNode
import org.nigao.zhihuLite.video.network.sharedVideoPlayInfoApi

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