package org.nigao.zhihuLite.base_ui

import androidx.compose.ui.graphics.Color

fun String.toColor(): Color {
    return parseColor(this)
}