package com.lm.shiguang.utils

import android.app.Activity
import android.app.Dialog
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import com.lm.shiguang.R
import com.lm.shiguang.network.Announcement
import com.lm.shiguang.network.BaseResponse
import com.lm.shiguang.network.VersionInfo
import com.lm.shiguang.network.RetrofitClient
import kotlinx.coroutines.*
import retrofit2.Response
import java.io.File
import java.nio.charset.Charset

/**
 * 最终版：移除动态广播注册，改用静态注册，解决所有访问权限和FLAG警告问题
 * 核心修复：
 * 1. 公开必要常量供广播接收器访问
 * 2. 移除动态广播相关代码，避免FLAG警告
 * 3. 保留所有核心功能（公告、版本更新、下载、安装）
 */
object PopupManager {
    const val REQUEST_INSTALL_PERMISSION = 1001 // 安装权限请求码
    const val TAG_PUBLIC = "PopupManager"       // 公开TAG供广播接收器访问
    var downloadIdPublic: Long = -1L            // 公开下载ID供广播接收器访问
        private set
    var activityRef: Activity? = null           // 公开Activity引用供广播接收器访问

    // SP配置常量
    private const val SP_NAME = "popup_config"
    private const val KEY_ANNOUNCEMENT_READ = "announcement_read"
    private const val KEY_LAST_VERSION_PROMPT_TIME = "last_version_prompt_time"

    // 全局变量
    private var _activity: Activity? = null
    private var _versionName: String? = null
    private var _downloadUrl: String? = null
    private val activity: Activity
        get() = _activity ?: throw IllegalStateException("Activity未初始化")
    private val versionName: String
        get() = _versionName ?: "1.0"

    // 自定义协程作用域
    private var customCoroutineScope: CoroutineScope? = null

    // ========== 1. 接口定义 ==========
    private interface AppApiService {
        @retrofit2.http.GET("app/announcement")
        suspend fun getAnnouncement(): Response<BaseResponse<Announcement>>

        @retrofit2.http.GET("app/version")
        suspend fun getVersionInfo(): Response<BaseResponse<VersionInfo>>
    }

    // ========== 2. Retrofit 初始化 ==========
    private val apiService: AppApiService by lazy {
        RetrofitClient.retrofitInstance.create(AppApiService::class.java)
    }

    // ========== 3. 乱码转码工具方法 ==========
    private fun convertToUtf8(str: String?): String {
        if (str.isNullOrEmpty()) return ""
        return try {
            String(str.toByteArray(Charset.forName("ISO-8859-1")), Charset.forName("UTF-8"))
        } catch (e1: Exception) {
            try {
                String(str.toByteArray(Charset.forName("GBK")), Charset.forName("UTF-8"))
            } catch (e2: Exception) {
                Log.e(TAG_PUBLIC, "转码失败，使用原字符串: ${e2.message}")
                str
            }
        }
    }

    // ========== 4. 公告弹窗 ==========
    fun fetchAnnouncementAndShow(activity: Activity) {
        _activity = activity
        customCoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        customCoroutineScope?.launch(Dispatchers.IO) {
            try {
                val response = apiService.getAnnouncement()
                if (response.isSuccessful && response.body() != null) {
                    val baseResponse = response.body()!!
                    if (baseResponse.code == 200 && baseResponse.data != null) {
                        val announcement = baseResponse.data!!
                        val id = convertToUtf8(announcement.id)
                        val title = convertToUtf8(announcement.title)
                        val content = convertToUtf8(announcement.content)

                        Log.d(TAG_PUBLIC, "公告解析成功: id=$id, title=$title")

                        withContext(Dispatchers.Main) {
                            showAnnouncementPopup(activity, mapOf(
                                "id" to id,
                                "title" to title,
                                "content" to content
                            ))
                        }
                    } else {
                        Log.e(TAG_PUBLIC, "公告接口业务失败: ${baseResponse.message}")
                    }
                } else {
                    Log.e(TAG_PUBLIC, "获取公告失败: 响应码${response.code()}")
                }
            } catch (e: Exception) {
                Log.e(TAG_PUBLIC, "获取公告失败: ${e.message}", e)
            }
        }
    }

