package com.example.cards

import android.content.Intent
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.example.cards.auth.AuthRepository
import com.example.cards.base.BaseActivity
import com.example.cards.databinding.ActivityLoginBinding
import com.example.cards.session.SessionManager
import com.example.cards.util.AvatarCodec
import kotlinx.coroutines.launch

class LoginActivity : BaseActivity<ActivityLoginBinding>() {

    private val authRepository = AuthRepository()
    private lateinit var sessionManager: SessionManager

    private var verified = false
    private var selectedAvatarBase64: String? = null
    private var verifiedAvatarBase64: String? = null

    private val pickAvatarLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                val bytes = AvatarCodec.readBytes(contentResolver, uri)
                if (bytes != null) {
                    val encoded = AvatarCodec.encodeJpeg(bytes)
                    if (encoded.isNotBlank()) {
                        selectedAvatarBase64 = encoded
                        AvatarCodec.setAvatarOrDefault(mBinding.ivRegisterAvatar, selectedAvatarBase64)
                        mBinding.tvRegisterState.text = "头像已选择"
                    } else {
                        mBinding.tvRegisterState.text = "头像读取失败，请重试"
                    }
                } else {
                    mBinding.tvRegisterState.text = "头像读取失败，请重试"
                }
            }
        }

    override fun initUI() {
        sessionManager = SessionManager(this)

        val cached = sessionManager.getPlayerId()
        if (cached != null) {
            sessionManager.ensureCharacterCacheDefaults()
            goLobby(cached)
            return
        }

        showLoginPanel(animated = false)
        AvatarCodec.setAvatarOrDefault(mBinding.ivLoginAvatar, null)
        AvatarCodec.setAvatarOrDefault(mBinding.ivRegisterAvatar, null)

        mBinding.btnVerifyHuman.setOnClickListener {
            verified = true
            mBinding.btnVerifyHuman.text = "已验证"
            loadLoginAvatarByUsername()
        }

        mBinding.btnLogin.setOnClickListener {
            if (!verified) {
                mBinding.tvLoginState.text = "请先点击验证"
                return@setOnClickListener
            }

            val username = mBinding.etLoginUsername.text?.toString().orEmpty()
            val password = mBinding.etLoginPassword.text?.toString().orEmpty()

            lifecycleScope.launch {
                val result = authRepository.login(username, password)
                result.onSuccess { playerId ->
                    mBinding.tvLoginState.text = "登录成功"
                    sessionManager.savePlayerId(playerId)
                    sessionManager.saveCachedUsername(username.trim())
                    // 优先用验证阶段拿到的头像，避免首页首帧闪默认图。
                    sessionManager.saveCachedAvatarBase64(verifiedAvatarBase64)
                    val profileAvatar = authRepository.fetchProfile(playerId)?.avatarBase64
                    if (!profileAvatar.isNullOrBlank()) {
                        sessionManager.saveCachedAvatarBase64(profileAvatar)
                    }
                    sessionManager.ensureCharacterCacheDefaults()
                    goLobby(playerId)
                }.onFailure {
                    mBinding.tvLoginState.text = "登录失败: ${it.message}"
                }
            }
        }

        mBinding.tvToRegister.setOnClickListener { showRegisterPanel(animated = true) }
        mBinding.tvBackLogin.setOnClickListener { showLoginPanel(animated = true) }

        // 注册页面：直接点击头像进行选择
        mBinding.ivRegisterAvatar.setOnClickListener {
            pickAvatarLauncher.launch("image/*")
        }

        mBinding.btnRegisterConfirm.setOnClickListener {
            val username = mBinding.etRegisterUsername.text?.toString().orEmpty()
            val pwd = mBinding.etRegisterPassword.text?.toString().orEmpty()
            val confirm = mBinding.etRegisterPasswordConfirm.text?.toString().orEmpty()

            if (pwd != confirm) {
                mBinding.tvRegisterState.text = "两次密码不一致"
                return@setOnClickListener
            }

            lifecycleScope.launch {
                val result = authRepository.register(username, pwd, selectedAvatarBase64)
                result.onSuccess {
                    mBinding.tvRegisterState.text = "注册成功"
                    mBinding.etLoginUsername.setText(username)
                    showLoginPanel(animated = true)
                    mBinding.tvLoginState.text = "注册完成，请点击验证后登录"
                }.onFailure {
                    mBinding.tvRegisterState.text = "注册失败: ${it.message}"
                }
            }
        }
    }

    private fun showLoginPanel(animated: Boolean) {
        verified = false
        verifiedAvatarBase64 = null
        mBinding.btnVerifyHuman.text = "点击验证"
        switchPanel(showLogin = true, animated = animated)
    }

    private fun showRegisterPanel(animated: Boolean) {
        switchPanel(showLogin = false, animated = animated)
    }

    private fun switchPanel(showLogin: Boolean, animated: Boolean) {
        val inView = if (showLogin) mBinding.layoutLoginPanel else mBinding.layoutRegisterPanel
        val outView = if (showLogin) mBinding.layoutRegisterPanel else mBinding.layoutLoginPanel

        if (!animated) {
            inView.visibility = View.VISIBLE
            outView.visibility = View.GONE
            return
        }

        outView.animate().alpha(0f).setDuration(120).withEndAction {
            outView.visibility = View.GONE
            outView.alpha = 1f
            inView.alpha = 0f
            inView.visibility = View.VISIBLE
            inView.animate().alpha(1f).setDuration(140).start()
        }.start()
    }

    private fun goLobby(playerId: String) {
        startActivity(Intent(this, LobbyActivity::class.java).putExtra("player_id", playerId))
        finish()
    }

    private fun loadLoginAvatarByUsername() {
        val username = mBinding.etLoginUsername.text?.toString().orEmpty().trim()
        if (username.isEmpty()) {
            AvatarCodec.setAvatarOrDefault(mBinding.ivLoginAvatar, null)
            mBinding.tvLoginState.text = "验证通过，请先输入用户名"
            return
        }

        mBinding.tvLoginState.text = "验证通过，正在识别头像..."
        lifecycleScope.launch {
            val profile = authRepository.fetchProfile(username)
            if (profile?.avatarBase64.isNullOrBlank()) {
                verifiedAvatarBase64 = null
                AvatarCodec.setAvatarOrDefault(mBinding.ivLoginAvatar, null)
                mBinding.tvLoginState.text = "验证通过，未找到头像，可直接登录"
            } else {
                verifiedAvatarBase64 = profile?.avatarBase64
                AvatarCodec.setAvatarOrDefault(mBinding.ivLoginAvatar, verifiedAvatarBase64)
                mBinding.tvLoginState.text = "验证通过，已加载账号头像"
            }
        }
    }
}
