package com.lm.shiguang.repository

import com.lm.shiguang.network.ApiService
import com.lm.shiguang.network.BaseResponse
import com.lm.shiguang.network.Comment
import com.lm.shiguang.network.CommentReply
import com.lm.shiguang.network.CommentReplyRequest
import com.lm.shiguang.network.CommentRequest
import com.lm.shiguang.network.CommunityNote
import com.lm.shiguang.network.PageResponse
import com.lm.shiguang.network.RetrofitClient
import com.lm.shiguang.utils.UserManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log

class CommunityRepository {

    private val apiService: ApiService = RetrofitClient.instance
    private val TAG = "CommunityRepository"
    private val TOKEN_EXPIRED_CODE = 500

    // 获取社区公开笔记（无需 Token，保持不变）
    suspend fun getCommunityNotes(pageNum: Int, pageSize: Int): Result<BaseResponse<PageResponse<CommunityNote>>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.getCommunityNotes(pageNum, pageSize)
                if (response.isSuccessful) {
                    Result.success(response.body() ?: BaseResponse(404, "数据为空", null))
                } else {
                    Result.failure(Exception("网络请求失败，错误码: ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(Exception("获取社区笔记异常：${e.message}", e))
            }
        }
    }

    // 获取笔记评论（无需 Token，保持不变）
    suspend fun getCommentsByNoteId(noteId: Long): Result<BaseResponse<PageResponse<Comment>>> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "开始获取评论，noteId: $noteId")
                val response = apiService.getCommentsByNoteId(noteId)
                Log.d(TAG, "评论响应码: ${response.code()}")
                Log.d(TAG, "评论响应体: ${response.body()}")

                if (response.isSuccessful) {
                    val body = response.body()
                    Log.d(TAG, "评论数据: ${body?.data?.content?.size ?: 0} 条")
                    Result.success(body ?: BaseResponse(404, "无数据", null))
                } else {
                    Log.e(TAG, "获取评论失败: ${response.code()}")
                    Result.failure(Exception("获取评论失败: ${response.code()}"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "获取评论异常：${e.message}", e)
                Result.failure(Exception("获取评论异常：${e.message}", e))
            }
        }
    }

    // 发布评论（添加 Token 过期判断）
    suspend fun addComment(noteId: Long, content: String): Result<BaseResponse<Unit>> {
        return withContext(Dispatchers.IO) {
            try {
                val token = UserManager.getToken() ?: return@withContext Result.failure(Exception("未登录"))
                val request = CommentRequest(noteId, content)
                val response = apiService.addComment(request, "Bearer $token")

                // 处理 Token 过期
                if (response.code() == TOKEN_EXPIRED_CODE) {
                    return@withContext Result.failure(Exception("登录过期"))
                }

                if (response.isSuccessful) {
                    Result.success(response.body() ?: BaseResponse(200, "发布成功", null))
                } else {
                    Result.failure(Exception("发布评论失败: ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(Exception(if (e.message == "登录过期") "登录过期" else "发布评论异常：${e.message}"))
            }
        }
    }

    // 点赞（同步添加 Token 过期处理）
    suspend fun likeNote(noteId: Long): Result<BaseResponse<Unit>> {
        return withContext(Dispatchers.IO) {
            try {
                val token = UserManager.getToken() ?: return@withContext Result.failure(Exception("未登录"))
                val response = apiService.likeNote(noteId, "Bearer $token")

                if (response.code() == TOKEN_EXPIRED_CODE) {
                    return@withContext Result.failure(Exception("登录过期"))
                }

                if (response.isSuccessful) {
                    Result.success(response.body() ?: BaseResponse(200, "点赞成功", null))
                } else {
                    Result.failure(Exception("点赞失败，错误码: ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(Exception(if (e.message == "登录过期") "登录过期" else "点赞异常：${e.message}"))
            }
        }
    }

    // 取消点赞（需要 Token，补充 Token 传递）
    suspend fun unlikeNote(noteId: Long): Result<BaseResponse<Unit>> {
        return withContext(Dispatchers.IO) {
            try {
                val token = UserManager.getToken() ?: return@withContext Result.failure(Exception("未登录，无法取消点赞"))
                val response = apiService.unlikeNote(noteId, "Bearer $token")

                // 补充 Token 过期判断（与其他方法保持一致）
                if (response.code() == TOKEN_EXPIRED_CODE) {
                    return@withContext Result.failure(Exception("登录过期"))
                }

                if (response.isSuccessful) {
                    Result.success(response.body() ?: BaseResponse(200, "取消点赞成功", null))
                } else {
                    Result.failure(Exception("取消点赞失败，错误码: ${response.code()}，错误信息：${response.message()}"))
                }
            } catch (e: Exception) {
                Result.failure(Exception(if (e.message == "登录过期") "登录过期" else "取消点赞异常：${e.message}", e))
            }
        }
    }

    // 删除一级评论
    suspend fun deleteComment(commentId: Long): Result<BaseResponse<Unit>> {
        return withContext(Dispatchers.IO) {
            try {
                // 1. 校验登录状态
                val token = UserManager.getToken() ?: return@withContext Result.failure(Exception("未登录，无法删除评论"))

                // 2. 调用删除评论接口（传递Bearer Token）
                val response = apiService.deleteComment(commentId, "Bearer $token")

                // 3. 处理Token过期
                if (response.code() == TOKEN_EXPIRED_CODE) {
                    return@withContext Result.failure(Exception("登录过期"))
                }

                // 4. 处理响应结果
                if (response.isSuccessful) {
                    Result.success(response.body() ?: BaseResponse(200, "删除评论成功", null))
                } else {
                    // 解析后端返回的错误信息
                    val errorMsg = response.body()?.message ?: "删除评论失败，错误码: ${response.code()}"
                    Result.failure(Exception(errorMsg))
                }
            } catch (e: Exception) {
                // 统一异常处理（区分Token过期和其他异常）
                Result.failure(Exception(
                    if (e.message == "登录过期") "登录过期" else "删除评论异常：${e.message}",
                    e
                ))
            }
        }
    }

    // ========== 二级评论相关方法（最终修正版） ==========
    /**
     * 获取一级评论的二级评论列表
     */
    suspend fun getCommentReplies(commentId: Long): Result<BaseResponse<PageResponse<CommentReply>>> {
        return withContext(Dispatchers.IO) {
            try {
                val token = UserManager.getToken() ?: return@withContext Result.failure(Exception("未登录"))
                val response = apiService.getCommentReplies(
                    commentId = commentId,
                    pageNum = 1,
                    pageSize = 20,
                    token = "Bearer $token"
                )

                // Token过期处理
                if (response.code() == TOKEN_EXPIRED_CODE) {
                    Log.e(TAG, "获取二级评论失败：Token过期")
                    return@withContext Result.failure(Exception("登录过期"))
                }

                Log.d(TAG, "获取二级评论响应码: ${response.code()}")
                Log.d(TAG, "获取二级评论响应体: ${response.body()}")

                if (response.isSuccessful) {
                    val body = response.body()
                    Log.d(TAG, "二级评论数据: ${body?.data?.content?.size ?: 0} 条")
                    Result.success(body ?: BaseResponse(404, "无二级评论数据", null))
                } else {
                    Log.e(TAG, "获取二级评论失败: ${response.code()}")
                    Result.failure(Exception("获取二级评论失败: ${response.code()}"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "获取二级评论异常：${e.message}", e)
                Result.failure(Exception(
                    if (e.message == "登录过期") "登录过期" else "获取二级评论异常：${e.message}",
                    e
                ))
            }
        }
    }

    /**
     * 发布二级评论（回复评论）
     */
    suspend fun addCommentReply(request: CommentReplyRequest): Result<BaseResponse<CommentReply>> {
        return withContext(Dispatchers.IO) {
            try {
                val token = UserManager.getToken() ?: return@withContext Result.failure(Exception("未登录"))
                Log.d(TAG, "发布二级评论请求参数: $request")

                val response = apiService.addCommentReply(
                    request = request,
                    token = "Bearer $token"
                )

                // Token过期处理
                if (response.code() == TOKEN_EXPIRED_CODE) {
                    Log.e(TAG, "发布二级评论失败：Token过期")
                    return@withContext Result.failure(Exception("登录过期"))
                }

                Log.d(TAG, "发布二级评论响应码: ${response.code()}")
                Log.d(TAG, "发布二级评论响应体: ${response.body()}")

                if (response.isSuccessful) {
                    Result.success(response.body() ?: BaseResponse(200, "回复成功", null))
                } else {
                    val errorMsg = response.body()?.message ?: "发布二级评论失败: ${response.code()}"
                    Log.e(TAG, errorMsg)
                    Result.failure(Exception(errorMsg))
                }
            } catch (e: Exception) {
                Log.e(TAG, "发布二级评论异常：${e.message}", e)
                Result.failure(Exception(
                    if (e.message == "登录过期") "登录过期" else "发布二级评论异常：${e.message}",
                    e
                ))
            }
        }
    }

    /**
     * 删除二级评论
     */
    suspend fun deleteCommentReply(replyId: Long): Result<BaseResponse<Unit>> {
        return withContext(Dispatchers.IO) {
            try {
                val token = UserManager.getToken() ?: return@withContext Result.failure(Exception("未登录"))
                val response = apiService.deleteCommentReply(
                    replyId = replyId,
                    token = "Bearer $token"
                )

                // Token过期处理
                if (response.code() == TOKEN_EXPIRED_CODE) {
                    return@withContext Result.failure(Exception("登录过期"))
                }

                if (response.isSuccessful) {
                    Result.success(response.body() ?: BaseResponse(200, "删除回复成功", null))
                } else {
                    val errorMsg = response.body()?.message ?: "删除二级评论失败: ${response.code()}"
                    Result.failure(Exception(errorMsg))
                }
            } catch (e: Exception) {
                Result.failure(Exception(
                    if (e.message == "登录过期") "登录过期" else "删除二级评论异常：${e.message}",
                    e
                ))
            }
        }
    }
}