    private fun showAnnouncementPopup(activity: Activity, announcement: Map<String, String>) {
        val sp = activity.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
        val announcementId = announcement["id"] ?: return
        if (sp.getBoolean("${KEY_ANNOUNCEMENT_READ}_$announcementId", false)) return

        val dialog = Dialog(activity, R.style.CustomDialogStyle).apply {
            setCanceledOnTouchOutside(false)
            requestWindowFeature(Window.FEATURE_NO_TITLE)
        }

        val view = LayoutInflater.from(activity).inflate(R.layout.popup_announcement, null)
        dialog.setContentView(view)

        val tvTitle = view.findViewById<TextView>(R.id.tv_announcement_title)
        val tvContent = view.findViewById<TextView>(R.id.tv_announcement_content)
        val btnConfirm = view.findViewById<Button>(R.id.btn_announcement_confirm)

        tvTitle?.text = announcement["title"]
        tvContent?.text = announcement["content"]

        btnConfirm?.setOnClickListener {
            sp.edit().putBoolean("${KEY_ANNOUNCEMENT_READ}_$announcementId", true).apply()
            dialog.dismiss()
        }

        setDialogStyle(dialog, activity)
        dialog.show()
    }

    // ========== 5. 版本更新 ==========
    fun fetchVersionAndCheckUpdate(activity: Activity) {
        _activity = activity
        customCoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        customCoroutineScope?.launch(Dispatchers.IO) {
            try {
                val response = apiService.getVersionInfo()
                if (response.isSuccessful && response.body() != null) {
                    val baseResponse = response.body()!!
                    if (baseResponse.code == 200 && baseResponse.data != null) {
                        val versionInfo = baseResponse.data!!
                        val serverCode = versionInfo.latestVersionCode
                        val latestVersionName = convertToUtf8(versionInfo.latestVersionName)
                        val updateContent = convertToUtf8(versionInfo.updateContent)
                        val downloadUrl = convertToUtf8(versionInfo.downloadUrl)

                        Log.d(TAG_PUBLIC, "版本信息解析成功: code=$serverCode, name=$latestVersionName")

                        val localCode = getLocalVersionCode(activity)
                        if (serverCode > localCode) {
                            val sp = activity.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
                            val lastPrompt = sp.getLong(KEY_LAST_VERSION_PROMPT_TIME, 0)
                            val currentTime = System.currentTimeMillis()

                            _versionName = latestVersionName
                            _downloadUrl = downloadUrl
                            withContext(Dispatchers.Main) {
                                showVersionUpdatePopup(activity, sp, currentTime, serverCode, latestVersionName, updateContent, downloadUrl)
                            }
                        } else {
                            Log.d(TAG_PUBLIC, "当前版本已是最新: local=$localCode, server=$serverCode")
                        }
                    } else {
                        Log.e(TAG_PUBLIC, "版本接口业务失败: ${baseResponse.message}")
                    }
                } else {
                    Log.e(TAG_PUBLIC, "获取版本信息失败: 响应码${response.code()}")
                }
            } catch (e: Exception) {
                Log.e(TAG_PUBLIC, "检测版本更新失败: ${e.message}", e)
            }
        }
    }

