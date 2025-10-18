package org.nigao.zhihu_lite

import android.app.Application
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.nigao.zhihu_lite.di.initKoin
import org.nigao.zhihu_lite.web.JsEvaluator

class DefaultApplication: Application() {
    override fun onCreate() {
        super.onCreate()

        initKoin()

        Napier.base(DebugAntilog())

        CoroutineScope(Dispatchers.IO).launch {
            JsEvaluator.initialize(this@DefaultApplication)
        }
    }
}