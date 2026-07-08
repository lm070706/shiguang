package com.lm.shiguang

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.lm.shiguang.databinding.ItemCommunityNoteBinding
import com.lm.shiguang.network.CommunityNote
import com.lm.shiguang.repository.CommunityRepository
import com.lm.shiguang.utils.TimeUtils
import com.lm.shiguang.utils.UserManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CommunityNoteAdapter(
    private val data: MutableList<CommunityNote>,
    private val onItemClick: (CommunityNote) -> Unit,
    private val onCommentClick: (CommunityNote) -> Unit
) : RecyclerView.Adapter<CommunityNoteAdapter.ViewHolder>() {

    // ViewHolder（ViewBinding版）
    inner class ViewHolder(private val binding: ItemCommunityNoteBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(note: CommunityNote) {
            // 绑定基础数据
            binding.tvAuthor.text = note.author.nickname
            binding.tvCreateTime.text = TimeUtils.formatTime(note.createTime) ?: note.createTime
            binding.tvNoteTitle.text = note.title
            binding.tvNoteContent.text = note.content
            binding.tvLikeCount.text = note.likeCount.toString()
            binding.tvCommentCount.text = note.commentCount.toString()

            // 头像默认图
            binding.ivAvatar.setImageResource(R.drawable.ic_avatar_default)

            // 初始化点赞图标状态
            val isLiked = note.isLiked ?: false
            binding.ivLike.setImageResource(if (isLiked) R.drawable.ic_like_selected else R.drawable.ic_like)

            // 点赞逻辑
            binding.ivLike.setOnClickListener {
                val position = adapterPosition
                if (position == RecyclerView.NO_POSITION) return@setOnClickListener

                val currentNote = data[position]
                val token = UserManager.getToken() ?: ""

                // 未登录提示
                if (token.isEmpty()) {
                    Toast.makeText(binding.root.context, "请先登录", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                // 已点赞拦截
                val currentIsLiked = currentNote.isLiked ?: false
                if (currentIsLiked) {
                    Toast.makeText(binding.root.context, "已点赞过啦～", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                // 禁用按钮防止重复点击
                binding.ivLike.isClickable = false

                // 发起点赞请求
                CoroutineScope(Dispatchers.IO).launch {
                    val repo = CommunityRepository()
                    val result = repo.likeNote(currentNote.id)

                    withContext(Dispatchers.Main) {
                        // 恢复按钮点击
                        binding.ivLike.isClickable = true

                        val response = result.getOrNull()
                        if (response?.code == 200) {
                            // 更新数据和UI
                            val updatedNote = currentNote.copy(
                                likeCount = currentNote.likeCount + 1,
                                isLiked = true
                            )
                            data[position] = updatedNote
                            notifyItemChanged(position)
                            Toast.makeText(binding.root.context, "点赞成功", Toast.LENGTH_SHORT).show()
                        } else {
                            // 500错误特殊处理
                            if (response?.code == 500) {
                                Toast.makeText(binding.root.context, "服务器异常，请稍后再试", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(binding.root.context, "点赞失败", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }

            // 评论按钮点击
            binding.ivComment.setOnClickListener {
                onCommentClick(note)
            }

            // 条目点击
            itemView.setOnClickListener {
                onItemClick(note)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCommunityNoteBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(data[position])
    }

    override fun getItemCount(): Int = data.size

    /**
     * 全量更新数据
     */
    fun updateData(newData: List<CommunityNote>) {
        data.clear()
        data.addAll(newData)
        notifyDataSetChanged()
    }

    /**
     * 增量添加数据
     */
    fun addData(newData: List<CommunityNote>) {
        val startPosition = data.size
        data.addAll(newData)
        notifyItemRangeInserted(startPosition, newData.size)
    }

    /**
     * 清空数据
     */
    fun clearData() {
        data.clear()
        notifyDataSetChanged()
    }
}