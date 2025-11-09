package com.nigao.gaia

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class GaiaListen(
    val key: String
)