package com.lm.shiguang.network

import android.content.Context
import com.lm.shiguang.utils.UserManager
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private const val BASE_URL = "http://115.159.100.244:24681/api/"
    private const val TIMEOUT = 30L

    // 1. 核心：Retrofit 实例（供自定义接口创建用，如 PopupManager）
    private val retrofit: Retrofit by lazy {
        createRetrofitClient()
    }

    // 2. 仓库层需要的 ApiService 实例（直接返回接口代理类，解决类型不匹配）
    val apiService: ApiService by lazy {
        retrofit.create(ApiService::class.java)
    }

    // 兼容旧代码：保留 instance 别名（如果有代码直接用 instance 调用 ApiService）
    @Deprecated("请使用 apiService 替代", ReplaceWith("RetrofitClient.apiService"))
    val instance: ApiService by lazy {
        apiService
    }

    // 如果你需要在创建时传入 Context（例如初始化 UserManager），可以保留这个方法
    fun getInstance(context: Context): ApiService {
        return apiService
    }

    private fun createRetrofitClient(): Retrofit {
        val loggingInterceptor = HttpLoggingInterceptor { msg ->
            println("OkHttp: $msg")
        }.setLevel(HttpLoggingInterceptor.Level.BODY)

        val authInterceptor = Interceptor { chain ->
            val originalRequest = chain.request()

            // 不需要认证的接口列表
            val noAuthPaths = listOf("login", "register", "send-code")
            val path = originalRequest.url.pathSegments.lastOrNull() ?: ""

            // 如果是登录、注册等不需要认证的接口，则直接放行
            if (noAuthPaths.any { path.contains(it, ignoreCase = true) }) {
                return@Interceptor chain.proceed(originalRequest)
            }

            // 从 UserManager 获取 Token
            val token = UserManager.getToken()

            // 如果 Token 不为空，则为请求添加 Authorization 头
            val requestWithAuth = token?.let {
                originalRequest.newBuilder()
                    .header("Authorization", "Bearer $it")
                    .build()
            } ?: originalRequest

            chain.proceed(requestWithAuth)
        }

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor(authInterceptor) // 添加认证拦截器
            .connectTimeout(TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    // 供 PopupManager 使用的 Retrofit 实例（单独暴露，不影响仓库层）
    val retrofitInstance: Retrofit by lazy {
        createRetrofitClient()
    }
}