package com.lm.shiguang

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.lm.shiguang.network.Comment
import com.lm.shiguang.network.CommunityNote
import com.lm.shiguang.network.CommentReply
import com.lm.shiguang.network.CommentReplyRequest
import com.lm.shiguang.repository.CommunityRepository
import com.lm.shiguang.utils.TimeUtils
import com.lm.shiguang.utils.UserManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import android.content.Intent
import java.io.File

class CommunityNoteDetailActivity : AppCompatActivity() {
    private val TAG = "CommunityNoteDetail"
    private val communityRepository by lazy { CommunityRepository() }
    private lateinit var commentAdapter: CommentAdapter
    private val commentList = mutableListOf<Comment>()
    private var noteId = -1L
    private var isLoading = false
    private var isNoteLiked = false

    // 新增：服务器基础URL（拼接相对路径，和私密页保持一致）
    private val SERVER_BASE_URL = "http://115.159.100.244:24681"

    // 控件声明
    private lateinit var outerScrollView: ScrollView
    private lateinit var ivBack: ImageView
    private lateinit var tvNoteTitle: TextView
    private lateinit var tvNoteAuthor: TextView
    private lateinit var tvNoteCreateTime: TextView
    private lateinit var tvNoteContent: TextView
    private lateinit var llNoteImages: LinearLayout
    private lateinit var horizontalScrollView: HorizontalScrollView
    private lateinit var ivNoteLike: ImageView
    private lateinit var tvNoteLikeCount: TextView
    private lateinit var tvNoteCommentCount: TextView
    private lateinit var llCommentToggle: LinearLayout
    private lateinit var tvCommentToggle: TextView
    private lateinit var ivCommentArrow: ImageView
    private lateinit var llCommentContainer: LinearLayout
    private lateinit var rvNoteComments: RecyclerView
    private lateinit var tvCommentEmpty: TextView
    private lateinit var etCommentContent: EditText
    private lateinit var btnSendComment: Button
    private lateinit var btnCancelReply: Button // 新增取消按钮
    private lateinit var llInputContainer: LinearLayout
    private lateinit var progressBar: ProgressBar

    // 回复相关状态
    private var isReplyMode = false
    private var replyTargetType = ReplyTargetType.NONE
    private var targetCommentId = -1L
    private var targetReplyId = -1L
    private var targetReplyToUserId = -1L
    private var targetReplyToUserName = ""
    private val originCommentHint = "输入评论内容..."

    // 回复类型枚举
    private enum class ReplyTargetType {
        NONE,       // 无目标，发表一级评论
        COMMENT,    // 回复一级评论
        REPLY       // 回复二级评论
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_community_note_detail)

