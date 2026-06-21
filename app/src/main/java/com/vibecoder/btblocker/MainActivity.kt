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
import android.widget.EditText
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
    private lateinit var btnParentalZone: Button
    private lateinit var tvParentalStatus: TextView

    private var currentAnswer = 0

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
        btnParentalZone = findViewById(R.id.btnParentalZone)
        tvParentalStatus = findViewById(R.id.tvParentalStatus)

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
        updateParentalZoneState()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        switchBlock.setOnCheckedChangeListener { _, checked ->
            if (Prefs.isParentalZoneActive(this)) {
                switchBlock.isChecked = Prefs.isBlocked(this)
                Toast.makeText(this, "🔒 Переключатель заблокирован родительской зоной", Toast.LENGTH_SHORT).show()
                return@setOnCheckedChangeListener
            }

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
            if (Prefs.isParentalZoneActive(this)) {
                switchHard.isChecked = Prefs.isHardMode(this)
                Toast.makeText(this, "🔒 Жёсткий режим заблокирован родительской зоной", Toast.LENGTH_SHORT).show()
                return@setOnCheckedChangeListener
            }

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
            openBatterySettings()
        }

        btnSafeDelete.setOnClickListener {
            if (Prefs.isParentalZoneActive(this)) {
                Toast.makeText(this, "🔒 Удаление заблокировано родительской зоной", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showSafeDeleteDialog()
        }

        btnParentalZone.setOnClickListener {
            showParentalZoneDialog()
        }

        updateBatteryButton()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
        updateBatteryButton()
        updateParentalZoneState()
    }

    private fun showParentalZoneDialog() {
        val isActive = Prefs.isParentalZoneActive(this)

        val num1 = (2..9).random()
        val num2 = (2..9).random()
        currentAnswer = num1 * num2

        val actionText = if (isActive) "отключить" else "активировать"

        val editText = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            hint = "Ваш ответ"
            setPadding(48, 32, 48, 32)
            textSize = 18f
        }

        AlertDialog.Builder(this)
            .setTitle(" Родительская проверка")
            .setMessage("Решите пример, чтобы $actionText родительскую зону:\n\n$num1 × $num2 = ?")
            .setView(editText)
            .setPositiveButton("Проверить") { _, _ ->
                val userAnswer = editText.text.toString().toIntOrNull()
                if (userAnswer == currentAnswer) {
                    Prefs.setParentalZoneActive(this, !isActive)
                    updateParentalZoneState()
                    val msg = if (!isActive) {
                        "✅ Правильно! Родительская зона активирована"
                    } else {
                        "✅ Правильно! Родительская зона отключена"
                    }
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "❌ Неправильно! Правильный ответ: $currentAnswer", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun updateParentalZoneState() {
        val isActive = Prefs.isParentalZoneActive(this)

        if (isActive) {
            btnParentalZone.text = "🔓 Разблокировать переключатели и удаление"
            btnParentalZone.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            tvParentalStatus.text = "Родительская зона: АКТИВНА 🔒"
            tvParentalStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            
            switchBlock.isEnabled = false
            switchHard.isEnabled = false
            btnSafeDelete.isEnabled = false
        } else {
            btnParentalZone.text = "🔒 Блокировать переключатели и удаление"
            btnParentalZone.setBackgroundColor(ContextCompat.getColor(this, 0xFF9C27B0.toInt()))
            tvParentalStatus.text = "Родительская зона: выключена"
            tvParentalStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            
            switchBlock.isEnabled = RootManager.checkRoot()
            switchHard.isEnabled = switchBlock.isChecked
            btnSafeDelete.isEnabled = true
        }
    }

    private fun updateBatteryButton() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val isIgnoring = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pm.isIgnoringBatteryOptimizations(packageName)
        } else false

        btnBattery.text = if (isIgnoring) "✅ Уже в исключениях" else "🔋 Разрешить игнорировать батарею"
        btnBattery.isEnabled = !isIgnoring
    }

    private fun openBatterySettings() {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
                Toast.makeText(this, "Найди 'Контроль активности' → 'Без ограничений'", Toast.LENGTH_LONG).show()
            } catch (e2: Exception) {
                Toast.makeText(this, "Открой настройки приложения вручную", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showSafeDeleteDialog() {
        AlertDialog.Builder(this)
            .setTitle("️ Безопасное удаление")
            .setMessage("Это безопасное удаление. Мы восстановим Bluetooth, прежде чем удалить приложение.\n\n" +
                    "Не рекомендуем удалять вручную, если вы не выключили блокировку Bluetooth, " +
                    "иначе вы потеряете Bluetooth навсегда и переустановка приложения может не помочь.\n\n" +
                    "Продолжить?")
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
