package com.vibecoder.btblocker

import java.io.DataOutputStream
import java.io.BufferedReader
import java.io.InputStreamReader

object RootManager {
    private var hasRoot: Boolean? = null
    private var rootGranted: Boolean = false
    
    fun checkRoot(): Boolean {
        if (hasRoot != null) return hasRoot!!
        
        hasRoot = try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val result = reader.readLine()
            process.waitFor()
            result != null && result.contains("uid=0")
        } catch (e: Exception) {
            false
        }
        return hasRoot!!
    }
    
    fun run(cmd: String): Boolean {
        if (!rootGranted && !checkRoot()) return false
        
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val errorReader = BufferedReader(InputStreamReader(process.errorStream))
            
            // Читаем вывод чтобы процесс не завис
            while (reader.readLine() != null) {}
            while (errorReader.readLine() != null) {}
            
            val exitCode = process.waitFor()
            rootGranted = (exitCode == 0)
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
        
        // Перезапускаем SystemUI чтобы изменения применились
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
        
        // Перезапускаем SystemUI
        run("pkill -f com.android.systemui")
    }
}
