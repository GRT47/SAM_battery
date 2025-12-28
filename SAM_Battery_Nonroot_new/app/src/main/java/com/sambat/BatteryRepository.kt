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

    // Pure API implementation (No Shizuku)
    suspend fun getBatteryInfo(): BatteryInfo {
        return getRealtimeStatsApiOnly()
    }
    
    data class RealtimeData(
        val voltage: Int, 
        val current: Int, 
        val power: Double, // Added detailed Power field
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
             
             // 1. Direct Sticky Intent Poll (Single Source of Truth)
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
             // We use ABS(V*I) then apply the strict sign based on text
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

    private suspend fun getRealtimeStatsApiOnly(): BatteryInfo {
        return withContext(Dispatchers.IO) {
            val rt = getRealtimeStats()
             
             // Debug Log Builder
             val debugSb = StringBuilder()
             debugSb.append("API Mode (No Shizuku)\n")
             
             // Get other props (Health, Tech)
             val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
             val batteryStatus = context.registerReceiver(null, intentFilter)
             
             var health = "알 수 없음"
             var tech = "Li-ion"

             
             if (batteryStatus != null) {
                val healthInt = batteryStatus.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)
                health = mapHealth(healthInt)
                tech = batteryStatus.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "Li-ion"
                
                // Cycle logic moved below to match original version

             }
             
             val batteryManager = context.getSystemService(BATTERY_SERVICE) as BatteryManager
             val chargeCounterRaw = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
             
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
             
             // V13 Final Strategy (Ported):
             // 1. TargetSDK 35 unlocks 'android.os.extra.CYCLE_COUNT' in the ACTION_BATTERY_CHANGED intent.
             // 2. We read this directly. This avoids the 'Permission denied' error of getIntProperty(8).
             var finalCycles = -1
             
             if (finalCycles == -1) {
                 if (batteryStatus != null) {
                     // Try standard API 34 constant (if compiled against it) or string literal
                     if (batteryStatus.hasExtra("android.os.extra.CYCLE_COUNT")) {
                         finalCycles = batteryStatus.getIntExtra("android.os.extra.CYCLE_COUNT", -1)
                         // debugSb.append("V13: Read Extra Success -> $finalCycles\n")
                     }
                     
                     // Fallback check for other known keys just in case
                     if (finalCycles == -1) finalCycles = batteryStatus.getIntExtra("battery_cycle_count", -1)
                     if (finalCycles == -1) finalCycles = batteryStatus.getIntExtra("cycle_count", -1)
                 }
             }
             
             // 3. Fallback to API 34 Property (If broadcast missed it, though unlikely)
             if (finalCycles == -1 && android.os.Build.VERSION.SDK_INT >= 34) {
                 try {
                     val apiCycles = batteryManager.getIntProperty(8) // BATTERY_PROPERTY_CYCLE_COUNT
                     if (apiCycles > 0 && apiCycles != Int.MIN_VALUE) {
                         finalCycles = apiCycles
                     }
                 } catch (e: Exception) {
                     // Ignore permission errors as we likely got it from intent
                 }
             }
             
             // 4. Legacy File Fallback (Proc/Sys)
             if (finalCycles == -1) {
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
             
             // Power Calculation (Safe & Brute Force)
             val powerAbs = (rt.voltage.toDouble() * kotlin.math.abs(rt.current.toDouble())) / 1000000.0
             
             // Explicitly re-evaluate sign based on WYSIWYG logic (Text Check)
             // User Request: "If text says Discharging, force negative."
             val powerSignStr = if (rt.status.contains("방전")) -1 else 1
             val powerW = powerAbs * powerSignStr
             
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
                debugLog = debugSb.toString()
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
