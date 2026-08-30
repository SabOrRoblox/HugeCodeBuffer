package com.hugecode.buffer

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

object StorageManager {

    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "LocalSystemKey"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_SIZE = 12
    private const val TAG_SIZE = 128

    private lateinit var itemsDir: File
    private val lock = ReentrantReadWriteLock()
    private val cache = ConcurrentHashMap<Int, BufferItem>()
    private lateinit var secretKey: SecretKey

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
        itemsDir = File(context.filesDir, "local_system_items")
        itemsDir.mkdirs()
        secretKey = getOrCreateKey()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let {
            return it.secretKey
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            KEYSTORE
        )
        
        val parameterSpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).apply {
            setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            setKeySize(256)
            setRandomizedEncryptionRequired(true)
            setUserAuthenticationRequired(false)
        }.build()

        keyGenerator.init(parameterSpec)
        return keyGenerator.generateKey()
    }

    private fun encrypt(data: String): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        
        val iv = cipher.iv
        val encrypted = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
        
        val combined = ByteArray(IV_SIZE + encrypted.size)
        System.arraycopy(iv, 0, combined, 0, IV_SIZE)
        System.arraycopy(encrypted, 0, combined, IV_SIZE, encrypted.size)
        
        return combined
    }

    private fun decrypt(data: ByteArray): String? {
        return try {
            val iv = ByteArray(IV_SIZE)
            val encrypted = ByteArray(data.size - IV_SIZE)
            
            System.arraycopy(data, 0, iv, 0, IV_SIZE)
            System.arraycopy(data, IV_SIZE, encrypted, 0, encrypted.size)
            
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_SIZE, iv))
            
            val decrypted = cipher.doFinal(encrypted)
            String(decrypted, Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
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
            val file = File(itemsDir, "$id.dat")
            val encryptedData = encrypt("$id|$timestamp|${text.length}\n$text")
            file.writeBytes(encryptedData)
        } catch (_: Exception) { }

        return id
    }

    fun getText(id: Int): String? {
        cache[id]?.let { return it.text }

        val file = File(itemsDir, "$id.dat")
        if (!file.exists()) return null

        return try {
            val encryptedData = file.readBytes()
            val content = decrypt(encryptedData)
            val firstLineEnd = content?.indexOf('\n') ?: return null
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
                val encryptedData = file.readBytes()
                val content = decrypt(encryptedData) ?: return@mapNotNull null
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
        File(itemsDir, "$id.dat").delete()
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
