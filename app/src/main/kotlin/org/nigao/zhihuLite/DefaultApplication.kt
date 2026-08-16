package org.nigao.zhihuLite

import android.app.Application
import com.nigao.gaia.registerAll
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier

class DefaultApplication: Application() {
    override fun onCreate() {
        super.onCreate()

        Napier.base(DebugAntilog())
        registerAll()
    }
}
