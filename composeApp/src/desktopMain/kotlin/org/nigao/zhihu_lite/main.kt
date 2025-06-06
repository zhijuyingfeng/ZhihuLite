package org.nigao.zhihu_lite

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier

fun main() = application {
    init()

    Window(
        onCloseRequest = ::exitApplication,
        title = "ZhihuLite",
    ) {
        App()
    }
}

fun init() {
    Napier.base(DebugAntilog())
}