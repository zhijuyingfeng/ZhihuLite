package org.nigao.zhihuLite.di

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import kotlinx.serialization.json.Json
import io.ktor.serialization.kotlinx.json.json
import org.koin.core.context.startKoin
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.nigao.zhihuLite.data.FeedRepository
import org.nigao.zhihuLite.data.FeedStorage
import org.nigao.zhihuLite.data.MemoryFeedStorage
import org.nigao.zhihuLite.model.FeedItem
import org.nigao.zhihuLite.network.FeedApi
import org.nigao.zhihuLite.network.KtorFeedApi
import org.nigao.zhihuLite.network.WebviewFeedApi
import org.nigao.zhihuLite.mainFeed.ui.FeedViewModel
import org.nigao.zhihuLite.answerFeed.ui.AnswerViewModel
import org.nigao.zhihuLite.eventReporter.EventReporter
import org.nigao.zhihuLite.h5Parser.HtmlNode
import org.nigao.zhihuLite.video.network.VideoPlayInfoApi
import org.nigao.zhihuLite.video.network.VideoPlayInfoWebApi
import org.nigao.zhihuLite.video.ui.VideoElementViewModel

val NATIVE_API = named("native_api")
val WEBVIEW_API = named("webview_api")

val dataModule = module {
    single {
        val json = Json { ignoreUnknownKeys = true }
        HttpClient {
            install(ContentNegotiation) {
                // TODO Fix API so it serves application/json
                json(json, contentType = ContentType.Any)
            }
        }
    }

    single<FeedApi>(NATIVE_API) { KtorFeedApi(get()) }
    single<FeedApi>(WEBVIEW_API) { WebviewFeedApi() }
    single<VideoPlayInfoApi> { VideoPlayInfoWebApi() }
    single<EventReporter> { EventReporter(get()) }

    factory<FeedStorage> {
        MemoryFeedStorage()
    }

    factory(NATIVE_API) { (baseUrl: String, initialItems: List<FeedItem>) ->
        FeedRepository(
            initialUrl = baseUrl,
            feedApi = get(NATIVE_API),
            feedStorage = get(),
            initialItems = initialItems,
        )
    }

    factory(WEBVIEW_API) { (baseUrl: String, initialItems: List<FeedItem>) ->
        FeedRepository(
            initialUrl = baseUrl,
            feedApi = get(WEBVIEW_API),
            feedStorage = get(),
            initialItems = initialItems,
        )
    }
}

val viewModelModule = module {
    factory { (baseUrl: String, initialItems: List<FeedItem>) ->
        FeedViewModel(
            feedRepository = get(NATIVE_API) {
                parametersOf(baseUrl, initialItems)
            },
            eventReporter = get()
        )
    }

    factory { (baseUrl: String, initialItems: List<FeedItem>) ->
        AnswerViewModel(
            feedRepository = get(WEBVIEW_API) {
                parametersOf(baseUrl, initialItems)
            },
            eventReporter = get()
        )
    }

    factory { (feedItem: FeedItem, element: HtmlNode.Element) ->
        VideoElementViewModel(
            feedItem = feedItem,
            element = element,
            api = get()
        )
    }
}

fun initKoin() {
    startKoin {
        modules(
            dataModule,
            viewModelModule,
        )
    }
}