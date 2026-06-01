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
                NotificationChannel(chId, "HCB Bubble", NotificationManager.IMPORTANCE_LOW).apply {
                    setShowBadge(false)
                }
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
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 120
            y = 400
        }

        bubbleView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#CC1B5E20"))
            setPadding(4, 4, 4, 4)
            alpha = 0f
            startAnimation(AlphaAnimation(0f, 1f).apply { duration = 200; fillAfter = true })

            addView(TextView(context).apply {
                actionText = this
                text = if (mode == "capture") "Захватить" else "Вставить"
                setTextColor(Color.WHITE)
                textSize = 13f
                setPadding(24, 14, 24, 14)
                gravity = Gravity.CENTER
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
            })
        }

        bubbleView?.setOnTouchListener { view, event ->
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
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) isMoving = true
                    params.x = initialX + dx.toInt()
                    params.y = initialY + dy.toInt()
                    wm?.updateViewLayout(bubbleView, params)
                    true
                }
                else -> false
            }
        }

        wm?.addView(bubbleView, params)

        val scale = ScaleAnimation(0.5f, 1f, 0.5f, 1f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f)
        scale.duration = 200
        bubbleView?.startAnimation(scale)
    }

    private fun update() {
        actionText?.text = if (mode == "capture") "Захватить" else "Вставить"
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
                Toast.makeText(this, "Захвачено #$id (${text.length})", Toast.LENGTH_SHORT).show()
                hide()
            }
        } else {
            handler.post { Toast.makeText(this, "Ничего не выделено", Toast.LENGTH_SHORT).show() }
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
                text = "#${p.id} ${p.preview}"
                setTextColor(Color.WHITE)
                textSize = 11f
                setPadding(20, 10, 20, 10)
                setBackgroundColor(Color.parseColor("#33FFFFFF"))
                (layoutParams as LinearLayout.LayoutParams).topMargin = 4
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
                            Toast.makeText(this@BubbleService, "Вставлено (${full.length})", Toast.LENGTH_SHORT).show()
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
