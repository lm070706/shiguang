package com.lm.shiguang.repository

import android.app.Application
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import com.lm.shiguang.AppDatabase
import com.lm.shiguang.NoteDao
import com.lm.shiguang.network.ApiService
import com.lm.shiguang.network.NoteRequest
import com.lm.shiguang.network.RetrofitClient
import com.lm.shiguang.network.ServerNote
import com.lm.shiguang.network.UserNote
import com.lm.shiguang.utils.UserManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class NoteRepository(application: Application) {
    private val noteDao: NoteDao = AppDatabase.getInstance(application).noteDao()
    private val apiService: ApiService = RetrofitClient.getInstance(application)
    private val timeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    private val TAG = "NoteRepository"

    // 观察用户笔记（实时更新）
    fun observeNotesByUserId(userId: Long): LiveData<List<UserNote>> {
        return noteDao.observeNotesByUserId(userId).asLiveData()
    }

    // 观察公开笔记
    fun observePublicNotes(userId: Long): LiveData<List<UserNote>> {
        return noteDao.observePublicNotes(userId).asLiveData()
    }

    // 根据本地ID获取笔记
    suspend fun getNoteByLocalId(localId: Long): UserNote? = withContext(Dispatchers.IO) {
        noteDao.getNoteByLocalId(localId)
    }

    // ========== 新增：单条笔记同步到服务器（核心修复图片URL同步 + 空安全） ==========
    suspend fun syncNoteToServer(note: UserNote, token: String) = withContext(Dispatchers.IO) {
        if (token.isBlank()) {
            Log.e(TAG, "同步笔记失败：Token为空")
            return@withContext
        }

        val validNote = if (note.serverId == 0L) note.copy(serverId = null) else note
        // 构建包含图片URL的请求体（空安全处理）
        val noteRequest = NoteRequest(
            userId = validNote.userId,
            title = validNote.title,
            content = validNote.content,
            createTime = validNote.createTime.format(timeFormatter),
            updateTime = validNote.updateTime.format(timeFormatter),
            isPublic = validNote.isPublic,
            imageUrls = validNote.imageUrls ?: emptyList() // 空安全：null则返回空列表
        )

        try {
            val addResponse = apiService.addNote(noteRequest, "Bearer $token")
            if (addResponse.isSuccessful) {
                val serverNote = addResponse.body() ?: run {
                    Log.e(TAG, "同步笔记失败：服务器返回空")
                    return@withContext
                }
                // 更新本地笔记的serverId和同步状态
                val syncedNote = validNote.copy(
                    serverId = serverNote.id,
                    isSynced = true
                )
                noteDao.updateNote(syncedNote)
                Log.d(
                    TAG,
                    "单条笔记同步成功：localId=${validNote.localId}，serverId=${serverNote.id}，图片数=${validNote.imageUrls?.size ?: 0}"
                )
            } else {
                Log.e(TAG, "同步笔记接口失败：错误码=${addResponse.code()}，信息=${addResponse.message()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "同步笔记异常：localId=${validNote.localId}，异常=${e.message}", e)
        }
    }

    // 新增私密笔记
    suspend fun addNewNote(title: String, content: String, userId: Long) {
        addNewNote(title, content, userId, isPublic = false)
    }

    // 新增笔记（支持公开/私密）
    suspend fun addNewNote(title: String, content: String, userId: Long, isPublic: Boolean) = withContext(Dispatchers.IO) {
        val newNote = UserNote(
            serverId = null,
            title = title,
            content = content,
            userId = userId,
            createTime = LocalDateTime.now(),
            updateTime = LocalDateTime.now(),
            isSynced = false,
            isDeleted = false,
            isPublic = isPublic,
            imageUrls = emptyList() // 初始化图片列表（非空）
        )

        // 3秒内重复笔记过滤（防重复插入）
        val existNote = noteDao.getNoteByContentAndTime(
            title, content, userId,
            newNote.createTime.minusSeconds(3),
            newNote.createTime.plusSeconds(3)
        )
        if (existNote != null) {
            Log.d(TAG, "检测到3秒内重复笔记，跳过插入：title=$title")
            return@withContext
        }

        noteDao.insertNoteReturnId(newNote)
        Log.d(TAG, "本地笔记插入成功：title=$title，isPublic=$isPublic，serverId=null")
    }

    // 新增笔记（直接传入Note对象，核心支持图片URL + 空安全）
    suspend fun addNewNote(note: UserNote) = withContext(Dispatchers.IO) {
        val validNote = if (note.serverId == 0L) note.copy(serverId = null) else note

        // 重复笔记过滤
        val existNote = noteDao.getNoteByContentAndTime(
            validNote.title, validNote.content, validNote.userId,
            validNote.createTime.minusSeconds(3),
            validNote.createTime.plusSeconds(3)
        )
        if (existNote != null) {
            Log.d(TAG, "笔记已存在，跳过插入：title=${validNote.title}")
            return@withContext
        }

        noteDao.insertNoteReturnId(validNote)
        Log.d(
            TAG,
            "本地笔记插入成功：title=${validNote.title}，serverId=${validNote.serverId}，图片数=${validNote.imageUrls?.size ?: 0}"
        )
    }

    // 更新笔记（同步到服务器，包含imageUrls + 空安全）
    suspend fun updateNote(note: UserNote) = withContext(Dispatchers.IO) {
        val validNote = if (note.serverId == 0L) note.copy(serverId = null) else note
        val updatedNote = validNote.copy(
            updateTime = LocalDateTime.now(),
            isSynced = false
        )
        noteDao.updateNote(updatedNote)
        Log.d(
            TAG,
            "本地笔记更新成功：localId=${validNote.localId}，serverId=${validNote.serverId}，图片数=${validNote.imageUrls?.size ?: 0}"
        )

        // 有服务器ID才同步到服务端
        if (validNote.serverId != null && validNote.serverId != 0L) {
            val token = UserManager.getToken() ?: run {
                Log.e(TAG, "更新同步失败：Token为空")
                return@withContext
            }
            val request = NoteRequest(
                userId = validNote.userId,
                title = validNote.title,
                content = validNote.content,
                createTime = validNote.createTime.format(timeFormatter),
                updateTime = updatedNote.updateTime.format(timeFormatter),
                isPublic = validNote.isPublic,
                imageUrls = validNote.imageUrls ?: emptyList() // 空安全：null则返回空列表
            )
            try {
                val response = apiService.updateNote(validNote.serverId, request, "Bearer $token")
                if (response.isSuccessful) {
                    val syncedNote = updatedNote.copy(isSynced = true)
                    noteDao.updateNote(syncedNote)
                    Log.d(
                        TAG,
                        "服务器笔记更新成功：serverId=${validNote.serverId}，图片数=${validNote.imageUrls?.size ?: 0}"
                    )
                } else {
                    Log.e(TAG, "更新接口失败：错误码=${response.code()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "更新接口异常：${e.message}")
            }
        }
    }

    // 删除笔记（同步到服务器 + 同步删除图片 + 空安全）
    suspend fun deleteNoteByLocalId(localId: Long) = withContext(Dispatchers.IO) {
        val note = noteDao.getNoteByLocalId(localId) ?: run {
            Log.e(TAG, "删除失败：本地笔记不存在（localId=$localId）")
            return@withContext
        }

        val validServerId = if (note.serverId == 0L) null else note.serverId
        Log.d(
            TAG,
            "开始删除笔记：localId=$localId，原始serverId=${note.serverId}，有效serverId=$validServerId，图片数=${note.imageUrls?.size ?: 0}"
        )

        // 无服务器ID：直接删除本地草稿
        if (validServerId == null) {
            noteDao.deleteNoteCompletelyByLocalId(localId)
            Log.d(TAG, "本地草稿删除成功：localId=$localId")
            return@withContext
        }

        // 有服务器ID：标记删除并同步到服务端
        val deletedNote = note.copy(
            isDeleted = true,
            updateTime = LocalDateTime.now(),
            isSynced = false,
            serverId = validServerId
        )
        noteDao.updateNote(deletedNote)
        Log.d(TAG, "本地笔记标记为删除：localId=$localId，serverId=$validServerId")

        val token = UserManager.getToken() ?: run {
            Log.e(TAG, "同步删除失败：Token为空，无法调用服务器接口")
            return@withContext
        }

        try {
            // 1. 先调用服务器删除笔记接口（触发后端删除图片）
            val deleteResponse = apiService.deleteNote(validServerId, "Bearer $token")
            if (deleteResponse.isSuccessful) {
                // 2. 删除成功后清理本地笔记
                noteDao.deleteNoteCompletelyByLocalId(localId)
                Log.d(
                    TAG,
                    "服务器笔记删除成功：serverId=$validServerId，本地已清理，关联图片数=${note.imageUrls?.size ?: 0}"
                )
            } else {
                Log.e(TAG, "删除接口调用失败：错误码=${deleteResponse.code()}，信息=${deleteResponse.message()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "删除接口异常：${e.message}", e)
        }
    }

    // 拉取服务器笔记并更新本地（增量更新，包含imageUrls + 空安全）
    suspend fun fetchNotesFromServerBatch(userId: Long, token: String) = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getNotesByUserId(userId, "Bearer $token")
            if (response.isSuccessful) {
                val apiResponse = response.body() ?: run {
                    Log.e(TAG, "拉取笔记失败：响应体为空")
                    return@withContext
                }
                if (!apiResponse.success) {
                    Log.e(TAG, "拉取笔记失败：${apiResponse.message ?: "接口返回失败"}")
                    return@withContext
                }

                val serverNotes = apiResponse.data ?: emptyList()
                Log.d(TAG, "拉取服务器笔记成功：共${serverNotes.size}条")

                // 增量更新：只新增/更新差异数据（修复imageUrls字段映射 + 空安全）
                val localNotes = noteDao.getNotesByUserIdSync(userId)
                val notesToSync = serverNotes.map { serverNote ->
                    val localNote = localNotes.find { it.serverId == serverNote.id }
                    UserNote(
                        localId = localNote?.localId ?: 0,
                        serverId = serverNote.id,
                        userId = userId,
                        title = serverNote.title,
                        content = serverNote.content,
                        createTime = LocalDateTime.parse(serverNote.createTime, timeFormatter),
                        updateTime = LocalDateTime.parse(serverNote.updateTime, timeFormatter),
                        isSynced = true,
                        isDeleted = false,
                        isPublic = serverNote.isPublic ?: false,
                        imageUrls = serverNote.imageUrls ?: emptyList() // 空安全：null则返回空列表
                    )
                }

                noteDao.insertOrReplaceNotesBatch(notesToSync)
                Log.d(TAG, "笔记同步完成：本地新增/更新${notesToSync.size}条")

                // 清理本地无服务器ID的草稿（解决瞬间消失问题 + 空安全）
                val draftNotes = localNotes.filter { it.serverId == null || it.serverId == 0L }
                if (draftNotes.isNotEmpty()) {
                    draftNotes.forEach { note ->
                        noteDao.deleteNoteCompletelyByLocalId(note.localId)
                        Log.d(TAG, "清理本地无serverId草稿：localId=${note.localId}，图片数=${note.imageUrls?.size ?: 0}")
                    }
                    Log.d(TAG, "共清理 ${draftNotes.size} 条本地无效笔记")
                }

            } else {
                Log.e(TAG, "拉取笔记接口失败：错误码=${response.code()}，信息=${response.message()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "拉取笔记异常：${e.message}", e)
        }
    }

    // 重试同步未同步的笔记（包含imageUrls + 空安全）
    suspend fun retrySyncUnsyncedNotes(userId: Long, token: String) = withContext(Dispatchers.IO) {
        if (userId <= 0 || token.isBlank()) {
            Log.e(TAG, "同步失败：userId无效或Token为空")
            return@withContext
        }

        val unsyncedNotes = noteDao.getUnsyncedNotes(userId)
        Log.d(TAG, "开始同步未同步笔记：共${unsyncedNotes.size}条")

        unsyncedNotes.forEach { note ->
            try {
                val validServerId = if (note.serverId == 0L) null else note.serverId
                Log.d(
                    TAG,
                    "处理笔记：localId=${note.localId}，serverId=${validServerId}，图片数=${note.imageUrls?.size ?: 0}"
                )

                // 已同步/有服务器ID：跳过
                if (note.isSynced || (validServerId != null && validServerId > 0)) {
                    Log.d(TAG, "笔记已同步，跳过：localId=${note.localId}")
                    return@forEach
                }

                // 已标记删除：直接清理本地
                if (note.isDeleted) {
                    noteDao.deleteNoteCompletelyByLocalId(note.localId)
                    Log.d(TAG, "本地无效删除笔记清理：localId=${note.localId}")
                    return@forEach
                }

                // 推送未同步笔记到服务器（包含imageUrls + 空安全）
                val noteRequest = NoteRequest(
                    userId = userId,
                    title = note.title,
                    content = note.content,
                    createTime = note.createTime.format(timeFormatter),
                    updateTime = note.updateTime.format(timeFormatter),
                    isPublic = note.isPublic,
                    imageUrls = note.imageUrls ?: emptyList() // 空安全：null则返回空列表
                )

                val addResponse = apiService.addNote(noteRequest, "Bearer $token")
                if (addResponse.isSuccessful) {
                    val serverNote = addResponse.body()
                    if (serverNote != null) {
                        val syncedNote = note.copy(
                            serverId = serverNote.id,
                            isSynced = true
                        )
                        noteDao.updateNote(syncedNote)
                        Log.d(
                            TAG,
                            "笔记同步成功：localId=${note.localId}，serverId=${serverNote.id}，图片数=${note.imageUrls?.size ?: 0}"
                        )
                    } else {
                        Log.e(TAG, "同步失败：服务器返回笔记数据为空")
                    }
                } else {
                    Log.e(TAG, "新增接口调用失败：错误码=${addResponse.code()}，信息=${addResponse.message()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "同步笔记异常：localId=${note.localId}，${e.message}", e)
            }
        }
    }
}