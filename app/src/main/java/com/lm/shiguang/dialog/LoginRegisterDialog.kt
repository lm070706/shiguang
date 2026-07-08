package com.lm.shiguang.dialog

import android.app.Dialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.lm.shiguang.R
import com.lm.shiguang.repository.AuthRepository
import com.lm.shiguang.network.LoginResponse
import com.lm.shiguang.network.RegisterRequest
import com.lm.shiguang.utils.UserManager
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class LoginRegisterDialog : DialogFragment() {
    private lateinit var authRepository: AuthRepository
    var onLoginSuccess: ((LoginResponse) -> Unit)? = null
    private val TAG = "login"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        dialog?.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val view = inflater.inflate(R.layout.dialog_login_register, container, false)
        authRepository = AuthRepository(requireContext())
        initTabSwitch(view)
        initLoginLogic(view)
        initRegisterLogic(view)
        return view
    }

    // 切换登录/注册标签
    private fun initTabSwitch(view: View) {
        val tvLogin = view.findViewById<TextView>(R.id.tv_login)
        val tvRegister = view.findViewById<TextView>(R.id.tv_register)
        val layoutLogin = view.findViewById<View>(R.id.layout_login)
        val layoutRegister = view.findViewById<View>(R.id.layout_register)

        tvLogin.setOnClickListener {
            tvLogin.setTextColor(resources.getColor(R.color.blue))
            tvLogin.setBackgroundResource(R.drawable.tab_indicator)
            tvRegister.setTextColor(resources.getColor(R.color.gray))
            tvRegister.setBackgroundResource(0)
            layoutLogin.visibility = View.VISIBLE
            layoutRegister.visibility = View.GONE
        }

        tvRegister.setOnClickListener {
            tvRegister.setTextColor(resources.getColor(R.color.blue))
            tvRegister.setBackgroundResource(R.drawable.tab_indicator)
            tvLogin.setTextColor(resources.getColor(R.color.gray))
            tvLogin.setBackgroundResource(0)
            layoutRegister.visibility = View.VISIBLE
            layoutLogin.visibility = View.GONE
        }
    }

    // 登录逻辑
    private fun initLoginLogic(view: View) {
        val etEmail = view.findViewById<EditText>(R.id.et_login_email)
        val etPwd = view.findViewById<EditText>(R.id.et_login_pwd)
        val btnLogin = view.findViewById<Button>(R.id.btn_login)

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val pwd = etPwd.text.toString().trim()

            // 输入校验
            if (email.isEmpty() || !email.contains("@") || pwd.isEmpty()) {
                Toast.makeText(requireContext(), "请输入有效的邮箱和密码", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnLogin.isEnabled = false
            btnLogin.text = "登录中..."

            lifecycleScope.launch {
                val result: Result<LoginResponse> = authRepository.login(email, pwd)
                btnLogin.isEnabled = true
                btnLogin.text = "登录"

                result.onSuccess { loginResponse ->
                    // 校验返回数据
                    if (loginResponse.userId != 0L && !loginResponse.token.isNullOrEmpty()) {

                        // 保存登录信息（双重保障，和MainActivity回调保存一致）
                        UserManager.saveLoginInfo(
                            loginResponse.userId,
                            loginResponse.token,
                            loginResponse.nickname ?: "用户",
                            email
                        )
                        Toast.makeText(requireContext(), "登录成功", Toast.LENGTH_SHORT).show()
                        // 回调通知MainActivity
                        onLoginSuccess?.invoke(loginResponse)
                        dismiss()
                    } else {

                        Toast.makeText(requireContext(), "登录失败：返回数据不完整", Toast.LENGTH_SHORT).show()
                    }
                }.onFailure { e ->

                    Toast.makeText(requireContext(), "登录失败：${e.message}", Toast.LENGTH_SHORT).show()
                    Log.e(TAG, "错误码=${e.message}")
                }
            }
        }
    }

    // 注册逻辑
    private fun initRegisterLogic(view: View) {
        val etNickname = view.findViewById<EditText>(R.id.et_reg_nickname)
        val etEmail = view.findViewById<EditText>(R.id.et_reg_email)
        val etPwd = view.findViewById<EditText>(R.id.et_reg_pwd)
        val etConfirmPwd = view.findViewById<EditText>(R.id.et_reg_confirm_pwd)
        val etVerifyCode = view.findViewById<EditText>(R.id.et_reg_verify_code)
        val btnGetVerifyCode = view.findViewById<Button>(R.id.btn_get_verify_code)
        val btnRegister = view.findViewById<Button>(R.id.btn_register)

        btnGetVerifyCode.setOnClickListener {
            val email = etEmail.text.toString().trim()
            if (email.isEmpty() || !email.contains("@")) {
                Toast.makeText(requireContext(), "请输入有效的邮箱", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnGetVerifyCode.isEnabled = false
            btnGetVerifyCode.text = "发送中..."

            lifecycleScope.launch {
                val result: Result<String> = authRepository.sendVerificationCode(email)
                btnGetVerifyCode.isEnabled = true
                btnGetVerifyCode.text = "获取验证码"

                result.onSuccess {
                    Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                }.onFailure { e ->
                    Toast.makeText(requireContext(), "发送失败：${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        btnRegister.setOnClickListener {
            val nickname = etNickname.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val pwd = etPwd.text.toString().trim()
            val confirmPwd = etConfirmPwd.text.toString().trim()
            val verifyCode = etVerifyCode.text.toString().trim()

            // 输入校验
            if (nickname.isEmpty() || email.isEmpty() || pwd.isEmpty() || confirmPwd.isEmpty() || verifyCode.isEmpty()) {
                Toast.makeText(requireContext(), "请填写所有必填项", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (pwd != confirmPwd) {
                Toast.makeText(requireContext(), "两次输入的密码不一致", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val request = RegisterRequest(
                email = email,
                password = pwd,
                confirmPassword = confirmPwd,
                nickname = nickname,
                verifyCode = verifyCode
            )

            btnRegister.isEnabled = false
            btnRegister.text = "注册中..."

            lifecycleScope.launch {
                val result: Result<String> = authRepository.register(request)
                btnRegister.isEnabled = true
                btnRegister.text = "注册"

                result.onSuccess {
                    Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                    dismiss()
                }.onFailure { e ->
                    Toast.makeText(requireContext(), "注册失败：${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        lifecycleScope.cancel()
    }
}