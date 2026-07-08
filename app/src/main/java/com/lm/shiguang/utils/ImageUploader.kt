package com.lm.shiguang.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

object ImageUploader {
    private const val TAG = "ImageUploader"

    suspend fun uploadImage(context: Context, imagePath: String, token: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val file = File(imagePath)
                if (!file.exists()) {
                    Log.e(TAG, "图片文件不存在: $imagePath")
                    return@withContext Result.failure(Exception("图片文件不存在"))
                }

                Log.d(TAG, "开始上传图片: ${file.name}, 大小: ${file.length()} bytes")

                val requestFile = file.asRequestBody("image/*".toMediaTypeOrNull())
                val body = MultipartBody.Part.createFormData("file", file.name, requestFile)

                val apiService = com.lm.shiguang.network.RetrofitClient.apiService
                val response = apiService.uploadImage(body, "Bearer $token")

                Log.d(TAG, "图片上传响应码: ${response.code()}")

                if (response.isSuccessful) {
                    val apiResponse = response.body()
                    Log.d(TAG, "上传响应体: $apiResponse")

                    if (apiResponse != null && apiResponse.code == 200) {
                        val imageRelativePath = apiResponse.data ?: ""
                        if (imageRelativePath.isNotEmpty()) {
                            Log.d(TAG, "图片上传成功，相对路径: $imageRelativePath")
                            return@withContext Result.success(imageRelativePath)
                        } else {
                            return@withContext Result.failure(Exception("上传失败：返回的图片路径为空"))
                        }
                    } else {
                        val errorMsg = apiResponse?.message ?: "上传失败：接口返回失败"
                        return@withContext Result.failure(Exception(errorMsg))
                    }
                } else {
                    val errorBody = response.errorBody()?.string() ?: "未知错误"
                    return@withContext Result.failure(Exception("上传失败: ${response.code()}, 错误: $errorBody"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "图片上传异常", e)
                return@withContext Result.failure(Exception("图片上传异常: ${e.message}"))
            }
        }
    }

    suspend fun uploadMultipleImages(context: Context, imagePaths: List<String>, token: String): Result<List<String>> {
        return withContext(Dispatchers.IO) {
            try {
                val urls = mutableListOf<String>()
                for ((index, imagePath) in imagePaths.withIndex()) {
                    Log.d(TAG, "上传图片 [$index/${imagePaths.size}]: $imagePath")
                    val result = uploadImage(context, imagePath, token)
                    if (result.isSuccess) {
                        val path = result.getOrNull()
                        if (path != null && path.isNotEmpty()) {
                            urls.add(path)
                            Log.d(TAG, "图片 [$index] 上传成功: $path")
                        }
                    } else {
                        Log.e(TAG, "图片 [$index] 上传失败: ${result.exceptionOrNull()?.message}")
                    }
                }
                Log.d(TAG, "批量上传完成，成功 ${urls.size}/${imagePaths.size} 张")
                return@withContext Result.success(urls)
            } catch (e: Exception) {
                Log.e(TAG, "批量上传图片异常", e)
                return@withContext Result.failure(Exception("批量上传图片异常: ${e.message}"))
            }
        }
    }

    /**
     * 新增：删除服务器单张图片
     * @param imageRelativePath 相对路径，如 /images/2026/01/23/xxx.jpg
     * @param token 用户token
     * @return Result<Boolean> 是否删除成功
     */
    suspend fun deleteImage(imageRelativePath: String, token: String): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "开始删除服务器图片：$imageRelativePath")

                val apiService = com.lm.shiguang.network.RetrofitClient.apiService
                val response = apiService.deleteImage(imageRelativePath, "Bearer $token")

                Log.d(TAG, "删除图片响应码: ${response.code()}")

                if (response.isSuccessful) {
                    val baseResponse = response.body()
                    Log.d(TAG, "删除响应体: $baseResponse")

                    if (baseResponse != null && baseResponse.code == 200) {
                        val isSuccess = baseResponse.data ?: false
                        Log.d(TAG, "图片删除${if (isSuccess) "成功" else "失败"}：$imageRelativePath")
                        return@withContext Result.success(isSuccess)
                    } else {
                        val errorMsg = baseResponse?.message ?: "删除失败：接口返回非200码"
                        return@withContext Result.failure(Exception(errorMsg))
                    }
                } else {
                    val errorBody = response.errorBody()?.string() ?: "未知错误"
                    return@withContext Result.failure(Exception("删除失败: ${response.code()}, 错误: $errorBody"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "删除图片异常", e)
                return@withContext Result.failure(Exception("删除图片异常: ${e.message}"))
            }
        }
    }
}