package org.nigao.zhihuLite.base_navigation

import kotlinx.serialization.Serializable

/**
 * Typed navigation destinations.
 *
 * These live below `assemble` on purpose: a screen in `business_ui` has to be able to *name* a
 * destination it navigates to, and `business_ui` is not allowed to import `assemble`. The route
 * objects are therefore shared vocabulary, like `ApiResult` — while the graph that *registers* them
 * stays in `assemble/navigation`.
 *
 * Type safety replaces the previous string templates (`"question_detail/$questionId/$answerId"`),
 * where an argument could be renamed or reordered with no compile-time complaint — and where a
 * missing optional argument had to be faked with a sentinel like `"0"`.
 */
sealed interface AppRoute

@Serializable
data object LogInRoute : AppRoute

@Serializable
data object LogOutRoute : AppRoute

@Serializable
data object MainFeedRoute : AppRoute

/**
 * Question detail.
 *
 * [answerId] is optional because the feed may not know which answer the user tapped (and because a
 * question can be opened from elsewhere entirely); the previous version passed `"0"` as a
 * placeholder, which the destination had to special-case.
 */
/**
 * Full-screen video playback.
 *
 * Both ids are required: the play-info request needs the answer as `content_id`, and a video plate
 * only offers playback when it has both (a video inside a comment has no answer id).
 */
@Serializable
data class FullScreenVideoRoute(
    val answerId: String,
    val videoId: String,
) : AppRoute

@Serializable
data class QuestionDetailRoute(
    val questionId: String,
    val answerId: String? = null,
) : AppRoute

/**
 * Full-screen image viewer.
 *
 * [page] used to be smuggled through `savedStateHandle` (and was therefore always null, since
 * nothing ever put it there); as a typed argument it is simply passed.
 */
@Serializable
data class ImageViewerRoute(
    val answerId: String,
    val page: Int = 0,
    /**
     * The picture that was tapped, when the tap came from inside the body.
     *
     * It selects *which* list the viewer pages through: the answer's body images, starting at this
     * one. The card's cover carries no url and pages through the answer's `thumbnails` as before.
     */
    val imageUrl: String? = null,
) : AppRoute
