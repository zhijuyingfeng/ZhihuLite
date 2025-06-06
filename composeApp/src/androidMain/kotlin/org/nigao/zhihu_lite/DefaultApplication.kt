package org.nigao.zhihu_lite

import android.app.Application
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import org.nigao.zhihu_lite.di.initKoin

class DefaultApplication: Application() {
    override fun onCreate() {
        super.onCreate()

        initKoin()

        Napier.base(DebugAntilog())
    }
}