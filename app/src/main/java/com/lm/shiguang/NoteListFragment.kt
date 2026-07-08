package com.lm.shiguang

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.lm.shiguang.NoteAdapter
import com.lm.shiguang.NoteDetailActivity
import com.lm.shiguang.NoteViewModel
import com.lm.shiguang.EditNoteActivity
import com.lm.shiguang.network.UserNote
import com.lm.shiguang.databinding.FragmentNoteListBinding
import com.lm.shiguang.utils.UserManager
import java.time.format.DateTimeFormatter

class NoteListFragment : Fragment() {
    private lateinit var binding: FragmentNoteListBinding
    private lateinit var viewModel: NoteViewModel
    private val tag = "NoteListFragment"
    // 新增：保存userId，避免重复获取
    private var currentUserId: Long? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentNoteListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 修复1：ViewModel改为Fragment独立作用域（避免和Activity共享导致数据混乱）
        viewModel = ViewModelProvider(this)[NoteViewModel::class.java]

        // 初始化Adapter（空安全+默认值）
        val noteAdapter = NoteAdapter(
            onNoteClick = { note: UserNote ->
                val intent = Intent(requireContext(), NoteDetailActivity::class.java).apply {
                    putExtra("NOTE_LOCAL_ID", note.localId)
                }
                startActivity(intent)
            },
            onNoteEdit = { note: UserNote ->
                val intent = Intent(requireContext(), EditNoteActivity::class.java).apply {
                    putExtra("NOTE_LOCAL_ID", note.localId)
                }
                startActivity(intent)
            },
            onNoteShare = { note: UserNote ->
                val shareContent = buildString {
                    if (note.title.isNotBlank()) append("${note.title}\n\n")
                    append(note.content)
                    val timeFormat = note.createTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                    append("\n\n创建时间：$timeFormat")
                }
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, shareContent)
                }
                startActivity(Intent.createChooser(shareIntent, "分享笔记"))
            },
            onNoteDelete = { localId: Long ->
                AlertDialog.Builder(requireContext())
                    .setTitle("删除笔记")
                    .setMessage("确定删除这条笔记吗？")
                    .setPositiveButton("删除") { _, _ ->
                        viewModel.deleteNoteByLocalId(localId)
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
        )
        binding.rvNoteList.layoutManager = LinearLayoutManager(requireContext())
        binding.rvNoteList.adapter = noteAdapter

        // 修复2：重构登录状态+userId校验逻辑（避免return退出）
        if (UserManager.isLogin()) {
            currentUserId = UserManager.getUserId()
            if (currentUserId == null || currentUserId == 0L) {
                Log.e(tag, "用户已登录，但userId为空/无效：${currentUserId}")
                binding.tvEmptyHint.text = "登录状态异常，请重新登录"
                binding.tvEmptyHint.visibility = View.VISIBLE
                return
            }
            // 正常观察数据
            viewModel.observeNotesByUserId(currentUserId!!).observe(viewLifecycleOwner) { notes: List<UserNote>? ->
                // 修复3：空安全处理 + 过滤已删除笔记
                val filteredNotes = notes?.filter { !it.isDeleted } ?: emptyList()
                Log.d(tag, "观察到笔记数据：总数=${notes?.size ?: 0}，过滤后=${filteredNotes.size}")
                noteAdapter.submitList(filteredNotes)
                // 修复4：空提示文本优化（区分无数据/登录异常）
                binding.tvEmptyHint.visibility = if (filteredNotes.isEmpty()) View.VISIBLE else View.GONE
                binding.tvEmptyHint.text = if (filteredNotes.isEmpty()) "暂无笔记，点击右下角+创建" else ""
            }
        } else {
            // 未登录状态：清空列表+提示登录
            noteAdapter.submitList(emptyList())
            binding.tvEmptyHint.text = "请先登录查看/创建笔记"
            binding.tvEmptyHint.visibility = View.VISIBLE
            Log.d(tag, "用户未登录，显示登录提示")
        }
    }

    // 修复5：重新进入Fragment时刷新数据（比如登录后返回）
    override fun onResume() {
        super.onResume()
        if (UserManager.isLogin() && currentUserId != null) {
            // 主动触发数据刷新
            viewModel.observeNotesByUserId(currentUserId!!).observe(viewLifecycleOwner) { notes ->
                val filteredNotes = notes?.filter { !it.isDeleted } ?: emptyList()
                (binding.rvNoteList.adapter as? NoteAdapter)?.submitList(filteredNotes)
            }
        }
    }
}