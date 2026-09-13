package org.nigao.zhihuLite.business_ui.comment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.nigao.zhihuLite.business_ui.shared.LoadMoreResult
import org.nigao.zhihuLite.model.comment.ChildCommentResponse
import org.nigao.zhihuLite.model.comment.Comment
import org.nigao.zhihuLite.model.comment.CommentResponse
import org.nigao.zhihuLite.business_logic.comment.CommentApi
import org.nigao.zhihuLite.business_logic.comment.CommentSortType
import org.nigao.zhihuLite.business_logic.comment.CommentSource

class CommentViewModel(
    val answerId: String,
    /**
     * How a source is built for a given ordering.
     *
     * A factory rather than a source so the suite can drive paging and replies with a fake: the
     * production default is [CommentApi], and a test passes its own.
     */
    private val createSource: (CommentSortType) -> CommentSource = { CommentApi(answerId, it) },
) : ViewModel() {
    private val _uiState: MutableStateFlow<CommentViewUiState> = MutableStateFlow(CommentViewUiState.Loading)
    val uiState: StateFlow<CommentViewUiState> = _uiState.asStateFlow()

    private var sortType = CommentSortType.SCORE

    var repository: CommentSource = createSource(CommentSortType.SCORE)

    /** Serializes root pages so a pending request can never be joined by a second one. */
    private val loadMutex = Mutex()

    /**
     * Serializes reply pages.
     *
     * One lock for all comments rather than one per comment: expanding and paging replies are
     * one-at-a-time reader actions, and the state's own `isLoading` flag is what stops a double tap.
     */
    private val childrenMutex = Mutex()

    init {
        viewModelScope.launch {
            loadComments()
        }
    }

    suspend fun loadComments(): LoadMoreResult = loadMutex.withLock {
        val repositoryAtStart = repository
        val currentState = uiState.value
        if (currentState is CommentViewUiState.Success &&
            (!currentState.hasMore || !repositoryAtStart.hasMore)
        ) {
            return@withLock LoadMoreResult.NO_MORE_DATA
        }

        val response = repositoryAtStart.loadRootComments()

        // The sort was switched while this page was in flight: drop the stale result instead
        // of appending it to the freshly reset list.
        if (repositoryAtStart !== repository) {
            return@withLock LoadMoreResult.FAILED
        }

        if (uiState.value !is CommentViewUiState.Success && response == null) {
            _uiState.value = CommentViewUiState.Failed("Failed to load comments")
            return@withLock LoadMoreResult.FAILED
        } else if (response != null) {
            when (val successfulState = _uiState.value) {
                is CommentViewUiState.Success -> {
                    _uiState.value = successfulState.copy(
                        totalCount = response.paging.totals,
                        comments = successfulState.comments + response.commentUiStates(),
                        hasMore = !response.paging.isEnd,
                    )
                }
                else -> {
                    _uiState.value = CommentViewUiState.Success(
                        sortType = sortType,
                        totalCount = response.paging.totals,
                        comments = response.commentUiStates(),
                        hasMore = !response.paging.isEnd,
                    )
                }
            }
            return@withLock if (response.hasComments) LoadMoreResult.SUCCESS else LoadMoreResult.NO_MORE_DATA
        }
        return@withLock LoadMoreResult.FAILED
    }

    /**
     * Switching the ordering rebuilds the source (whose ordering is fixed at construction) which also
     * clears the pagination cursor and every comment's replies, resets the list to loading and
     * reloads from the first page of the new ordering.
     */
    fun updateSortType(newSortType: CommentSortType) {
        if (sortType == newSortType) return
        sortType = newSortType
        repository = createSource(newSortType)
        _uiState.value = CommentViewUiState.Loading
        viewModelScope.launch {
            loadComments()
        }
    }

    /** Show or hide one comment's replies, loading the first page the first time it is opened. */
    fun toggleChildren(commentId: String) {
        val current = childrenState(commentId)
        if (current.isExpanded) {
            updateChildren(commentId) { it.copy(isExpanded = false) }
            return
        }
        updateChildren(commentId) { it.copy(isExpanded = true, failed = false) }
        if (current.comments.isEmpty()) {
            loadChildrenPage(commentId, append = false)
        }
    }

    /** Retry after a failed first page (or a failed "show more"). */
    fun retryChildren(commentId: String) {
        updateChildren(commentId) { it.copy(failed = false) }
        loadChildrenPage(commentId, append = childrenState(commentId).comments.isNotEmpty())
    }

    /** Append the next page of replies. */
    fun loadMoreChildren(commentId: String) {
        loadChildrenPage(commentId, append = true)
    }

    private fun loadChildrenPage(commentId: String, append: Boolean) {
        val current = childrenState(commentId)
        // The state is the guard, not the lock: a second tap while a page is in flight must not
        // queue another identical request.
        if (current.isLoading) return
        if (append && (!current.hasMore || current.nextCursor == null)) return
        val cursor = if (append) current.nextCursor else null
        val knownReplies = current.comments

        updateChildren(commentId) { it.copy(isLoading = true, failed = false) }

        viewModelScope.launch {
            childrenMutex.withLock {
                val source = repository
                val response = try {
                    source.loadChildComments(commentId, cursor)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }

                // The ordering changed while this page was in flight: its replies belong to a list
                // that no longer exists.
                if (source !== repository) {
                    updateChildren(commentId) { it.copy(isLoading = false) }
                    return@withLock
                }

                if (response == null) {
                    // Keep whatever was already loaded; only the "show more" attempt failed.
                    updateChildren(commentId) { it.copy(isLoading = false, failed = true) }
                    return@withLock
                }

                val page = replyUiStates(response, commentId, knownReplies)
                updateChildren(commentId) { previous ->
                    // `isExpanded` is deliberately not touched: the reader may have collapsed the
                    // section while this page was in flight, and re-opening it under them would be
                    // the page's decision rather than theirs.
                    previous.copy(
                        comments = if (append) previous.comments + page else page,
                        isLoading = false,
                        failed = false,
                        hasMore = !response.paging.isEnd && response.paging.next.isNotBlank(),
                        nextCursor = response.paging.next.takeIf { it.isNotBlank() },
                    )
                }
            }
        }
    }

    /**
     * Maps one page of replies, resolving "reply to whom".
     *
     * The reply object only carries `reply_comment_id`, so the name comes from whoever is known: the
     * parent the server sends back with every page, the replies already loaded, and this page itself.
     * A reply aimed at the root gets no prefix — the indentation already says it.
     */
    private fun replyUiStates(
        response: ChildCommentResponse,
        rootCommentId: String,
        alreadyLoaded: List<CommentUiState>,
    ): List<CommentUiState> {
        val authorsById = buildMap {
            response.root?.let { put(it.id, it.authorNameOf()) }
            alreadyLoaded.forEach { reply -> reply.id?.let { put(it, reply.authorName) } }
            response.data.forEach { reply -> put(reply.id, reply.authorNameOf()) }
        }
        return response.data.map { reply ->
            val target = reply.replyCommentId
            val replyTo = if (target.isNotBlank() && target != "0" && target != rootCommentId) {
                authorsById[target]
            } else {
                null
            }
            reply.toUiState(replyToAuthor = replyTo)
        }
    }

    private fun childrenState(commentId: String): CommentChildrenState {
        val success = _uiState.value as? CommentViewUiState.Success ?: return CommentChildrenState()
        return success.children[commentId] ?: CommentChildrenState()
    }

    private fun updateChildren(
        commentId: String,
        transform: (CommentChildrenState) -> CommentChildrenState,
    ) {
        val success = _uiState.value as? CommentViewUiState.Success ?: return
        val next = transform(success.children[commentId] ?: CommentChildrenState())
        _uiState.value = success.copy(children = success.children + (commentId to next))
    }
}

private fun Comment.authorNameOf(): String = author.name

/** Replies are mapped through the same path as root comments. */
private fun ChildCommentResponse.commentUiStates(): List<CommentUiState> = data.map { it.toUiState() }
