package com.sambat

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.Context.BATTERY_SERVICE
import kotlin.math.abs

class BatteryRepository(private val context: Context) {

    // Shizuku-Enabled Implementation
    suspend fun getBatteryInfo(): BatteryInfo {
        return getRealtimeStatsWithShizuku()
    }
    
    data class RealtimeData(
        val voltage: Int, 
        val current: Int, 
        val power: Double,
        val temp: Int, 
        val status: String,
        val statusInt: Int, 
        val level: Int
    )

    suspend fun getRealtimeStats(): RealtimeData {
        return withContext(Dispatchers.IO) {
             var voltage = 0
             var temp = 0
             var level = 0
             var statusStr = "알 수 없음"
             var status = -1
             
             // 1. Direct Sticky Intent Poll
             val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
             val batteryStatus = context.registerReceiver(null, intentFilter)
             
             if (batteryStatus != null) {
                 voltage = batteryStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
                 temp = batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
                 level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
                 
                 val s = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                 status = s
                 val plugged = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
                 statusStr = mapStatusAida64(s, plugged)
             }
             
             // 2. Get Current via BatteryManager API
             val batteryManager = context.getSystemService(BATTERY_SERVICE) as BatteryManager
             var currentRaw = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
             
             // 3. Normalized Magnitude
             var magnitude = 0
             if (currentRaw != Int.MIN_VALUE && currentRaw != Int.MAX_VALUE) {
                 magnitude = if (abs(currentRaw) > 10000) {
                     abs(currentRaw) / 1000
                 } else {
                     abs(currentRaw)
                 }
             }
             
             // 4. Sign Logic (WYSIWYG - Text Based)
             val isDischarging = statusStr.contains("방전")
             val sign = if (isDischarging) -1 else 1
             
             val current = magnitude * sign
             
             // 5. Power Calculation (Centralized)
             val powerAbs = (voltage.toDouble() * kotlin.math.abs(current.toDouble())) / 1000000.0
             val powerSign = if (statusStr.contains("방전")) -1 else 1
             val power = powerAbs * powerSign
             
             RealtimeData(voltage, current, power, temp, statusStr, status, level)
        }
    }
    
