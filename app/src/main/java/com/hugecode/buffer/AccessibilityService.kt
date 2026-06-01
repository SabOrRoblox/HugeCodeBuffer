package com.hugecode.buffer

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class AccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var lastSelectedText: String? = null
    private var isBubbleShown = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        StorageManager.init(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED -> {
                handleTextSelection()
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                handler.postDelayed({ handleTextSelection() }, 200)
            }
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                if (event.className?.toString()?.contains("EditText") == true) {
                    handler.postDelayed({ handleTextSelection() }, 300)
                }
            }
        }
    }

    private fun handleTextSelection() {
        val root = rootInActiveWindow ?: return
        val focusedNode = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)

        if (focusedNode == null) {
            hideBubble()
            return
        }

        val selectedText = try {
            if (focusedNode.text != null && focusedNode.textSelectionStart >= 0 && focusedNode.textSelectionEnd > focusedNode.textSelectionStart) {
                focusedNode.text.toString().substring(focusedNode.textSelectionStart, focusedNode.textSelectionEnd)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }

        if (selectedText != null && selectedText.isNotEmpty()) {
            lastSelectedText = selectedText
            showBubble("capture")
        } else if (focusedNode.isEditable) {
            showBubble("paste")
        } else {
            hideBubble()
        }

        root.recycle()
    }

    private fun showBubble(mode: String) {
        if (isBubbleShown) {
            updateBubble(mode)
            return
        }

        isBubbleShown = true
        val intent = Intent(this, BubbleService::class.java).apply {
            action = "SHOW_BUBBLE"
            putExtra("mode", mode)
        }
        startService(intent)
    }

    private fun updateBubble(mode: String) {
        val intent = Intent(this, BubbleService::class.java).apply {
            action = "UPDATE_BUBBLE"
            putExtra("mode", mode)
        }
        startService(intent)
    }

    private fun hideBubble() {
        if (!isBubbleShown) return
        isBubbleShown = false

        val intent = Intent(this, BubbleService::class.java).apply {
            action = "HIDE_BUBBLE"
        }
        startService(intent)
    }

    fun captureText(): String? {
        val root = rootInActiveWindow ?: return null
        val focusedNode = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        val text = try {
            if (focusedNode != null && focusedNode.text != null && focusedNode.textSelectionStart >= 0 && focusedNode.textSelectionEnd > focusedNode.textSelectionStart) {
                focusedNode.text.toString().substring(focusedNode.textSelectionStart, focusedNode.textSelectionEnd)
            } else {
                lastSelectedText
            }
        } catch (e: Exception) {
            lastSelectedText
        }
        root.recycle()
        return text
    }

    fun pasteText(text: String) {
        val root = rootInActiveWindow ?: return
        val focusedNode = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)

        if (focusedNode != null && focusedNode.isEditable) {
            val arguments = android.os.Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        }

        root.recycle()
    }

    override fun onInterrupt() {
        hideBubble()
    }

    companion object {
        var instance: AccessibilityService? = null
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }
}