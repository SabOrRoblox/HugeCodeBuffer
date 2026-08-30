package com.hugecode.buffer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class RestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val serviceIntent = Intent(context, DeviceService::class.java)
        context.startForegroundService(serviceIntent)
    }
}
