package org.nigao.zhihu_lite.web

expect object JsEvaluator {
    suspend fun evaluate(script: String): String
}