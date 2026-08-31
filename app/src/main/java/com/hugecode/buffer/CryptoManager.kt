package com.hugecode.buffer

import java.security.*
import java.security.spec.*
import javax.crypto.*
import javax.crypto.spec.*
import java.util.Base64
import javax.crypto.KeyAgreement

object CryptoManager {
    
    private const val AES_GCM = "AES/GCM/NoPadding"
    private const val KEY_AGREEMENT = "X25519"
    private const val AES_KEY_SIZE = 32
    
    init {
        System.loadLibrary("sieve_hash")
    }
    
    external fun sieveHash256(data: ByteArray): ByteArray
    external fun sieveHash512(data: ByteArray): ByteArray
    
    fun generateKeyPair(): KeyPair {
        return KeyPairGenerator.getInstance(KEY_AGREEMENT).generateKeyPair()
    }
    
    fun x3dh(
        myPrivateKey: PrivateKey,
        myPublicKey: PublicKey,
        theirPublicKey: PublicKey,
        theirIdentityKey: PublicKey,
        myIdentityKey: PrivateKey
    ): ByteArray {
        val dh1 = x25519(myPrivateKey, theirPublicKey)
        val dh2 = x25519(myIdentityKey, theirPublicKey)
        val dh3 = x25519(myIdentityKey, theirIdentityKey)
        
        val combined = ByteArray(dh1.size + dh2.size + dh3.size)
        System.arraycopy(dh1, 0, combined, 0, dh1.size)
        System.arraycopy(dh2, 0, combined, dh1.size, dh2.size)
        System.arraycopy(dh3, 0, combined, dh1.size + dh2.size, dh3.size)
        
        return sieveHash256(combined)
    }
    
    private fun x25519(privateKey: PrivateKey, publicKey: PublicKey): ByteArray {
        val ka = KeyAgreement.getInstance(KEY_AGREEMENT)
        ka.init(privateKey)
        ka.doPhase(publicKey, true)
        return ka.generateSecret()
    }
    
    fun encryptAES(plaintext: String, key: ByteArray): String {
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
        
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        
        val combined = ByteArray(iv.size + encrypted.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(encrypted, 0, combined, iv.size, encrypted.size)
        
        return Base64.getEncoder().encodeToString(combined)
    }
    
    fun decryptAES(encryptedBase64: String, key: ByteArray): String {
        val combined = Base64.getDecoder().decode(encryptedBase64)
        val iv = combined.copyOfRange(0, 12)
        val encrypted = combined.copyOfRange(12, combined.size)
        
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        
        return String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }
    
    fun encryptAESBytes(data: ByteArray, key: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
        
        val iv = cipher.iv
        val encrypted = cipher.doFinal(data)
        
        val combined = ByteArray(iv.size + encrypted.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(encrypted, 0, combined, iv.size, encrypted.size)
        
        return combined
    }
    
    fun decryptAESBytes(data: ByteArray, key: ByteArray): ByteArray {
        val iv = data.copyOfRange(0, 12)
        val encrypted = data.copyOfRange(12, data.size)
        
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        
        return cipher.doFinal(encrypted)
    }
    
    fun deriveKey(sharedSecret: ByteArray, context: String): ByteArray {
        val info = context.toByteArray(Charsets.UTF_8)
        val combined = ByteArray(sharedSecret.size + info.size)
        System.arraycopy(sharedSecret, 0, combined, 0, sharedSecret.size)
        System.arraycopy(info, 0, combined, sharedSecret.size, info.size)
        
        return sieveHash256(combined)
    }
}
