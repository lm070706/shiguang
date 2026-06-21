package com.lm.shiguang.repository

import android.content.Context
import com.lm.shiguang.network.ApiResponse
import com.lm.shiguang.network.ApiService
import com.lm.shiguang.network.LoginRequest
import com.lm.shiguang.network.LoginResponse
import com.lm.shiguang.network.RegisterRequest
import com.lm.shiguang.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AuthRepository(private val context: Context) {
    private val apiService: ApiService by lazy {
        RetrofitClient.getInstance(context)
    }

    suspend fun login(loginAccount: String, password: String): Result<LoginResponse> {
        return withContext(Dispatchers.IO) {
            try {
                when {
                    loginAccount.isEmpty() -> return@withContext Result.failure(Exception("请输入登录账号"))
                    password.isEmpty() -> return@withContext Result.failure(Exception("请输入密码"))
                }
                val response = apiService.login(LoginRequest(loginAccount, password))
                if (response.isSuccessful) {
                    val apiResponse: ApiResponse<LoginResponse>? = response.body()
                    if (apiResponse?.success == true && apiResponse.data != null) {
                        Result.success(apiResponse.data)
                    } else {
                        Result.failure(Exception(apiResponse?.message ?: "登录失败"))
                    }
                } else {
                    Result.failure(Exception("服务器错误，响应码: ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun sendVerificationCode(email: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                if (email.isEmpty() || !email.contains("@")) {
                    return@withContext Result.failure(Exception("请输入有效的邮箱"))
                }
                val response = apiService.sendVerificationCode(email)
                if (response.isSuccessful) {
                    val body: ApiResponse<*>? = response.body()
                    if (body?.success == true) {
                        Result.success(body.message ?: "验证码发送成功")
                    } else {
                        Result.failure(Exception(body?.message ?: "验证码发送失败"))
                    }
                } else {
                    Result.failure(Exception("服务器错误，响应码: ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun register(request: RegisterRequest): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                when {
                    request.email.isEmpty() || !request.email.contains("@") ->
                        return@withContext Result.failure(Exception("请输入有效的邮箱"))
                    request.password.isEmpty() || request.password.length < 6 ->
                        return@withContext Result.failure(Exception("密码不能为空且长度至少6位"))
                    request.password != request.confirmPassword ->
                        return@withContext Result.failure(Exception("两次输入的密码不一致"))
                    request.nickname.isEmpty() ->
                        return@withContext Result.failure(Exception("请输入昵称"))
                    request.verifyCode.isEmpty() ->
                        return@withContext Result.failure(Exception("请输入验证码"))
                }
                val response = apiService.register(request)
                if (response.isSuccessful) {
                    val body: ApiResponse<*>? = response.body()
                    if (body?.success == true) {
                        Result.success(body.message ?: "注册成功")
                    } else {
                        Result.failure(Exception(body?.message ?: "注册失败"))
                    }
                } else {
                    Result.failure(Exception("服务器错误，响应码: ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}