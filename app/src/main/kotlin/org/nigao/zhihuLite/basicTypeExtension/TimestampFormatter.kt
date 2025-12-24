package org.nigao.zhihuLite.basicTypeExtension

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.util.*

object TimestampFormatter {

    // 预定义的常用格式模式映射
    private val PREDEFINED_FORMATS = mapOf(
        "YYYYMMDD" to "yyyyMMdd",
        "YYYY-MM-DD" to "yyyy-MM-dd",
        "YYYY/MM/DD" to "yyyy/MM/dd",
        "YYYY年MM月DD日" to "yyyy年MM月dd日",
        "YYMMDD" to "yyMMdd",
        "YY年MM月DD日" to "yy年MM月dd日",
        "DD/MM/YYYY" to "dd/MM/yyyy",
        "DD-MM-YYYY" to "dd-MM-yyyy",
        "MM/DD/YYYY" to "MM/dd/yyyy",
        "MM-DD-YYYY" to "MM-dd-yyyy",
        "YYYY-MM-DD HH:mm:ss" to "yyyy-MM-dd HH:mm:ss",
        "YYYY/MM/DD HH:mm:ss" to "yyyy/MM/dd HH:mm:ss",
        "YYYYMMDDHHmmss" to "yyyyMMddHHmmss",
        "HH:mm:ss" to "HH:mm:ss",
        "HH:mm" to "HH:mm",
        "YYYY年MM月DD日 HH:mm:ss" to "yyyy年MM月dd日 HH:mm:ss",
        "YYYY-MM-DDTHH:mm:ssZ" to "yyyy-MM-dd'T'HH:mm:ssXXX",
        "YYYY-MM-DDTHH:mm:ss" to "yyyy-MM-dd'T'HH:mm:ss",
        "YYYY-MM" to "yyyy-MM",
        "MM/DD" to "MM/dd",
        "MM-DD" to "MM-dd",
        "YYYY" to "yyyy",
        "MM" to "MM",
        "DD" to "dd"
    )

    // 时区设置，可以根据需要调整
    private val DEFAULT_TIME_ZONE = ZoneId.systemDefault() // 系统默认时区

    /**
     * 将时间戳（秒）格式化为字符串
     *
     * @param timestamp 时间戳（秒）
     * @param format 目标格式，支持预定义格式或自定义格式
     * @param timeZone 时区，默认为系统时区
     * @return 格式化后的字符串
     */
    fun formatTimestamp(
        timestamp: Long,
        format: String = "YYYY-MM-DD HH:mm:ss",
        timeZone: ZoneId = DEFAULT_TIME_ZONE
    ): String {
        return try {
            // 获取对应的 Java 格式模式
            val pattern = PREDEFINED_FORMATS[format] ?: convertCustomFormat(format)

            // 创建 DateTimeFormatter
            val formatter = createFormatter(pattern)

            // 转换时间戳
            val instant = Instant.ofEpochSecond(timestamp)
            val zonedDateTime = ZonedDateTime.ofInstant(instant, timeZone)

            // 格式化
            zonedDateTime.format(formatter)
        } catch (e: Exception) {
            // 出错时返回原始时间戳或空字符串
            e.printStackTrace()
            "格式转换错误: ${e.message}"
        }
    }

    /**
     * 将自定义格式转换为 Java DateTimeFormatter 可识别的格式
     * 支持从简单格式转换为标准格式
     */
    private fun convertCustomFormat(format: String): String {
        return format
            .replace("YYYY", "yyyy")
            .replace("YY", "yy")
            .replace("DD", "dd")
            .replace("HH", "HH")
            .replace("hh", "hh")
            .replace("mm", "mm")
            .replace("ss", "ss")
            .replace("SSS", "SSS")
    }

    /**
     * 创建 DateTimeFormatter，支持可选字段
     */
    private fun createFormatter(pattern: String): DateTimeFormatter {
        return try {
            DateTimeFormatterBuilder()
                .appendPattern(pattern)
                .parseDefaulting(ChronoField.HOUR_OF_DAY, 0)
                .parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0)
                .parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0)
                .toFormatter(Locale.getDefault())
        } catch (e: Exception) {
            // 如果创建失败，使用简单模式
            DateTimeFormatter.ofPattern(pattern, Locale.getDefault())
        }
    }

    /**
     * 获取当前时间戳并格式化
     */
    fun formatCurrentTime(format: String = "YYYY-MM-DD HH:mm:ss"): String {
        val currentTimestamp = System.currentTimeMillis() / 1000
        return formatTimestamp(currentTimestamp, format)
    }

    /**
     * 批量格式化时间戳
     */
    fun batchFormatTimestamps(
        timestamps: List<Long>,
        format: String = "YYYY-MM-DD"
    ): List<String> {
        return timestamps.map { formatTimestamp(it, format) }
    }

    /**
     * 获取所有支持的格式列表
     */
    fun getSupportedFormats(): List<String> {
        return PREDEFINED_FORMATS.keys.toList()
    }
}

/**
 * 扩展函数，为 Long 类型添加格式化功能
 */
fun Long.toFormattedString(
    format: String = "YYYY-MM-DD HH:mm:ss",
    timeZone: ZoneId = ZoneId.systemDefault()
): String {
    return TimestampFormatter.formatTimestamp(this, format, timeZone)
}

/**
 * 扩展函数，为 Instant 类型添加格式化功能
 */
fun Instant.toFormattedString(
    format: String = "YYYY-MM-DD HH:mm:ss",
    timeZone: ZoneId = ZoneId.systemDefault()
): String {
    val timestamp = this.epochSecond
    return TimestampFormatter.formatTimestamp(timestamp, format, timeZone)
}