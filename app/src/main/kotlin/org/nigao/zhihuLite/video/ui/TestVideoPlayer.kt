package org.nigao.zhihuLite.video.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.nigao.zhihuLite.h5Parser.HtmlToComposeUi
import org.nigao.zhihuLite.model.FeedItem
import org.nigao.zhihuLite.model.Target
import org.nigao.zhihuLite.model.User

@Composable
fun TestVideoPlayer() {
    val feedItem = mockFeedItem()
    HtmlToComposeUi(
        html = feedItem.target?.content.toString(),
        answerId = "1952510213304334301",
        modifier = Modifier.fillMaxSize(),
    )
}

fun mockFeedItem(): FeedItem {
    val target = Target(
        id = "1952510213304334301",
        type = "answer",
        url = "https://www.zhihu.com/answer/123456",
        author = User(
            id = "user_0",
            url = "",
            userType = "",
            urlToken = "",
            name = "Anonymous",
            headline = "",
            avatarUrl = "",
            isOrg = false,
            gender = 0,
            followersCount = 0,
            isFollowing = false,
            isFollowed = false
        ),
        createdTime = 1672531200000,  // Timestamp for Jan 1, 2023
        updatedTime = 1672617600000,  // Timestamp for Jan 2, 2023
        voteupCount = 256,
        thanksCount = 32,
        commentCount = 18,
        isCopyable = true,
        question = null,
        thumbnail = "https://thumbnail.example.com/video_cover.jpg",
        thumbnails = listOf(
            "https://thumbnail.example.com/cover_small.jpg",
            "https://thumbnail.example.com/cover_large.jpg"
        ),
        excerpt = "Here's a step-by-step guide to implementing a video player with cover image in Kotlin Multiplatform...",
        excerptNew = "Updated guide for KMP video player implementation",
        previewType = "video",
        previewText = "Click to watch the tutorial",
        reshipmentSettings = "allowed",
        content = """
            <p data-pid="8QQEkOLd">看看我们大广西的猛男蛇吧，桂林一带的</p>
            <a class="video-box" href="https://link.zhihu.com/?target=https%3A//www.zhihu.com/video/1952509956533236810" target="_blank" data-video-id="" data-video-playable="" data-name="" data-poster=
            "https://pic1.zhimg.com/v2-a0c708c3776bd3bb23ef8f9349f8fef0_720w.jpg?source=ccfced1a" data-lens-id="1952509956533236810"><img class="thumbnail" src="https://pic1.zhimg.com/v2-a0c708c3776bd3bb23ef8f9349f8fef0_720w.jpg?source=ccfced1a"/><span class="content"><span class="title"><span class="z-ico-extern-gray"></span><span class="z-ico-extern-blue"></span></span>
            <span
                    class="url"><span class="z-ico-video"></span>https://www.zhihu.com/video/1952509956533236810</span>
            </span>
            </a>
        """.trimIndent(),
        relationship = null,
        isLabeled = true,
        visitedCount = 1200,
        favoriteCount = 150,
        answerType = "video_tutorial",
        isNavigator = true,
        navigatorVote = false,
        voteNextStep = "confirm"
    )
    return FeedItem(target = target)
}