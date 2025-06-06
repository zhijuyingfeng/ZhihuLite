package org.nigao.zhihu_lite.di

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import kotlinx.serialization.json.Json
import io.ktor.serialization.kotlinx.json.json
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module
import org.nigao.zhihu_lite.data.FeedRepository
import org.nigao.zhihu_lite.data.FeedStorage
import org.nigao.zhihu_lite.data.MemoryFeedStorage
import org.nigao.zhihu_lite.network.FeedApi
import org.nigao.zhihu_lite.network.KtorFeedApi
import org.nigao.zhihu_lite.ui.FeedViewModel

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

    single<FeedApi> { KtorFeedApi(get()) }
    single<FeedStorage> { MemoryFeedStorage() }
    single<FeedRepository> { FeedRepository(get(), get()) }
}

val viewModelModule = module {
    factoryOf(::FeedViewModel)
}

fun initKoin() {
    startKoin {
        modules(
            dataModule,
            viewModelModule,
        )
    }
}