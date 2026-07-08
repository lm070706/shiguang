package com.lm.shiguang

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.lm.shiguang.databinding.FragmentCommunityBinding
import com.lm.shiguang.network.CommunityNote
import com.lm.shiguang.repository.CommunityRepository
import com.lm.shiguang.utils.UserManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CommunityFragment : Fragment() {
    private var _binding: FragmentCommunityBinding? = null
    private val binding: FragmentCommunityBinding
        get() = _binding ?: throw IllegalStateException("FragmentCommunityBinding is null!")

    private lateinit var communityAdapter: CommunityNoteAdapter  // 使用正确的类名
    private val communityRepository by lazy { CommunityRepository() }
    private val noteList = mutableListOf<CommunityNote>()
    private var currentPage = 1
    private val pageSize = 10
    private var isLoading = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCommunityBinding.inflate(inflater, container, false)
        initView()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadCommunityNotes()
    }

    private fun initView() {
        // 修复：使用正确的 CommunityNoteAdapter
        communityAdapter = CommunityNoteAdapter(
            noteList,
            onItemClick = { note ->
                if (note.id <= 0) {
                    Toast.makeText(requireContext(), "无效的笔记ID", Toast.LENGTH_SHORT).show()
                    return@CommunityNoteAdapter
                }
                // 跳转到详情页
                val intent = Intent(requireContext(), CommunityNoteDetailActivity::class.java).apply {
                    putExtra("NOTE_ID", note.id)
                    putExtra("COMMUNITY_NOTE", note)
                }
                startActivity(intent)
            },
            onCommentClick = { note ->
                if (note.id <= 0) {
                    Toast.makeText(requireContext(), "无效的笔记ID", Toast.LENGTH_SHORT).show()
                    return@CommunityNoteAdapter
                }
                // 跳转到评论页
                val intent = Intent(requireContext(), CommunityNoteDetailActivity::class.java).apply {
                    putExtra("NOTE_ID", note.id)
                    putExtra("COMMUNITY_NOTE", note)
                }
                startActivity(intent)
            }
        )

        binding.communityRvNotes.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = communityAdapter
            addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                    val lastVisibleItemPosition = layoutManager.findLastCompletelyVisibleItemPosition()
                    // 防刷新闪退：增加多重校验
                    if (!isLoading
                        && isAdded // Fragment存活
                        && lastVisibleItemPosition == communityAdapter.itemCount - 1
                        && communityAdapter.itemCount > 0) {
                        currentPage++
                        loadCommunityNotes()
                    }
                }
            })
        }

        // 修复下拉刷新逻辑
        binding.communitySwipeRefresh.setOnRefreshListener {
            refreshCommunityNotes()
        }
    }

    /**
     * 刷新笔记列表 + 防闪退
     */
    private fun refreshCommunityNotes() {
        if (isLoading) return

        currentPage = 1
        lifecycleScope.launch(Dispatchers.Main) {
            noteList.clear()
            communityAdapter.clearData() // 使用正确的方法名
            loadCommunityNotes()
        }
    }

    /**
     * 加载社区笔记 + 生命周期绑定 + 防闪退
     */
    private fun loadCommunityNotes() {
        if (isLoading || !isAdded) return

        isLoading = true
        binding.communitySwipeRefresh.isRefreshing = true
        binding.communityTvEmpty.visibility = View.GONE

        val token = UserManager.getToken() ?: ""
        if (token.isEmpty()) {
            Toast.makeText(requireContext(), "请先登录", Toast.LENGTH_SHORT).show()
            isLoading = false
            binding.communitySwipeRefresh.isRefreshing = false
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val result = communityRepository.getCommunityNotes(currentPage, pageSize)
                withContext(Dispatchers.Main) {
                    // 再次校验Fragment存活状态
                    if (!isAdded) return@withContext

                    isLoading = false
                    binding.communitySwipeRefresh.isRefreshing = false

                    if (result.isSuccess) {
                        val response = result.getOrNull()
                        if (response?.code == 200 && response.data != null) {
                            val newNotes = response.data.content
                            if (newNotes.isNotEmpty()) {
                                if (currentPage == 1) {
                                    noteList.clear()
                                    communityAdapter.updateData(newNotes) // 使用正确的方法名
                                } else {
                                    communityAdapter.addData(newNotes) // 使用正确的方法名
                                }
                            } else if (currentPage == 1) {
                                binding.communityTvEmpty.visibility = View.VISIBLE
                            }
                        } else {
                            showToast("加载失败: ${response?.message ?: "未知错误"}")
                        }
                    } else {
                        val exception = result.exceptionOrNull()
                        showToast("网络异常: ${exception?.message ?: "未知错误"}")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    if (!isAdded) return@withContext

                    isLoading = false
                    binding.communitySwipeRefresh.isRefreshing = false
                    showToast("请求异常: ${e.message ?: "未知错误"}")
                }
            }
        }
    }

    /**
     * 安全显示Toast + 上下文校验
     */
    private fun showToast(msg: String) {
        if (context == null || !isAdded) return
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        noteList.clear()
        _binding = null // 释放Binding，避免内存泄漏
    }
}