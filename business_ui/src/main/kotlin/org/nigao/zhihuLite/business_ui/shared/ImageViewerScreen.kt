package org.nigao.zhihuLite.business_ui.shared

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import org.nigao.zhihuLite.business_logic.feed.data.sharedAnswerApi

/**
 * Full-screen image viewer.
 *
 * Hosts the loading of the answer's thumbnails: the route carries only ids (see
 * docs/REFACTOR_PLAN.md §4.8), so the screen resolves them itself instead of reading a
 * process-wide item map that did not survive process death.
 */
@Composable
fun ImageViewerScreen(
    answerId: String,
    initialPage: Int = 0,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Resolving by id also fixes a real defect: the old lookup used the *answer* id against a map
    // keyed by feed-item id, so it always missed and the viewer never opened at all.
    val thumbnails by produceState<List<String>?>(initialValue = null, answerId) {
        value = sharedAnswerApi.getAnswer(answerId)
            ?.target?.thumbnails
            ?.takeIf { it.isNotEmpty() }
    }

    thumbnails?.let { urls ->
        ImageViewer(
            imageUrls = urls,
            initialPage = initialPage,
            onDismiss = onDismiss,
            modifier = modifier
        )
    }
}
