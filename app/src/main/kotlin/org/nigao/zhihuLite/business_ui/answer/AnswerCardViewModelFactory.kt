package org.nigao.zhihuLite.business_ui.answer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class AnswerCardViewModelFactory(
    val answerId: String
): ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (!modelClass.isAssignableFrom(AnswerCardViewModel::class.java)) {
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
        return AnswerCardViewModel(answerId) as T
    }
}