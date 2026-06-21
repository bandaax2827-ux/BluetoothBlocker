package com.vibecoder.btblocker

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.snackbar.Snackbar

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

        val hasRoot = RootManager.checkRoot()
        tvRoot.text = if (hasRoot) "✅ Root получен" else "❌ Root не найден"
        tvRoot.setTextColor(ContextCompat.getColor(this,
            if (hasRoot) android.R.color.holo_green_dark else android.R.color.holo_red_dark))

        if (!hasRoot) {
            switchBlock.isEnabled = false
            switchHard.isEnabled = false
        }

        val blocked = Prefs.isBlocked(this)
        val hard = Prefs.isHardMode(this)
        switchBlock.isChecked = blocked
        switchHard.isChecked = hard
        switchHard.isEnabled = blocked
        updateStatus()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

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

        switchHard.setOnCheckedChangeListener { _, checked ->
            Prefs.setHardMode(this, checked)
            if (checked) {
                RootManager.disableAllBluetoothPackages()
                Snackbar.make(btnBattery,
                    "Жёсткий режим активирован. Чтобы вернуть — выключи этот режим.",
                    Snackbar.LENGTH_LONG).setAction("OK"){}.show()
            } else {
                RootManager.enableAllBluetoothPackages()
                Snackbar.make(btnBattery,
                    "Жёсткий режим отключён. Bluetooth восстановлен.",
                    Snackbar.LENGTH_LONG).setAction("OK"){}.show()
            }
            updateStatus()
        }

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

        btnSafeDelete.setOnClickListener {
            showSafeDeleteDialog()
        }
    }

    private fun showSafeDeleteDialog() {
        AlertDialog.Builder(this)
            .setTitle("⚠️ Безопасное удаление")
            .setMessage("Мы восстановим Bluetooth перед удалением приложения. Продолжить?")
            .setPositiveButton("Восстановить и удалить") { _, _ ->
                performSafeDelete()
            }
            .setNegativeButton("Отмена") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun performSafeDelete() {
        Toast.makeText(this, "Восстанавливаем Bluetooth...", Toast.LENGTH_SHORT).show()

        stopBlocker()
        Prefs.setBlocked(this, false)
        Prefs.setHardMode(this, false)

        RootManager.enableAllBluetoothPackages()
        RootManager.run("svc bluetooth enable")
        RootManager.run("settings put global bluetooth_on 1")

        Handler(Looper.getMainLooper()).postDelayed({
            Toast.makeText(this, "Bluetooth восстановлен. Удаляем приложение...", Toast.LENGTH_LONG).show()
            try {
                val intent = Intent(Intent.ACTION_DELETE).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Не удалось запустить удаление", Toast.LENGTH_SHORT).show()
            }
        }, 3000)
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    override fun onDestroy() {
        super.onDestroy()
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
