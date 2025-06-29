package org.nigao.zhihu_lite.utils.JsEvaluator

expect object JsEvaluator {
    suspend fun evaluate(script: String): String
}