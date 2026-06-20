package com.vibecoder.btblocker

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
    private var isRunning = false
    
    private val tick = object : Runnable {
        override fun run() {
            if (!isRunning) return
            
            if (!Prefs.isBlocked(this@BlockerService)) {
                stopSelf()
                return
            }
            
            try {
                // Проверяем и выключаем Bluetooth через API
                val bm = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                bm.adapter?.let { 
                    if (it.isEnabled) {
                        it.disable()
                    }
                }
            } catch (_: Exception) {}
            
            // Дополнительно через root команду
            RootManager.run("svc bluetooth disable")
            
            handler.postDelayed(this, 700)
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        isRunning = true
        
        // Создаём канал уведомлений
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "bt_block",
                "BT Blocker",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "Блокировка Bluetooth"
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        
        // Создаём уведомление
        val notification = NotificationCompat.Builder(this, "bt_block")
            .setContentTitle("Bluetooth заблокирован")
            .setContentText("Сторож активен")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .build()
        
        startForeground(1, notification)
        
        // Блокируем засыпание
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BTBlocker::WakeLock")
        wakeLock?.acquire(24 * 60 * 60 * 1000L)
        
        // Запускаем цикл проверки
        handler.post(tick)
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        isRunning = false
        handler.removeCallbacks(tick)
        wakeLock?.let { 
            if (it.isHeld) it.release() 
        }
        super.onDestroy()
    }
}
