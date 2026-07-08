package com.lm.shiguang

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.lm.shiguang.network.UserNote
import com.lm.shiguang.NoteDao
// 核心修改：导入 utils 包下的 Converters
import com.lm.shiguang.utils.Converters

@Database(entities = [UserNote::class], version = 1, exportSchema = false)
// 关键：同时注册 DateTimeConverter 和 utils 包下的 Converters
@TypeConverters(DateTimeConverter::class, Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "shiguang_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}