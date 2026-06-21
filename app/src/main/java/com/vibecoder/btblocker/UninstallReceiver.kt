package com.vibecoder.btblocker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class UninstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_PACKAGE_REMOVED ||
            intent.action == Intent.ACTION_PACKAGE_FULLY_REMOVED) {
            
            val packageName = intent.data?.schemeSpecificPart
            if (packageName == context.packageName) {
                Log.d("UninstallReceiver", "Приложение удаляется, восстанавливаем Bluetooth...")
                RootManager.enableAllBluetoothPackages()
                RootManager.run("settings put global bluetooth_on 1")
                RootManager.run("svc bluetooth enable")
            }
        }
    }
}
