package com.vibecoder.btblocker

import java.io.BufferedReader
import java.io.InputStreamReader

object RootManager {
    private var rootChecked = false
    private var rootAvailable = false

    fun checkRoot(): Boolean {
        if (rootChecked) return rootAvailable

        rootAvailable = try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val result = reader.readLine()
            process.waitFor()
            result != null && result.contains("uid=0")
        } catch (e: Exception) {
            false
        }
        rootChecked = true
        return rootAvailable
    }

    fun run(cmd: String): Boolean {
        if (!checkRoot()) return false

        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            val outReader = BufferedReader(InputStreamReader(process.inputStream))
            val errReader = BufferedReader(InputStreamReader(process.errorStream))
            while (outReader.readLine() != null) {}
            while (errReader.readLine() != null) {}
            process.waitFor() == 0
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
        run("settings put secure enabled_accessibility_services '' 2>/dev/null")
        run("pkill -f com.android.systemui 2>/dev/null")
    }

    fun enableAllBluetoothPackages() {
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
        run("pkill -f com.android.systemui 2>/dev/null")
    }
}
