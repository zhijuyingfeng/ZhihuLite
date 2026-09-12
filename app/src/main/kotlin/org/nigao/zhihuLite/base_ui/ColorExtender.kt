package org.nigao.zhihuLite.base_ui

import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.core.graphics.toColorInt

fun parseColor(hexString: String): ComposeColor {
    return ComposeColor(hexString.toColorInt())
}