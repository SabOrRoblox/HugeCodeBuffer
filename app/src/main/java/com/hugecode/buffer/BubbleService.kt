package com.hugecode.buffer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.ScaleAnimation
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class BubbleService : Service() {

    private var wm: WindowManager? = null
    private var bubbleView: LinearLayout? = null
    private var bubbleList: LinearLayout? = null
    private var actionText: TextView? = null
    private var isListShown = false
    private var mode = "paste"
    private val handler = Handler(Looper.getMainLooper())
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isMoving = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val chId = "hcb_bubble"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(chId, "HCB", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) }
            )
        }
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        startForeground(1, Notification.Builder(this, chId)
            .setContentTitle("HCB Smart")
            .setContentText("Ready")
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentIntent(pi)
            .setOngoing(true)
            .build())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "SHOW_BUBBLE" -> { mode = intent.getStringExtra("mode") ?: "paste"; show() }
            "UPDATE_BUBBLE" -> { mode = intent.getStringExtra("mode") ?: "paste"; update() }
            "HIDE_BUBBLE" -> hide()
        }
        return START_STICKY
    }

    private fun show() {
        hide()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 300
        }

        bubbleView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.argb(220, 30, 30, 30))
                cornerRadius = 60f
                setStroke(2, Color.argb(80, 255, 255, 255))
            }
            setPadding(8, 8, 8, 8)
            elevation = 24f
            alpha = 0f

            addView(TextView(context).apply {
                actionText = this
                text = if (mode == "capture") "ЗАХВАТИТЬ" else "ВСТАВИТЬ"
                setTextColor(Color.WHITE)
                textSize = 12f
                setPadding(28, 16, 28, 16)
                gravity = Gravity.CENTER
                setBackgroundColor(Color.argb(80, 76, 175, 80))
                background = GradientDrawable().apply {
                    setColor(Color.argb(200, 76, 175, 80))
                    cornerRadius = 50f
                }
                setOnClickListener {
                    if (!isMoving) {
                        if (mode == "capture") capture() else toggleList()
                    }
                }
            })

            addView(LinearLayout(context).apply {
                bubbleList = this
                orientation = LinearLayout.VERTICAL
                visibility = View.GONE
                setPadding(8, 8, 8, 4)
            })
        }

        bubbleView?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isMoving = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (Math.abs(dx) > 5 || Math.abs(dy) > 5) isMoving = true
                    params.x = initialX + dx.toInt()
                    params.y = initialY + dy.toInt()
                    wm?.updateViewLayout(bubbleView, params)
                    true
                }
                else -> false
            }
        }

        wm?.addView(bubbleView, params)

        val fadeIn = AlphaAnimation(0f, 1f).apply { duration = 250; fillAfter = true }
        val scale = ScaleAnimation(0.7f, 1f, 0.7f, 1f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f).apply { duration = 250 }
        bubbleView?.startAnimation(fadeIn)
        bubbleView?.startAnimation(scale)
    }

    private fun update() {
        actionText?.text = if (mode == "capture") "ЗАХВАТИТЬ" else "ВСТАВИТЬ"
        (actionText?.background as? GradientDrawable)?.setColor(
            if (mode == "capture") Color.argb(200, 76, 175, 80) else Color.argb(200, 33, 150, 243)
        )
        bubbleList?.visibility = View.GONE
        isListShown = false
    }

    private fun hide() {
        try {
            if (bubbleView != null) {
                wm?.removeView(bubbleView)
                bubbleView = null
                bubbleList = null
                actionText = null
                isListShown = false
            }
        } catch (_: Exception) {}
    }

    private fun capture() {
        val acc = com.hugecode.buffer.AccessibilityService.instance ?: return
        val root = acc.rootInActiveWindow ?: return
        val node = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        val text = if (node != null && node.text != null) node.text.toString() else ""
        root.recycle()
        if (text.isNotEmpty()) {
            val id = StorageManager.addText(text)
            handler.post {
                Toast.makeText(this, "OK #$id (${text.length})", Toast.LENGTH_SHORT).show()
                hide()
            }
        } else {
            handler.post { Toast.makeText(this, "Пусто", Toast.LENGTH_SHORT).show() }
        }
    }

    private fun toggleList() {
        if (isListShown) {
            bubbleList?.visibility = View.GONE
            isListShown = false
            return
        }
        val list = bubbleList ?: return
        list.removeAllViews()
        val previews = StorageManager.getLastPreviews(3)
        if (previews.isEmpty()) {
            handler.post { Toast.makeText(this, "Буфер пуст", Toast.LENGTH_SHORT).show() }
            return
        }
        for (p in previews) {
            list.addView(TextView(this).apply {
                text = "${p.preview}"
                setTextColor(Color.WHITE)
                textSize = 11f
                setPadding(16, 10, 16, 10)
                setBackgroundColor(Color.argb(60, 255, 255, 255))
                background = GradientDrawable().apply {
                    setColor(Color.argb(60, 255, 255, 255))
                    cornerRadius = 30f
                }
                (layoutParams as LinearLayout.LayoutParams).topMargin = 6
                setOnClickListener {
                    val full = StorageManager.getText(p.id)
                    if (full != null) {
                        val acc = com.hugecode.buffer.AccessibilityService.instance
                        if (acc != null) {
                            val r = acc.rootInActiveWindow
                            if (r != null) {
                                val n = r.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                                if (n != null && n.isEditable) {
                                    n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                                        putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, full)
                                    })
                                }
                                r.recycle()
                            }
                        }
                        handler.post {
                            Toast.makeText(this@BubbleService, "OK (${full.length})", Toast.LENGTH_SHORT).show()
                            hide()
                        }
                    }
                }
            })
        }
        list.visibility = View.VISIBLE
        isListShown = true
    }

    override fun onDestroy() { hide(); super.onDestroy() }
}
