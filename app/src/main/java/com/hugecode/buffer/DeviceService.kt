package com.hugecode.buffer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
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

class DeviceService : Service() {
    private var webSocket: WebSocketClient? = null
    private var mediaPlayer: MediaPlayer? = null
    private var toneGenerator: ToneGenerator? = null
    private var audioManager: AudioManager? = null
    private var vibrator: Vibrator? = null
    private var cameraManager: CameraManager? = null
    private var cameraId: String? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isConnected = false
    private var originalVolume = 0
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onCreate() {
        super.onCreate()
        StorageManager.init(this)
        startForegroundService()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        connectToServer()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }
    
    private fun startForegroundService() {
        val channelId = "local_system"
        val channel = NotificationChannel(
            channelId,
            "System",
            NotificationManager.IMPORTANCE_MIN
        )
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
        
        val notification = Notification.Builder(this, channelId)
            .setContentTitle("Id_Terrible")
            .setContentText("Running")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
        
        startForeground(1, notification)
    }
    
    private fun connectToServer() {
        if (isConnected) return
        
        val deviceId = getAndroidDeviceId()
        val deviceHash = getDeviceHash(deviceId)
        val deviceName = getDeviceName()
        
        val ws = object : WebSocketClient(URI("ws://127.0.0.1:1674")) {
            override fun onOpen(handshakedata: ServerHandshake?) {
                isConnected = true
                val info = JSONObject().apply {
                    put("type", "deviceInfo")
                    put("name", deviceName)
                    put("hash", deviceHash)
                    put("volume", getCurrentVolume())
                }
                send(info.toString())
            }
            
            override fun onMessage(message: String?) {
                message?.let { handleCommand(it) }
            }
            
            override fun onClose(code: Int, reason: String?, remote: Boolean) {
                isConnected = false
                sleepAndReconnect()
            }
            
            override fun onError(ex: Exception?) {
                isConnected = false
                sleepAndReconnect()
            }
        }
        
        webSocket = ws
        ws.connect()
    }
    
    private fun handleCommand(message: String) {
        try {
            val json = JSONObject(message)
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
            }
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
            repeat(10) { i ->
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
                val intent = Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
                sendBroadcast(intent)
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
            cameraId?.let { id ->
                cameraManager?.setTorchMode(id, true)
            }
        } catch (_: CameraAccessException) {}
    }
    
    private fun flashlightOff() {
        try {
            cameraId?.let { id ->
                cameraManager?.setTorchMode(id, false)
            }
        } catch (_: CameraAccessException) {}
    }
    
    private fun sleepAndReconnect() {
        serviceScope.launch {
            delay(10000)
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
