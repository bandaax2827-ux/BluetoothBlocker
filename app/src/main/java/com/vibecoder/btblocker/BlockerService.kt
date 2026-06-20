package com.vibecoder.btblocker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat

class BlockerService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null
    private val channelId = "bt_blocker_channel"

    private val tick = object : Runnable {
        override fun run() {
            if (!Prefs.isBlocked(this@BlockerService)) {
                stopSelf()
                return
            }
            forceDisableBluetooth()
            handler.postDelayed(this, 700)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(1, buildNotification())

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BTBlocker::lock")
        wakeLock?.acquire(24 * 60 * 60 * 1000L)

        handler.post(tick)
    }

    private fun forceDisableBluetooth() {
        try {
            val bm = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            bm.adapter?.let { if (it.isEnabled) it.disable() }
        } catch (_: SecurityException) { }

        RootManager.run("svc bluetooth disable")
        RootManager.run("settings put global bluetooth_disabled 1")
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                channelId,
                "Bluetooth Blocker",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Сервис блокировки Bluetooth"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(ch)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Bluetooth заблокирован")
            .setContentText("Сторож активен")
            .setSmallIcon(R.drawable.ic_bluetooth_block)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }
}