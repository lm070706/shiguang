package com.lm.shiguang

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.lm.shiguang.databinding.ActivitySplashBinding

class SplashActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySplashBinding
    private val PREFS_NAME = "AppPrefs"
    private val KEY_FIRST_LAUNCH = "isFirstLaunch"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 隐藏标题栏
        supportActionBar?.hide()

        // 检查是否是首次启动
        val sharedPref: SharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val isFirstLaunch = sharedPref.getBoolean(KEY_FIRST_LAUNCH, true)

        if (isFirstLaunch) {
            // 首次启动：加载SplashFragment
            supportFragmentManager.beginTransaction()
                .replace(R.id.splash_fragment_container, SplashFragment())
                .commit()

            // 延迟2秒跳转，并标记为非首次
            Handler(Looper.getMainLooper()).postDelayed({
                sharedPref.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply()
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }, 2000)
        } else {
            // 非首次启动：直接跳主页面
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}