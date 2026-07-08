package com.lm.shiguang

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.lm.shiguang.network.UserNote
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

@Dao
interface NoteDao {
    // 基础查询（原有方法，无修改）
    @Query("SELECT * FROM user_note")
    fun getAllNotes(): Flow<List<UserNote>>

    @Query("SELECT * FROM user_note WHERE userId = :userId AND isDeleted = 0 ORDER BY updateTime DESC")
    fun observeNotesByUserId(userId: Long): Flow<List<UserNote>>

    @Query("SELECT * FROM user_note WHERE userId = :userId ORDER BY updateTime DESC")
    suspend fun getNotesByUserIdSync(userId: Long): List<UserNote>

    @Query("SELECT * FROM user_note WHERE localId = :localId")
    suspend fun getNoteByLocalId(localId: Long): UserNote?

    @Query("SELECT * FROM user_note WHERE serverId = :serverId")
    suspend fun getNoteByServerId(serverId: Long): UserNote?

    @Query("SELECT * FROM user_note WHERE userId = :userId AND isSynced = 0")
    suspend fun getUnsyncedNotes(userId: Long): List<UserNote>

    // 批量操作（原有方法，无修改）
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplaceNotesBatch(notes: List<UserNote>)

    @Update
    suspend fun updateNotesBatch(notes: List<UserNote>)

    @Query("DELETE FROM user_note WHERE serverId IN (:serverIds)")
    suspend fun deleteNotesCompletelyByServerIds(serverIds: List<Long>)

    // 单条操作（原有方法，无修改）
    @Insert
    suspend fun insertNoteReturnId(note: UserNote): Long

    @Update
    suspend fun updateNote(note: UserNote)

    /**
     * 标记一条笔记为已删除（逻辑删除）
     */
    @Query("UPDATE user_note SET isDeleted = 1, updateTime = :updateTime, isSynced = 0 WHERE localId = :localId")
    suspend fun deleteNoteByLocalId(localId: Long, updateTime: LocalDateTime)

    /**
     * 根据服务器ID彻底删除一条笔记（物理删除）
     */
    @Query("DELETE FROM user_note WHERE serverId = :serverId")
    suspend fun deleteNoteCompletelyByServerId(serverId: Long)

    /**
     * 根据本地ID彻底删除一条笔记（物理删除）
     * 适用于删除从未同步到服务器的本地草稿。
     */
    @Query("DELETE FROM user_note WHERE localId = :localId")
    suspend fun deleteNoteCompletelyByLocalId(localId: Long)

    @Query("UPDATE user_note SET isSynced = 1 WHERE localId = :localId")
    suspend fun markAsSynced(localId: Long)

    // 原有公开笔记查询（无修改）
    @Query("SELECT * FROM user_note WHERE userId = :userId AND isPublic = 1 AND isDeleted = 0")
    fun observePublicNotes(userId: Long): Flow<List<UserNote>>

    // ===================== 仅新增以下查重方法（核心去重逻辑） =====================
    /**
     * 查重方法：按标题+内容+用户ID+时间范围查询笔记
     * 用于避免短时间内重复添加相同笔记
     */
    @Query("""
        SELECT * FROM user_note 
        WHERE title = :title 
        AND content = :content 
        AND userId = :userId 
        AND createTime BETWEEN :startTime AND :endTime 
        AND isDeleted = 0
        LIMIT 1
    """)
    suspend fun getNoteByContentAndTime(
        title: String,
        content: String,
        userId: Long,
        startTime: LocalDateTime,
        endTime: LocalDateTime
    ): UserNote?
}