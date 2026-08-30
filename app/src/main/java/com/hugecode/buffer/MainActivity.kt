package com.hugecode.buffer

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        if (!notificationManager.isNotificationPolicyAccessGranted) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        }
        
        startService(Intent(this, DeviceService::class.java))
        finish()
    }
}
