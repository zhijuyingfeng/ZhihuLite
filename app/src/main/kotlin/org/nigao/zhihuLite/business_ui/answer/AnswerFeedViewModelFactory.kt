package org.nigao.zhihuLite.business_ui.answer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import org.nigao.zhihuLite.business_ui.AnswerWiring
import org.nigao.zhihuLite.business_ui.requireWiring

/**
 * Builds [AnswerFeedViewModel] with the process-wide Room database.
 *
 * `CreationExtras` is used (rather than holding a `Context` in the factory) so the factory cannot
 * leak an Activity, and so this can be swapped for a container-injected version in Phase 3.
 */
class AnswerFeedViewModelFactory(
    private val baseUrl: String,
    private val questionId: String,
    private val answerId: String?,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        if (!modelClass.isAssignableFrom(AnswerFeedViewModel::class.java)) {
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
        val wiring = extras.requireWiring<AnswerWiring>()
        return AnswerFeedViewModel(
            baseUrl = baseUrl,
            storage = wiring.storage,
            feedApi = wiring.feedApi,
            answerApi = wiring.answerApi,
            questionId = questionId,
        ) as T
    }
}
