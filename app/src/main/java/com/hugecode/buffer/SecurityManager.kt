package com.hugecode.buffer

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import java.security.MessageDigest
import java.util.Base64

object SecurityManager {
    private const val EXPECTED_SIGNATURE = "b02fba9cd256597c4a954356690a2f0ee0a83df45bfc5491d66a1b87429505ab"
    private const val LICENSE_KEY = "ea5d3866e5a3c83716a6bad64df8a9bb2ef32e8bf705b975a474dc13dfd2636f"
    private const val LICENSE_SALT = "f2ff223a7d8ee77a26fee55ca1a78455e1cdc9bbd3fb82aa128d77279d6cb0d9"
    
    private lateinit var context: Context
    private var verified = false
    private var verificationCount = 0
    
    fun init(context: Context) {
        this.context = context
    }
    
    fun verifyApp(): Boolean {
        if (verified && verificationCount < 5) {
            return true
        }
        
        val signatureValid = verifySignature()
        val licenseValid = verifyLicense()
        val integrityValid = verifyIntegrity()
        val tamperValid = verifyTamperDetection()
        
        verified = signatureValid && licenseValid && integrityValid && tamperValid
        verificationCount++
        
        return verified
    }
    
    fun verifySignature(): Boolean {
        return try {
            val signature = getAppSignature()
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(signature)
            val signatureHex = hash.joinToString("") { "%02x".format(it) }
            signatureHex == EXPECTED_SIGNATURE
        } catch (_: Exception) {
            false
        }
    }
    
    fun verifyLicense(): Boolean {
        return try {
            val deviceId = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            )
            val expectedLicense = generateLicense(deviceId)
            val storedLicense = StorageManager.getText(999)
            storedLicense == expectedLicense
        } catch (_: Exception) {
            false
        }
    }
    
    fun verifyIntegrity(): Boolean {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val lastUpdateTime = packageInfo.lastUpdateTime
            val installTime = packageInfo.firstInstallTime
            val currentTime = System.currentTimeMillis()
            installTime <= currentTime && lastUpdateTime <= currentTime
        } catch (_: Exception) {
            false
        }
    }
    
    fun verifyTamperDetection(): Boolean {
        return try {
            val buildFingerprint = Build.FINGERPRINT
            val storedFingerprint = StorageManager.getText(998)
            if (storedFingerprint == null) {
                StorageManager.addTextWithId(998, buildFingerprint)
                true
            } else {
                storedFingerprint == buildFingerprint
            }
        } catch (_: Exception) {
            false
        }
    }
    
    fun generateLicense(deviceId: String): String {
        return try {
            val combined = "$LICENSE_KEY|$deviceId|$LICENSE_SALT"
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(combined.toByteArray())
            Base64.getEncoder().encodeToString(hash)
        } catch (_: Exception) {
            ""
        }
    }
    
    fun activateLicense(): Boolean {
        return try {
            val deviceId = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            )
            val license = generateLicense(deviceId)
            StorageManager.addTextWithId(999, license)
            verifyLicense()
        } catch (_: Exception) {
            false
        }
    }
    
    fun generateServerHash(deviceId: String): String {
        return try {
            val combined = "$deviceId|$LICENSE_KEY|$LICENSE_SALT"
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(combined.toByteArray())
            hash.joinToString("") { "%02x".format(it) }.substring(0, 32)
        } catch (_: Exception) {
            ""
        }
    }
    
    fun verifyDebuggerDetached(): Boolean {
        return try {
            !android.os.Debug.isDebuggerConnected()
        } catch (_: Exception) {
            false
        }
    }
    
    fun verifyRootDetection(): Boolean {
        return try {
            val paths = listOf(
                "/system/app/Superuser.apk",
                "/sbin/su",
                "/system/bin/su",
                "/system/xbin/su",
                "/data/local/xbin/su",
                "/data/local/bin/su",
                "/system/sd/xbin/su",
                "/system/bin/failsafe/su",
                "/data/local/su"
            )
            paths.none { java.io.File(it).exists() }
        } catch (_: Exception) {
            false
        }
    }
    
    fun verifyEmulatorDetection(): Boolean {
        return try {
            val fingerprint = Build.FINGERPRINT
            val model = Build.MODEL
            val manufacturer = Build.MANUFACTURER
            val product = Build.PRODUCT
            val hardware = Build.HARDWARE
            
            val isEmulator = fingerprint.contains("generic") ||
                fingerprint.contains("emulator") ||
                model.contains("google_sdk") ||
                model.contains("Emulator") ||
                model.contains("Android SDK built for x86") ||
                manufacturer.contains("Genymotion") ||
                product.contains("sdk") ||
                product.contains("emulator") ||
                hardware.contains("goldfish") ||
                hardware.contains("ranchu")
            
            !isEmulator
        } catch (_: Exception) {
            false
        }
    }
    
    fun verifyAllSecurity(): Boolean {
        return verifyApp() &&
            verifyDebuggerDetached() &&
            verifyRootDetection() &&
            verifyEmulatorDetection()
    }
    
    fun getVerificationCount(): Int {
        return verificationCount
    }
    
    fun isVerified(): Boolean {
        return verified
    }
    
    private fun getAppSignature(): ByteArray {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNING_CERTIFICATES
            ).signingInfo.apkContentsSigners.first().toByteArray()
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNATURES
            ).signatures.first().toByteArray()
        }
    }
}
