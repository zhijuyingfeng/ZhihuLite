package org.nigao.zhihuLite.comment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class CommentViewModelFactory(
    val answerId: String
): ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (!modelClass.isAssignableFrom(CommentViewModel::class.java)) {
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }

        @Suppress("UNCHECKED_CAST")
        return CommentViewModel(answerId) as T
    }
}