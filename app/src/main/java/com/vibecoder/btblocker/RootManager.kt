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

    fun disableAllBluetoothPackages() {
        val packages = listOf(
            "com.android.bluetooth",
            "com.android.bluetooth.midiservice",
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
        run("svc bluetooth disable")
        run("settings put global bluetooth_on 0")
    }

    fun enableAllBluetoothPackages() {
        // 1. Включаем все пакеты Bluetooth
        val packages = listOf(
            "com.android.bluetooth",
            "com.android.bluetooth.midiservice",
            "com.xiaomi.bluetooth",
            "com.miui.bluetooth",
            "com.samsung.bluetooth",
            "com.huawei.bluetooth"
        )
        packages.forEach { pkg ->
            run("pm enable $pkg 2>/dev/null")
            run("pm enable --user 0 $pkg 2>/dev/null")
        }

        // 2. Сбрасываем настройки Bluetooth
        run("settings put global bluetooth_on 1")
        run("settings delete global bluetooth_disabled_profiles 2>/dev/null")
        run("settings delete global ble_scan_always_enabled 2>/dev/null")
        run("settings delete system bluetooth_enable_default 2>/dev/null")

        // 3. Останавливаем и запускаем службы Bluetooth
        run("am force-stop com.android.bluetooth 2>/dev/null")
        run("am startservice -n com.android.bluetooth/.btservice.AdapterService 2>/dev/null")

        // 4. Очищаем кэш Bluetooth
        run("pm clear com.android.bluetooth 2>/dev/null")

        // 5. Перезапускаем SystemUI
        run("pkill -f com.android.systemui 2>/dev/null")
        run("am start -n com.android.systemui/.SystemUIApplication 2>/dev/null")

        // 6. Ждём стабилизации
        try {
            Thread.sleep(2000)
        } catch (e: Exception) {}

        // 7. Включаем Bluetooth через svc
        run("svc bluetooth enable")
    }

    fun quickDisableBluetooth() {
        run("svc bluetooth disable")
        run("settings put global bluetooth_on 0")
        run("am force-stop com.android.bluetooth 2>/dev/null")
    }
}
