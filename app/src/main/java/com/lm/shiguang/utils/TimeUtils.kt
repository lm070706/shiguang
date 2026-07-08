
package com.lm.shiguang.utils

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object TimeUtils {
    // 全局统一ISO时间格式（与后端LocalDateTime兼容）
    val ISO_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    // LocalDateTime转字符串
    fun localDateTimeToString(time: LocalDateTime): String {
        return time.format(ISO_TIME_FORMATTER)
    }

    // 字符串转LocalDateTime（后端返回时间时用）
    fun stringToLocalDateTime(timeStr: String): LocalDateTime {
        return LocalDateTime.parse(timeStr, ISO_TIME_FORMATTER)

    }
    fun formatTime(timeStr: String): String {
        // 示例：将"2025-11-30T12:00:00"格式化为"2025-11-30 12:00"
        return timeStr.replace("T", " ").substringBeforeLast(":")
    }
}