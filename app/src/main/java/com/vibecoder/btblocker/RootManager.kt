package com.vibecoder.btblocker

import java.io.DataOutputStream

object RootManager {

    fun checkRoot(): Boolean {
        return try {
            val p = Runtime.getRuntime().exec("su -c id")
            val code = p.waitFor()
            code == 0
        } catch (e: Exception) {
            false
        }
    }

    fun run(cmd: String): Boolean {
        return try {
            val p = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(p.outputStream)
            os.writeBytes("$cmd\n")
            os.writeBytes("exit\n")
            os.flush()
            p.waitFor() == 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}