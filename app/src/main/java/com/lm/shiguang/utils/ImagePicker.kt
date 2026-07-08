package com.lm.shiguang.utils

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.app.ActivityCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ImagePicker {
    private const val REQUEST_CODE_ALBUM = 1001
    private const val REQUEST_CODE_PERMISSION = 1003
    private var onImageSelected: ((String) -> Unit)? = null
    private const val APP_PACKAGE_NAME = "com.lm.shiguang"

    fun init() {
        onImageSelected = null
    }

    /**
     * 直接打开系统相册（无任何弹窗，仅做权限检测）
     */
    fun openAlbumDirectly(activity: Activity, callback: (String) -> Unit) {
        init()
        onImageSelected = callback

        // 分版本检测相册权限（仅检测，无多余逻辑）
        val isAlbumGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.checkSelfPermission(
                activity,
                android.Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ActivityCompat.checkSelfPermission(
                activity,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }

        if (isAlbumGranted) {
            launchAlbum(activity) // 直接开相册
        } else {
            // 仅申请相册权限（无多余弹窗，直接调系统权限框）
            val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(android.Manifest.permission.READ_MEDIA_IMAGES)
            } else {
                arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            ActivityCompat.requestPermissions(activity, perm, REQUEST_CODE_PERMISSION)
        }
    }

    // 权限回调：授权后直接开相册，拒绝仅简单提示（无多余弹窗）
    fun onRequestPermissionsResult(
        activity: Activity,
        requestCode: Int,
        grantResults: IntArray
    ) {
        if (requestCode != REQUEST_CODE_PERMISSION) return
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            launchAlbum(activity)
        } else {
            Toast.makeText(activity, "未授权相册权限，无法选择图片", Toast.LENGTH_SHORT).show()
        }
    }

    // 相册结果回调：**完全保留你之前能正常加载的逻辑**，无压缩/无Uri转换
    fun onActivityResult(
        activity: Activity,
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        if (requestCode == REQUEST_CODE_ALBUM && resultCode == Activity.RESULT_OK && data != null) {
            val imageUri = data.data ?: return
            // 核心：直接将Uri转成文件路径（你之前能正常加载的方式，无任何修改）
            val imagePath = getRealPathFromUri(activity, imageUri)
            imagePath?.let {
                onImageSelected?.invoke(it)
            } ?: Toast.makeText(activity, "获取图片路径失败", Toast.LENGTH_SHORT).show()
        }
    }

    // 直接打开系统相册（原生Intent，无多余Flag）
    private fun launchAlbum(activity: Activity) {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        intent.type = "image/*"
        activity.startActivityForResult(intent, REQUEST_CODE_ALBUM)
    }

    // 你之前能用的Uri转真实路径方法（无修改）
    private fun getRealPathFromUri(activity: Activity, uri: Uri): String? {
        val projection = arrayOf(MediaStore.Images.Media.DATA)
        val cursor = activity.contentResolver.query(uri, projection, null, null, null)
        return try {
            if (cursor?.moveToFirst() == true) {
                val index = cursor.getColumnIndex(MediaStore.Images.Media.DATA)
                cursor.getString(index)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        } finally {
            cursor?.close()
        }
    }
}

// 仅保留你项目中需要的基础工具类（无多余代码）
class AppContextProvider : android.app.Application() {
    companion object {
        lateinit var appContext: android.content.Context
            private set
    }

    override fun onCreate() {
        super.onCreate()
        appContext = this
    }
}

object ToastUtil {
    fun show(msg: String) {
        Toast.makeText(AppContextProvider.appContext, msg, Toast.LENGTH_SHORT).show()
    }
}