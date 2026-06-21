package com.lm.shiguang.sync

import android.content.Context
import android.util.Log
import com.lm.shiguang.AppDatabase
import com.lm.shiguang.network.ApiService
import com.lm.shiguang.network.NoteRequest
import com.lm.shiguang.network.ServerNote
import com.lm.shiguang.network.UserNote
import com.lm.shiguang.utils.UserManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class SyncManager(private val apiService: ApiService, private val context: Context) {
    private val noteDao by lazy { AppDatabase.getInstance(context).noteDao() }
    private val timeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    private val TAG = "SyncManager"
    private var syncJob: Job? = null // 同步任务控制，避免重复同步

    // 获取用户Token
    private fun getToken(): String? {
        val token = UserManager.getToken()
        Log.d(TAG, "同步时获取 Token：${if (token.isNullOrBlank()) "空" else "已获取"}")
        return token
    }

    /**
     * 启动同步（流畅执行，无阻塞）
     * 流程：推送本地变更 → 拉取服务器数据 → 清理草稿
     */
    suspend fun startSync(): Result<Unit> = withContext(Dispatchers.IO) {
        // 取消已有同步任务，避免冲突
        syncJob?.cancelAndJoin()

        return@withContext try {
            val userId = UserManager.getUserId() ?: return@withContext Result.failure(Exception("用户未登录"))
            val token = getToken() ?: return@withContext Result.failure(Exception("Token 为空，无法同步"))

            // 并行执行推送和拉取（但按顺序完成），提升效率
            coroutineScope {
                syncJob = coroutineContext[Job]
                val pushDeferred = async { pushLocalChanges(userId, token) }
                val pullDeferred = async { pullServerChanges(userId, token) }
                awaitAll(pushDeferred, pullDeferred) // 等待所有任务完成
            }
            Result.success(Unit)
        } catch (e: Exception) {
            if (!e.message.equals("Job was cancelled", ignoreCase = true)) {
                Log.e(TAG, "同步失败：${e.message}", e)
            }
            Result.failure(e)
        }
    }

    /**
     * 推送本地变更到服务器（新增/更新/删除）
     */
    private suspend fun pushLocalChanges(userId: Long, token: String) = coroutineScope {
        val userNotes = noteDao.getNotesByUserIdSync(userId)
        val toAddOrUpdate = userNotes.filter { !it.isSynced && !it.isDeleted }
        val toDelete = userNotes.filter { it.isDeleted && it.serverId != null }

        // 推送新增/更新的笔记
        val syncTasks = toAddOrUpdate.map { note ->
            async {
                try {
                    if (note.isSynced || (note.serverId != null && note.serverId > 0)) {
                        Log.d(TAG, "笔记已同步，跳过推送：localId=${note.localId}")
                        return@async null
                    }

                    val request = NoteRequest(
                        userId = userId,
                        title = note.title,
                        content = note.content,
                        createTime = note.createTime.format(timeFormatter),
                        updateTime = note.updateTime.format(timeFormatter),
                        isPublic = note.isPublic
                    )
                    val response = if (note.serverId == null) {
                        apiService.addNote(request, "Bearer $token")
                    } else {
                        apiService.updateNote(note.serverId, request, "Bearer $token")
                    }
                    if (response.isSuccessful) {
                        val serverNote = response.body()
                        note.copy(
                            serverId = serverNote?.id,
                            isSynced = true,
                            createTime = serverNote?.createTime?.let { LocalDateTime.parse(it, timeFormatter) } ?: note.createTime,
                            updateTime = serverNote?.updateTime?.let { LocalDateTime.parse(it, timeFormatter) } ?: note.updateTime
                        )
                    } else {
                        Log.e(TAG, "同步笔记失败：serverId=${note.serverId}，错误码=${response.code()}")
                        null
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "同步笔记异常：localId=${note.localId}，${e.message}", e)
                    null
                }
            }
        }

        // 批量更新同步成功的笔记
        val syncedNotes = syncTasks.awaitAll().filterNotNull()
        if (syncedNotes.isNotEmpty()) {
            noteDao.updateNotesBatch(syncedNotes)
            Log.d(TAG, "成功同步 ${syncedNotes.size} 条笔记")
        }

        // 推送删除操作
        val deleteTasks = toDelete.map { note ->
            async {
                try {
                    val response = apiService.deleteNote(note.serverId!!, "Bearer $token")
                    if (response.isSuccessful) {
                        Log.d(TAG, "同步删除笔记成功：serverId=${note.serverId}")
                        note.serverId
                    } else {
                        Log.e(TAG, "同步删除笔记失败：serverId=${note.serverId}，错误码=${response.code()}")
                        null
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "同步删除笔记异常：serverId=${note.serverId}，${e.message}", e)
                    null
                }
            }
        }

        // 批量清理已删除的笔记
        val deletedServerIds = deleteTasks.awaitAll().filterNotNull()
        if (deletedServerIds.isNotEmpty()) {
            noteDao.deleteNotesCompletelyByServerIds(deletedServerIds)
            Log.d(TAG, "成功删除 ${deletedServerIds.size} 条服务器笔记")
        }
    }

    /**
     * 拉取服务器数据并更新本地（增量更新，流畅无闪烁）
     */
    private suspend fun pullServerChanges(userId: Long, token: String) = coroutineScope {
        try {
            val response = apiService.getNotesByUserId(userId, "Bearer $token")
            if (response.isSuccessful) {
                val apiResponse = response.body()
                if (apiResponse == null || !apiResponse.success) {
                    Log.e(TAG, "拉取服务器笔记失败：${apiResponse?.message ?: "响应体为空"}")
                    return@coroutineScope
                }

                val serverNotes = apiResponse.data ?: emptyList()
                val localNotes = noteDao.getNotesByUserIdSync(userId)

                // 增量更新：只更新有变化的笔记
                val notesToInsertOrUpdate = serverNotes.map { serverNote ->
                    val existingNote = localNotes.find { it.serverId == serverNote.id }
                    UserNote(
                        localId = existingNote?.localId ?: 0,
                        serverId = serverNote.id,
                        userId = userId,
                        title = serverNote.title,
                        content = serverNote.content,
                        createTime = LocalDateTime.parse(serverNote.createTime, timeFormatter),
                        updateTime = LocalDateTime.parse(serverNote.updateTime, timeFormatter),
                        isSynced = true,
                        isDeleted = false,
                        isPublic = serverNote.isPublic ?: false
                    )
                }

                // 批量插入/更新，减少数据库IO次数
                noteDao.insertOrReplaceNotesBatch(notesToInsertOrUpdate)
                Log.d(TAG, "拉取服务器笔记成功，共 ${serverNotes.size} 条，更新本地 ${notesToInsertOrUpdate.size} 条")

                // 清理本地无服务器ID的草稿（解决瞬间消失问题）
                cleanLocalDraftNotes(userId)

            } else {
                Log.e(TAG, "拉取服务器笔记失败：错误码=${response.code()}，错误信息=${response.message()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "拉取服务器笔记异常：${e.message}", e)
        }
    }

    /**
     * 清理本地无服务器ID的草稿笔记
     */
    private suspend fun cleanLocalDraftNotes(userId: Long) = coroutineScope {
        val localNotes = noteDao.getNotesByUserIdSync(userId)
        val draftNotes = localNotes.filter {
            (it.serverId == null || it.serverId == 0L) && !it.isSynced && !it.isDeleted
        }
        if (draftNotes.isNotEmpty()) {
            draftNotes.forEach { note ->
                noteDao.deleteNoteCompletelyByLocalId(note.localId)
                Log.d(TAG, "清理本地无serverId草稿：localId=${note.localId}，title=${note.title}")
            }
            Log.d(TAG, "共清理 ${draftNotes.size} 条本地草稿笔记")
        } else {
            Log.d(TAG, "无本地草稿笔记需要清理")
        }
    }

    /**
     * 取消同步任务
     */
    fun cancelSync() {
        syncJob?.cancel()
        Log.d(TAG, "同步已取消")
    }

    /**
     * 重试同步未同步的笔记
     */
    suspend fun retrySyncUnsyncedNotes(userId: Long) = coroutineScope {
        val token = getToken() ?: run {
            Log.e(TAG, "重试同步失败：Token 为空")
            return@coroutineScope
        }
        val unsyncedNotes = noteDao.getUnsyncedNotes(userId)
        Log.d(TAG, "开始重试同步未同步笔记，共 ${unsyncedNotes.size} 条")

        // 逐条重试，确保成功率
        unsyncedNotes.forEach { note ->
            try {
                if (note.isSynced || (note.serverId != null && note.serverId > 0)) {
                    Log.d(TAG, "笔记已同步，跳过重试：localId=${note.localId}")
                    return@forEach
                }

                val request = NoteRequest(
                    userId = userId,
                    title = note.title,
                    content = note.content,
                    createTime = note.createTime.format(timeFormatter),
                    updateTime = note.updateTime.format(timeFormatter),
                    isPublic = note.isPublic
                )
                val response = if (note.serverId == null) {
                    apiService.addNote(request, "Bearer $token")
                } else {
                    apiService.updateNote(note.serverId, request, "Bearer $token")
                }
                if (response.isSuccessful) {
                    val serverNote = response.body()
                    val updatedNote = note.copy(
                        serverId = serverNote?.id,
                        isSynced = true
                    )
                    noteDao.updateNote(updatedNote)
                    Log.d(TAG, "重试同步笔记成功：localId=${note.localId}")
                } else {
                    Log.e(TAG, "重试同步笔记失败：localId=${note.localId}，错误码=${response.code()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "重试同步笔记异常：localId=${note.localId}，${e.message}", e)
            }
        }
    }
}