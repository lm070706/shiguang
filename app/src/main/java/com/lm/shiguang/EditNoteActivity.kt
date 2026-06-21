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
import com.lm.shiguang.databinding.ActivityEditNoteBinding
import com.lm.shiguang.network.UserNote
import com.lm.shiguang.utils.ImagePicker
import com.lm.shiguang.utils.ImageUploader
import com.lm.shiguang.utils.UserManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime

class EditNoteActivity : AppCompatActivity() {
    private val TAG = "EditNoteActivity"
    private lateinit var binding: ActivityEditNoteBinding
    private lateinit var viewModel: NoteViewModel
    private var noteLocalId: Long = -1L
    private var isUpdating = false
    private val selectedImages = mutableListOf<String>() // 最终要保存的图片路径（相对路径/完整URL）
    private val localImagePaths = mutableListOf<String>() // 临时存储新选择的本地图片路径
    private val MAX_IMAGE_COUNT = 3
    private val SERVER_BASE_URL = "http://115.159.100.244:24681" // 图片服务器基础URL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditNoteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[NoteViewModel::class.java]

        noteLocalId = intent.getLongExtra("NOTE_LOCAL_ID", -1L)
        if (noteLocalId == -1L) {
            Toast.makeText(this, "无效的笔记ID", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        if (!UserManager.isLogin()) {
            Toast.makeText(this, "用户未登录，请重新登录", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        observeViewModelStatus()
        initUI()
        initImagePicker()
        loadNoteData()
    }

    private fun initUI() {
        binding.etEditTitle.requestFocus()
        binding.etEditContent.inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        binding.etEditContent.maxLines = Integer.MAX_VALUE

        binding.btnSaveEdit.setOnClickListener { saveChanges() }
        binding.btnCancel.setOnClickListener { finish() }

        binding.llImagePreview.orientation = LinearLayout.HORIZONTAL
        binding.llImagePreview.setPadding(0, 16, 0, 16)
    }

    /**
     * 修复：初始化图片选择逻辑（区分本地图片和网络图片）
     */
    private fun initImagePicker() {
        binding.btnAddImage.setOnClickListener {
            ImagePicker.openAlbumDirectly(this) { imagePath ->
                if (selectedImages.size >= MAX_IMAGE_COUNT) {
                    Toast.makeText(this, "最多只能选择3张图片", Toast.LENGTH_SHORT).show()
                    return@openAlbumDirectly
                }
                // 新选择的本地图片：先存到临时列表，上传后替换为相对路径
                localImagePaths.add(imagePath)
                selectedImages.add(imagePath) // 临时显示，保存时会替换
                addImageToPreview(imagePath)
                updateAddImageBtnStatus()
                Toast.makeText(this, "已选择图片：${imagePath.substringAfterLast("/")}", Toast.LENGTH_SHORT).show()
            }
        }
        updateAddImageBtnStatus()
    }

    /**
     * 核心修复：加载图片预览（兼容网络相对路径/本地路径）+ 单张删除逻辑
     */
    private fun addImageToPreview(imagePath: String) {
        val imageView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(200, 200).apply {
                setMargins(0, 0, 10, 10)
            }
            scaleType = ImageView.ScaleType.CENTER_CROP

            // 区分图片类型加载
            if (imagePath.startsWith("/images/")) {
                // 原有图片：相对路径 → 拼接完整URL用Glide加载
                val fullUrl = "$SERVER_BASE_URL$imagePath"
                Glide.with(this@EditNoteActivity)
                    .load(fullUrl)
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.ic_delete)
                    .into(this)
            } else if (imagePath.startsWith("http")) {
                // 完整URL：直接用Glide加载
                Glide.with(this@EditNoteActivity)
                    .load(imagePath)
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.ic_delete)
                    .into(this)
            } else {
                // 新选本地图片：加载本地文件
                val file = File(imagePath)
                if (file.exists()) {
                    Glide.with(this@EditNoteActivity)
                        .load(file)
                        .placeholder(android.R.drawable.ic_menu_gallery)
                        .error(android.R.drawable.ic_delete)
                        .into(this)
                } else {
                    setImageResource(android.R.drawable.ic_delete)
                }
            }

            // 长按删除（包含服务器图片删除）
            setOnLongClickListener {
                // 关键：提前保存当前ImageView的引用
                val currentImageView = this@apply
                if (imagePath.startsWith("/images/")) {
                    // 服务器图片：先删服务器，再删本地
                    val token = UserManager.getToken() ?: ""
                    if (token.isEmpty()) {
                        Toast.makeText(this@EditNoteActivity, "请先登录再删除图片", Toast.LENGTH_SHORT).show()
                        // 仍删除本地数据，保证UI一致性
                        localImagePaths.remove(imagePath)
                        selectedImages.remove(imagePath)
                        binding.llImagePreview.removeView(currentImageView)
                        updateAddImageBtnStatus()
                        return@setOnLongClickListener true
                    }

                    // 显示加载提示
                    Toast.makeText(this@EditNoteActivity, "正在删除图片...", Toast.LENGTH_SHORT).show()

                    // 协程处理服务器删除
                    lifecycleScope.launch(Dispatchers.IO) {
                        val deleteResult = ImageUploader.deleteImage(imagePath, token)

                        // 主线程更新UI
                        withContext(Dispatchers.Main) {
                            val tipMsg = if (deleteResult.isSuccess && deleteResult.getOrNull() == true) {
                                "图片删除成功"
                            } else {
                                val errorMsg = deleteResult.exceptionOrNull()?.message ?: "服务器删除失败"
                                "已移除本地图片（$errorMsg）"
                            }
                            Toast.makeText(this@EditNoteActivity, tipMsg, Toast.LENGTH_SHORT).show()

                            // 无论服务器删除是否成功，都移除本地显示和数据
                            localImagePaths.remove(imagePath)
                            selectedImages.remove(imagePath)
                            binding.llImagePreview.removeView(currentImageView) // 用提前保存的引用
                            updateAddImageBtnStatus()
                        }
                    }
                } else {
                    // 本地未上传图片：直接删除
                    localImagePaths.remove(imagePath)
                    selectedImages.remove(imagePath)
                    binding.llImagePreview.removeView(currentImageView) // 统一用保存的引用
                    updateAddImageBtnStatus()
                    Toast.makeText(this@EditNoteActivity, "已移除本地图片", Toast.LENGTH_SHORT).show()
                }
                true
            }
            // 点击放大预览（复用之前的ImagePreviewActivity）
            setOnClickListener {
                val intent = Intent(this@EditNoteActivity, ImagePreviewActivity::class.java).apply {
                    putExtra("IMAGE_PATH", if (imagePath.startsWith("/images/")) "$SERVER_BASE_URL$imagePath" else imagePath)
                    putStringArrayListExtra("IMAGE_LIST", ArrayList(selectedImages.map {
                        if (it.startsWith("/images/")) "$SERVER_BASE_URL$it" else it
                    }))
                    putExtra("CURRENT_INDEX", selectedImages.indexOf(imagePath))
                    putExtra("NOTE_ID", noteLocalId)
                }
                startActivity(intent)
            }
        }
        binding.llImagePreview.addView(imageView)
    }

