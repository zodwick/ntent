package com.scrnstr.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView

class TerminalTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private val handler = Handler(Looper.getMainLooper())
    private var baseText: String = ""
    private var dotCount = 0
    private var isAnimating = false

    private val dotRunnable = object : Runnable {
        override fun run() {
            if (!isAnimating) return
            dotCount = (dotCount + 1) % 4
            text = baseText + ".".repeat(dotCount)
            handler.postDelayed(this, 500)
        }
    }

    fun startDotAnimation(base: String) {
        baseText = base
        dotCount = 0
        isAnimating = true
        handler.post(dotRunnable)
    }

    fun stopDotAnimation() {
        isAnimating = false
        handler.removeCallbacks(dotRunnable)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopDotAnimation()
    }
}
