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
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class BubbleService : Service() {

    private var windowManager: WindowManager? = null
    private var bubbleView: View? = null
    private var bubbleList: LinearLayout? = null
    private var isListShown = false
    private var currentMode = "paste"
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "SHOW_BUBBLE" -> {
                currentMode = intent.getStringExtra("mode") ?: "paste"
                createBubble()
            }
            "UPDATE_BUBBLE" -> {
                currentMode = intent.getStringExtra("mode") ?: "paste"
                updateBubbleUI()
            }
            "HIDE_BUBBLE" -> {
                removeBubble()
            }
        }
        return START_STICKY
    }

    private fun startForeground() {
        val channelId = "hcb_bubble_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "HCB Bubble",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Служба быстрой вставки"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(this, channelId)
            .setContentTitle("HCB Smart")
            .setContentText("Служба быстрой вставки активна")
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(1, notification)
    }

    private fun createBubble() {
        removeBubble()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 100
        }

        bubbleView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8, 8, 8, 8)

            background = GradientDrawable().apply {
                setColor(Color.parseColor("#E53935"))
                cornerRadius = 24f
            }

            addView(TextView(context).apply {
                id = android.R.id.text1
                text = if (currentMode == "capture") "📥 Захватить" else "📤 Вставить"
                setTextColor(Color.WHITE)
                textSize = 14f
                setPadding(16, 12, 16, 12)
                gravity = Gravity.CENTER

                setOnClickListener {
                    if (currentMode == "capture") {
                        handleCapture()
                    } else {
                        if (!isListShown) {
                            showPreviewsList()
                        } else {
                            hidePreviewsList()
                        }
                    }
                }
            })

            addView(LinearLayout(context).apply {
                bubbleList = this
                id = android.R.id.list
                orientation = LinearLayout.VERTICAL
                visibility = View.GONE
            })
        }

        windowManager?.addView(bubbleView, params)
    }

    private fun updateBubbleUI() {
        val textView = bubbleView?.findViewById<TextView>(android.R.id.text1)
        textView?.text = if (currentMode == "capture") "📥 Захватить" else "📤 Вставить"
        hidePreviewsList()
    }

    private fun removeBubble() {
        try {
            if (bubbleView != null) {
                windowManager?.removeView(bubbleView)
                bubbleView = null
                bubbleList = null
                isListShown = false
            }
        } catch (_: Exception) { }
    }

    private fun handleCapture() {
        val accService = AccessibilityService.instance ?: return
        val rawText = accService.captureText()
        val text = rawText?.toString()

        if (text != null && text.isNotEmpty()) {
            val id = StorageManager.addText(text)
            handler.post {
                Toast.makeText(this, "Захвачено #$id · ${text.length} симв.", Toast.LENGTH_SHORT).show()
                removeBubble()
            }
        } else {
            handler.post {
                Toast.makeText(this, "Ничего не выделено", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showPreviewsList() {
        val list = bubbleList ?: return
        list.removeAllViews()

        val previews = StorageManager.getLastPreviews(3)

        if (previews.isEmpty()) {
            handler.post {
                Toast.makeText(this, "Буфер пуст", Toast.LENGTH_SHORT).show()
            }
            return
        }

        for (preview in previews) {
            val itemView = TextView(this).apply {
                text = "#${preview.id} · ${preview.preview}"
                setTextColor(Color.WHITE)
                textSize = 12f
                setPadding(16, 8, 16, 8)

                setOnClickListener {
                    val fullText = StorageManager.getText(preview.id)
                    if (fullText != null) {
                        val accService = AccessibilityService.instance
                        accService?.pasteText(fullText)
                        handler.post {
                            Toast.makeText(this@BubbleService, "Вставлено ${fullText.length} симв.", Toast.LENGTH_SHORT).show()
                            removeBubble()
                        }
                    }
                }
            }
            list.addView(itemView)
        }

        list.visibility = View.VISIBLE
        isListShown = true
    }

    private fun hidePreviewsList() {
        bubbleList?.visibility = View.GONE
        isListShown = false
    }

    override fun onDestroy() {
        removeBubble()
        super.onDestroy()
    }
}
