package org.nigao.zhihuLite.business_ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.CoroutineScope
import org.nigao.zhihuLite.business_logic.feed.data.AnswerApi
import org.nigao.zhihuLite.business_logic.feed.data.FeedStorage
import org.nigao.zhihuLite.business_logic.feed.FeedOperations
import org.nigao.zhihuLite.business_logic.zhihu.FeedApi

/**
 * What a screen needs from the container, declared on the screen's side of the boundary.
 *
 * The point is the **direction of the dependency**: `business_ui` must not import `assemble`
 * (docs/REFACTOR_PLAN.md §3.1 rule 6), yet it needs the objects the container builds. So each
 * feature declares the slice it needs, `assemble/container/AppContainer` implements it, and the
 * container type never appears in a feature.
 *
 * These interfaces deliberately live at the root of `business_ui` rather than in `base_logic` or
 * `base_navigation`: both of those are Compose- and Android-free (that is what makes them testable
 * in a plain JVM test, §3.4), and resolving a wiring needs `CreationExtras`. A feature module reads
 * its wiring from here; modules still never import each other.
 *
 * Naming: each interface is named after the feature it serves, and its properties are general
 * (`feedOperations`, `feedStorage`) rather than repeated per feature — one container implements
 * several of these at once, so identical names would not compile. The names below are shared only
 * because the answer list and the recommendation feed genuinely need the same three objects.
 */
interface FeedWiring {
    val operations: FeedOperations
    val storage: FeedStorage
    val feedApi: FeedApi
}

interface AnswerWiring {
    val storage: FeedStorage
    val feedApi: FeedApi
    val answerApi: AnswerApi

    /**
     * A scope that outlives a screen, for work that has to survive the ViewModel.
     *
     * `onCleared` is the only place that knows the reader left, and by then `viewModelScope` is
     * already cancelled — a teardown delete started there would never run. The container owns the
     * scope (process-wide), the screen only borrows it.
     */
    val screenTeardownScope: CoroutineScope
}

/**
 * Implemented by the `Application`: it hands out the composition root it built.
 *
 * Declared here rather than in `assemble` because it is what [requireWiring] looks the container up
 * through, and this layer must not import `assemble`. The container's own type stays opaque at this
 * level (`C`), so `assemble` names `AppContainer` at the implementation site and nowhere else.
 */
interface ContainerHolder<out C : Any> {
    val container: C
}

/**
 * Resolves a wiring interface from the app container, via the `Application` androidx puts into
 * `CreationExtras`.
 *
 * The feature calls this without knowing anything about `assemble`: the lookup goes
 * `CreationExtras` -> `Application` -> [ContainerHolder.container] -> the requested wiring slice.
 *
 * The hop through [ContainerHolder] is not optional: the `Application` is the holder, **not** the
 * wiring. Casting the `Application` straight to `T` compiles but always fails at runtime, because
 * `DefaultApplication` implements `ContainerHolder` while `AppContainer` implements the `*Wiring`
 * interfaces — which crashed the app on launch with "The Application does not provide FeedWiring".
 *
 * Fails loudly. A missing wiring means the screen was created outside the app's container, and
 * returning null would only move the failure to some unrelated later call.
 */
inline fun <reified T : Any> CreationExtras.requireWiring(): T {
    val application = requireNotNull(
        this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY],
    ) {
        "No Application in CreationExtras: cannot resolve ${T::class.simpleName}"
    }
    val holder = requireNotNull(application as? ContainerHolder<*>) {
        "The Application (${application.javaClass.name}) does not implement ContainerHolder: " +
            "cannot resolve ${T::class.simpleName}"
    }
    return requireNotNull(holder.container as? T) {
        "The app container (${holder.container.javaClass.name}) does not provide " +
            "${T::class.simpleName}: the screen must be created by the app's container"
    }
}
