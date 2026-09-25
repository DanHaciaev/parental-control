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
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A raw WindowManager overlay (not an Activity) — this is what SYSTEM_ALERT_WINDOW is actually
 * for, and it lets the foreground service interrupt an app that's already open and unlocked
 * without fighting Android's background-activity-start restrictions.
 * Plain Android views by design: this runs with no Activity/Lifecycle context, so hosting
 * Compose here would need a hand-rolled LifecycleOwner — not worth it for one static screen.
 *
 * Deliberately cannot show above the secure lock screen — confirmed live (dumpsys window showed
 * mPolicyVisibility=false, isVisible=false even with FLAG_SHOW_WHEN_LOCKED/FLAG_DISMISS_KEYGUARD
 * set) that Android hard-blocks a plain TYPE_APPLICATION_OVERLAY from rendering over the keyguard
 * regardless of its flags — reasonably so, since otherwise any app with the overlay permission
 * could paint a fake lock screen to steal PINs. Only a genuine Activity using
 * Activity.setShowWhenLocked() gets that privilege (see [com.teo.child.block.LockScreenBlockActivity],
 * used alongside this for the airplane-mode/location-disabled blocks specifically).
 *
 * A singleton shared between MonitorForegroundService's poll loop and
 * ProtectionAccessibilityService's instant on-open check — both can trigger a block, and
 * [blockedPackage] is tracked here (not per-caller) so whichever one triggered a block is the one
 * whose "is this still the blocked app" cleanup check controls hiding it again.
 */
@Singleton
class OverlayBlockerController @Inject constructor(@ApplicationContext private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null
    private var titleView: TextView? = null
    private var messageView: TextView? = null
    private var requestButton: Button? = null
    private var actionButton: Button? = null
    private var homeButtonView: Button? = null

    /** The package the currently-showing overlay is blocking, if any — null covers both "nothing
     *  shown" and device-wide blocks (schedules, location) that aren't about one specific app. */
    var blockedPackage: String? = null
        private set

    /** [actionLabel]/[onAction] is a second, generic button (e.g. "Открыть настройки геолокации")
     *  independent of the existing "request more time" button below — kept separate so this new
     *  use case can't disturb that button's own click-once-and-disable behavior. [showHomeButton]
     *  hides the "На главный экран" button entirely for cases where it's redundant (e.g. the
     *  location-disabled screen, where the one relevant action is fixing the setting). */
    fun show(
        reason: String,
        allowRequestMore: Boolean,
        onGoHome: () -> Unit,
        onRequestMore: () -> Unit,
        title: String = "Время вышло",
        actionLabel: String? = null,
        onAction: () -> Unit = {},
        showHomeButton: Boolean = true,
        blockedPackage: String? = null
    ) {
        this.blockedPackage = blockedPackage
        if (overlayView != null) {
            titleView?.text = title
            messageView?.text = reason
            homeButtonView?.visibility = if (showHomeButton) View.VISIBLE else View.GONE
            requestButton?.visibility = if (allowRequestMore) View.VISIBLE else View.GONE
            actionButton?.text = actionLabel ?: ""
            actionButton?.visibility = if (actionLabel != null) View.VISIBLE else View.GONE
            return
        }

        val density = context.resources.displayMetrics.density
        val buttonWidth = (280 * density).toInt()

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

        val titleText = TextView(context).apply {
            text = title
            textSize = 26f
            setTextColor(Color.parseColor("#2B231D"))
            gravity = Gravity.CENTER
        }
        titleView = titleText

        val message = TextView(context).apply {
            text = reason
            textSize = 17f
            setTextColor(Color.parseColor("#5A5048"))
            gravity = Gravity.CENTER
            setPadding(0, (16 * density).toInt(), 0, 0)
        }
        messageView = message

        fun actionButtonLayoutParams(topMarginDp: Int) =
            LinearLayout.LayoutParams(buttonWidth, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (topMarginDp * density).toInt()
            }

        val homeButton = Button(context).apply {
            text = "На главный экран"
            isAllCaps = false
            textSize = 16f
            visibility = if (showHomeButton) View.VISIBLE else View.GONE
            setOnClickListener {
                onGoHome()
                // Don't wait for the next ~3s poll tick to notice the foreground app changed and
                // decide to hide this — the tap itself is already an explicit "let me out" request,
                // so hide immediately. If the child ends up back in a blocked app, the next tick
                // re-shows it the same way it always has.
                hide()
            }
            layoutParams = actionButtonLayoutParams(32)
        }
        homeButtonView = homeButton

        val moreTimeButton = Button(context).apply {
            text = "Попросить ещё времени"
            isAllCaps = false
            textSize = 16f
            visibility = if (allowRequestMore) View.VISIBLE else View.GONE
            setOnClickListener {
                text = "Запрос отправлен"
                isEnabled = false
                onRequestMore()
            }
            layoutParams = actionButtonLayoutParams(12)
        }
        requestButton = moreTimeButton

        val secondaryActionButton = Button(context).apply {
            text = actionLabel ?: ""
            isAllCaps = false
            textSize = 16f
            visibility = if (actionLabel != null) View.VISIBLE else View.GONE
            setOnClickListener { onAction() }
            layoutParams = actionButtonLayoutParams(if (showHomeButton) 12 else 32)
        }
        actionButton = secondaryActionButton

        content.addView(titleText)
        content.addView(message)
        content.addView(homeButton)
        content.addView(moreTimeButton)
        content.addView(secondaryActionButton)
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
            titleView = null
            messageView = null
            requestButton = null
            actionButton = null
            homeButtonView = null
        }
        blockedPackage = null
    }

    fun isShowing(): Boolean = overlayView != null
}
