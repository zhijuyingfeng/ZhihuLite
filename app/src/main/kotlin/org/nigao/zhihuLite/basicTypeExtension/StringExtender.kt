package org.nigao.zhihuLite.basicTypeExtension

import androidx.compose.ui.graphics.Color

fun String.toColor(): Color {
    return parseColor(this)
}