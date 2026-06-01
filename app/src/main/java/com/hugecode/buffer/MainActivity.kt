package com.hugecode.buffer

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.method.ScrollingMovementMethod
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var countText: TextView
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        StorageManager.init(this)

        val isDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 64, 48, 48)
            setBackgroundColor(if (isDark) 0xFF121212.toInt() else 0xFFF5F5F5.toInt())
        }

        root.addView(TextView(this).apply {
            text = "HCB Smart"
            textSize = 28f
            setTextColor(if (isDark) 0xFFFFFFFF.toInt() else 0xFF1B5E20.toInt())
            setPadding(0, 0, 0, 8)
        })

        root.addView(TextView(this).apply {
            text = "Умный буфер обмена"
            textSize = 14f
            setTextColor(if (isDark) 0xFFAAAAAA.toInt() else 0xFF666666.toInt())
            setPadding(0, 0, 0, 32)
        })

        val accEnabled = isAccessibilityEnabled()
        statusText = TextView(this).apply {
            text = if (accEnabled) "● Служба активна" else "○ Служба не активна"
            textSize = 14f
            setTextColor(if (accEnabled) 0xFF4CAF50.toInt() else 0xFFFF5252.toInt())
            setPadding(0, 0, 0, 8)
        }
        root.addView(statusText)

        countText = TextView(this).apply {
            text = "Сохранено: ${StorageManager.getCount()}"
            textSize = 14f
            setTextColor(if (isDark) 0xFFCCCCCC.toInt() else 0xFF444444.toInt())
            setPadding(0, 0, 0, 32)
        }
        root.addView(countText)

        if (!accEnabled) {
            root.addView(makeButton("Включить службу", 0xFF1B5E20.toInt()) {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            })
            root.addView(gap())
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            root.addView(makeButton("Разрешить оверлей", 0xFF0D3B0F.toInt()) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:com.hugecode.buffer")))
            })
            root.addView(gap())
        }

        root.addView(makeButton("Все записи", 0xFF2E7D32.toInt()) { showAllItems() })
        root.addView(gap())
        root.addView(makeButton("Очистить всё", 0xFFC62828.toInt()) {
            StorageManager.clearAll()
            countText.text = "Сохранено: 0"
            Toast.makeText(this, "Очищено", Toast.LENGTH_SHORT).show()
        })

        setContentView(root)
    }

    private fun makeButton(text: String, color: Int, click: () -> Unit): Button {
        return Button(this).apply {
            this.text = text
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 14f
            setBackgroundColor(color)
            setPadding(32, 20, 32, 20)
            setOnClickListener { click() }
        }
    }

    private fun gap(): LinearLayout {
        return LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 16)
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        val svc = "$packageName/.AccessibilityService"
        val list = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        return list.contains(svc)
    }

    private fun showAllItems() {
        val previews = StorageManager.getAllPreviews()
        if (previews.isEmpty()) {
            Toast.makeText(this, "Пусто", Toast.LENGTH_SHORT).show()
            return
        }
        val items = previews.map { "#${it.id} (${it.length} симв.)\n${it.preview}" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Записи (${previews.size})")
            .setItems(items) { _, i ->
                Thread {
                    val txt = StorageManager.getText(previews[i].id)
                    handler.post { if (txt != null) showText(previews[i].id, txt) }
                }.start()
            }
            .setNegativeButton("Закрыть", null)
            .show()
    }

    private fun showText(id: Int, text: String) {
        val sv = ScrollView(this)
        val tv = TextView(this).apply {
            this.text = text
            textSize = 12f
            setPadding(24, 24, 24, 24)
            setTextIsSelectable(true)
            movementMethod = ScrollingMovementMethod()
        }
        sv.addView(tv)
        AlertDialog.Builder(this)
            .setTitle("#$id (${text.length} симв.)")
            .setView(sv)
            .setPositiveButton("Удалить") { _, _ ->
                StorageManager.deleteItem(id)
                countText.text = "Сохранено: ${StorageManager.getCount()}"
                Toast.makeText(this, "Удалено", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("Закрыть", null)
            .show()
    }
}
