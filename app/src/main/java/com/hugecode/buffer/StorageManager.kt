package com.hugecode.buffer

import android.content.Context
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object StorageManager {
    private lateinit var itemsDir: File
    private val cache = ConcurrentHashMap<Int, BufferItem>()
    
    data class BufferItem(
        val id: Int,
        val text: String,
        val timestamp: Long,
        val length: Int
    )
    
    fun init(context: Context) {
        itemsDir = File(context.filesDir, "items")
        itemsDir.mkdirs()
    }
    
    fun addText(text: String): Int {
        val id = (itemsDir.listFiles()?.mapNotNull { it.nameWithoutExtension.toIntOrNull() }?.maxOrNull() ?: 0) + 1
        addTextWithId(id, text)
        return id
    }
    
    fun addTextWithId(id: Int, text: String) {
        val timestamp = System.currentTimeMillis()
        cache[id] = BufferItem(id, text, timestamp, text.length)
        try {
            File(itemsDir, "$id.txt").writeText(text)
        } catch (_: Exception) {}
    }
    
    fun getText(id: Int): String? {
        cache[id]?.let { return it.text }
        val file = File(itemsDir, "$id.txt")
        return if (file.exists()) try { file.readText() } catch (_: Exception) { null } else null
    }
    
    fun deleteItem(id: Int): Boolean {
        cache.remove(id)
        File(itemsDir, "$id.txt").delete()
        return true
    }
    
    fun clearAll() {
        cache.clear()
        itemsDir.listFiles()?.forEach { it.delete() }
    }
    
    fun getCount(): Int {
        return itemsDir.listFiles()?.size ?: 0
    }
}
