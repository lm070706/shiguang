package com.lm.shiguang

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.lm.shiguang.databinding.ActivityNoteDetailBinding
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

class NoteDetailActivity : AppCompatActivity() {
    private val TAG = "NoteDetailActivity"
    private lateinit var binding: ActivityNoteDetailBinding
    private lateinit var noteViewModel: NoteViewModel
    private var noteLocalId: Long = -1L
    private val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    // 1. 定义服务器基础URL（仅用于前端加载图片时拼接）
    private val SERVER_BASE_URL = "http://115.159.100.244:24681"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNoteDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        noteViewModel = ViewModelProvider(this)[NoteViewModel::class.java]

        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "笔记详情"
        }

        noteLocalId = intent.getLongExtra("NOTE_LOCAL_ID", -1L)
        Log.d(TAG, "接收到的笔记本地ID：$noteLocalId")

        if (noteLocalId == -1L) {
            Toast.makeText(this, "无效的笔记ID", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        noteViewModel.currentNote.observe(this) { note ->
            Log.d(TAG, "观察到笔记数据：${note?.title ?: "null"}，图片数量：${note?.imageUrls?.size ?: 0}")

            note?.let {
                binding.tvNoteTitle.text = it.title ?: "无标题"
                binding.tvNoteContent.text = it.content ?: "无内容"
                binding.tvNoteTime.text = "创建时间：${formatCreateTime(it.createTime)}"

                // 展示图片（核心：这里拼接完整URL）
                showNoteImages(it.imageUrls)
            } ?: run {
                Toast.makeText(this, "笔记已被删除或不存在", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

        noteViewModel.loadNoteByLocalId(noteLocalId)
    }

    // 2. 核心修复：加载图片时拼接完整URL + Glide缓存优化
    private fun showNoteImages(imageUrls: List<String>?) {
        binding.llNoteImages.removeAllViews()

        if (imageUrls.isNullOrEmpty()) {
            binding.horizontalScrollView.visibility = View.GONE
            return
        }

        binding.horizontalScrollView.visibility = View.VISIBLE

        // Glide缓存配置（内存+磁盘缓存，提升加载效率）
        val glideOptions = RequestOptions()
            .placeholder(R.drawable.ic_image_placeholder) // 加载中占位图
            .error(R.drawable.ic_image_error) // 加载失败占位图
            .diskCacheStrategy(DiskCacheStrategy.ALL) // 缓存所有版本（原图+缩略图）
            .skipMemoryCache(false) // 不跳过内存缓存

        imageUrls.forEach { imagePath ->
            try {
                val imageView = ImageView(this).apply {
                    layoutParams = androidx.appcompat.widget.LinearLayoutCompat.LayoutParams(
                        200,
                        200
                    ).apply {
                        setMargins(0, 0, 10, 10)
                    }
                    scaleType = ImageView.ScaleType.CENTER_CROP

                    // 关键：判断是相对路径/完整URL，拼接成可访问的完整URL
                    val finalImageUrl = if (imagePath.startsWith("http")) {
                        imagePath // 已有完整URL，直接用
                    } else {
                        // 相对路径拼接成完整URL
                        if (imagePath.startsWith("/")) {
                            "$SERVER_BASE_URL$imagePath"
                        } else {
                            "$SERVER_BASE_URL/$imagePath"
                        }
                    }
                    Log.d(TAG, "加载图片URL：$finalImageUrl")

                    // 用Glide加载，带缓存优化
                    Glide.with(this@NoteDetailActivity)
                        .load(finalImageUrl)
                        .apply(glideOptions)
                        .into(this)

                    // 点击图片放大预览
                    setOnClickListener {
                        val intent = Intent(this@NoteDetailActivity, ImagePreviewActivity::class.java).apply {
                            putExtra("IMAGE_PATH", finalImageUrl)
                            putStringArrayListExtra("IMAGE_LIST", ArrayList(imageUrls))
                            putExtra("CURRENT_INDEX", imageUrls.indexOf(imagePath))
                            putExtra("NOTE_ID", noteLocalId)
                        }
                        startActivity(intent)
                    }
                }
                binding.llNoteImages.addView(imageView)
            } catch (e: Exception) {
                Log.e(TAG, "加载图片失败：$imagePath，错误：${e.message}")
            }
        }
    }

    // 时间格式化（无修改）
    private fun formatCreateTime(createTime: Any?): String {
        return try {
            when (createTime) {
                is LocalDateTime -> createTime.format(timeFormatter)
                is String -> {
                    if (createTime.contains("T")) {
                        LocalDateTime.parse(createTime).format(timeFormatter)
                    } else {
                        createTime
                    }
                }
                else -> "未知时间"
            }
        } catch (e: DateTimeParseException) {
            "格式错误"
        } catch (e: Exception) {
            "加载失败"
        }
    }

    // ActionBar事件（无修改）
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_edit -> {
                val intent = Intent(this, EditNoteActivity::class.java).apply {
                    putExtra("NOTE_LOCAL_ID", noteLocalId)
                }
                startActivity(intent)
                finish()
                true
            }
            R.id.action_share -> {
                shareNote()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_note_detail, menu)
        return true
    }

    // 分享逻辑（无修改）
    private fun shareNote() {
        val note = noteViewModel.currentNote.value ?: run {
            Toast.makeText(this, "暂无笔记可分享", Toast.LENGTH_SHORT).show()
            return
        }

        val shareContent = buildString {
            if (note.title.isNotBlank()) append("${note.title}\n\n")
            append(note.content)
            append("\n\n创建时间：${formatCreateTime(note.createTime)}")
            if (!note.imageUrls.isNullOrEmpty()) {
                append("\n\n包含${note.imageUrls.size}张图片")
            }
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareContent)
        }

        startActivity(Intent.createChooser(shareIntent, "分享笔记"))
    }
}