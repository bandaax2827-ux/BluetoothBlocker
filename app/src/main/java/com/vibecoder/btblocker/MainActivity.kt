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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.materialswitch.MaterialSwitch

class MainActivity : AppCompatActivity() {
    private lateinit var switchBlock: MaterialSwitch
    private lateinit var switchHard: MaterialSwitch
    private lateinit var tvStatus: TextView
    private lateinit var tvRoot: TextView
    private lateinit var btnBattery: Button
    private lateinit var btnSafeDelete: Button

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
        btnBattery = findViewById(R.id.btnBattery)
        btnSafeDelete = findViewById(R.id.btnSafeDelete)

        // Проверка root
        val hasRoot = RootManager.checkRoot()
        tvRoot.text = if (hasRoot) "✅ Root получен" else "❌ Root не найден"
        tvRoot.setTextColor(ContextCompat.getColor(this,
            if (hasRoot) android.R.color.holo_green_dark else android.R.color.holo_red_dark))

        if (!hasRoot) {
            switchBlock.isEnabled = false
            switchHard.isEnabled = false
        }

        // Загрузка состояния
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
            if (!RootManager.checkRoot()) {
                switchBlock.isChecked = false
                Toast.makeText(this, "Нужен root!", Toast.LENGTH_SHORT).show()
                return@setOnCheckedChangeListener
            }
            Prefs.setBlocked(this, checked)
            switchHard.isEnabled = checked
            if (checked) startBlocker() else stopBlocker()
            updateStatus()
        }

        // Жёсткий режим
        switchHard.setOnCheckedChangeListener { _, checked ->
            Prefs.setHardMode(this, checked)
            if (checked) {
                RootManager.disableAllBluetoothPackages()
                Toast.makeText(this, "Жёсткий режим активирован", Toast.LENGTH_SHORT).show()
            } else {
                RootManager.enableAllBluetoothPackages()
                Toast.makeText(this, "Жёсткий режим отключён. Bluetooth восстановлен.", Toast.LENGTH_SHORT).show()
            }
            updateStatus()
        }

        // Кнопка батареи
        btnBattery.setOnClickListener {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Не удалось открыть настройки", Toast.LENGTH_SHORT).show()
            }
        }

        // Кнопка безопасного удаления
        btnSafeDelete.setOnClickListener {
            showSafeDeleteDialog()
        }
    }

    private fun showSafeDeleteDialog() {
        AlertDialog.Builder(this)
            .setTitle("⚠️ Безопасное удаление")
            .setMessage("Мы восстановим Bluetooth перед удалением приложения. Продолжить?")
            .setPositiveButton("Удалить") { _, _ ->
                performSafeDelete()
            }
            .setNegativeButton("Отмена") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun performSafeDelete() {
        // Восстанавливаем Bluetooth
        try {
            RootManager.enableAllBluetoothPackages()
            Prefs.setBlocked(this, false)
            Prefs.setHardMode(this, false)
            stopBlocker()
            Toast.makeText(this, "Bluetooth восстановлен. Удаляем приложение...", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка восстановления Bluetooth", Toast.LENGTH_SHORT).show()
        }

        // Запускаем удаление приложения
        try {
            val intent = Intent(Intent.ACTION_DELETE).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Не удалось запустить удаление", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Если приложение закрывается и был жёсткий режим — восстанавливаем Bluetooth
        if (Prefs.isHardMode(this)) {
            RootManager.enableAllBluetoothPackages()
            Prefs.setHardMode(this, false)
        }
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

    private fun startBlocker() {
        val intent = Intent(this, BlockerService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopBlocker() {
        stopService(Intent(this, BlockerService::class.java))
    }
}
