package com.lm.shiguang

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.github.chrisbanes.photoview.PhotoView
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

/**
 * 通用图片预览页（新增：长按保存图片到相册）
 */
class ImagePreviewActivity : AppCompatActivity() {
    private val TAG = "ImagePreviewActivity"
    private lateinit var photoView: PhotoView
    private lateinit var tvPage: TextView
    private var imageList: List<String> = emptyList()
    private var currentIndex = 0
    private val SERVER_BASE_URL = "http://115.159.100.244:24681"
    // 权限请求码
    private val REQUEST_STORAGE_PERMISSION = 1001
    // 当前要保存的图片路径/URL
    private var currentImagePath = ""
    // 标记是否正在执行保存操作（控制回调是否执行）
    private var isSavingImage = false
    // Glide请求的引用，用于取消请求
    private var glideSaveRequest: CustomTarget<Bitmap>? = null

    // 笔记ID（从Intent传入）
    private var noteId: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 沉浸式状态栏
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.apply {
                statusBarColor = android.graphics.Color.TRANSPARENT
                decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            }
        }
        setContentView(R.layout.activity_image_preview)

        // 初始化控件
        photoView = findViewById(R.id.photo_view)
        tvPage = findViewById(R.id.tv_page)
        val ivClose = findViewById<android.widget.ImageView>(R.id.iv_close)

        // 获取传递的参数
        currentImagePath = intent.getStringExtra("IMAGE_PATH") ?: ""
        imageList = intent.getStringArrayListExtra("IMAGE_LIST") ?: emptyList()
        currentIndex = intent.getIntExtra("CURRENT_INDEX", 0)
        // 获取笔记ID
        noteId = intent.getLongExtra("NOTE_ID", -1L)
        Log.d(TAG, "接收的笔记ID：$noteId") // 调试日志

        // 显示页码
        updatePageText()

        // 加载图片
        loadImage(currentImagePath)

        // 关闭按钮点击
        ivClose.setOnClickListener {
            cancelSaveImage()
            finish()
        }

        // 单击空白处关闭
        photoView.setOnPhotoTapListener { _, _, _ ->
            cancelSaveImage()
            finish()
        }

        // 左右滑动切换图片
        photoView.setOnSingleFlingListener { _, _, velocityX, _ ->
            cancelSaveImage()

            if (velocityX > 200 && currentIndex < imageList.size - 1) {
                currentIndex++
                currentImagePath = imageList[currentIndex]
                loadImage(currentImagePath)
                updatePageText()
                return@setOnSingleFlingListener true
            } else if (velocityX < -200 && currentIndex > 0) {
                currentIndex--
                currentImagePath = imageList[currentIndex]
                loadImage(currentImagePath)
                updatePageText()
                return@setOnSingleFlingListener true
            }
            return@setOnSingleFlingListener false
        }

        // 长按保存图片
        photoView.setOnLongClickListener {
            if (currentImagePath.isEmpty()) {
                Toast.makeText(this, "图片路径为空，无法保存", Toast.LENGTH_SHORT).show()
                return@setOnLongClickListener true
            }
            isSavingImage = true
            checkPermissionAndSaveImage()
            true
        }
    }

    /**
     * 取消保存操作（重置状态+取消Glide请求）
     */
    private fun cancelSaveImage() {
        isSavingImage = false
        glideSaveRequest?.let {
            Glide.with(this).clear(it)
        }
        glideSaveRequest = null
    }

    /**
     * 检查权限并保存图片
     */
    private fun checkPermissionAndSaveImage() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ""
        } else {
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        }

        if (permission.isNotEmpty() && ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(permission),
                REQUEST_STORAGE_PERMISSION
            )
        } else {
            saveImageToGallery()
        }
    }

    /**
     * 保存图片到相册（核心方法）
     */
    private fun saveImageToGallery() {
        if (!isSavingImage) return

        if (currentImagePath.startsWith("http") || currentImagePath.startsWith("/images/")) {
            val finalUrl = if (currentImagePath.startsWith("/images/")) {
                "$SERVER_BASE_URL$currentImagePath"
            } else {
                currentImagePath
            }

            glideSaveRequest = object : CustomTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    if (isSavingImage) {
                        saveBitmapToGallery(resource)
                    }
                    glideSaveRequest = null
                }

                override fun onLoadCleared(placeholder: Drawable?) {
                    if (isSavingImage) {
                        Toast.makeText(this@ImagePreviewActivity, "图片加载失败，无法保存", Toast.LENGTH_SHORT).show()
                    }
                    glideSaveRequest = null
                }

                override fun onLoadFailed(errorDrawable: Drawable?) {
                    super.onLoadFailed(errorDrawable)
                    if (isSavingImage) {
                        Toast.makeText(this@ImagePreviewActivity, "图片加载失败，无法保存", Toast.LENGTH_SHORT).show()
                    }
                    glideSaveRequest = null
                }
            }

            Glide.with(this)
                .asBitmap()
                .load(finalUrl)
                .into(glideSaveRequest!!)
        } else {
            val localFile = File(currentImagePath)
            if (localFile.exists()) {
                try {
                    val bitmap = android.graphics.BitmapFactory.decodeFile(localFile.absolutePath)
                    saveBitmapToGallery(bitmap)
                } catch (e: Exception) {
                    Log.e(TAG, "读取本地图片失败", e)
                    if (isSavingImage) {
                        Toast.makeText(this, "读取本地图片失败：${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                if (isSavingImage) {
                    Toast.makeText(this, "本地图片文件不存在", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * 优化：精准删除旧文件（解决Android 10+删除失效问题）
     */
    private fun deleteOldFile(fileName: String) {
        try {
            Log.d(TAG, "开始删除旧文件：$fileName") // 调试日志
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+：优化筛选条件，先查后删（更精准）
                val contentResolver = contentResolver
                val projection = arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.RELATIVE_PATH
                )
                // 只筛选Shiguang文件夹下的目标文件
                val selection = "${MediaStore.Images.Media.DISPLAY_NAME} = ?"
                val selectionArgs = arrayOf(fileName)

                val cursor = contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    null
                )

                cursor?.use {
                    if (it.moveToFirst()) {
                        val idColumn = it.getColumnIndex(MediaStore.Images.Media._ID)
                        val fileId = it.getLong(idColumn)
                        val deleteUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI.buildUpon()
                            .appendPath(fileId.toString())
                            .build()
                        val deleteCount = contentResolver.delete(deleteUri, null, null)
                        Log.d(TAG, "Android 10+ 删除成功，文件ID：$fileId，删除数量：$deleteCount")
                    } else {
                        Log.d(TAG, "Android 10+ 未找到旧文件：$fileName")
                    }
                }
            } else {
                // Android 10以下：直接删除文件
                val pictureDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES + "/Shiguang")
                val oldFile = File(pictureDir, fileName)
                if (oldFile.exists()) {
                    val isDeleted = oldFile.delete()
                    Log.d(TAG, "Android 10以下 删除旧文件：$fileName，是否删除成功：$isDeleted")
                } else {
                    Log.d(TAG, "Android 10以下 未找到旧文件：$fileName")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "删除旧文件失败：$fileName，错误：${e.message}", e)
        }
    }

    /**
     * 将Bitmap保存到相册（文件名：shiguang_笔记ID_图片序号.jpg）
     */
    private fun saveBitmapToGallery(bitmap: Bitmap) {
        if (!isSavingImage) return

        try {
            // 图片序号：currentIndex + 1（从1开始）
            val imageIndex = currentIndex + 1
            // 文件名规则：shiguang_笔记ID_图片序号.jpg
            val fileName = if (noteId != -1L) {
                "shiguang_${noteId}_${imageIndex}.jpg"
            } else {
                "shiguang_unknown_${imageIndex}.jpg"
            }
            Log.d(TAG, "生成文件名：$fileName") // 调试日志

            // 核心：保存前先删除同名旧文件（解决(1)后缀问题）
            deleteOldFile(fileName)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = android.content.ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Shiguang")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }

                val contentResolver = contentResolver
                val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                uri?.let {
                    val outputStream: OutputStream? = contentResolver.openOutputStream(it)
                    outputStream?.use { stream ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
                    }
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    contentResolver.update(it, contentValues, null, null)
                    Log.d(TAG, "Android 10+ 保存成功，URI：$uri")
                } ?: Log.e(TAG, "Android 10+ 保存失败：URI为空")
            } else {
                val pictureDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES + "/Shiguang")
                if (!pictureDir.exists()) {
                    pictureDir.mkdirs()
                    Log.d(TAG, "创建文件夹：$pictureDir")
                }
                val file = File(pictureDir, fileName)
                val outputStream = FileOutputStream(file)
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                outputStream.flush()
                outputStream.close()

                val mediaScanIntent = android.content.Intent(android.content.Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                mediaScanIntent.data = android.net.Uri.fromFile(file)
                sendBroadcast(mediaScanIntent)
                Log.d(TAG, "Android 10以下 保存成功，文件路径：${file.absolutePath}")
            }

            Toast.makeText(this, "图片已保存到相册", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e(TAG, "保存图片失败", e)
            if (isSavingImage) {
                Toast.makeText(this, "保存失败：${e.message}", Toast.LENGTH_SHORT).show()
            }
        } finally {
            isSavingImage = false
            glideSaveRequest = null
        }
    }

    /**
     * 权限请求回调
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_STORAGE_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                saveImageToGallery()
            } else {
                isSavingImage = false
                Toast.makeText(this, "需要存储权限才能保存图片", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 加载图片（兼容本地/网络/相对路径）
     */
    private fun loadImage(imagePath: String) {
        try {
            if (imagePath.startsWith("http")) {
                Glide.with(this)
                    .load(imagePath)
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.ic_delete)
                    .into(photoView)
            } else if (imagePath.startsWith("/images/")) {
                val fullUrl = "$SERVER_BASE_URL$imagePath"
                Glide.with(this)
                    .load(fullUrl)
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.ic_delete)
                    .into(photoView)
            } else {
                val file = File(imagePath)
                if (file.exists()) {
                    Glide.with(this)
                        .load(file)
                        .placeholder(android.R.drawable.ic_menu_gallery)
                        .error(android.R.drawable.ic_delete)
                        .into(photoView)
                } else {
                    photoView.setImageResource(android.R.drawable.ic_delete)
                    Toast.makeText(this, "图片文件不存在", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "加载预览图片失败：$imagePath", e)
            photoView.setImageResource(android.R.drawable.ic_delete)
            Toast.makeText(this, "图片加载失败", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 更新页码提示
     */
    private fun updatePageText() {
        tvPage.text = "${currentIndex + 1}/${imageList.size}"
    }

    /**
     * 页面销毁时取消所有保存操作
     */
    override fun onDestroy() {
        super.onDestroy()
        cancelSaveImage()
    }
}