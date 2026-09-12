package org.nigao.zhihuLite.base_navigation

/**
 * Navigation requests raised by screens and ViewModels.
 *
 * Exists so a ViewModel can say "the user wants to open this answer" without holding a
 * `NavController`: that would tie `business_ui` to a navigation implementation (and to `assemble`,
 * which registers the graph). The shell implements this interface.
 *
 * Deliberately only *forward* navigation: going back is a UI concern handled by the screen that
 * owns the button, not by a business rule.
 */
interface AppNavigator {
    fun navigateTo(route: AppRoute)
}
