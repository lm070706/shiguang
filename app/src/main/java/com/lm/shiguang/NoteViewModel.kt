package com.lm.shiguang

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.lm.shiguang.network.UserNote
import com.lm.shiguang.repository.NoteRepository
import com.lm.shiguang.utils.UserManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime

class NoteViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: NoteRepository = NoteRepository(application)
    private val _currentNote = MutableLiveData<UserNote?>()
    val currentNote: LiveData<UserNote?> = _currentNote
    private val _operationStatus = MutableLiveData<OperationStatus?>()
    val operationStatus: LiveData<OperationStatus?> = _operationStatus
    private val TAG = "NoteViewModel"

    private fun getToken(): String {
        val token = UserManager.getToken() ?: ""
        Log.d(TAG, "获取 Token：${if (token.isBlank()) "空" else "已获取（长度=${token.length}）"}")
        return token
    }

    fun observeNotesByUserId(userId: Long): LiveData<List<UserNote>> {
        return repository.observeNotesByUserId(userId)
    }

    fun loadNoteByLocalId(localId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _currentNote.postValue(repository.getNoteByLocalId(localId))
            } catch (e: Exception) {
                Log.e(TAG, "加载笔记失败：localId=$localId，异常=${e.message}", e)
                _currentNote.postValue(null)
            }
        }
    }

    // ========== 原有方法（保留不变） ==========
    fun addNewNote(title: String, content: String) {
        addNewNote(title, content, isPublic = false)
    }

    fun addNewNote(title: String, content: String, isPublic: Boolean) {
        val userId = UserManager.getUserId() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val newNote = UserNote(
                    title = title,
                    content = content,
                    userId = userId,
                    createTime = LocalDateTime.now(),
                    updateTime = LocalDateTime.now(),
                    isSynced = false,
                    isDeleted = false,
                    isPublic = isPublic,
                    imageUrls = emptyList() // 原有逻辑：默认无图片
                )
                repository.addNewNote(newNote)
                _operationStatus.postValue(OperationStatus.ADD_SUCCESS)
            } catch (e: Exception) {
                Log.e(TAG, "新增笔记失败：title=$title，isPublic=$isPublic，异常=${e.message}", e)
                _operationStatus.postValue(OperationStatus.ERROR)
            }
        }
    }

    // ========== 新增：带图片的重载方法（核心修复） ==========
    /**
     * 私密笔记新增（带图片）
     * 适配 AddNoteActivity 调用：addNewNote(title, content, selectedImages)
     */
    fun addNewNote(title: String, content: String, imageUrls: List<String>) {
        addNewNote(title, content, imageUrls, false)
    }

    /**
     * 通用笔记新增（带图片+公开/私密标识）
     * 适配 AddPublicNoteActivity 调用：addNewNote(title, content, selectedImages, true)
     */
    fun addNewNote(title: String, content: String, imageUrls: List<String>, isPublic: Boolean) {
        val userId = UserManager.getUserId() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val newNote = UserNote(
                    title = title,
                    content = content,
                    userId = userId,
                    createTime = LocalDateTime.now(),
                    updateTime = LocalDateTime.now(),
                    isSynced = false,
                    isDeleted = false,
                    imageUrls = imageUrls, // 传入图片列表
                    isPublic = isPublic
                )
                repository.addNewNote(newNote)
                _operationStatus.postValue(OperationStatus.ADD_SUCCESS)
                Log.d(TAG, "新增带图片笔记成功：title=$title，图片数=${imageUrls.size}，公开=$isPublic")
            } catch (e: Exception) {
                Log.e(TAG, "新增带图片笔记失败：title=$title，异常=${e.message}", e)
                _operationStatus.postValue(OperationStatus.ERROR)
            }
        }
    }

    // ========== 修复：编辑后主动触发同步（针对无serverId的笔记） ==========
    fun updateNote(note: UserNote) {
        val userId = UserManager.getUserId() ?: return
        val token = getToken()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val updatedNote = note.copy(updateTime = LocalDateTime.now(), isSynced = false)
                repository.updateNote(updatedNote)
                // 无serverId的笔记，更新后触发重试同步
                if (updatedNote.serverId == null || updatedNote.serverId == 0L) {
                    if (token.isNotBlank()) {
                        repository.retrySyncUnsyncedNotes(userId, token)
                        Log.d(TAG, "编辑无serverId笔记，触发同步")
                    }
                }
                _operationStatus.postValue(OperationStatus.UPDATE_SUCCESS)
            } catch (syncException: Exception) {
                Log.e(TAG, "同步更新笔记异常：localId=${note.localId}，异常=${syncException.message}", syncException)
                _operationStatus.postValue(OperationStatus.ERROR)
            } catch (e: Exception) {
                Log.e(TAG, "更新笔记失败：localId=${note.localId}，异常=${e.message}", e)
                _operationStatus.postValue(OperationStatus.ERROR)
            }
        }
    }

    fun deleteNoteByLocalId(localId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.deleteNoteByLocalId(localId)
                _operationStatus.postValue(OperationStatus.DELETE_SUCCESS)
            } catch (e: Exception) {
                Log.e(TAG, "删除笔记失败：localId=$localId，异常=${e.message}", e)
                _operationStatus.postValue(OperationStatus.ERROR)
            }
        }
    }

    suspend fun fetchNotesFromServer() {
        val userId = UserManager.getUserId() ?: return
        val token = getToken()
        if (token.isBlank()) {
            Log.e(TAG, "拉取笔记失败：Token 为空")
            return
        }
        try {
            repository.fetchNotesFromServerBatch(userId, token)
            Log.d(TAG, "拉取服务器笔记完成：userId=$userId")
        } catch (e: Exception) {
            Log.e(TAG, "拉取服务器笔记异常：userId=$userId，异常=${e.message}", e)
        }
    }

    suspend fun retrySyncUnsyncedNotes() {
        val userId = UserManager.getUserId() ?: return
        val token = getToken()
        if (token.isBlank()) {
            Log.e(TAG, "同步笔记失败：Token 为空")
            return
        }
        try {
            repository.retrySyncUnsyncedNotes(userId, token)
            Log.d(TAG, "重试同步笔记完成：userId=$userId")
        } catch (e: Exception) {
            Log.e(TAG, "重试同步笔记异常：userId=$userId，异常=${e.message}", e)
        }
    }

    fun resetOperationStatus() {
        _operationStatus.value = null
    }

    enum class OperationStatus {
        ADD_SUCCESS, UPDATE_SUCCESS, DELETE_SUCCESS, ERROR
    }
}