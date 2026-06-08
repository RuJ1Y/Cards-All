package com.example.cards

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import com.example.cards.base.BaseActivity
import com.example.cards.databinding.ActivitySplashBinding
import com.example.cards.session.SessionManager

class SplashActivity : BaseActivity<ActivitySplashBinding>() {

    private var animatorSet: AnimatorSet? = null
    private lateinit var sessionManager: SessionManager

    override fun initUI() {
        sessionManager = SessionManager(this)
        startSplashAnimation()
    }

    private fun startSplashAnimation() {
        mBinding.tvCards.alpha = 0f

        val fadeIn = ObjectAnimator.ofFloat(mBinding.tvCards, "alpha", 0f, 1f).apply {
            duration = 700
        }
        val fadeOut = ObjectAnimator.ofFloat(mBinding.tvCards, "alpha", 1f, 0f).apply {
            duration = 700
            startDelay = 350
        }

        animatorSet = AnimatorSet().apply {
            playSequentially(fadeIn, fadeOut)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    val cachedPlayer = sessionManager.getPlayerId()
                    val targetIntent = if (cachedPlayer.isNullOrBlank()) {
                        Intent(this@SplashActivity, LoginActivity::class.java)
                    } else {
                        Intent(this@SplashActivity, LobbyActivity::class.java).apply {
                            putExtra("player_id", cachedPlayer)
                        }
                    }
                    startActivity(targetIntent)
                    overridePendingTransition(R.anim.activity_fade_in, 0)
                    finish()
                }
            })
            start()
        }
    }

    override fun onDestroy() {
        animatorSet?.cancel()
        super.onDestroy()
    }
}
