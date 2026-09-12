package org.nigao.zhihuLite.business_ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import org.nigao.zhihuLite.business_ui.FeedWiring
import org.nigao.zhihuLite.business_ui.requireWiring

/**
 * Builds [FeedViewModel] from the recommendation feed's wiring.
 *
 * Note what this file does **not** do: import `assemble`, or name the container. The wiring interface
 * is declared on this side of the boundary (`business_ui/Wiring.kt`) and implemented by the container,
 * so the dependency points downward (docs/REFACTOR_PLAN.md §3.1 rule 6).
 */
class FeedViewModelFactory : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        if (!modelClass.isAssignableFrom(FeedViewModel::class.java)) {
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
        val wiring = extras.requireWiring<FeedWiring>()
        return FeedViewModel(operations = wiring.operations) as T
    }
}
