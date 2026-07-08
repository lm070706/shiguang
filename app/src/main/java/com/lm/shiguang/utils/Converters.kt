package com.lm.shiguang.utils

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type

/**
 * Room 类型转换器：用于将 List<String> 转换为 JSON 字符串存储，读取时转回 List<String>
 */
class Converters {
    private val gson = Gson()

    // 将 List<String> 转为 JSON 字符串
    @TypeConverter
    fun fromStringList(value: List<String>?): String? {
        if (value == null || value.isEmpty()) {
            return null
        }
        val type: Type = object : TypeToken<List<String>>() {}.type
        return gson.toJson(value, type)
    }

    // 将 JSON 字符串转回 List<String>
    @TypeConverter
    fun toStringList(value: String?): List<String>? {
        if (value == null || value.isEmpty()) {
            return emptyList()
        }
        val type: Type = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(value, type)
    }
}