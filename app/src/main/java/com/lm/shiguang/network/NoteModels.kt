package com.lm.shiguang.network

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime
import com.google.gson.annotations.SerializedName
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

// ==================== 数据库实体类 ====================
@Entity(tableName = "user_note")
data class UserNote(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val title: String,
    val content: String,
    val userId: Long,
    val serverId: Long? = null,
    val createTime: LocalDateTime,
    val updateTime: LocalDateTime,
    var isSynced: Boolean = false,
    var isDeleted: Boolean = false,
    val imageUrls: List<String>? = emptyList(),
    val isPublic: Boolean = false
)

// ==================== 网络请求/响应模型 ====================
data class NoteRequest(
    val userId: Long,
    val title: String,
    val content: String,
    val createTime: String,
    val updateTime: String,
    val imageUrls: List<String>? = emptyList(), // 新增图片字段
    val isPublic: Boolean
)

data class ServerNote(
    val id: Long,
    val userId: Long,
    val title: String,
    val content: String,
    val createTime: String,
    val updateTime: String,
    val imageUrls: List<String>? = emptyList(), // 新增图片字段
    val isPublic: Boolean
)

data class ApiResponse<T>(
    val success: Boolean,
    val message: String?,
    val data: T?
)

data class LoginRequest(val loginAccount: String, val password: String)

data class LoginResponse(
    val token: String,
    val userId: Long,
    val nickname: String,
    val email: String
)

data class RegisterRequest(
    val email: String,
    val password: String,
    val confirmPassword: String,
    val nickname: String,
    val verifyCode: String
)

// ==================== 新增的用户信息更新和密码重置请求模型 ====================
data class UpdateUserInfoRequest(
    val nickname: String?,
    val newEmail: String?
)

data class ResetPasswordRequest(
    val newPassword: String
)

// ==================== 社区相关模型（添加 Parcelize 序列化） ====================
// 改造原有Comment模型，添加层级和左边距
@Parcelize
data class Comment(
    @SerializedName("id") val id: Long,
    @SerializedName("noteId") val noteId: Long,
    @SerializedName("userId") val userId: Long,
    @SerializedName("content") val content: String,
    @SerializedName("createTime") val createTime: String,
    @SerializedName("author") val author: Author,
    @SerializedName("level") val level: Int = 1, // 新增：层级1
    @SerializedName("marginLeft") val marginLeft: Int = 15, // 新增：左边距15
    // 新增：存储该一级评论的二级评论列表
    var replyList: List<CommentReply> = emptyList()
) : Parcelable
@Parcelize
data class CommentRequest(
    @SerializedName("noteId") val noteId: Long,
    @SerializedName("content") val content: String
) : Parcelable

@Parcelize // 关键：CommunityNote必须实现Parcelable
data class CommunityNote(
    @SerializedName("id") val id: Long,
    @SerializedName("userId") val userId: Long,
    @SerializedName("title") val title: String,
    @SerializedName("content") val content: String,
    @SerializedName("isPublic") val isPublic: Boolean,
    @SerializedName("likeCount") var likeCount: Int,
    @SerializedName("commentCount") val commentCount: Int,
    @SerializedName("createTime") val createTime: String,
    @SerializedName("author") val author: Author,
    @SerializedName("isLiked") val isLiked: Boolean? = false,
    // 新增：笔记图片地址列表（和后端字段名保持一致，用SerializedName确保解析正确）
    @SerializedName("imageUrls") val imageUrls: List<String>? = emptyList()
) : Parcelable
// 新增二级评论请求模型
@Parcelize
data class CommentReplyRequest(
    @SerializedName("commentId") val commentId: Long, // 一级评论ID
    @SerializedName("replyToUserId") val replyToUserId: Long, // 被回复人ID
    @SerializedName("content") val content: String // 回复内容
) : Parcelable

// 新增二级评论响应模型
@Parcelize
data class CommentReply(
    @SerializedName("id") val id: Long,
    @SerializedName("commentId") val commentId: Long,
    @SerializedName("userId") val userId: Long,
    @SerializedName("replyToUserId") val replyToUserId: Long,
    @SerializedName("content") val content: String, // 后端拼接的@昵称：内容
    @SerializedName("createTime") val createTime: String,
    @SerializedName("author") val author: Author, // 回复人信息
    @SerializedName("replyToAuthor") val replyToAuthor: Author, // 被回复人信息
    @SerializedName("level") val level: Int = 2, // 层级：2
    @SerializedName("marginLeft") val marginLeft: Int = 30 // 左边距
) : Parcelable



@Parcelize // Author也需要实现Parcelable
data class Author(
    @SerializedName("id") val id: Long,
    @SerializedName("nickname") val nickname: String
) : Parcelable

// 分页响应模型
data class PageResponse<T>(
    @SerializedName("content") val content: List<T>,
    @SerializedName("totalElements") val total: Long,
    @SerializedName("totalPages") val totalPages: Int
)

// ==================== 公告/版本响应模型（适配后端BaseResponse） ====================
data class BaseResponse<T>(
    @SerializedName("code") val code: Int,
    @SerializedName("message") val message: String,
    @SerializedName("data") val data: T?
)

@Parcelize
data class Announcement(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("content") val content: String
) : Parcelable

@Parcelize
data class VersionInfo(
    @SerializedName("latestVersionCode") val latestVersionCode: Int,
    @SerializedName("versionCode") val versionCode: Int,
    @SerializedName("latestVersionName") val latestVersionName: String,
    @SerializedName("versionName") val versionName: String,
    @SerializedName("updateContent") val updateContent: String,
    @SerializedName("downloadUrl") val downloadUrl: String
) : Parcelable

// 图片上传响应模型
data class UploadImageResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("code") val code: Int,
    @SerializedName("message") val message: String?,
    @SerializedName("data") val data: String? // 后端返回的图片相对路径
) {
    // 扩展方法：拼接完整URL
    fun getFullImageUrl(baseUrl: String): String? {
        return if (data.isNullOrBlank()) {
            null
        } else {
            if (data.startsWith("http")) data else "$baseUrl$data"
        }
    }
}