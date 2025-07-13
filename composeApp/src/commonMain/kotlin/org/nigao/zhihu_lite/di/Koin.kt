package org.nigao.zhihu_lite.di

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import kotlinx.serialization.json.Json
import io.ktor.serialization.kotlinx.json.json
import org.koin.core.context.startKoin
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.nigao.zhihu_lite.data.FeedRepository
import org.nigao.zhihu_lite.data.FeedStorage
import org.nigao.zhihu_lite.data.MemoryFeedStorage
import org.nigao.zhihu_lite.model.FeedItem
import org.nigao.zhihu_lite.network.FeedApi
import org.nigao.zhihu_lite.network.KtorFeedApi
import org.nigao.zhihu_lite.network.WebviewFeedApi
import org.nigao.zhihu_lite.ui.MainFeed.FeedViewModel
import org.nigao.zhihu_lite.ui.QuestionFeed.QuestionViewModel
import org.nigao.zhihu_lite.utils.EventReporter

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

    factory(NATIVE_API) {

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
        QuestionViewModel(
            feedRepository = get(WEBVIEW_API) {
                parametersOf(baseUrl, initialItems)
            },
            eventReporter = get()
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