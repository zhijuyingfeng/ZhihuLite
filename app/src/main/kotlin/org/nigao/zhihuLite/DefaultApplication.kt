package org.nigao.zhihuLite

import android.app.Application
import com.nigao.gaia.registerAll
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.nigao.zhihuLite.web.JsEvaluator

class DefaultApplication: Application() {
    override fun onCreate() {
        super.onCreate()

        Napier.base(DebugAntilog())
        registerAll()

        CoroutineScope(Dispatchers.IO).launch {
            JsEvaluator.initialize(this@DefaultApplication)
        }
    }
}