package com.lm.shiguang.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

object UserManager {
    private const val PREF_NAME = "user_info_prefs" // 换个SP文件名，避免旧数据冲突
    private const val KEY_USER_ID = "user_id"
    private const val KEY_TOKEN = "token"
    private const val KEY_NICKNAME = "nickname"
    private const val KEY_EMAIL = "email"
    private const val TAG = "LoginTest"

    // 强引用存ApplicationContext，生命周期和App一致，不会泄漏
    private var appContext: Context? = null
    private val spLock = Any() // 避免并发读写冲突

    // 初始化：必须在MainActivity最顶部调用
    fun init(context: Context) {
        if (appContext == null) {
            synchronized(this) {
                appContext = context.applicationContext

            }
        }
    }

    // 保存登录信息：同步写入，必成功
    fun saveLoginInfo(userId: Long, token: String, nickname: String, email: String) {
        synchronized(spLock) {
            val prefs = getPrefs() ?: run {

                return
            }
            val success = prefs.edit()
                .putLong(KEY_USER_ID, userId)
                .putString(KEY_TOKEN, token)
                .putString(KEY_NICKNAME, nickname)
                .putString(KEY_EMAIL, email)
                .commit() // 同步保存，立即写入磁盘

        }
    }

    // 读取方法：加锁确保读取准确
    fun getUserId(): Long {
        synchronized(spLock) {
            return getPrefs()?.getLong(KEY_USER_ID, 0L) ?: 0L
        }
    }

    fun getToken(): String {
        synchronized(spLock) {
            return getPrefs()?.getString(KEY_TOKEN, "") ?: ""
        }
    }

    fun getNickname(): String {
        synchronized(spLock) {
            return getPrefs()?.getString(KEY_NICKNAME, "用户") ?: "用户"
        }
    }

    fun getEmail(): String {
        synchronized(spLock) {
            return getPrefs()?.getString(KEY_EMAIL, "") ?: ""
        }
    }

    // 登录判断：双重校验，确保准确
    fun isLogin(): Boolean {
        synchronized(spLock) {
            val userIdValid = getUserId() != 0L
            val tokenValid = getToken().isNotEmpty()
            val isLogin = userIdValid && tokenValid

            return isLogin
        }
    }

    // 登出：同步清除
    fun logout() {
        synchronized(spLock) {
            val success = getPrefs()?.edit()?.clear()?.commit() ?: false

        }
    }

    fun clearUser() = logout()

    // 内部获取SP：确保appContext不为空
    private fun getPrefs(): SharedPreferences? {
        if (appContext == null) {

            return null
        }
        return appContext?.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }
}