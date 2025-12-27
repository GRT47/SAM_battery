package com.example.batteryhealth

import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

object ShizukuHelper {

    fun isShizukuInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun isShizukuAvailable(): Boolean {
        // Shizuku.pingBinder() checks if the service is running
        return Shizuku.pingBinder()
    }

    fun hasPermission(): Boolean {
        if (!isShizukuAvailable()) return false
        return if (Shizuku.isPreV11()) {
            false // Simplified for now, assuming modern android
        } else {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }
    }

    fun requestPermission(requestCode: Int) {
        if (Shizuku.isPreV11()) {
            return
        }
        Shizuku.requestPermission(requestCode)
    }

    fun executeShellCommand(command: String): String {
        if (!hasPermission()) return "Error: No Shizuku Permission"

        return try {
            val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            process.waitFor()
            output.toString()
        } catch (e: Exception) {
            "Exception: ${e.message}"
        }
    }
}