    private fun showVersionUpdatePopup(
        activity: Activity,
        sp: SharedPreferences,
        currentTime: Long,
        serverCode: Int,
        versionName: String,
        updateContent: String,
        downloadUrl: String
    ) {
        val dialog = Dialog(activity, R.style.CustomDialogStyle).apply {
            setCanceledOnTouchOutside(false)
            requestWindowFeature(Window.FEATURE_NO_TITLE)
        }

        val view = LayoutInflater.from(activity).inflate(R.layout.popup_version_update, null)
        dialog.setContentView(view)

        val tvVersion = view.findViewById<TextView>(R.id.tv_version_name)
        val tvContent = view.findViewById<TextView>(R.id.tv_update_content)
        val btnUpdate = view.findViewById<Button>(R.id.btn_update)
        val btnIgnore = view.findViewById<Button>(R.id.btn_ignore)

        tvVersion?.text = "发现新版本 v$versionName"
        tvContent?.text = updateContent.replace("\\n", "\n")

        btnUpdate?.setOnClickListener {
            if (downloadUrl.isBlank() || !downloadUrl.endsWith(".apk")) {
                Toast.makeText(activity, "无效的APK下载链接", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
                return@setOnClickListener
            }

            // Android 8.0+ 申请安装权限
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!activity.packageManager.canRequestPackageInstalls()) {
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}"))
                    activity.startActivityForResult(intent, REQUEST_INSTALL_PERMISSION)
                    Toast.makeText(activity, "请开启安装未知来源应用权限", Toast.LENGTH_LONG).show()
                    dialog.dismiss()
                    return@setOnClickListener
                }
            }

            // 直接开始下载（无需注册广播）
            startApkDownload(activity, downloadUrl, versionName)
            Toast.makeText(activity, "开始下载新版本 v$versionName", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        btnIgnore?.setOnClickListener {
            sp.edit().putLong(KEY_LAST_VERSION_PROMPT_TIME, currentTime).apply()
            dialog.dismiss()
        }

        setDialogStyle(dialog, activity)
        dialog.show()
    }

    // ========== 6. APK下载 ==========
    private fun startApkDownload(activity: Activity, downloadUrl: String, versionName: String) {
        try {
            val downloadManager = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val apkFileName = "shiguang_v$versionName.apk"

            // 应用私有目录（无需存储权限）
            val apkDir = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            if (apkDir == null) {
                Toast.makeText(activity, "无法创建下载目录", Toast.LENGTH_SHORT).show()
                return
            }
            if (!apkDir.exists()) apkDir.mkdirs()
            val apkFile = File(apkDir, apkFileName)
            if (apkFile.exists()) apkFile.delete()

            Log.d(TAG_PUBLIC, "下载路径: ${apkFile.absolutePath}")

            val request = DownloadManager.Request(Uri.parse(downloadUrl))
                .setTitle("拾光圈 v$versionName 下载")
                .setDescription("正在下载新版本安装包...")
                .setMimeType("application/vnd.android.package-archive")
                .setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
                .setDestinationUri(Uri.fromFile(apkFile))
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(false)

            // 保存公开的下载ID和Activity引用
            downloadIdPublic = downloadManager.enqueue(request)
            activityRef = activity
            Log.d(TAG_PUBLIC, "下载任务已提交，ID: $downloadIdPublic")
        } catch (e: Exception) {
            Log.e(TAG_PUBLIC, "下载APK失败: ${e.message}", e)
            Toast.makeText(activity, "下载失败，请重试", Toast.LENGTH_SHORT).show()
        }
    }

    // ========== 7. 安装APK ==========
    fun installApk(activity: Activity) {
        val apkFileName = "shiguang_v$versionName.apk"
        val apkDir = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: run {
            Toast.makeText(activity, "下载目录不存在", Toast.LENGTH_SHORT).show()
            return
        }
        val apkFile = File(apkDir, apkFileName)

        // 校验文件
        if (!apkFile.exists()) {
            Log.e(TAG_PUBLIC, "APK文件不存在: ${apkFile.absolutePath}")
            Toast.makeText(activity, "安装包不存在，请重新下载", Toast.LENGTH_SHORT).show()
            return
        }
        if (apkFile.length() < 1024 * 1024) {
            Log.e(TAG_PUBLIC, "APK文件损坏，大小: ${apkFile.length()} bytes")
            Toast.makeText(activity, "安装包损坏，请重新下载", Toast.LENGTH_SHORT).show()
            apkFile.delete()
            return
        }

        try {
            // FileProvider 兼容 Android 7.0+
            val apkUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(
                    activity,
                    "com.lm.shiguang.fileprovider",
                    apkFile
                )
            } else {
                Uri.fromFile(apkFile)
            }

            // 安装意图
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }

