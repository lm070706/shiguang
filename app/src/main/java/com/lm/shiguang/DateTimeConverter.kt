package com.lm.shiguang

import androidx.room.TypeConverter
import com.lm.shiguang.utils.TimeUtils
import java.time.LocalDateTime

class DateTimeConverter {
    @TypeConverter
    fun fromLocalDateTime(value: LocalDateTime?): String? {
        return value?.let { TimeUtils.localDateTimeToString(it) }
    }

    @TypeConverter
    fun toLocalDateTime(value: String?): LocalDateTime? {
        return value?.let { TimeUtils.stringToLocalDateTime(it) }
    }
}