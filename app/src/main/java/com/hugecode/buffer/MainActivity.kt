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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        StorageManager.init(this)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        val title = TextView(this).apply {
            text = "HCB Smart Buffer"
            textSize = 22f
            setPadding(0, 0, 0, 16)
        }
        layout.addView(title)

        val statusText = TextView(this).apply {
            text = if (isAccessibilityEnabled()) "● Служба активна" else "○ Служба не активна"
            textSize = 14f
            setPadding(0, 0, 0, 8)
        }
        layout.addView(statusText)

        val countText = TextView(this).apply {
            text = "Записей: ${StorageManager.getCount()}"
            textSize = 14f
            setPadding(0, 0, 0, 16)
        }
        layout.addView(countText)

        if (!isAccessibilityEnabled()) {
            val enableButton = Button(this).apply {
                text = "Включить службу"
                setOnClickListener {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            }
            layout.addView(enableButton)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val overlayButton = Button(this).apply {
                text = "Разрешить поверх других приложений"
                setOnClickListener {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:com.hugecode.buffer")
                    )
                    startActivity(intent)
                }
            }
            layout.addView(overlayButton)
        }

        val showButton = Button(this).apply {
            text = "Показать все записи"
            setOnClickListener { showAllItems() }
        }
        layout.addView(showButton)

        val clearButton = Button(this).apply {
            text = "Очистить всё"
            setOnClickListener {
                StorageManager.clearAll()
                countText.text = "Записей: 0"
                Toast.makeText(this@MainActivity, "Буфер очищен", Toast.LENGTH_SHORT).show()
            }
        }
        layout.addView(clearButton)

        setContentView(layout)
    }

    private fun isAccessibilityEnabled(): Boolean {
        val service = "$packageName/.AccessibilityService"
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.contains(service) || enabledServices.contains("com.hugecode.buffer")
    }

    private fun showAllItems() {
        val previews = StorageManager.getAllPreviews()

        if (previews.isEmpty()) {
            Toast.makeText(this, "Буфер пуст", Toast.LENGTH_SHORT).show()
            return
        }

        val items = previews.map { "#${it.id} · ${it.length} симв.\n${it.preview}" }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Все записи (${previews.size})")
            .setItems(items) { _, which ->
                val preview = previews[which]
                Thread {
                    val text = StorageManager.getText(preview.id)
                    handler.post {
                        if (text != null) {
                            showTextDialog(preview.id, text)
                        }
                    }
                }.start()
            }
            .setNegativeButton("Закрыть", null)
            .show()
    }

    private fun showTextDialog(id: Int, text: String) {
        val scrollView = ScrollView(this)
        val textView = TextView(this).apply {
            this.text = text
            textSize = 12f
            setPadding(16, 16, 16, 16)
            setTextIsSelectable(true)
            movementMethod = ScrollingMovementMethod()
        }
        scrollView.addView(textView)

        AlertDialog.Builder(this)
            .setTitle("Запись #$id")
            .setView(scrollView)
            .setPositiveButton("Удалить") { _, _ ->
                StorageManager.deleteItem(id)
                Toast.makeText(this, "Удалено", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("Закрыть", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        recreate()
    }
}