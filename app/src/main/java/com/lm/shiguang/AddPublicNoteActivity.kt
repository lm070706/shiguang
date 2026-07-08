package com.lm.shiguang

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.lm.shiguang.databinding.ActivityAddPublicNoteBinding
import com.lm.shiguang.utils.ImagePicker
import com.lm.shiguang.utils.ImageUploader
import com.lm.shiguang.utils.UserManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 公开笔记发布页面（修复+图片预览）
 */
class AddPublicNoteActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAddPublicNoteBinding
    private lateinit var noteViewModel: NoteViewModel
    private val TAG = "AddPublicNoteActivity"
    private var isPublishing = false
    private val selectedImages = mutableListOf<String>()
    private val MAX_IMAGE_COUNT = 3
    // 服务器基础URL（用于图片预览拼接）
    private val SERVER_BASE_URL = "http://115.159.100.244:24681"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddPublicNoteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 前置登录校验
        if (!UserManager.isLogin()) {
            Toast.makeText(this, "请先登录再发布笔记", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 初始化ViewModel
        noteViewModel = ViewModelProvider(this)[NoteViewModel::class.java]
        // 初始化图片选择功能
        initImagePicker()
        // 观察发布状态
        observeViewModel()
        // 绑定发布事件
        bindPublishEvent()
    }

    /**
     * 初始化图片选择相关逻辑
     */
    private fun initImagePicker() {
        binding.btnAddImage.setOnClickListener {
            ImagePicker.openAlbumDirectly(this) { imagePath ->
                if (selectedImages.size >= MAX_IMAGE_COUNT) {
                    Toast.makeText(this, "最多只能选择3张图片", Toast.LENGTH_SHORT).show()
                    return@openAlbumDirectly
                }
                // 避免重复添加同一张图片
                if (!selectedImages.contains(imagePath)) {
                    selectedImages.add(imagePath)
                    addImageToPreview(imagePath)
                    Toast.makeText(this, "已选择图片：${imagePath.substringAfterLast("/")}", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "该图片已选择", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * 添加图片预览到布局（新增：点击放大预览 + Glide优化加载）
     */
    private fun addImageToPreview(imagePath: String) {
        val imageView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(200, 200).apply {
                setMargins(0, 0, 10, 10)
            }
            scaleType = ImageView.ScaleType.CENTER_CROP

            // 优化：用Glide加载本地图片（更高效，支持缓存）
            Glide.with(this@AddPublicNoteActivity)
                .load(java.io.File(imagePath))
                .placeholder(R.drawable.ic_image_placeholder)
                .error(R.drawable.ic_image_error)
                .into(this)

            // 1. 长按移除图片（避免误触）
            setOnLongClickListener {
                selectedImages.remove(imagePath)
                binding.llImagePreview.removeView(this)
                Toast.makeText(this@AddPublicNoteActivity, "已移除图片", Toast.LENGTH_SHORT).show()
                true
            }

            // 2. 点击放大预览图片（核心：跳转到图片预览页）
            setOnClickListener {
                val intent = Intent(this@AddPublicNoteActivity, ImagePreviewActivity::class.java).apply {
                    // 传递当前图片路径 + 所有已选图片列表 + 当前索引
                    putExtra("IMAGE_PATH", imagePath)
                    putStringArrayListExtra("IMAGE_LIST", ArrayList(selectedImages))
                    putExtra("CURRENT_INDEX", selectedImages.indexOf(imagePath))
                }
                startActivity(intent)
            }
        }
        binding.llImagePreview.addView(imageView)
    }

    /**
     * 处理权限和图片选择回调
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        ImagePicker.onRequestPermissionsResult(this, requestCode, grantResults)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        ImagePicker.onActivityResult(this, requestCode, resultCode, data)
    }

    /**
     * 绑定发布按钮点击事件（修复：空token判断 + 异常兜底）
     */
    private fun bindPublishEvent() {
        binding.btnPublishPublicNote.setOnClickListener {
            if (isPublishing) return@setOnClickListener

            val title = binding.etPublicTitle.text.toString().trim()
            val content = binding.etPublicContent.text.toString().trim()
            if (title.isEmpty() || content.isEmpty()) {
                Toast.makeText(this, "标题和内容不能为空", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            isPublishing = true
            binding.btnPublishPublicNote.isEnabled = false
            binding.progressBar.visibility = View.VISIBLE

            Log.d(TAG, "开始发布公开笔记：title=$title，图片数=${selectedImages.size}")

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val token = UserManager.getToken() ?: ""
                    if (token.isEmpty()) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@AddPublicNoteActivity, "登录状态失效，请重新登录", Toast.LENGTH_SHORT).show()
                            resetPublishState()
                        }
                        return@launch
                    }

                    var uploadedImageUrls = emptyList<String>()

                    // 上传图片
                    if (selectedImages.isNotEmpty()) {
                        Log.d(TAG, "开始上传图片...")
                        val uploadResult = ImageUploader.uploadMultipleImages(
                            context = this@AddPublicNoteActivity,
                            imagePaths = selectedImages,
                            token = token
                        )

                        if (uploadResult.isSuccess) {
                            uploadedImageUrls = uploadResult.getOrNull() ?: emptyList()
                            Log.d(TAG, "图片上传成功: ${uploadedImageUrls.size} 张")
                        } else {
                            val errorMsg = uploadResult.exceptionOrNull()?.message ?: "未知错误"
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@AddPublicNoteActivity, "图片上传失败：$errorMsg，将继续发布笔记", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }

                    // 发布笔记
                    withContext(Dispatchers.Main) {
                        noteViewModel.addNewNote(
                            title = title,
                            content = content,
                            imageUrls = uploadedImageUrls,
                            isPublic = true
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "发布笔记异常", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@AddPublicNoteActivity, "发布失败: ${e.message}", Toast.LENGTH_SHORT).show()
                        resetPublishState()
                    }
                }
            }
        }
    }

    /**
     * 重置发布状态（抽离复用）
     */
    private fun resetPublishState() {
        isPublishing = false
        binding.btnPublishPublicNote.isEnabled = true
        binding.progressBar.visibility = View.GONE
    }

    /**
     * 观察ViewModel的发布状态
     */
    private fun observeViewModel() {
        noteViewModel.operationStatus.observe(this) { status ->
            resetPublishState()

            status?.let {
                when (it) {
                    NoteViewModel.OperationStatus.ADD_SUCCESS -> {
                        Toast.makeText(this@AddPublicNoteActivity, "发布成功", Toast.LENGTH_SHORT).show()
                        Log.d(TAG, "公开笔记发布成功，同步并拉取最新数据")
                        lifecycleScope.launch(Dispatchers.IO) {
                            noteViewModel.retrySyncUnsyncedNotes()
                            noteViewModel.fetchNotesFromServer()
                        }
                        setResult(Activity.RESULT_OK)
                        finish()
                    }
                    NoteViewModel.OperationStatus.ERROR -> {
                        Log.e(TAG, "公开笔记发布失败")
                        Toast.makeText(this@AddPublicNoteActivity, "发布失败，请稍后重试", Toast.LENGTH_SHORT).show()
                    }
                    else -> {}
                }
                noteViewModel.resetOperationStatus()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        noteViewModel.operationStatus.removeObservers(this)
    }
}