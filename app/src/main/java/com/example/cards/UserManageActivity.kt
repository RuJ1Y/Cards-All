package com.example.cards

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.example.cards.auth.AuthRepository
import com.example.cards.base.BaseActivity
import com.example.cards.databinding.ActivityUserManageBinding
import com.example.cards.databinding.DialogConfirmActionBinding
import com.example.cards.databinding.DialogChangePasswordBinding
import com.example.cards.session.SessionManager
import com.example.cards.util.AvatarCodec
import kotlinx.coroutines.launch

class UserManageActivity : BaseActivity<ActivityUserManageBinding>() {

    private val authRepository = AuthRepository()
    private lateinit var sessionManager: SessionManager

    private var playerId: String = ""
    private var working = false

    private val pickAvatarLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri == null || working) return@registerForActivityResult
            val bytes = AvatarCodec.readBytes(contentResolver, uri)
            if (bytes == null) {
                showStatus("头像读取失败，请重试")
                return@registerForActivityResult
            }
            val encoded = AvatarCodec.encodeJpeg(bytes)
            if (encoded.isBlank()) {
                showStatus("头像读取失败，请重试")
                return@registerForActivityResult
            }
            uploadAvatar(encoded)
        }

    override fun initUI() {
        sessionManager = SessionManager(this)
        playerId = sessionManager.getPlayerId().orEmpty()
        if (playerId.isBlank()) {
            goLogin()
            return
        }

        mBinding.tvUsername.text = sessionManager.getCachedUsername() ?: playerId
        AvatarCodec.setAvatarOrDefault(mBinding.ivAvatar, sessionManager.getCachedAvatarBase64())

        mBinding.ivBack.setOnClickListener { finish() }
        mBinding.ivAvatar.setOnClickListener { pickAvatarLauncher.launch("image/*") }

        mBinding.btnChangePassword.setOnClickListener {
            if (!working) showChangePasswordDialog()
        }

        mBinding.btnLogout.setOnClickListener {
            if (!working) {
                showConfirmDialog(
                    title = "退出登录",
                    message = "确认退出当前账号吗？",
                    confirmText = "退出"
                ) {
                    sessionManager.clear()
                    goLogin()
                }
            }
        }

        mBinding.btnDeleteAccount.setOnClickListener {
            if (!working) showDeleteAccountDialog()
        }

        refreshProfile()
    }

    private fun refreshProfile() {
        showStatus("正在加载账号信息...")
        lifecycleScope.launch {
            val username = authRepository.fetchUsername(playerId)
            val profile = authRepository.fetchProfile(playerId)
            mBinding.tvUsername.text = username
            sessionManager.saveCachedUsername(username)
            sessionManager.saveCachedAvatarBase64(profile?.avatarBase64)
            AvatarCodec.setAvatarOrDefault(mBinding.ivAvatar, sessionManager.getCachedAvatarBase64())
            showStatus("")
        }
    }

    private fun uploadAvatar(avatarBase64: String) {
        setWorking(true)
        showStatus("正在更新头像...")
        lifecycleScope.launch {
            authRepository.updateAvatar(playerId, avatarBase64)
                .onSuccess {
                    sessionManager.saveCachedAvatarBase64(avatarBase64)
                    AvatarCodec.setAvatarOrDefault(mBinding.ivAvatar, sessionManager.getCachedAvatarBase64())
                    showStatus("头像已更新")
                }
                .onFailure {
                    showStatus("头像更新失败: ${it.message ?: "请稍后重试"}")
                }
            setWorking(false)
        }
    }

    private fun showChangePasswordDialog() {
        val dialogBinding = DialogChangePasswordBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        dialogBinding.btnCancel.setOnClickListener {
            if (!working) dialog.dismiss()
        }
        dialogBinding.btnConfirm.setOnClickListener {
            if (working) return@setOnClickListener

            val oldPassword = dialogBinding.etOldPassword.text?.toString().orEmpty().trim()
            val newPassword = dialogBinding.etNewPassword.text?.toString().orEmpty().trim()
            val confirmPassword = dialogBinding.etConfirmPassword.text?.toString().orEmpty().trim()

            if (oldPassword.isBlank()) {
                dialogBinding.etOldPassword.error = "请输入当前密码"
                return@setOnClickListener
            }
            if (newPassword.length < 6) {
                dialogBinding.etNewPassword.error = "新密码至少 6 位"
                return@setOnClickListener
            }
            if (newPassword != confirmPassword) {
                dialogBinding.etConfirmPassword.error = "两次输入不一致"
                return@setOnClickListener
            }

            setWorking(true)
            dialogBinding.tvError.text = "正在更新密码..."
            lifecycleScope.launch {
                authRepository.changePassword(playerId, oldPassword, newPassword)
                    .onSuccess {
                        showStatus("密码已更新")
                        Toast.makeText(this@UserManageActivity, "密码修改成功", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                    .onFailure {
                        val message = it.message ?: "请稍后重试"
                        dialogBinding.tvError.text = "密码修改失败: $message"
                        showStatus("密码修改失败: $message")
                    }
                setWorking(false)
            }
        }

        dialog.show()
        styleDialogWindow(dialog, 0.47f)
    }

    private fun showDeleteAccountDialog() {
        showConfirmDialog(
            title = "注销账号",
            message = "将删除该账号的头像、角色、金币和账号信息，且无法恢复。确认继续吗？",
            confirmText = "确认注销"
        ) {
            deleteAccountForever()
        }
    }

    private fun showConfirmDialog(
        title: String,
        message: String,
        confirmText: String,
        onConfirm: () -> Unit
    ) {
        val dialogBinding = DialogConfirmActionBinding.inflate(layoutInflater)
        dialogBinding.tvTitle.text = title
        dialogBinding.tvMessage.text = message
        dialogBinding.btnConfirm.text = confirmText

        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        dialogBinding.btnCancel.setOnClickListener {
            if (!working) dialog.dismiss()
        }
        dialogBinding.btnConfirm.setOnClickListener {
            if (working) return@setOnClickListener
            dialog.dismiss()
            onConfirm.invoke()
        }

        dialog.show()
        styleDialogWindow(dialog, 0.50f)
    }

    private fun styleDialogWindow(dialog: AlertDialog, widthPercent: Float) {
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        val width = (resources.displayMetrics.widthPixels * widthPercent).toInt()
        dialog.window?.setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun deleteAccountForever() {
        setWorking(true)
        showStatus("正在注销账号并清理数据...")
        lifecycleScope.launch {
            authRepository.deleteAccount(playerId)
                .onSuccess {
                    sessionManager.clear()
                    Toast.makeText(this@UserManageActivity, "账号已注销", Toast.LENGTH_SHORT).show()
                    goLogin()
                }
                .onFailure {
                    showStatus("注销失败: ${it.message ?: "请稍后重试"}")
                    setWorking(false)
                }
        }
    }

    private fun showStatus(text: String) {
        mBinding.tvStatus.text = text
    }

    private fun setWorking(isWorking: Boolean) {
        working = isWorking
        mBinding.ivBack.isEnabled = !isWorking
        mBinding.ivAvatar.isEnabled = !isWorking
        mBinding.btnChangePassword.isEnabled = !isWorking
        mBinding.btnLogout.isEnabled = !isWorking
        mBinding.btnDeleteAccount.isEnabled = !isWorking
        mBinding.panelActions.alpha = if (isWorking) 0.85f else 1f
    }

    private fun goLogin() {
        startActivity(
            Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
        overridePendingTransition(R.anim.activity_fade_in, R.anim.activity_fade_out)
        finish()
    }
}
