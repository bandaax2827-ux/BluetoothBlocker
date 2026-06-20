package com.vibecoder.btblocker

import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader

object RootManager {
    private var rootAvailable: Boolean? = null
    private var rootGranted: Boolean = false

    fun checkRoot(): Boolean {
        rootAvailable?.let { return it }
        rootAvailable = try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val result = reader.readLine()
            process.waitFor()
            val hasRoot = result != null && result.contains("uid=0")
            if (hasRoot) rootGranted = true
            hasRoot
        } catch (e: Exception) {
            false
        }
        return rootAvailable!!
    }

    fun run(cmd: String): Boolean {
        if (!checkRoot()) return false
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            val outReader = BufferedReader(InputStreamReader(process.inputStream))
            val errReader = BufferedReader(InputStreamReader(process.errorStream))
            while (outReader.readLine() != null) {}
            while (errReader.readLine() != null) {}
            val exitCode = process.waitFor()
            if (exitCode == 0) rootGranted = true
            exitCode == 0
        } catch (e: Exception) {
            false
        }
    }

    fun disableAllBluetoothPackages() {
        // 1. Отключаем все Bluetooth-пакеты
        val packages = listOf(
            "com.android.bluetooth",
            "com.android.bluetooth.midipackage",
            "com.xiaomi.bluetooth",
            "com.miui.bluetooth",
            "com.samsung.bluetooth",
            "com.huawei.bluetooth"
        )
        packages.forEach { pkg ->
            run("pm disable-user --user 0 $pkg 2>/dev/null")
            run("pm disable $pkg 2>/dev/null")
            run("am force-stop $pkg 2>/dev/null")
        }

        // 2. Пытаемся скрыть Bluetooth из настроек (работает на некоторых устройствах)
        run("settings put global bluetooth_disabled_profiles 1 2>/dev/null")
        run("settings put secure bluetooth_show_switch 0 2>/dev/null")
        run("settings put system bluetooth_show_switch 0 2>/dev/null")
        run("settings put global bluetooth_name ' ' 2>/dev/null")

        // 3. Пытаемся убрать Bluetooth из Quick Settings (шторки)
        // Сохраняем текущие тайлы и убираем bluetooth
        run("""
            TILES=$(settings get secure sysui_qs_tiles)
            NEW_TILES=$(echo "$TILES" | sed 's/bt(.*),//g; s/,bt(.*)//g; s/bt(.*)//g')
            settings put secure sysui_qs_tiles "$NEW_TILES"
        """.trimIndent())

        // 4. Выключаем Bluetooth принудительно
        run("svc bluetooth disable")
        run("settings put global bluetooth_on 0")

        // 5. Перезапускаем SystemUI чтобы изменения применились
        run("pkill -f com.android.systemui")
        run("settings put global development_settings_enabled 0 2>/dev/null")
    }

    fun enableAllBluetoothPackages() {
        val packages = listOf(
            "com.android.bluetooth",
            "com.android.bluetooth.midipackage",
            "com.xiaomi.bluetooth",
            "com.miui.bluetooth",
            "com.samsung.bluetooth",
            "com.huawei.bluetooth"
        )
        packages.forEach { pkg ->
            run("pm enable $pkg 2>/dev/null")
            run("pm enable --user 0 $pkg 2>/dev/null")
        }

        // Восстанавливаем настройки
        run("settings put global bluetooth_disabled_profiles 0 2>/dev/null")
        run("settings delete secure bluetooth_show_switch 2>/dev/null")
        run("settings delete system bluetooth_show_switch 2>/dev/null")
        run("settings delete global bluetooth_name 2>/dev/null")

        // Включаем Bluetooth
        run("svc bluetooth enable")
        run("settings put global bluetooth_on 1")

        // Перезапускаем SystemUI
        run("pkill -f com.android.systemui")
    }
}
