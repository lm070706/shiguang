package com.lm.shiguang

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.lm.shiguang.databinding.ActivityAddNoteBinding
import com.lm.shiguang.utils.ImagePicker
import com.lm.shiguang.utils.UserManager
import com.lm.shiguang.utils.ImageUploader  // 添加这个导入
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext  // 添加这个导入
import android.util.Log

/**
 * 私密笔记创建页面
 */
class AddNoteActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAddNoteBinding
    private lateinit var noteViewModel: NoteViewModel
    private var isSaving = false
    private val selectedImages = mutableListOf<String>()
    private val MAX_IMAGE_COUNT = 3
    private val TAG = "AddNoteActivity"  // 添加TAG常量

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddNoteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 初始化ViewModel
        noteViewModel = ViewModelProvider(this)[NoteViewModel::class.java]

        // 前置登录校验
        if (!UserManager.isLogin()) {
            Toast.makeText(this, "请先登录再创建笔记", Toast.LENGTH_SHORT).show()
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        // 初始化图片选择按钮事件
        initImagePicker()
        // 观察操作状态
        observeViewModel()
        // 初始化UI
        initUI()
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
                selectedImages.add(imagePath)
                addImageToPreview(imagePath)
                Toast.makeText(this, "已选择图片：${imagePath.substringAfterLast("/")}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 添加图片预览到布局中
     */
    private fun addImageToPreview(imagePath: String) {
        val imageView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(200, 200).apply {
                setMargins(0, 0, 10, 10)
            }
            setImageURI(android.net.Uri.fromFile(java.io.File(imagePath)))
            scaleType = ImageView.ScaleType.CENTER_CROP
            setOnClickListener {
                selectedImages.remove(imagePath)
                binding.llImagePreview.removeView(this)
                Toast.makeText(this@AddNoteActivity, "已移除图片", Toast.LENGTH_SHORT).show()
            }
        }
        binding.llImagePreview.addView(imageView)
    }

    /**
     * 处理权限和图片选择的回调
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
     * 观察ViewModel状态，处理保存结果
     */
    private fun observeViewModel() {
        noteViewModel.operationStatus.observe(this) { status ->
            isSaving = false
            binding.btnSaveNote.isEnabled = true
            binding.progressBar.visibility = View.GONE

            status?.let {
                when (it) {
                    NoteViewModel.OperationStatus.ADD_SUCCESS -> {
                        Toast.makeText(this, "笔记保存成功", Toast.LENGTH_SHORT).show()
                        lifecycleScope.launch(Dispatchers.IO) {
                            noteViewModel.retrySyncUnsyncedNotes()
                            noteViewModel.fetchNotesFromServer()
                        }
                        setResult(Activity.RESULT_OK)
                        finish()
                    }
                    NoteViewModel.OperationStatus.ERROR -> {
                        Toast.makeText(this, "保存失败，请稍后重试", Toast.LENGTH_SHORT).show()
                    }
                    else -> {}
                }
                noteViewModel.resetOperationStatus()
            }
        }
    }

    /**
     * 初始化UI和事件绑定
     */
    private fun initUI() {
        binding.etNoteTitle.requestFocus()
        binding.etNoteContent.apply {
            inputType = InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                    InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            maxLines = Integer.MAX_VALUE
            hint = "请输入笔记内容..."
        }

        binding.btnSaveNote.setOnClickListener { saveNote() }
        binding.btnCancel.setOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
    }

    /**
     * 保存笔记核心逻辑
     */
    private fun saveNote() {
        if (isSaving) return

        val title = binding.etNoteTitle.text.toString().trim()
        val content = binding.etNoteContent.text.toString().trim()
        if (title.isEmpty() || content.isEmpty()) {
            Toast.makeText(this, "标题和内容不能为空", Toast.LENGTH_SHORT).show()
            return
        }

        isSaving = true
        binding.btnSaveNote.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val token = UserManager.getToken() ?: ""
                var uploadedImageUrls = emptyList<String>()

                // 如果有选择图片，先上传图片
                if (selectedImages.isNotEmpty()) {
                    if (token.isEmpty()) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@AddNoteActivity, "请先登录再上传图片", Toast.LENGTH_SHORT).show()
                            noteViewModel.addNewNote(title, content, emptyList(), false)
                        }
                        return@launch
                    }

                    Log.d(TAG, "开始上传 ${selectedImages.size} 张图片")
                    val uploadResult = ImageUploader.uploadMultipleImages(
                        context = this@AddNoteActivity,
                        imagePaths = selectedImages,
                        token = token
                    )

                    if (uploadResult.isSuccess) {
                        uploadedImageUrls = uploadResult.getOrNull() ?: emptyList()
                        Log.d(TAG, "图片上传成功，获取到 ${uploadedImageUrls.size} 个URL")
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@AddNoteActivity, "部分图片上传失败，将继续保存笔记", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                // 使用上传后的图片URL创建笔记
                withContext(Dispatchers.Main) {
                    noteViewModel.addNewNote(
                        title = title,
                        content = content,
                        imageUrls = uploadedImageUrls,
                        isPublic = false
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "保存笔记异常", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AddNoteActivity, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    noteViewModel.addNewNote(
                        title = title,
                        content = content,
                        imageUrls = emptyList(),
                        isPublic = false
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        noteViewModel.operationStatus.removeObservers(this)
    }
}