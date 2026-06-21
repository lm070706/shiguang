package com.lm.shiguang

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lm.shiguang.databinding.ItemNoteBinding
import java.time.format.DateTimeFormatter
import com.lm.shiguang.network.UserNote

class NoteAdapter(
    private val onNoteClick: (UserNote) -> Unit,
    private val onNoteEdit: (UserNote) -> Unit,
    private val onNoteShare: (UserNote) -> Unit,
    private val onNoteDelete: (Long) -> Unit
) : ListAdapter<UserNote, NoteAdapter.NoteViewHolder>(DiffCallback()) {

    inner class NoteViewHolder(val binding: ItemNoteBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
        val binding = ItemNoteBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return NoteViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        val note = getItem(position)
        with(holder.binding) {
            tvNoteTitle.text = note.title
            tvNoteContent.text = note.content
            tvNoteTime.text = formatTime(note.createTime)

            // 单击事件
            holder.itemView.setOnClickListener { onNoteClick(note) }

            // 长按事件（弹出菜单）
            holder.itemView.setOnLongClickListener {
                showPopupMenu(holder.itemView.context, holder.itemView, note)
                true
            }
        }
    }

    /** 格式化 LocalDateTime 为 "yyyy-MM-dd HH:mm" */
    private fun formatTime(time: java.time.LocalDateTime): String {
        return time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
    }

    /** 显示长按菜单 */
    private fun showPopupMenu(context: Context, view: View, note: UserNote) {
        val popupMenu = PopupMenu(context, view)
        popupMenu.inflate(R.menu.menu_note_operation)

        popupMenu.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_edit -> {
                    onNoteEdit(note)
                    true
                }
                R.id.action_share -> {
                    onNoteShare(note)
                    true
                }
                R.id.action_delete -> {
                    onNoteDelete(note.localId)
                    true
                }
                else -> false
            }
        }
        popupMenu.show()
    }

    /** DiffUtil 高效更新列表 */
    class DiffCallback : DiffUtil.ItemCallback<UserNote>() {
        override fun areItemsTheSame(oldItem: UserNote, newItem: UserNote): Boolean {
            return oldItem.localId == newItem.localId
        }

        override fun areContentsTheSame(oldItem: UserNote, newItem: UserNote): Boolean {
            return oldItem == newItem
        }
    }
}