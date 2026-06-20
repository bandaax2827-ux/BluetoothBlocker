package com.vibecoder.btblocker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.snackbar.Snackbar

class MainActivity : AppCompatActivity() {
    private lateinit var switchBlock: MaterialSwitch
    private lateinit var switchHard: MaterialSwitch
    private lateinit var tvStatus: TextView
    private lateinit var tvRoot: TextView
    private lateinit var btnGrantBattery: Button
    
    private val notifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        switchBlock = findViewById(R.id.switchBlock)
        switchHard = findViewById(R.id.switchHard)
        tvStatus = findViewById(R.id.tvStatus)
        tvRoot = findViewById(R.id.tvRoot)
        btnGrantBattery = findViewById(R.id.btnBattery)
        
        // Проверяем root один раз
        val hasRoot = RootManager.checkRoot()
        tvRoot.text = if (hasRoot) "✅ Root получен" else "❌ Root не найден"
        tvRoot.setTextColor(ContextCompat.getColor(this,
            if (hasRoot) android.R.color.holo_green_dark else android.R.color.holo_red_dark))
        
        if (!hasRoot) {
            switchBlock.isEnabled = false
            switchHard.isEnabled = false
            Toast.makeText(this, "Нужен root доступ!", Toast.LENGTH_LONG).show()
            return
        }
        
        // Загружаем сохранённое состояние
        val blocked = Prefs.isBlocked(this)
        val hard = Prefs.isHardMode(this)
        switchBlock.isChecked = blocked
        switchHard.isChecked = hard
        switchHard.isEnabled = blocked
        updateStatus()
        
        // Запрос уведомлений (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        
        // Главный переключатель
        switchBlock.setOnCheckedChangeListener { _, checked ->
            Prefs.setBlocked(this, checked)
            switchHard.isEnabled = checked
            
            if (checked) {
                // Запускаем сервис
                val intent = Intent(this, BlockerService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
                Toast.makeText(this, "Сторож активирован", Toast.LENGTH_SHORT).show()
            } else {
                // Останавливаем сервис
                stopService(Intent(this, BlockerService::class.java))
                Toast.makeText(this, "Сторж деактивирован", Toast.LENGTH_SHORT).show()
            }
            updateStatus()
        }
        
        // Жёсткий режим
        switchHard.setOnCheckedChangeListener { _, checked ->
            Prefs.setHardMode(this, checked)
            
            if (checked) {
                RootManager.disableAllBluetoothPackages()
                Snackbar.make(btnGrantBattery,
                    "Жёсткий режим активирован. Bluetooth отключён системно.",
                    Snackbar.LENGTH_LONG).setAction("OK"){}.show()
            } else {
                RootManager.enableAllBluetoothPackages()
                Snackbar.make(btnGrantBattery,
                    "Жёсткий режим отключён. Bluetooth восстановлен.",
                    Snackbar.LENGTH_LONG).setAction("OK"){}.show()
            }
            updateStatus()
        }
        
        // Кнопка батареи
        btnGrantBattery.setOnClickListener {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Не удалось открыть настройки", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        updateStatus()
    }
    
    private fun updateStatus() {
        val blocked = Prefs.isBlocked(this)
        val hard = Prefs.isHardMode(this)
        tvStatus.text = when {
            !blocked -> "Bluetooth: свободен"
            blocked && hard -> "Bluetooth: ЗАБЛОКИРОВАН (жёсткий режим)"
            else -> "Bluetooth: ЗАБЛОКИРОВАН (сторож)"
        }
    }
}
