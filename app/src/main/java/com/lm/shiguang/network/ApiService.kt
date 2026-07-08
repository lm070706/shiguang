package com.lm.shiguang.network

import retrofit2.Response
import retrofit2.http.*
import okhttp3.MultipartBody

interface ApiService {

    // 登录注册相关接口
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<ApiResponse<LoginResponse>>

    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<ApiResponse<Unit>>

    @POST("auth/send-code")
    suspend fun sendVerificationCode(@Query("email") email: String): Response<ApiResponse<Unit>>

    // 用户信息相关接口
    @PUT("users/{userId}")
    suspend fun updateUserInfo(
        @Path("userId") userId: Long,
        @Body request: UpdateUserInfoRequest,
        @Header("Authorization") token: String
    ): Response<ApiResponse<Unit>>

    @PUT("users/{userId}/password")
    suspend fun resetPassword(
        @Path("userId") userId: Long,
        @Body request: ResetPasswordRequest,
        @Header("Authorization") token: String
    ): Response<ApiResponse<Unit>>

    // 笔记相关接口
    @POST("notes")
    suspend fun addNote(
        @Body request: NoteRequest,
        @Header("Authorization") token: String
    ): Response<ServerNote>

    @PUT("notes/{noteId}")
    suspend fun updateNote(
        @Path("noteId") noteId: Long,
        @Body request: NoteRequest,
        @Header("Authorization") token: String
    ): Response<ServerNote>

    @DELETE("notes/{noteId}")
    suspend fun deleteNote(
        @Path("noteId") noteId: Long,
        @Header("Authorization") token: String
    ): Response<Unit>

    @GET("notes/user")
    suspend fun getNotesByUserId(
        @Query("userId") userId: Long,
        @Header("Authorization") token: String
    ): Response<ApiResponse<List<ServerNote>>>

    // 图片上传接口
    @Multipart
    @POST("upload/image")
    suspend fun uploadImage(
        @Part file: MultipartBody.Part,
        @Header("Authorization") token: String
    ): Response<BaseResponse<String>>

    // 单张图片删除
    @DELETE("upload/image")
    suspend fun deleteImage(
        @Query("path") imagePath: String,
        @Header("Authorization") token: String
    ): Response<BaseResponse<Boolean>>



    // 社区相关接口
    @GET("community/notes/community/public")
    suspend fun getCommunityNotes(
        @Query("pageNum") pageNum: Int,
        @Query("pageSize") pageSize: Int
    ): Response<BaseResponse<PageResponse<CommunityNote>>>

    @POST("community/notes/{noteId}/like")
    suspend fun likeNote(
        @Path("noteId") noteId: Long,
        @Header("Authorization") token: String
    ): Response<BaseResponse<Unit>>

    @POST("community/notes/{noteId}/unlike")
    suspend fun unlikeNote(
        @Path("noteId") noteId: Long,
        @Header("Authorization") token: String
    ): Response<BaseResponse<Unit>>

    @GET("community/notes/{noteId}/comments")
    suspend fun getCommentsByNoteId(
        @Path("noteId") noteId: Long,
        @Query("pageNum") pageNum: Int = 1,
        @Query("pageSize") pageSize: Int = 100
    ): Response<BaseResponse<PageResponse<Comment>>>

    @POST("community/notes/comments")
    suspend fun addComment(
        @Body request: CommentRequest,
        @Header("Authorization") token: String
    ): Response<BaseResponse<Unit>>

    // ========== 二级评论接口（和Repository完全匹配） ==========
    // 发布二级评论
    @POST("community/notes/comments/reply")
    suspend fun addCommentReply(
        @Body request: CommentReplyRequest,
        @Header("Authorization") token: String
    ): Response<BaseResponse<CommentReply>>

    // 获取一级评论的二级评论列表
    @GET("community/notes/comments/{commentId}/replies")
    suspend fun getCommentReplies(
        @Path("commentId") commentId: Long,
        @Query("pageNum") pageNum: Int = 1,
        @Query("pageSize") pageSize: Int = 20,
        @Header("Authorization") token: String
    ): Response<BaseResponse<PageResponse<CommentReply>>>

    // 删除二级评论
    @POST("community/notes/comments/replies/{replyId}/delete")
    suspend fun deleteCommentReply(
        @Path("replyId") replyId: Long,
        @Header("Authorization") token: String
    ): Response<BaseResponse<Unit>>

    // 删除一级评论
    @POST("community/notes/comments/{commentId}/delete")
    suspend fun deleteComment(
        @Path("commentId") commentId: Long,
        @Header("Authorization") token: String
    ): Response<BaseResponse<Unit>>

    // 关注/取消关注接口
    @POST("community/users/{userId}/follow")
    suspend fun followUser(
        @Path("userId") userId: Long,
        @Header("Authorization") token: String
    ): Response<ApiResponse<Unit>>

    @POST("community/users/{userId}/unfollow")
    suspend fun unfollowUser(
        @Path("userId") userId: Long,
        @Header("Authorization") token: String
    ): Response<ApiResponse<Unit>>

    // 公告接口
    @GET("app/announcement")
    suspend fun getAnnouncement(): Response<Map<String, String>>

    // 版本更新接口
    @GET("app/version")
    suspend fun getVersionInfo(): Response<Map<String, Any>>
}