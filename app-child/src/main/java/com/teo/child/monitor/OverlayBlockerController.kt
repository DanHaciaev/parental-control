package com.teo.child.monitor

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * A raw WindowManager overlay (not an Activity) — this is what SYSTEM_ALERT_WINDOW is actually
 * for, and it lets the foreground service interrupt an app that's already open and unlocked
 * without fighting Android's background-activity-start restrictions.
 * Plain Android views by design: this runs with no Activity/Lifecycle context, so hosting
 * Compose here would need a hand-rolled LifecycleOwner — not worth it for one static screen.
 */
class OverlayBlockerController(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null
    private var messageView: TextView? = null
    private var requestButton: Button? = null

    fun show(reason: String, allowRequestMore: Boolean, onGoHome: () -> Unit, onRequestMore: () -> Unit) {
        if (overlayView != null) {
            messageView?.text = reason
            requestButton?.visibility = if (allowRequestMore) View.VISIBLE else View.GONE
            return
        }

        val density = context.resources.displayMetrics.density

        val root = FrameLayout(context).apply {
            setBackgroundColor(Color.parseColor("#FFF8F1"))
            isFocusableInTouchMode = true
            setOnKeyListener { _, keyCode, _ ->
                // Swallow back presses so the block can't be dismissed by navigating away.
                keyCode == KeyEvent.KEYCODE_BACK
            }
        }

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val padding = (32 * density).toInt()
            setPadding(padding, padding, padding, padding)
        }

        val title = TextView(context).apply {
            text = "Время вышло"
            textSize = 26f
            setTextColor(Color.parseColor("#2B231D"))
            gravity = Gravity.CENTER
        }

        val message = TextView(context).apply {
            text = reason
            textSize = 17f
            setTextColor(Color.parseColor("#5A5048"))
            gravity = Gravity.CENTER
            setPadding(0, (16 * density).toInt(), 0, 0)
        }
        messageView = message

        val homeButton = Button(context).apply {
            text = "На главный экран"
            setOnClickListener { onGoHome() }
            setPadding(0, (32 * density).toInt(), 0, 0)
        }

        val moreTimeButton = Button(context).apply {
            text = "Попросить ещё времени"
            visibility = if (allowRequestMore) View.VISIBLE else View.GONE
            setOnClickListener {
                text = "Запрос отправлен"
                isEnabled = false
                onRequestMore()
            }
            setPadding(0, (12 * density).toInt(), 0, 0)
        }
        requestButton = moreTimeButton

        content.addView(title)
        content.addView(message)
        content.addView(homeButton)
        content.addView(moreTimeButton)
        root.addView(
            content,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        )

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.OPAQUE
        )

        windowManager.addView(root, params)
        overlayView = root
        root.requestFocus()
    }

    fun hide() {
        overlayView?.let {
            runCatching { windowManager.removeView(it) }
            overlayView = null
            messageView = null
            requestButton = null
        }
    }

    fun isShowing(): Boolean = overlayView != null
}
