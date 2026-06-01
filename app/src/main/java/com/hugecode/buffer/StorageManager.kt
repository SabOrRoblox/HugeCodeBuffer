package com.hugecode.buffer

import android.content.Context
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

object StorageManager {

    private lateinit var itemsDir: File
    private val lock = ReentrantReadWriteLock()
    private val cache = ConcurrentHashMap<Int, BufferItem>()

    data class ItemPreview(
        val id: Int,
        val length: Int,
        val preview: String,
        val timestamp: Long
    )

    data class BufferItem(
        val id: Int,
        val text: String,
        val timestamp: Long,
        val length: Int
    )

    fun init(context: Context) {
        itemsDir = File(context.filesDir, "hcb_items")
        itemsDir.mkdirs()
    }

    fun addText(text: String): Int {
        val id = lock.write {
            val existing = itemsDir.listFiles()
                ?.mapNotNull { it.nameWithoutExtension.toIntOrNull() }
                ?.maxOrNull() ?: 0
            existing + 1
        }

        val timestamp = System.currentTimeMillis()
        val item = BufferItem(id, text, timestamp, text.length)
        cache[id] = item

        try {
            val file = File(itemsDir, "$id.txt")
            file.writeText("$id|$timestamp|${text.length}\n$text")
        } catch (_: Exception) { }

        return id
    }

    fun getText(id: Int): String? {
        cache[id]?.let { return it.text }

        val file = File(itemsDir, "$id.txt")
        if (!file.exists()) return null

        return try {
            val content = file.readText()
            val firstLineEnd = content.indexOf('\n')
            if (firstLineEnd > 0) {
                content.substring(firstLineEnd + 1)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun getLastText(): String? {
        val files = itemsDir.listFiles() ?: return null
        val lastFile = files.maxByOrNull { it.nameWithoutExtension.toIntOrNull() ?: 0 } ?: return null
        return getText(lastFile.nameWithoutExtension.toIntOrNull() ?: return null)
    }

    fun getLastPreviews(count: Int = 3): List<ItemPreview> {
        return getAllPreviews().take(count)
    }

    fun getAllPreviews(): List<ItemPreview> {
        val files = itemsDir.listFiles() ?: return emptyList()
        return files.mapNotNull { file ->
            val id = file.nameWithoutExtension.toIntOrNull() ?: return@mapNotNull null
            try {
                val content = file.readText()
                val firstLineEnd = content.indexOf('\n')
                if (firstLineEnd <= 0) return@mapNotNull null

                val header = content.substring(0, firstLineEnd)
                val parts = header.split("|")
                if (parts.size < 3) return@mapNotNull null

                val timestamp = parts[1].toLongOrNull() ?: 0L
                val length = parts[2].toIntOrNull() ?: 0
                val text = content.substring(firstLineEnd + 1)
                val preview = if (text.length > 50) text.take(50) + "…" else text
                ItemPreview(id, length, preview, timestamp)
            } catch (_: Exception) {
                null
            }
        }.sortedByDescending { it.id }
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