            // 授予URI权限
            val pm = activity.packageManager
            val resolveInfo = pm.queryIntentActivities(installIntent, PackageManager.MATCH_DEFAULT_ONLY)
            resolveInfo.forEach {
                activity.grantUriPermission(
                    it.activityInfo.packageName,
                    apkUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }

            Log.d(TAG_PUBLIC, "启动安装，URI: $apkUri")
            activity.startActivity(installIntent)
        } catch (e: SecurityException) {
            Log.e(TAG_PUBLIC, "安装权限被拒绝: ${e.message}", e)
            Toast.makeText(activity, "安装权限被拒绝，请手动开启", Toast.LENGTH_LONG).show()
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}"))
            activity.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG_PUBLIC, "安装APK失败: ${e.message}", e)
            Toast.makeText(activity, "安装失败，请手动打开${apkDir.absolutePath}安装", Toast.LENGTH_LONG).show()
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.fromFile(apkDir), "resource/folder")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val pm = activity.packageManager
            if (pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null) {
                activity.startActivity(intent)
            }
        }
    }

    // ========== 8. 处理权限申请结果 ==========
    fun onActivityResult(requestCode: Int, resultCode: Int) {
        if (requestCode == REQUEST_INSTALL_PERMISSION) {
            _activity?.let { activity ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    if (activity.packageManager.canRequestPackageInstalls()) {
                        // 权限开启后重新下载
                        _downloadUrl?.let { url ->
                            startApkDownload(activity, url, versionName)
                            Toast.makeText(activity, "开始下载新版本", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(activity, "未开启安装权限，无法更新", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // ========== 9. 工具方法 ==========
    private fun getLocalVersionCode(context: Context): Int {
        return try {
            val pm = context.packageManager
            val pkgName = context.packageName
            val pkgInfo: PackageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(pkgName, PackageManager.PackageInfoFlags.of(0))
            } else {
                pm.getPackageInfo(pkgName, 0)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pkgInfo.longVersionCode.toInt()
            } else {
                pkgInfo.versionCode
            }
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG_PUBLIC, "获取本地版本号失败: ${e.message}")
            1
        }
    }

    private fun setDialogStyle(dialog: Dialog, activity: Activity) {
        val window = dialog.window ?: return
        val params = WindowManager.LayoutParams().apply {
            copyFrom(window.attributes)
            width = (activity.resources.displayMetrics.widthPixels * 0.8).toInt()
            window.setBackgroundDrawableResource(android.R.color.transparent)
        }
        window.attributes = params
    }

    fun resetPopupStatus(context: Context) {
        if (context is Activity) {
            CoroutineScope(Dispatchers.Main).launch(Dispatchers.IO) {
                try {
                    val response = apiService.getAnnouncement()
                    if (response.isSuccessful && response.body() != null) {
                        val baseResponse = response.body()!!
                        if (baseResponse.code == 200 && baseResponse.data != null) {
                            val announcementId = convertToUtf8(baseResponse.data!!.id)
                            val sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
                            sp.edit()
                                .remove("${KEY_ANNOUNCEMENT_READ}_$announcementId")
                                .remove(KEY_LAST_VERSION_PROMPT_TIME)
                                .apply()
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "弹窗状态已重置", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG_PUBLIC, "重置弹窗状态失败: ${e.message}", e)
                }
            }
        }
    }

    // ========== 10. 资源清理 ==========
    fun clearResources() {
        _activity = null
        _versionName = null
        _downloadUrl = null
        activityRef = null // 清空Activity引用
        customCoroutineScope?.cancel()
        customCoroutineScope = null
    }
}