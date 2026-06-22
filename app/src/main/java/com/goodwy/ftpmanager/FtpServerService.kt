package com.goodwy.ftpmanager

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import org.apache.ftpserver.FtpServer
import org.apache.ftpserver.FtpServerFactory
import org.apache.ftpserver.ftplet.Authority
import org.apache.ftpserver.ftplet.Authentication
import org.apache.ftpserver.ftplet.User
import org.apache.ftpserver.ftplet.UserManager
import org.apache.ftpserver.listener.ListenerFactory
import org.apache.ftpserver.usermanager.UsernamePasswordAuthentication
import org.apache.ftpserver.usermanager.impl.BaseUser
import org.apache.ftpserver.usermanager.impl.WritePermission
import java.net.NetworkInterface
import java.net.ServerSocket
import java.util.Collections

class FtpServerService : Service() {

    companion object {
        const val ACTION_START = "com.goodwy.ftpmanager.START_FTP"
        const val ACTION_STOP = "com.goodwy.ftpmanager.STOP_FTP"
        const val BROADCAST_FTP_STATE = "com.goodwy.ftpmanager.FTP_STATE"

        const val KEY_IS_RUNNING = "is_running"
        const val KEY_URL = "url"
        const val KEY_USERNAME = "username"
        const val KEY_PASSWORD = "password"

        private const val CHANNEL_ID = "ftp_server_channel"
        private const val NOTIFICATION_ID = 991
    }

    private var ftpServer: FtpServer? = null
    private var currentPassword = ""
    private var currentUsername = ""
    private var currentPort = 0
    private var currentIp = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopServer()
            else -> {
                if (ftpServer == null) {
                    startServer()
                } else {
                    broadcastState()
                }
            }
        }
        return START_STICKY
    }

    private fun startServer() {
        try {
            // 1. Ask Android OS for any guaranteed open, unassigned port
            val socket = ServerSocket(0)
            currentPort = socket.localPort
            socket.close()

            // 2. Generate 6-digit dynamic password & pull static username
            currentPassword = (100000..999999).random().toString()
            val prefs = getSharedPreferences("ftp_prefs", Context.MODE_PRIVATE)
            currentUsername = prefs.getString("username", "goodwy") ?: "goodwy"

            // 3. Resolve Hotspot / Wi-Fi IP
            currentIp = getLocalIpAddress() ?: "127.0.0.1"

            // 4. Construct the RAM-Bound User
            val baseUser = BaseUser().apply {
                name = currentUsername
                password = currentPassword
                homeDirectory = "/storage" // The master Android mountpoint for Internal + SD Cards
                authorities = listOf<Authority>(WritePermission())
            }

            val ramUserManager = object : UserManager {
                override fun getUserByName(name: String?): User? = if (name == baseUser.name) baseUser else null
                override fun getAllUserNames(): Array<String> = arrayOf(baseUser.name)
                override fun delete(name: String?) {}
                override fun save(user: User?) {}
                override fun doesExist(name: String?): Boolean = name == baseUser.name
                override fun authenticate(auth: Authentication?): User? {
                    if (auth is UsernamePasswordAuthentication) {
                        if (auth.username == baseUser.name && auth.password == baseUser.password) {
                            return baseUser
                        }
                    }
                    return null
                }
                override fun getAdminName(): String = baseUser.name
                override fun isAdmin(name: String?): Boolean = true
            }

            // 5. Boot the Apache Engine
            val serverFactory = FtpServerFactory()
            val listenerFactory = ListenerFactory().apply { port = currentPort }

            serverFactory.addListener("default", listenerFactory.createListener())
            serverFactory.userManager = ramUserManager

            ftpServer = serverFactory.createServer()
            ftpServer?.start()

            // 6. Anchor to un-swipeable OS notification
            createNotificationChannel()
            startForeground(NOTIFICATION_ID, buildNotification())

            broadcastState()

        } catch (e: Exception) {
            e.printStackTrace()
            stopServer()
        }
    }

    private fun stopServer() {
        try {
            ftpServer?.stop()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            ftpServer = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            stopSelf()
            broadcastState(isRunning = false)
        }
    }

    private fun buildNotification(): android.app.Notification {
        val stopIntent = Intent(this, FtpServerService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val ftpAddress = "ftp://$currentIp:$currentPort"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FTP server is running") // Strict compliance
            .setContentText(ftpAddress)               // Strict compliance {ftp://<ip>:<port>}
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)                         // Un-swipeable lock
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "STOP", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "FTP Server Service", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun broadcastState(isRunning: Boolean = true) {
        val intent = Intent(BROADCAST_FTP_STATE).apply {
            setPackage(packageName) // Crucial: Locks the broadcast so other apps can't steal the password
            putExtra(KEY_IS_RUNNING, isRunning)
            if (isRunning) {
                putExtra(KEY_URL, "ftp://$currentIp:$currentPort")
                putExtra(KEY_USERNAME, currentUsername)
                putExtra(KEY_PASSWORD, currentPassword)
            }
        }
        sendBroadcast(intent)
    }

    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (networkInterface in interfaces) {
                val addresses = Collections.list(networkInterface.inetAddresses)
                for (address in addresses) {
                    if (!address.isLoopbackAddress && address.hostAddress?.indexOf(':') == -1) {
                        return address.hostAddress
                    }
                }
            }
        } catch (ignored: Exception) {}
        return null
    }
}