    private fun mapStatusAida64(status: Int, plugged: Int): String {
        return when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> {
                when (plugged) {
                    BatteryManager.BATTERY_PLUGGED_AC -> "충전 중 (AC)"
                    BatteryManager.BATTERY_PLUGGED_USB -> "충전 중 (USB)"
                    BatteryManager.BATTERY_PLUGGED_WIRELESS -> "충전 중 (무선)"
                    else -> "충전 중"
                }
            }
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "방전 중"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "충전 안 함"
            BatteryManager.BATTERY_STATUS_FULL -> "완충됨"
            else -> "알 수 없음"
        }
    }

    private suspend fun getRealtimeStatsWithShizuku(): BatteryInfo {
        return withContext(Dispatchers.IO) {
            val rt = getRealtimeStats()
             
             // Debug Log
             val debugSb = StringBuilder()
             debugSb.append("Full Mode (Shizuku)\n")
             
             val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
             val batteryStatus = context.registerReceiver(null, intentFilter)
             
             var health = "알 수 없음"
             var tech = "Li-ion"
             
             if (batteryStatus != null) {
                val healthInt = batteryStatus.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)
                health = mapHealth(healthInt)
                tech = batteryStatus.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "Li-ion"
             }

             // Shizuku Extraction
             var shizukuCycles = -1
             var shizukuChargeCounter = -1
             var shizukuRawOutput = ""
             
             if (ShizukuHelper.isShizukuAvailable()) {
                 try {
                     val output = ShizukuHelper.executeShellCommand("dumpsys battery")
                     shizukuRawOutput = output
                     output.lines().forEach { line ->
                         val trim = line.trim()
                         if (trim.startsWith("Charge counter:")) {
                             shizukuChargeCounter = trim.substringAfter(":").trim().toIntOrNull() ?: -1
                         }
                         if (trim.startsWith("Cycle count:")) { // AOSP default
                             val temp = trim.substringAfter(":").trim().toIntOrNull()
                             if (temp != null) shizukuCycles = temp
                         }
                        if (trim.startsWith("mSavedBatteryUsage:")) {
                            // Example: mSavedBatteryUsage: [28600] -> 286 cycles
                            val inside = trim.substringAfter("[").substringBefore("]")
                            val raw = inside.toIntOrNull()
                            if (raw != null && shizukuCycles <= 0) {
                                shizukuCycles = raw / 100
                            }
                        }
                     }
                     debugSb.append("Shizuku OK. Cycles: $shizukuCycles\n")
                 } catch (e: Exception) {
                     debugSb.append("Shizuku Fail: ${e.message}\n")
                 }
             } else {
                 debugSb.append("Shizuku Not Available (Binder Dead)\n")
             }

             val batteryManager = context.getSystemService(BATTERY_SERVICE) as BatteryManager
             val chargeCounterRaw = if (shizukuChargeCounter > 0) shizukuChargeCounter else batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
             
             val chargeCounterMah = if (chargeCounterRaw > 100000) {
                 chargeCounterRaw / 1000
             } else {
                 chargeCounterRaw
             }
             
             var designCap = -1
             try {
                 val powerProfileClass = Class.forName("com.android.internal.os.PowerProfile")
                 val powerProfile = powerProfileClass.getConstructor(Context::class.java).newInstance(context)
                 val getAvailableCapacity = powerProfileClass.getMethod("getBatteryCapacity")
                 val capacityDouble = getAvailableCapacity.invoke(powerProfile) as Double
                 designCap = capacityDouble.toInt()
             } catch (e: Exception) {
                 e.printStackTrace()
             }
             
             // Cycle Strategy: Shizuku -> API -> File
             var finalCycles = shizukuCycles
             
             if (finalCycles <= 0) {
                 if (batteryStatus != null && batteryStatus.hasExtra("android.os.extra.CYCLE_COUNT")) {
                      finalCycles = batteryStatus.getIntExtra("android.os.extra.CYCLE_COUNT", -1)
                 }
             }
             
             if (finalCycles <= 0) {
                 val cyclePaths = listOf(
                      "/proc/battery/cycle_count",
                      "/proc/bms/cycle_count",
                      "/sys/class/power_supply/bms/cycle_count"
                 )
                 for (path in cyclePaths) {
                      try {
                          val fis = java.io.FileInputStream(path)
                          val content = fis.bufferedReader().use { it.readText().trim() }
                          val cycles = content.toIntOrNull()
                          if (cycles != null && cycles > 0) {
                              finalCycles = cycles
                              break
                          }
                      } catch (e: Exception) { }
                 }
             }
             
             var usableFullCap = 0.0
             if (rt.level > 0 && chargeCounterMah > 0) {
                 usableFullCap = (chargeCounterMah.toDouble() / rt.level) * 100.0
             }
             
             // Power (Use computed)
             val powerW = rt.power
             
             var soh = 0.0
             if (designCap > 0 && usableFullCap > 0) {
                 soh = (usableFullCap / designCap) * 100.0
             }

            BatteryInfo(
                level = rt.level,
                status = rt.status,
                health = health,
                technology = tech,
                temperature = rt.temp, 
                voltage = rt.voltage,
                cycleCount = finalCycles,
                designCapacity = designCap, 
                chargeCounter = chargeCounterMah, 
                currentAverage = usableFullCap.toInt(), 
                usableCapacityPercentage = soh, 
                currentNow = rt.current,
                power = powerW,
                debugLog = debugSb.toString(),
                details = shizukuRawOutput
            )
        }
    }
    
    private fun mapHealth(i: Int): String {
         return when(i) {
             2 -> "좋음"
             3 -> "과열"
             4 -> "수명 다됨"
             5 -> "과전압"
             7 -> "저온"
             else -> "알 수 없음" 
         }
    }
}
