package com.vibecoder.btblocker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
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
    
    // Определяем Xiaomi
    private val isXiaomi: Boolean by lazy {
        val brand = Build.BRAND.lowercase()
        val manufacturer = Build.MANUFACTURER.lowercase()
        brand.contains("xiaomi") || brand.contains("redmi") ||
        brand.contains("poco") || manufacturer.contains("xiaomi")
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!isRunning) return

            if (!Prefs.isBlocked(this@BlockerService)) {
                stopSelf()
                return
            }

            // 1. Проверяем состояние BT через API
            val isBtOn = try {
                val bm = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                bm.adapter?.isEnabled == true
            } catch (_: Exception) {
                false
            }

            if (isBtOn) {
                // 2. Отключаем через API
                try {
                    @Suppress("DEPRECATION")
                    BluetoothAdapter.getDefaultAdapter()?.disable()
                } catch (_: Exception) {}

                // 3. Root-команды для отключения
                RootManager.run("svc bluetooth disable")
                RootManager.run("settings put global bluetooth_on 0")
                
                // 4. СПЕЦИАЛЬНЫЕ команды для Xiaomi
                if (isXiaomi) {
                    RootManager.run("settings put global ble_scan_always_enabled 0")
                    RootManager.run("am force-stop com.xiaomi.bluetooth 2>/dev/null")
                    RootManager.run("am force-stop com.miui.bluetooth 2>/dev/null")
                    RootManager.run("settings put global miui_bluetooth_auto_connect 0")
                    RootManager.run("settings put global miui_bluetooth_scan 0")
                }

                // 5. Убиваем процесс Bluetooth
                RootManager.run("am force-stop com.android.bluetooth 2>/dev/null")
                
                // 6. Отключаем BLE-сканирование (часто используется на Xiaomi)
                RootManager.run("settings put secure ble_scan_always_enabled 0")
            }

            // Интервал 300 мс для быстрого реагирования
            handler.postDelayed(this, 300)
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "bt_block",
                "BT Blocker",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Блокировка Bluetooth"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, "bt_block")
            .setContentTitle("Bluetooth заблокирован")
            .setContentText(if (isXiaomi) "Сторож активен (Xiaomi)" else "Сторож активен")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .build()

        startForeground(1, notification)

        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BTBlocker::WakeLock")
        wakeLock?.acquire(24 * 60 * 60 * 1000L)

        // Сразу применяем настройки Xiaomi при запуске
        if (isXiaomi) {
            RootManager.run("settings put global ble_scan_always_enabled 0")
            RootManager.run("settings put global miui_bluetooth_auto_connect 0")
            RootManager.run("settings put global miui_bluetooth_scan 0")
        }

        handler.post(tick)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isRunning = false
        handler.removeCallbacks(tick)
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }
}
