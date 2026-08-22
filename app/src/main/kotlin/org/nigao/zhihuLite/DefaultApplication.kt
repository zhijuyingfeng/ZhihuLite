package org.nigao.zhihuLite

import android.app.Application
import com.nigao.gaia.GaiaEvent
import com.nigao.gaia.GaiaEventManager
import com.nigao.gaia.registerAll
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier

class DefaultApplication: Application() {
    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Napier.base(DebugAntilog())
        }
        registerAll()
        GaiaEventManager.start(GaiaEvent(key = "register_route"))
    }
}
