package org.nigao.zhihu_lite.utils

fun Int.toReadableString(): String {
    if (this < 1_000) return "$this"
    if (this < 1_000_000) return "${this/1_000}K"
    if (this < 1_000_000_000) return "${this/1_000_000}M"
    return "${this/1_000_000_000}B"
}

