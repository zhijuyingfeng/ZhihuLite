package org.nigao.zhihuLite.performance

/**
 * Opts a class or method out of automatic business-method Perfetto tracing.
 *
 * Use this for extremely hot trivial methods when their trace volume would obscure useful work.
 */
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.CONSTRUCTOR
)
@Retention(AnnotationRetention.BINARY)
annotation class NoBusinessTrace
