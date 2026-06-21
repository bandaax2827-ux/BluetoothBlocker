package com.vibecoder.btblocker

import java.io.BufferedReader
import java.io.InputStreamReader

object RootManager {
    private var rootAvailable: Boolean? = null
    private var rootGranted: Boolean = false

    fun checkRoot(): Boolean {
        if (rootAvailable != null) return rootAvailable!!

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

    // Определяем, Xiaomi ли это устройство
    private fun isXiaomi(): Boolean {
        val brand = android.os.Build.BRAND.lowercase()
        val manufacturer = android.os.Build.MANUFACTURER.lowercase()
        return brand.contains("xiaomi") || brand.contains("redmi") ||
               brand.contains("poco") || manufacturer.contains("xiaomi")
    }

    fun disableAllBluetoothPackages() {
        // 1. Стандартные пакеты Bluetooth
        val standardPackages = listOf(
            "com.android.bluetooth",
            "com.android.bluetooth.midipackage",
            "com.android.bluetooth.midiservice"
        )

        // 2. Специфичные пакеты для Xiaomi/Redmi/POCO
        val xiaomiPackages = listOf(
            "com.xiaomi.bluetooth",
            "com.miui.bluetooth",
            "com.xiaomi.bluetooth.overlay",
            "com.miui.contentcatcher",
            "com.miui.mms.bluetooth",
            "com.xiaomi.mibrain.speech",
            "com.xiaomi.scanner.bluetooth"
        )

        // 3. Пакеты для других производителей (на всякий случай)
        val otherPackages = listOf(
            "com.samsung.bluetooth",
            "com.huawei.bluetooth",
            "com.oppo.bluetooth",
            "com.vivo.bluetooth",
            "com.oneplus.bluetooth"
        )

        // Собираем список пакетов
        val packages = standardPackages.toMutableList()
        if (isXiaomi()) {
            packages.addAll(xiaomiPackages)
        }
        packages.addAll(otherPackages)

        // Отключаем все пакеты
        packages.forEach { pkg ->
            run("pm disable-user --user 0 $pkg 2>/dev/null")
            run("pm disable $pkg 2>/dev/null")
            run("am force-stop $pkg 2>/dev/null")
        }

        // 4. Стандартные команды отключения BT
        run("svc bluetooth disable")
        run("settings put global bluetooth_on 0")

        // 5. СПЕЦИАЛЬНЫЕ команды для Xiaomi (MIUI/HyperOS)
        if (isXiaomi()) {
            // Отключаем постоянное BLE-сканирование (Xiaomi любит включать BT через это)
            run("settings put global ble_scan_always_enabled 0")
            run("settings put secure ble_scan_always_enabled 0")
            
            // Отключаем BT по умолчанию
            run("settings put system bluetooth_enable_default 0")
            run("settings put global bluetooth_disabled_profiles 1")
            
            // Отключаем автозапуск BT-сервисов MIUI
            run("settings put global miui_bluetooth_auto_connect 0")
            run("settings put global miui_bluetooth_scan 0")
            
            // Блокируем MIUI Bluetooth Service
            run("am force-stop com.xiaomi.bluetooth 2>/dev/null")
            run("am force-stop com.miui.bluetooth 2>/dev/null")
            
            // Отключаем быстрые настройки BT в MIUI
            run("settings put secure sysui_qs_tiles \"\" 2>/dev/null")
            
            // Блокируем автоподключение Xiaomi BT устройств
            run("settings put global miui_bluetooth_quick_connect 0")
            run("settings put global miui_smart_connect_enabled 0")
        }

        // 6. Перезапускаем SystemUI, чтобы изменения применились
        run("pkill -f com.android.systemui")
    }

    fun enableAllBluetoothPackages() {
        // 1. Стандартные пакеты
        val standardPackages = listOf(
            "com.android.bluetooth",
            "com.android.bluetooth.midipackage",
            "com.android.bluetooth.midiservice"
        )

        // 2. Специфичные пакеты для Xiaomi/Redmi/POCO
        val xiaomiPackages = listOf(
            "com.xiaomi.bluetooth",
            "com.miui.bluetooth",
            "com.xiaomi.bluetooth.overlay",
            "com.miui.contentcatcher",
            "com.miui.mms.bluetooth",
            "com.xiaomi.mibrain.speech",
            "com.xiaomi.scanner.bluetooth"
        )

        // 3. Пакеты для других производителей
        val otherPackages = listOf(
            "com.samsung.bluetooth",
            "com.huawei.bluetooth",
            "com.oppo.bluetooth",
            "com.vivo.bluetooth",
            "com.oneplus.bluetooth"
        )

        // Собираем список пакетов
        val packages = standardPackages.toMutableList()
        if (isXiaomi()) {
            packages.addAll(xiaomiPackages)
        }
        packages.addAll(otherPackages)

        // Включаем все пакеты обратно
        packages.forEach { pkg ->
            run("pm enable $pkg 2>/dev/null")
            run("pm enable --user 0 $pkg 2>/dev/null")
        }

        // 4. Стандартные команды включения BT
        run("svc bluetooth enable")
        run("settings put global bluetooth_on 1")

        // 5. ВОССТАНАВЛИВАЕМ настройки Xiaomi
        if (isXiaomi()) {
            run("settings put global ble_scan_always_enabled 1")
            run("settings put secure ble_scan_always_enabled 1")
            run("settings put system bluetooth_enable_default 1")
            run("settings delete global bluetooth_disabled_profiles 2>/dev/null")
            run("settings delete global miui_bluetooth_auto_connect 2>/dev/null")
            run("settings delete global miui_bluetooth_scan 2>/dev/null")
            run("settings delete global miui_bluetooth_quick_connect 2>/dev/null")
            run("settings delete global miui_smart_connect_enabled 2>/dev/null")
        }

        // 6. Перезапускаем SystemUI
        run("pkill -f com.android.systemui")
    }

    // Быстрое отключение BT (для режима "Сторож")
    fun quickDisableBluetooth() {
        run("svc bluetooth disable")
        run("settings put global bluetooth_on 0")
        
        if (isXiaomi()) {
            run("settings put global ble_scan_always_enabled 0")
            run("am force-stop com.xiaomi.bluetooth 2>/dev/null")
            run("am force-stop com.miui.bluetooth 2>/dev/null")
        }
        
        run("am force-stop com.android.bluetooth 2>/dev/null")
    }
}
