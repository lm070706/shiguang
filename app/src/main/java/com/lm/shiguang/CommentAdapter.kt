package com.lm.shiguang

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lm.shiguang.network.Comment
import com.lm.shiguang.network.CommentReply
import com.lm.shiguang.utils.TimeUtils
import com.lm.shiguang.utils.UserManager

// 长按事件枚举
enum class LongClickType {
    DELETE, // 删除（自己的评论）
    COPY    // 复制（他人的评论）
}

class CommentAdapter(
    private val context: Context,
    private val data: MutableList<Comment>,
    private val onCommentClick: (Comment) -> Unit,
    private val onCommentLongClick: (LongClickType, Comment) -> Boolean,
    private val onDeleteReplyClick: (CommentReply) -> Unit,
    private val onReplyClick: (CommentReply) -> Unit = {}  // 新增：二级回复点击回调（回复二级评论）
) : RecyclerView.Adapter<CommentAdapter.CommentViewHolder>() {

    private val TAG = "CommentAdapter"

    // 一级评论ViewHolder
    inner class CommentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // 控件绑定 - 匹配 item_comment.xml 中的ID
        val ivCommentAvatar: ImageView = itemView.findViewById(R.id.iv_comment_avatar)
        val tvCommentAuthor: TextView = itemView.findViewById(R.id.tv_comment_author)
        val tvCommentTime: TextView = itemView.findViewById(R.id.tv_comment_time)
        val tvCommentContent: TextView = itemView.findViewById(R.id.tv_comment_content)
        val rvCommentReplies: RecyclerView = itemView.findViewById(R.id.rv_comment_replies)

        // 二级回复适配器
        private lateinit var replyAdapter: ReplyAdapter

        fun bind(comment: Comment) {
            Log.d(TAG, "绑定一级评论: id=${comment.id}, 作者=${comment.author?.nickname}, 内容长度=${comment.content?.length ?: 0}")

            // 绑定一级评论数据
            tvCommentAuthor.text = comment.author?.nickname ?: "匿名用户"
            tvCommentTime.text = TimeUtils.formatTime(comment.createTime) ?: "未知时间"
            tvCommentContent.text = comment.content ?: ""
            ivCommentAvatar.setImageResource(R.drawable.ic_avatar_default)

            // 单击事件（回复一级评论）
            itemView.isClickable = true
            itemView.setOnClickListener {
                Log.d(TAG, "点击一级评论: ${comment.id}")
                onCommentClick(comment)
            }

            // 长按操作（匹配DELETE/COPY枚举）
            itemView.isLongClickable = true
            itemView.setOnLongClickListener {
                Log.d(TAG, "长按一级评论: ${comment.id}")
                val isMyComment = comment.author?.id == UserManager.getUserId()
                val clickType = if (isMyComment) LongClickType.DELETE else LongClickType.COPY
                onCommentLongClick(clickType, comment)
            }

            // 初始化二级回复
            val replyList = comment.replyList ?: emptyList()
            Log.d(TAG, "一级评论 ${comment.id} 的二级回复数量: ${replyList.size}")
            initReplyRecyclerView(rvCommentReplies, replyList)
        }

        private fun initReplyRecyclerView(rv: RecyclerView, replyList: List<CommentReply>) {
            replyAdapter = ReplyAdapter(context, replyList, onDeleteReplyClick, onReplyClick)
            rv.layoutManager = LinearLayoutManager(context)
            rv.adapter = replyAdapter
            rv.visibility = if (replyList.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    // 二级回复适配器（支持长按删除和点击回复）
    inner class ReplyAdapter(
        private val context: Context,
        private val replyList: List<CommentReply>,
        private val onDeleteClick: (CommentReply) -> Unit,
        private val onReplyClick: (CommentReply) -> Unit  // 新增：二级回复点击回调
    ) : RecyclerView.Adapter<ReplyAdapter.ReplyViewHolder>() {

        private val TAG_REPLY = "ReplyAdapter"

        inner class ReplyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            // 控件绑定 - 匹配 item_comment_reply.xml 中的ID
            val ivReplyAvatar: ImageView = itemView.findViewById(R.id.iv_reply_avatar)
            val tvReplyAuthor: TextView = itemView.findViewById(R.id.tv_reply_author)
            val tvReplyTime: TextView = itemView.findViewById(R.id.tv_reply_time)
            val tvReplyContent: TextView = itemView.findViewById(R.id.tv_reply_content)

            fun bind(reply: CommentReply) {
                Log.d(TAG_REPLY, "绑定二级回复: id=${reply.id}, 作者=${reply.author?.nickname}, 内容长度=${reply.content?.length ?: 0}")

                // 绑定二级回复数据
                tvReplyAuthor.text = reply.author?.nickname ?: "匿名用户"
                tvReplyContent.text = reply.content ?: ""
                tvReplyTime.text = TimeUtils.formatTime(reply.createTime) ?: "未知时间"
                ivReplyAvatar.setImageResource(R.drawable.ic_avatar_default)

                // 设置长按事件：只有自己的回复可以长按删除
                itemView.isLongClickable = true
                itemView.setOnLongClickListener {
                    if (reply.author?.id == UserManager.getUserId()) {
                        Log.d(TAG_REPLY, "长按删除二级回复: ${reply.id}")
                        onDeleteClick(reply)
                        true // 消费长按事件
                    } else {
                        false // 不消费长按事件，可以触发其他操作
                    }
                }

                // 设置点击事件：回复二级评论
                itemView.setOnClickListener {
                    Log.d(TAG_REPLY, "点击二级回复: ${reply.id}")
                    onReplyClick(reply)  // 触发二级回复点击回调
                }

                // 二级回复缩进（增加左边距，形成层级效果）
                itemView.setPadding(60, 0, 0, 0)
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReplyViewHolder {
            val itemView = LayoutInflater.from(context)
                .inflate(R.layout.item_comment_reply, parent, false)
            return ReplyViewHolder(itemView)
        }

        override fun onBindViewHolder(holder: ReplyViewHolder, position: Int) {
            if (position < replyList.size) {
                holder.bind(replyList[position])
            }
        }

        override fun getItemCount() = replyList.size

        // 更新二级回复数据
        fun updateData(newReplyList: List<CommentReply>) {
            Log.d(TAG_REPLY, "更新二级回复数据: 旧大小=${replyList.size}, 新大小=${newReplyList.size}")
            // 注意：由于 replyList 是只读的，我们需要在外部更新一级评论的 replyList
            // 这个方法主要供内部使用，实际更新通过 notifyDataSetChanged 完成
        }
    }

    // 一级评论适配器核心方法
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val itemView = LayoutInflater.from(context)
            .inflate(R.layout.item_comment, parent, false)
        return CommentViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        if (position < data.size) {
            holder.bind(data[position])
        } else {
            Log.e(TAG, "位置越界: position=$position, data大小=${data.size}")
        }
    }

    override fun getItemCount() = data.size

    // ========== 数据操作方法 ==========

    /**
     * 添加一级评论列表
     */
    fun addComments(newComments: List<Comment>) {
        Log.d(TAG, "添加评论: ${newComments.size} 条")
        val startPos = data.size
        data.addAll(newComments)
        notifyItemRangeInserted(startPos, newComments.size)
        Log.d(TAG, "当前总评论数: ${data.size}")
    }

    /**
     * 添加单条一级评论（添加到顶部）
     */
    fun addCommentToTop(comment: Comment) {
        Log.d(TAG, "添加评论到顶部: ${comment.id}")
        data.add(0, comment)
        notifyItemInserted(0)
        notifyItemRangeChanged(1, data.size - 1)
    }

    /**
     * 删除一级评论
     */
    fun removeComment(comment: Comment) {
        val index = data.indexOfFirst { it.id == comment.id }
        if (index != -1) {
            Log.d(TAG, "删除评论: index=$index, id=${comment.id}")
            data.removeAt(index)
            notifyItemRemoved(index)
            if (data.isEmpty()) {
                notifyDataSetChanged()
            }
        } else {
            Log.e(TAG, "未找到要删除的评论: id=${comment.id}")
        }
    }

    /**
     * 更新指定评论的二级回复列表
     */
    fun updateCommentReplies(commentId: Long, newReplyList: List<CommentReply>) {
        Log.d(TAG, "更新评论的二级回复: commentId=$commentId, 新回复数量=${newReplyList.size}")
        val index = data.indexOfFirst { it.id == commentId }
        if (index != -1) {
            data[index] = data[index].copy(replyList = newReplyList)
            notifyItemChanged(index)
            Log.d(TAG, "成功更新评论 $commentId 的二级回复")
        } else {
            Log.e(TAG, "未找到要更新二级回复的评论: commentId=$commentId")
        }
    }

    /**
     * 向指定评论添加一条二级回复
     */
    fun addReplyToComment(commentId: Long, reply: CommentReply) {
        val index = data.indexOfFirst { it.id == commentId }
        if (index != -1) {
            val currentReplies = data[index].replyList?.toMutableList() ?: mutableListOf()
            currentReplies.add(reply)
            data[index] = data[index].copy(replyList = currentReplies)
            notifyItemChanged(index)
            Log.d(TAG, "向评论 $commentId 添加一条二级回复")
        } else {
            Log.e(TAG, "未找到要添加回复的评论: commentId=$commentId")
        }
    }

    /**
     * 从指定评论删除一条二级回复
     */
    fun removeReplyFromComment(commentId: Long, replyId: Long) {
        val index = data.indexOfFirst { it.id == commentId }
        if (index != -1) {
            val currentReplies = data[index].replyList?.toMutableList() ?: mutableListOf()
            val replyIndex = currentReplies.indexOfFirst { it.id == replyId }
            if (replyIndex != -1) {
                currentReplies.removeAt(replyIndex)
                data[index] = data[index].copy(replyList = currentReplies)
                notifyItemChanged(index)
                Log.d(TAG, "从评论 $commentId 删除二级回复 $replyId")
            }
        }
    }

    /**
     * 清空所有数据
     */
    fun clear() {
        Log.d(TAG, "清空所有评论数据")
        val itemCount = data.size
        data.clear()
        if (itemCount > 0) {
            notifyItemRangeRemoved(0, itemCount)
        }
    }

    /**
     * 获取所有评论（深拷贝）
     */
    fun getAllComments(): List<Comment> = data.toList()

    /**
     * 根据ID查找评论
     */
    fun findCommentById(commentId: Long): Comment? {
        return data.find { it.id == commentId }
    }

    /**
     * 根据ID查找二级回复
     */
    fun findReplyById(replyId: Long): Pair<Comment, CommentReply>? {
        for (comment in data) {
            val reply = comment.replyList?.find { it.id == replyId }
            if (reply != null) {
                return Pair(comment, reply)
            }
        }
        return null
    }

    /**
     * 批量更新评论数据（用于下拉刷新等场景）
     */
    fun updateAllComments(newComments: List<Comment>) {
        Log.d(TAG, "批量更新评论: ${newComments.size} 条")
        data.clear()
        data.addAll(newComments)
        notifyDataSetChanged()
    }

    /**
     * 获取评论总数（包括二级回复）
     */
    fun getTotalCommentCount(): Int {
        var total = data.size
        data.forEach { comment ->
            total += comment.replyList?.size ?: 0
        }
        return total
    }

    /**
     * 检查是否有数据
     */
    fun isEmpty() = data.isEmpty()

    /**
     * 设置数据（替换现有数据）
     */
    fun setData(newData: List<Comment>) {
        Log.d(TAG, "设置评论数据: ${newData.size} 条")
        data.clear()
        data.addAll(newData)
        notifyDataSetChanged()
    }

    /**
     * 获取指定评论的二级回复数量
     */
    fun getReplyCount(commentId: Long): Int {
        return data.find { it.id == commentId }?.replyList?.size ?: 0
    }

    /**
     * 获取所有二级回复的总数
     */
    fun getTotalReplyCount(): Int {
        var total = 0
        data.forEach { comment ->
            total += comment.replyList?.size ?: 0
        }
        return total
    }

    /**
     * 检查评论是否存在
     */
    fun containsComment(commentId: Long): Boolean {
        return data.any { it.id == commentId }
    }

    /**
     * 检查二级回复是否存在
     */
    fun containsReply(replyId: Long): Boolean {
        for (comment in data) {
            if (comment.replyList?.any { it.id == replyId } == true) {
                return true
            }
        }
        return false
    }

    /**
     * 获取评论索引位置
     */
    fun getCommentPosition(commentId: Long): Int {
        return data.indexOfFirst { it.id == commentId }
    }

    /**
     * 获取评论的二级回复列表
     */
    fun getCommentReplies(commentId: Long): List<CommentReply> {
        return data.find { it.id == commentId }?.replyList ?: emptyList()
    }

    /**
     * 更新单条评论
     */
    fun updateComment(comment: Comment) {
        val index = data.indexOfFirst { it.id == comment.id }
        if (index != -1) {
            data[index] = comment
            notifyItemChanged(index)
        }
    }

    /**
     * 更新单条二级回复
     */
    fun updateReply(commentId: Long, reply: CommentReply) {
        val commentIndex = data.indexOfFirst { it.id == commentId }
        if (commentIndex != -1) {
            val comment = data[commentIndex]
            val replies = comment.replyList?.toMutableList() ?: mutableListOf()
            val replyIndex = replies.indexOfFirst { it.id == reply.id }
            if (replyIndex != -1) {
                replies[replyIndex] = reply
                data[commentIndex] = comment.copy(replyList = replies)
                notifyItemChanged(commentIndex)
            }
        }
    }

    /**
     * 插入评论到指定位置
     */
    fun insertComment(position: Int, comment: Comment) {
        data.add(position, comment)
        notifyItemInserted(position)
    }

    /**
     * 移动评论位置
     */
    fun moveComment(fromPosition: Int, toPosition: Int) {
        val comment = data.removeAt(fromPosition)
        data.add(toPosition, comment)
        notifyItemMoved(fromPosition, toPosition)
    }
}