    private fun updateAddImageBtnStatus() {
        binding.btnAddImage.isEnabled = selectedImages.size < MAX_IMAGE_COUNT
        binding.btnAddImage.text = if (selectedImages.size >= MAX_IMAGE_COUNT) {
            "最多3张图片"
        } else {
            "添加图片"
        }
    }

    /**
     * 修复：加载原有笔记数据（正确显示图片）
     */
    private fun loadNoteData() {
        viewModel.loadNoteByLocalId(noteLocalId)
        viewModel.currentNote.observe(this) { note: UserNote? ->
            note?.let {
                binding.etEditTitle.setText(it.title)
                binding.etEditContent.setText(it.content)
                supportActionBar?.title = "编辑笔记"

                // 清空原有预览和列表
                binding.llImagePreview.removeAllViews()
                selectedImages.clear()
                localImagePaths.clear()

                // 加载原有图片（相对路径）
                if (!it.imageUrls.isNullOrEmpty()) {
                    selectedImages.addAll(it.imageUrls)
                    it.imageUrls.forEach { imagePath ->
                        addImageToPreview(imagePath) // 会自动拼接URL加载
                    }
                    updateAddImageBtnStatus()
                }
            } ?: run {
                if (!isFinishing) {
                    Toast.makeText(this, "笔记已被删除或不存在", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    /**
     * 核心修复：保存修改（上传新选本地图片，替换为相对路径）
     */
    private fun saveChanges() {
        if (isUpdating) return

        val newTitle = binding.etEditTitle.text.toString().trim()
        val newContent = binding.etEditContent.text.toString().trim()

        if (newTitle.isEmpty() || newContent.isEmpty()) {
            Toast.makeText(this, "标题和内容不能为空", Toast.LENGTH_SHORT).show()
            return
        }

        val currentNote = viewModel.currentNote.value ?: run {
            Toast.makeText(this, "笔记数据未就绪", Toast.LENGTH_SHORT).show()
            return
        }

        isUpdating = true
        binding.btnSaveEdit.isEnabled = false

        // 协程处理图片上传
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val token = UserManager.getToken() ?: ""
                if (token.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@EditNoteActivity, "登录状态失效，请重新登录", Toast.LENGTH_SHORT).show()
                        isUpdating = false
                        binding.btnSaveEdit.isEnabled = true
                    }
                    return@launch
                }

                // 步骤1：上传新选择的本地图片，替换为相对路径
                val finalImageUrls = mutableListOf<String>()
                // 先添加原有未修改的图片（相对路径）
                finalImageUrls.addAll(selectedImages.filter { !localImagePaths.contains(it) })
                // 上传新选的本地图片
                if (localImagePaths.isNotEmpty()) {
                    val uploadResult = ImageUploader.uploadMultipleImages(
                        context = this@EditNoteActivity,
                        imagePaths = localImagePaths,
                        token = token
                    )
                    if (uploadResult.isSuccess) {
                        val uploadedPaths = uploadResult.getOrNull() ?: emptyList()
                        finalImageUrls.addAll(uploadedPaths) // 替换为相对路径
                        Log.d(TAG, "新图片上传成功，相对路径：$uploadedPaths")
                    } else {
                        val errorMsg = uploadResult.exceptionOrNull()?.message ?: "未知错误"
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@EditNoteActivity, "新图片上传失败：$errorMsg", Toast.LENGTH_SHORT).show()
                        }
                        // 上传失败则只保留原有图片
                    }
                }

                // 步骤2：更新笔记
                val updatedNote = currentNote.copy(
                    title = newTitle,
                    content = newContent,
                    imageUrls = finalImageUrls, // 最终保存相对路径
                    updateTime = LocalDateTime.now(),
                    isSynced = false
                )

                // 主线程更新ViewModel
                withContext(Dispatchers.Main) {
                    viewModel.updateNote(updatedNote)
                }
            } catch (e: Exception) {
                Log.e(TAG, "保存笔记异常", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@EditNoteActivity, "保存失败：${e.message}", Toast.LENGTH_SHORT).show()
                    isUpdating = false
                    binding.btnSaveEdit.isEnabled = true
                }
            }
        }
    }

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

    private fun observeViewModelStatus() {
        viewModel.operationStatus.observe(this) { status ->
            isUpdating = false
            binding.btnSaveEdit.isEnabled = true

            status?.let {
                when (it) {
                    NoteViewModel.OperationStatus.UPDATE_SUCCESS -> {
                        Toast.makeText(this, "笔记更新成功", Toast.LENGTH_SHORT).show()
                        lifecycleScope.launch(Dispatchers.IO) {
                            viewModel.retrySyncUnsyncedNotes()
                        }
                        finish()
                    }
                    NoteViewModel.OperationStatus.ERROR -> {
                        Toast.makeText(this, "更新失败，请稍后重试", Toast.LENGTH_SHORT).show()
                    }
                    else -> {}
                }
                viewModel.resetOperationStatus()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.operationStatus.removeObservers(this)
        viewModel.currentNote.removeObservers(this)
    }
}