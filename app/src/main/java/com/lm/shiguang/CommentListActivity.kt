package com.lm.shiguang

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.lm.shiguang.databinding.ActivityCommentListBinding
import com.lm.shiguang.network.Comment
import com.lm.shiguang.network.CommentReply
import com.lm.shiguang.network.CommentReplyRequest
import com.lm.shiguang.repository.CommunityRepository
import com.lm.shiguang.utils.UserManager
import kotlinx.coroutines.*

class CommentListActivity : AppCompatActivity(), CoroutineScope {
    private val job = SupervisorJob()
    override val coroutineContext = Dispatchers.Main + job

    // 使用ViewBinding
    private lateinit var binding: ActivityCommentListBinding
    private lateinit var commentAdapter: CommentAdapter
    private val commentList = mutableListOf<Comment>()
    private val communityRepo = CommunityRepository()

    private var noteId: Long = 0
    // 回复目标信息
    private var currentReplyCommentId: Long = -1
    private var currentReplyUserId: Long = -1
    private var currentReplyUserName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCommentListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 获取笔记ID
        noteId = intent.getLongExtra("NOTE_ID", 0)
        if (noteId == 0L) {
            Toast.makeText(this, "笔记ID异常", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initView()
        loadComments()

        // 返回按钮
        binding.ivBack.setOnClickListener { finish() }

        // 发送按钮逻辑
        binding.btnSend.setOnClickListener {
            val content = binding.etCommentContent.text.toString().trim()
            if (content.isEmpty()) {
                Toast.makeText(this, "内容不能为空", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (currentReplyCommentId == -1L) {
                // 发布一级评论
                sendComment(content)
            } else {
                // 发布二级评论（仅传纯内容，不再拼接@昵称）
                sendReplyComment(content)
            }
        }
    }

    private fun initView() {
        // 初始化适配器（使用修改后的CommentAdapter）
        commentAdapter = CommentAdapter(
            context = this,
            data = commentList,
            onCommentClick = { comment ->
                // 一级评论单击回复
                currentReplyCommentId = comment.id
                currentReplyUserId = comment.author?.id ?: -1
                currentReplyUserName = comment.author?.nickname ?: "用户"
                // Hint保留@昵称提示，用户体验不变
                binding.etCommentContent.hint = "回复 @$currentReplyUserName："
                binding.etCommentContent.requestFocus()
            },
            onCommentLongClick = { type, comment ->
                // 一级评论长按
                when (type) {
                    LongClickType.DELETE -> {
                        deleteComment(comment)
                        true
                    }
                    LongClickType.COPY -> {
                        copyCommentContent(comment.content ?: "")
                        true
                    }
                }
            },
            onDeleteReplyClick = { reply ->
                // 二级评论长按删除
                deleteCommentReply(reply)
            }
        )

        // 设置RecyclerView
        binding.rvComments.apply {
            layoutManager = LinearLayoutManager(this@CommentListActivity)
            adapter = commentAdapter
            setHasFixedSize(true)
        }
    }

    // ========== 新增：二级回复长按删除 ==========
    private fun deleteCommentReply(reply: CommentReply) {
        // 确认对话框
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("确认删除")
            .setMessage("确定要删除这条回复吗？")
            .setPositiveButton("删除") { _, _ ->
                launch {
                    val result = withContext(Dispatchers.IO) {
                        communityRepo.deleteCommentReply(reply.id)
                    }
                    if (result.isSuccess) {
                        // 使用适配器的方法从列表中移除回复
                        commentAdapter.removeReplyFromComment(reply.commentId, reply.id)
                        Toast.makeText(this@CommentListActivity, "删除回复成功", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@CommentListActivity, "删除回复失败", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ========== 其他方法保持不变 ==========
    private fun loadComments() {
        launch {
            try {
                binding.tvEmpty.visibility = View.GONE

                val result = withContext(Dispatchers.IO) {
                    communityRepo.getCommentsByNoteId(noteId)
                }

                if (result.isSuccess) {
                    val response = result.getOrNull()
                    response?.data?.content?.let { comments ->
                        commentList.clear()

                        // 按顺序加载每条评论的二级回复
                        for (comment in comments) {
                            val replyResult = withContext(Dispatchers.IO) {
                                communityRepo.getCommentReplies(comment.id)
                            }
                            val replyList = if (replyResult.isSuccess) {
                                replyResult.getOrNull()?.data?.content ?: emptyList()
                            } else {
                                emptyList()
                            }
                            commentList.add(comment.copy(replyList = replyList))
                        }

                        commentAdapter.notifyDataSetChanged()

                        // 空数据提示
                        binding.tvEmpty.visibility = if (commentList.isEmpty()) View.VISIBLE else View.GONE
                    }
                } else {
                    Toast.makeText(this@CommentListActivity, "加载评论失败", Toast.LENGTH_SHORT).show()
                    binding.tvEmpty.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                Toast.makeText(this@CommentListActivity, "加载异常：${e.message}", Toast.LENGTH_SHORT).show()
                binding.tvEmpty.visibility = View.VISIBLE
            }
        }
    }

    private fun sendComment(content: String) {
        launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    communityRepo.addComment(noteId, content)
                }
                if (result.isSuccess) {
                    binding.etCommentContent.setText("")
                    binding.etCommentContent.hint = "说点什么..."

                    // 重置回复状态
                    currentReplyCommentId = -1
                    currentReplyUserId = -1
                    currentReplyUserName = ""

                    // 重新加载评论
                    loadComments()
                    Toast.makeText(this@CommentListActivity, "发布成功", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@CommentListActivity, "发布失败", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@CommentListActivity, "发布异常：${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun sendReplyComment(content: String) {
        if (currentReplyCommentId == -1L) {
            Toast.makeText(this, "请先选择要回复的评论", Toast.LENGTH_SHORT).show()
            return
        }

        val replyCommentId = currentReplyCommentId // 保存副本
        val replyToUserId = currentReplyUserId

        launch {
            try {
                // 仅传用户输入的纯内容，不拼接@
                val request = CommentReplyRequest(replyCommentId, replyToUserId, content)
                val result = withContext(Dispatchers.IO) {
                    communityRepo.addCommentReply(request)
                }
                if (result.isSuccess) {
                    // 清空输入框
                    binding.etCommentContent.setText("")
                    binding.etCommentContent.hint = "说点什么..."

                    // 重置回复状态
                    currentReplyCommentId = -1
                    currentReplyUserId = -1
                    currentReplyUserName = ""

                    // 刷新对应评论的二级回复
                    loadCommentReplies(replyCommentId)
                    Toast.makeText(this@CommentListActivity, "回复成功", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@CommentListActivity, "回复失败", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@CommentListActivity, "回复异常：${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadCommentReplies(commentId: Long) {
        launch {
            val result = withContext(Dispatchers.IO) {
                communityRepo.getCommentReplies(commentId)
            }
            if (result.isSuccess) {
                val replyList = result.getOrNull()?.data?.content ?: emptyList()
                // 使用适配器的方法更新二级回复
                commentAdapter.updateCommentReplies(commentId, replyList)
            }
        }
    }

    private fun deleteComment(comment: Comment) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("确认删除")
            .setMessage("确定要删除这条评论吗？")
            .setPositiveButton("删除") { _, _ ->
                launch {
                    val result = withContext(Dispatchers.IO) {
                        communityRepo.deleteComment(comment.id)
                    }
                    if (result.isSuccess) {
                        commentAdapter.removeComment(comment)
                        Toast.makeText(this@CommentListActivity, "删除成功", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@CommentListActivity, "删除失败", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun copyCommentContent(content: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("评论内容", content)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "已复制评论内容", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }
}