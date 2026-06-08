package com.example.cards.base

import android.os.Bundle
import android.view.LayoutInflater
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.viewbinding.ViewBinding
import java.lang.reflect.ParameterizedType

abstract class BaseActivity<T : ViewBinding> : AppCompatActivity() {

    private lateinit var _binding: T

    val mBinding: T
        get() = _binding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        applyImmersiveMode()
        _binding = initBinding()
        setContentView(mBinding.root)
        applySafeInsets()
        initUI()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyImmersiveMode()
        }
    }

    open fun initUI() {}

    fun initBinding(): T {
        val type = (javaClass.genericSuperclass as ParameterizedType).actualTypeArguments[0]
        val clazz = type as Class<*>
        val method = clazz.getMethod("inflate", LayoutInflater::class.java)
        @Suppress("UNCHECKED_CAST")
        return method.invoke(null, layoutInflater) as T
    }

    private fun applyImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun applySafeInsets() {
        val root = mBinding.root
        val baseStart = root.paddingStart
        val baseTop = root.paddingTop
        val baseEnd = root.paddingEnd
        val baseBottom = root.paddingBottom
        val minHorizontalSafe = (14 * resources.displayMetrics.density).toInt()

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val cutoutInsets = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            val systemInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Use symmetric horizontal safe padding so both left/right reserve camera space.
            val symmetricSafe = maxOf(
                cutoutInsets.left,
                cutoutInsets.right,
                systemInsets.left,
                systemInsets.right,
                minHorizontalSafe
            )

            view.setPaddingRelative(
                baseStart + symmetricSafe,
                baseTop,
                baseEnd + symmetricSafe,
                baseBottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }
}
