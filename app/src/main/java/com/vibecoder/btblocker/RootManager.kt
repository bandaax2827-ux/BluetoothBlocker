package com.vibecoder.btblocker

import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader

object RootManager {
    private var rootAvailable: Boolean? = null
    private var rootGranted: Boolean = false

    fun checkRoot(): Boolean {
        // Если уже проверяли — возвращаем кэш
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
            // su -c "команда" — один процесс, один запрос прав
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            val outReader = BufferedReader(InputStreamReader(process.inputStream))
            val errReader = BufferedReader(InputStreamReader(process.errorStream))

            // Читаем вывод, чтобы процесс не завис
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
            "com.xiaomi.bluetooth",
            "com.miui.bluetooth",
            "com.samsung.bluetooth",
            "com.huawei.bluetooth"
        )
        packages.forEach { pkg ->
            run("pm disable-user --user 0 $pkg 2>/dev/null")
            run("pm disable $pkg 2>/dev/null")
        }
        run("pkill -f com.android.systemui")
    }

    fun enableAllBluetoothPackages() {
        val packages = listOf(
            "com.android.bluetooth",
            "com.xiaomi.bluetooth",
            "com.miui.bluetooth",
            "com.samsung.bluetooth",
            "com.huawei.bluetooth"
        )
        packages.forEach { pkg ->
            run("pm enable $pkg 2>/dev/null")
            run("pm enable --user 0 $pkg 2>/dev/null")
        }
        run("pkill -f com.android.systemui")
    }
}