        initViews()
        getNoteIdFromIntent()
        initCommentRecyclerView()
        bindEvents()
        loadNoteDetail()
        loadComments()
    }

    private fun initViews() {
        outerScrollView = findViewById(R.id.outer_scroll_view)
        ivBack = findViewById(R.id.iv_back)
        tvNoteTitle = findViewById(R.id.tv_note_title)
        tvNoteAuthor = findViewById(R.id.tv_note_author)
        tvNoteCreateTime = findViewById(R.id.tv_note_create_time)
        tvNoteContent = findViewById(R.id.tv_note_content)
        horizontalScrollView = findViewById(R.id.horizontalScrollView)
        llNoteImages = findViewById(R.id.ll_note_images)
        ivNoteLike = findViewById(R.id.iv_note_like)
        tvNoteLikeCount = findViewById(R.id.tv_note_like_count)
        tvNoteCommentCount = findViewById(R.id.tv_note_comment_count)
        llCommentToggle = findViewById(R.id.ll_comment_toggle)
        tvCommentToggle = findViewById(R.id.tv_comment_toggle)
        ivCommentArrow = findViewById(R.id.iv_comment_arrow)
        llCommentContainer = findViewById(R.id.ll_comment_container)
        rvNoteComments = findViewById(R.id.rv_note_comments)
        tvCommentEmpty = findViewById(R.id.tv_comment_empty)
        etCommentContent = findViewById(R.id.et_comment_content)
        btnSendComment = findViewById(R.id.btn_send_comment)
        btnCancelReply = findViewById(R.id.btn_cancel_reply) // 初始化取消按钮
        llInputContainer = findViewById(R.id.ll_input_container)
        progressBar = findViewById(R.id.progress_bar)
    }

    private fun getNoteIdFromIntent() {
        noteId = intent.getLongExtra("NOTE_ID", -1L)
        if (noteId <= 0) {
            Toast.makeText(this, "无效的笔记ID", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun initCommentRecyclerView() {
        commentAdapter = CommentAdapter(
            context = this,
            data = commentList,
            onCommentClick = { comment ->
                setReplyToComment(comment)
            },
            onCommentLongClick = { clickType, comment ->
                when (clickType) {
                    LongClickType.DELETE -> {
                        showDeleteCommentDialog(comment)
                        true
                    }
                    LongClickType.COPY -> {
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clipData = ClipData.newPlainText("评论内容", comment.content ?: "")
                        clipboard.setPrimaryClip(clipData)
                        Toast.makeText(this, "评论内容已复制", Toast.LENGTH_SHORT).show()
                        true
                    }
                    else -> false
                }
            },
            onDeleteReplyClick = { reply ->
                showDeleteReplyDialog(reply)
            },
            onReplyClick = { reply ->
                setReplyToReply(reply)
            }
        )

        // 修复RecyclerView设置
        rvNoteComments.layoutManager = LinearLayoutManager(this@CommunityNoteDetailActivity)
        rvNoteComments.adapter = commentAdapter
    }

    private fun bindEvents() {
        ivBack.setOnClickListener { finish() }

        llCommentToggle.setOnClickListener {
            toggleCommentContainer()
        }

        ivNoteLike.setOnClickListener {
            handleLikeClick()
        }

        btnSendComment.setOnClickListener {
            handleSendComment()
        }

        // 新增：取消按钮点击事件
        btnCancelReply.setOnClickListener {
            resetReplyState()
            btnCancelReply.visibility = View.GONE
            etCommentContent.hint = originCommentHint
            hideKeyboard()
            etCommentContent.clearFocus()
        }

        findViewById<View>(android.R.id.content).setOnClickListener {
            hideKeyboard()
        }

        etCommentContent.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && llCommentContainer.visibility != View.VISIBLE) {
                showCommentContainer()
            }
        }
    }

    // 设置回复一级评论（显示取消按钮）
    private fun setReplyToComment(comment: Comment) {
        isReplyMode = true
        replyTargetType = ReplyTargetType.COMMENT
        targetCommentId = comment.id
        targetReplyId = -1L
        targetReplyToUserId = comment.author?.id ?: -1L
        targetReplyToUserName = comment.author?.nickname ?: "用户"

        etCommentContent.hint = "回复 @${targetReplyToUserName}："
        etCommentContent.requestFocus()
        btnCancelReply.visibility = View.VISIBLE // 显示取消按钮
        showCommentContainer()
    }

    // 设置回复二级评论（显示取消按钮）
    private fun setReplyToReply(reply: CommentReply) {
        isReplyMode = true
        replyTargetType = ReplyTargetType.REPLY
        targetCommentId = reply.commentId
        targetReplyId = reply.id
        targetReplyToUserId = reply.author?.id ?: -1L
        targetReplyToUserName = reply.author?.nickname ?: "用户"

        etCommentContent.hint = "回复 @${targetReplyToUserName}："
        etCommentContent.requestFocus()
        btnCancelReply.visibility = View.VISIBLE // 显示取消按钮
        showCommentContainer()
    }

    // 显示评论容器
    private fun showCommentContainer() {
        llCommentContainer.visibility = View.VISIBLE
        ivCommentArrow.rotation = 180f
        tvCommentToggle.text = "收起评论区"

        // 滚动到评论区可见的位置
        Handler(Looper.getMainLooper()).postDelayed({
            outerScrollView.smoothScrollTo(0, llCommentToggle.bottom)
        }, 100)
    }

    // 切换评论容器显示/隐藏
    private fun toggleCommentContainer() {
        val isVisible = llCommentContainer.visibility == View.VISIBLE
        if (isVisible) {
            hideCommentContainer()
        } else {
            showCommentContainer()
        }
    }

    // 隐藏评论容器
    private fun hideCommentContainer() {
        llCommentContainer.visibility = View.GONE
        ivCommentArrow.rotation = 0f
        val commentCount = tvNoteCommentCount.text.toString()
        tvCommentToggle.text = "查看评论区 ($commentCount)"

        if (etCommentContent.hasFocus()) {
            etCommentContent.clearFocus()
            hideKeyboard()
        }
    }

    // 隐藏键盘
    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(etCommentContent.windowToken, 0)
    }

    // 处理点赞点击
    private fun handleLikeClick() {
        val token = UserManager.getToken() ?: ""
        if (token.isEmpty()) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show()
            return
        }
        if (isNoteLiked) {
            Toast.makeText(this, "已点赞过啦～", Toast.LENGTH_SHORT).show()
            return
        }
        ivNoteLike.isClickable = false

        lifecycleScope.launch(Dispatchers.IO) {
            val result = communityRepository.likeNote(noteId)
            withContext(Dispatchers.Main) {
                ivNoteLike.isClickable = true
                if (result.isSuccess) {
                    val response = result.getOrNull()
                    if (response?.code == 200) {
                        isNoteLiked = true
                        val currentCount = tvNoteLikeCount.text.toString().toIntOrNull() ?: 0
                        tvNoteLikeCount.text = (currentCount + 1).toString()
                        ivNoteLike.setImageResource(R.drawable.ic_like_selected)
                        Toast.makeText(this@CommunityNoteDetailActivity, "点赞成功", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // 处理发送评论
    private fun handleSendComment() {
        val content = etCommentContent.text.toString().trim()
        if (content.isEmpty()) {
            val action = when (replyTargetType) {
                ReplyTargetType.COMMENT -> "回复"
                ReplyTargetType.REPLY -> "回复"
                else -> "评论"
            }
            Toast.makeText(this, "请输入${action}内容", Toast.LENGTH_SHORT).show()
            return
        }
        val token = UserManager.getToken() ?: ""
        if (token.isEmpty()) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show()
            return
        }

        btnSendComment.isEnabled = false
        etCommentContent.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            val result = when (replyTargetType) {
                ReplyTargetType.COMMENT, ReplyTargetType.REPLY -> {
                    // 修复：删除前端@拼接，仅传用户输入的纯content
                    communityRepository.addCommentReply(
                        request = CommentReplyRequest(
                            commentId = targetCommentId,
                            replyToUserId = targetReplyToUserId,
                            content = content  // 关键：直接传原始内容，不拼接@
                        )
                    )
                }
                ReplyTargetType.NONE -> {
                    communityRepository.addComment(noteId, content)
                }
            }

            withContext(Dispatchers.Main) {
                btnSendComment.isEnabled = true
                etCommentContent.isEnabled = true

                if (result.isSuccess) {
                    val response = result.getOrNull()
                    if (response?.code == 200) {
                        val action = when (replyTargetType) {
                            ReplyTargetType.COMMENT, ReplyTargetType.REPLY -> "回复"
                            else -> "评论"
                        }
                        Toast.makeText(this@CommunityNoteDetailActivity, "${action}成功", Toast.LENGTH_SHORT).show()

                        etCommentContent.text.clear()
                        resetReplyState()
                        btnCancelReply.visibility = View.GONE // 隐藏取消按钮

                        loadComments()

                        val currentCommentCount = tvNoteCommentCount.text.toString().toIntOrNull() ?: 0
                        tvNoteCommentCount.text = (currentCommentCount + 1).coerceAtLeast(0).toString()
                        val newCommentCount = tvNoteCommentCount.text.toString()
                        tvCommentToggle.text = "收起评论区 ($newCommentCount)"
                    }
                } else {
                    Toast.makeText(this@CommunityNoteDetailActivity, "发布失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 重置回复状态
    private fun resetReplyState() {
        isReplyMode = false
        replyTargetType = ReplyTargetType.NONE
        targetCommentId = -1L
        targetReplyId = -1L
        targetReplyToUserId = -1L
        targetReplyToUserName = ""
        etCommentContent.hint = originCommentHint
        etCommentContent.text.clear()
    }

    private fun loadNoteDetail() {
        progressBar.visibility = View.VISIBLE

        val note = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("COMMUNITY_NOTE", CommunityNote::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("COMMUNITY_NOTE") as? CommunityNote
        }

        if (note != null) {
            progressBar.visibility = View.GONE
            tvNoteTitle.text = note.title ?: "无标题"
            tvNoteAuthor.text = note.author.nickname ?: "匿名用户"
            tvNoteCreateTime.text = TimeUtils.formatTime(note.createTime) ?: "未知时间"
            tvNoteContent.text = note.content ?: "无内容"
            tvNoteLikeCount.text = note.likeCount.toString()
            tvNoteCommentCount.text = note.commentCount.toString()
            tvCommentToggle.text = "查看评论区 (${note.commentCount})"
            isNoteLiked = note.isLiked ?: false
            ivNoteLike.setImageResource(if (isNoteLiked) R.drawable.ic_like_selected else R.drawable.ic_like)

            // 调用修改后的图片加载方法
            loadNoteImages(note.imageUrls ?: emptyList())
        } else {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val result = communityRepository.getCommunityNotes(1, 100)
                    withContext(Dispatchers.Main) {
                        progressBar.visibility = View.GONE
                        if (result.isSuccess) {
                            val response = result.getOrNull()
                            val noteList = response?.data?.content ?: emptyList()
                            val targetNote = noteList.find { it.id == noteId }
                            if (targetNote != null) {
                                tvNoteTitle.text = targetNote.title ?: "无标题"
                                tvNoteAuthor.text = targetNote.author.nickname ?: "匿名用户"
                                tvNoteCreateTime.text = TimeUtils.formatTime(targetNote.createTime) ?: "未知时间"
                                tvNoteContent.text = targetNote.content ?: "无内容"
                                tvNoteLikeCount.text = targetNote.likeCount.toString()
                                tvNoteCommentCount.text = targetNote.commentCount.toString()
                                tvCommentToggle.text = "查看评论区 (${targetNote.commentCount})"
                                isNoteLiked = targetNote.isLiked ?: false
                                ivNoteLike.setImageResource(if (isNoteLiked) R.drawable.ic_like_selected else R.drawable.ic_like)
                                loadNoteImages(targetNote.imageUrls ?: emptyList())
                            }
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        progressBar.visibility = View.GONE
                    }
                }
            }
        }
    }

    // 核心修改：完全借鉴私密笔记的图片加载逻辑
    private fun loadNoteImages(imageUrls: List<String>) {
        llNoteImages.removeAllViews()
        Log.d(TAG, "加载公开笔记图片：数量=${imageUrls.size}，URL列表=$imageUrls")

        if (imageUrls.isEmpty()) {
            horizontalScrollView.visibility = View.GONE
            return
        }

        horizontalScrollView.visibility = View.VISIBLE

        // 复用私密页的Glide缓存配置（提升加载效率和稳定性）
        val glideOptions = RequestOptions()
            .placeholder(R.drawable.ic_image_placeholder) // 和私密页一致的占位图
            .error(R.drawable.ic_image_error) // 和私密页一致的错误图
            .diskCacheStrategy(DiskCacheStrategy.ALL) // 缓存所有版本
            .skipMemoryCache(false) // 不跳过内存缓存

        imageUrls.forEachIndexed { index, url ->
            try {
                // 完全复用私密页的URL拼接逻辑
                val finalImageUrl = when {
                    url.startsWith("http") -> url // 已有完整URL，直接用
                    url.startsWith("/") -> "$SERVER_BASE_URL$url" // 相对路径拼接服务器地址
                    else -> "$SERVER_BASE_URL/$url" // 兜底拼接
                }
                Log.d(TAG, "公开笔记图片最终URL：$finalImageUrl")

                val imageView = ImageView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(200, 200).apply {
                        marginEnd = 10
                    }
                    scaleType = ImageView.ScaleType.CENTER_CROP

                    // 复用私密页的Glide加载逻辑
                    Glide.with(this@CommunityNoteDetailActivity)
                        .load(finalImageUrl)
                        .apply(glideOptions)
                        .into(this)

                    // 点击预览逻辑和私密页保持一致
                    setOnClickListener {
                        val intent = Intent(this@CommunityNoteDetailActivity, ImagePreviewActivity::class.java).apply {
                            putExtra("IMAGE_PATH", finalImageUrl)
                            putStringArrayListExtra("IMAGE_LIST", ArrayList(
                                imageUrls.map {
                                    when {
                                        it.startsWith("http") -> it
                                        it.startsWith("/") -> "$SERVER_BASE_URL$it"
                                        else -> "$SERVER_BASE_URL/$it"
                                    }
                                }
                            ))
                            putExtra("CURRENT_INDEX", index)
                            putExtra("NOTE_ID", noteId)
                        }
                        startActivity(intent)
                    }
                }
                llNoteImages.addView(imageView)
            } catch (e: Exception) {
                Log.e(TAG, "加载图片失败：$url，错误：${e.message}", e)
            }
        }
    }

    private fun loadComments() {
        if (isLoading) return
        isLoading = true
        tvCommentEmpty.visibility = View.GONE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val result = communityRepository.getCommentsByNoteId(noteId)
                withContext(Dispatchers.Main) {
                    isLoading = false
                    if (result.isSuccess) {
                        val response = result.getOrNull()
                        val pageResponse = response?.data as? com.lm.shiguang.network.PageResponse<Comment>
                        val comments = pageResponse?.content ?: emptyList()

                        if (comments.isNotEmpty()) {
                            commentList.clear()

                            // 为每条评论加载二级回复
                            comments.forEach { comment ->
                                lifecycleScope.launch(Dispatchers.IO) {
                                    val repliesResult = communityRepository.getCommentReplies(comment.id)
                                    if (repliesResult.isSuccess) {
                                        val repliesResponse = repliesResult.getOrNull()
                                        val replyList = repliesResponse?.data?.content ?: emptyList()

                                        val commentWithReplies = comment.copy(replyList = replyList)

                                        withContext(Dispatchers.Main) {
                                            commentList.add(commentWithReplies)
                                            commentAdapter.notifyDataSetChanged()

                                            tvCommentEmpty.visibility = if (commentList.isEmpty()) View.VISIBLE else View.GONE
                                        }
                                    } else {
                                        // 即使获取二级评论失败，也要显示一级评论
                                        withContext(Dispatchers.Main) {
                                            commentList.add(comment)
                                            commentAdapter.notifyDataSetChanged()
                                        }
                                    }
                                }
                            }
                        } else {
                            tvCommentEmpty.visibility = View.VISIBLE
                        }
                    } else {
                        tvCommentEmpty.visibility = View.VISIBLE
                        Toast.makeText(this@CommunityNoteDetailActivity, "加载评论失败", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isLoading = false
                    tvCommentEmpty.visibility = View.VISIBLE
                    Log.e(TAG, "加载评论异常", e)
                }
            }
        }
    }

    private fun showDeleteReplyDialog(reply: CommentReply) {
        AlertDialog.Builder(this)
            .setTitle("删除回复")
            .setMessage("确定要删除这条回复吗？")
            .setPositiveButton("确定") { _, _ ->
                deleteReply(reply)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun deleteReply(reply: CommentReply) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val result = communityRepository.deleteCommentReply(reply.id)
                withContext(Dispatchers.Main) {
                    if (result.isSuccess) {
                        Toast.makeText(this@CommunityNoteDetailActivity, "删除回复成功", Toast.LENGTH_SHORT).show()
                        commentAdapter.removeReplyFromComment(reply.commentId, reply.id)
                    } else {
                        Toast.makeText(this@CommunityNoteDetailActivity, "删除回复失败", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@CommunityNoteDetailActivity, "删除回复异常", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showDeleteCommentDialog(comment: Comment) {
        AlertDialog.Builder(this)
            .setTitle("删除评论")
            .setMessage("确定要删除这条评论吗？")
            .setPositiveButton("确定") { _, _ ->
                deleteComment(comment)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun deleteComment(comment: Comment) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val result = communityRepository.deleteComment(comment.id)
                withContext(Dispatchers.Main) {
                    if (result.isSuccess) {
                        Toast.makeText(this@CommunityNoteDetailActivity, "删除评论成功", Toast.LENGTH_SHORT).show()
                        commentAdapter.removeComment(comment)
                    } else {
                        Toast.makeText(this@CommunityNoteDetailActivity, "删除评论失败", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@CommunityNoteDetailActivity, "删除评论异常", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        commentList.clear()
        commentAdapter.clear()
    }
}