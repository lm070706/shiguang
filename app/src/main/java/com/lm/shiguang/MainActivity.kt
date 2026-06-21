package com.lm.shiguang

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.lm.shiguang.databinding.ActivityMainBinding
import com.lm.shiguang.dialog.LoginRegisterDialog
import com.lm.shiguang.utils.PopupManager
import com.lm.shiguang.NoteListFragment
import com.lm.shiguang.CommunityFragment
import com.lm.shiguang.AddNoteActivity
import com.lm.shiguang.AddPublicNoteActivity
import com.lm.shiguang.utils.UserManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var noteViewModel: NoteViewModel
    private val TAG = "LoginTest"

    // 启动器：添加私密笔记
    private val addNoteLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            supportFragmentManager.beginTransaction()
                .replace(binding.fragmentContainer.id, NoteListFragment())
                .commit()
            Toast.makeText(this, "笔记创建成功", Toast.LENGTH_SHORT).show()
        }
    }

    // 启动器：添加公开笔记
    private val addPublicNoteLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            supportFragmentManager.beginTransaction()
                .replace(binding.fragmentContainer.id, CommunityFragment())
                .commit()
            Toast.makeText(this, "公开笔记发布成功", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UserManager.init(this)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 状态栏设置
        val window = this.window
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        window.statusBarColor = android.graphics.Color.WHITE
        insetsController.isAppearanceLightStatusBars = true

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            @Suppress("DEPRECATION")
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        }

        PopupManager.fetchAnnouncementAndShow(this)
        PopupManager.fetchVersionAndCheckUpdate(this)

        noteViewModel = ViewModelProvider(this)[NoteViewModel::class.java]

        // Toolbar设置
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayShowTitleEnabled(false)
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_menu)
        }

        // 加载Fragment
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(binding.fragmentContainer.id, NoteListFragment())
                .commit()
        }

        // 底部导航
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_shiguanglu -> {
                    supportFragmentManager.beginTransaction()
                        .replace(binding.fragmentContainer.id, NoteListFragment())
                        .commit()
                    true
                }

                R.id.nav_shiguangquan -> {
                    supportFragmentManager.beginTransaction()
                        .replace(binding.fragmentContainer.id, CommunityFragment())
                        .commit()
                    true
                }

                else -> false
            }
        }

        // 悬浮按钮
        binding.fabAdd.setOnClickListener {
            val isLogin = UserManager.isLogin()

            if (!isLogin) {
                Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show()
                showLoginDialog()
                return@setOnClickListener
            }

            when (binding.bottomNav.selectedItemId) {
                R.id.nav_shiguanglu -> {
                    val intent = Intent(this, AddNoteActivity::class.java)
                    addNoteLauncher.launch(intent)
                }

                R.id.nav_shiguangquan -> {
                    val intent = Intent(this, AddPublicNoteActivity::class.java)
                    addPublicNoteLauncher.launch(intent)
                }
            }
        }

        initDrawer()

        // 启动时仅拉取服务器数据，不触发本地同步
        if (UserManager.isLogin()) {
            Log.d(TAG, "已登录，仅拉取服务器笔记（避免重复同步）")
            lifecycleScope.launch(Dispatchers.IO) {
                noteViewModel.fetchNotesFromServer()
                withContext(Dispatchers.Main) {
                    Log.d(TAG, "服务器笔记拉取完成")
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 移除无效的 unregisterDownloadReceiver 调用（静态广播无需取消注册）
        PopupManager.clearResources() // 仅保留资源清理
    }

    // 初始化侧拉栏
    private fun initDrawer() {
        val headerView = binding.navView.getHeaderView(0)
        val tvNickname = headerView.findViewById<TextView>(R.id.tv_nickname)
        val tvEmail = headerView.findViewById<TextView>(R.id.tv_email)

        updateDrawerUserInfo(tvNickname, tvEmail)

        binding.navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_login -> {
                    if (UserManager.isLogin()) {
                        UserManager.logout()
                        Toast.makeText(this, "已退出登录", Toast.LENGTH_SHORT).show()
                        updateDrawerUserInfo(tvNickname, tvEmail)
                        supportFragmentManager.beginTransaction()
                            .replace(binding.fragmentContainer.id, NoteListFragment())
                            .commit()
                    } else {
                        showLoginDialog()
                    }
                }

                R.id.nav_my_notes -> {
                    if (!UserManager.isLogin()) {
                        Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show()
                        showLoginDialog()
                        binding.drawerLayout.closeDrawer(binding.navView)
                        return@setNavigationItemSelectedListener true
                    }
                    Toast.makeText(this, "我的笔记", Toast.LENGTH_SHORT).show()
                }

                R.id.nav_settings -> Toast.makeText(this, "设置", Toast.LENGTH_SHORT).show()
                R.id.nav_about -> Toast.makeText(this, "关于", Toast.LENGTH_SHORT).show()
            }
            binding.drawerLayout.closeDrawer(binding.navView)
            true
        }

        binding.toolbar.setNavigationOnClickListener {
            binding.drawerLayout.openDrawer(binding.navView)
        }
    }

    // 更新侧拉栏用户信息
    private fun updateDrawerUserInfo(tvNickname: TextView, tvEmail: TextView) {
        if (UserManager.isLogin()) {
            tvNickname.text = UserManager.getNickname()
            tvEmail.text = UserManager.getEmail()
        } else {
            tvNickname.text = "未登录"
            tvEmail.text = "请登录/注册"
        }
    }

    // 显示登录对话框（串行同步逻辑）
    private fun showLoginDialog() {
        val dialog = LoginRegisterDialog()
        dialog.onLoginSuccess = { loginResponse ->
            // 保存登录信息
            UserManager.saveLoginInfo(
                loginResponse.userId,
                loginResponse.token,
                loginResponse.nickname ?: "用户",
                loginResponse.email ?: ""
            )

            // 刷新侧拉栏
            val headerView = binding.navView.getHeaderView(0)
            updateDrawerUserInfo(
                headerView.findViewById(R.id.tv_nickname),
                headerView.findViewById(R.id.tv_email)
            )

            // 串行执行同步逻辑：先推本地→再拉服务器→最后刷新
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    // 第一步：同步本地未同步笔记到服务器
                    noteViewModel.retrySyncUnsyncedNotes()
                    Log.d(TAG, "本地未同步笔记推送完成")

                    // 第二步：拉取服务器最新数据（自动清理本地草稿）
                    noteViewModel.fetchNotesFromServer()
                    Log.d(TAG, "服务器笔记拉取完成")

                    // 第三步：切回主线程刷新页面
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "笔记同步成功", Toast.LENGTH_SHORT).show()
                        supportFragmentManager.beginTransaction()
                            .replace(binding.fragmentContainer.id, NoteListFragment())
                            .commit()
                    }
                } catch (e: Exception) {
                    // 同步失败处理
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@MainActivity,
                            "同步失败：${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                        Log.e(TAG, "登录后同步失败", e)
                    }
                }
            }
        }
        dialog.show(supportFragmentManager, "login_dialog")
    }

    // 顶部菜单
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.top_right_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                Toast.makeText(this, "设置", Toast.LENGTH_SHORT).show()
                true
            }

            R.id.action_about -> {
                Toast.makeText(this, "关于", Toast.LENGTH_SHORT).show()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }
}