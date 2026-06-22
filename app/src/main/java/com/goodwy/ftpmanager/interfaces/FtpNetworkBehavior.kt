package com.goodwy.ftpmanager.interfaces

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.goodwy.ftpmanager.FtpServerService

interface FtpNetworkBehavior {
    
    // Check if our volatile RAM FTP Service thread is alive in the OS kernel
    @Suppress("DEPRECATION")
    fun isFtpServiceRunning(context: Context): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (FtpServerService::class.java.name == service.service.className) return true
        }
        return false
    }

    // Safely fire or kill the background FTP engine
    fun toggleFtpEngine(context: Context, start: Boolean) {
        val serviceIntent = Intent(context, FtpServerService::class.java)
        if (start) {
            serviceIntent.action = FtpServerService.ACTION_START
            ContextCompat.startForegroundService(context, serviceIntent)
        } else {
            serviceIntent.action = FtpServerService.ACTION_STOP
            context.startService(serviceIntent)
        }
    }
}
