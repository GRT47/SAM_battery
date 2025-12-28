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

    // Full fetch (Static Data) - Still uses Shizuku for detailed stats
    suspend fun getBatteryInfo(): BatteryInfo {
        return withContext(Dispatchers.IO) {
            val dumpsysOutput = ShizukuHelper.executeShellCommand("dumpsys battery")
            val capacityOutput = ShizukuHelper.executeShellCommand("dumpsys batterystats | grep Capacity")
            
            // For checking initial V/I, we can reuse the API logic now!
            val rt = getRealtimeStats()

            // Parse legacy dumpsys for Samsung cycles if stuck, but now we prefer API-only path for consistency
            parse(dumpsysOutput, capacityOutput, rt.voltage, rt.current, rt.temp, rt.status, rt.level)
        }
    }
    
    data class RealtimeData(
        val voltage: Int, 
        val current: Int, 
        val temp: Int, 
        val status: String,
        val level: Int
    )

    // V34: Direct Sticky Intent Polling (Fundamental Fix)
    // No more caching. No more background listeners.
    // We fetch the absolute latest system state synchronously every time.
    
    // Lightweight fetch (Voltage/Current/Temp/Status) - API Version with AIDA64 Logic
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
             
             // 4. Sign Logic (Text-Based from the SNAPSHOT we just captured)
             val isDischarging = statusStr.contains("방전")
             val sign = if (isDischarging) -1 else 1
             
             val current = magnitude * sign
 
             // V18 Simple Mode: Follow the API raw sign directly (User Request)
             RealtimeData(voltage, current, temp, statusStr, level)
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

    suspend fun getBatteryInfoApiOnly(): BatteryInfo {
        return withContext(Dispatchers.IO) {
            val rt = getRealtimeStats()
             
             // Debug Log Builder
             val debugSb = StringBuilder()
             debugSb.append("TargetSDK: ${context.applicationInfo.targetSdkVersion}\n")
             
             // Get other props (Health, Tech)
             val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
             val batteryStatus = context.registerReceiver(null, intentFilter)
             
             var health = "알 수 없음"
             var tech = "Li-ion"
             var intentCycles = -1
             
             if (batteryStatus != null) {
                val healthInt = batteryStatus.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)
                health = mapHealth(healthInt)
                tech = batteryStatus.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "Li-ion"
                
                // Check Intent Keys (ALL)
                debugSb.append("Intent Keys: ")
                if (batteryStatus.extras != null) {
                    for (key in batteryStatus.extras!!.keySet()) {
                        val value = batteryStatus.extras!!.get(key)
                        debugSb.append("[$key] (${value?.javaClass?.simpleName})=$value\n")
                    }
                }
                debugSb.append("\n")

                if (batteryStatus.hasExtra("charge_count")) intentCycles = batteryStatus.getIntExtra("charge_count", -1)
                if (intentCycles == -1 && batteryStatus.hasExtra("battery_cycle_count")) intentCycles = batteryStatus.getIntExtra("battery_cycle_count", -1)
                if (intentCycles == -1 && batteryStatus.hasExtra("cycle_count")) intentCycles = batteryStatus.getIntExtra("cycle_count", -1)
             }
             
             val batteryManager = context.getSystemService(BATTERY_SERVICE) as BatteryManager
             val chargeCounterRaw = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
             
             // AIDA64 Logic: Auto-detect unit (uAh vs mAh)
             // Typical phone: 3000-5000 mAh = 3,000,000 - 5,000,000 uAh.
             // If value is > 100,000, it's likely uAh. (100,000 uAh = 100 mAh - too small for phone)
             // If value is < 100,000, it's likely mAh. (100,000 mAh is huge, so it must be 4000 etc.)
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
             
             // V13 Final Strategy:
             // 1. TargetSDK 35 unlocks 'android.os.extra.CYCLE_COUNT' in the ACTION_BATTERY_CHANGED intent.
             // 2. We read this directly. This avoids the 'Permission denied' error of getIntProperty(8).
             var finalCycles = -1
             
             if (finalCycles == -1) {
                 if (batteryStatus != null) {
                     // Try standard API 34 constant (if compiled against it) or string literal
                     if (batteryStatus.hasExtra("android.os.extra.CYCLE_COUNT")) {
                         finalCycles = batteryStatus.getIntExtra("android.os.extra.CYCLE_COUNT", -1)
                         debugSb.append("V13: Read Extra Success -> $finalCycles\n")
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
             
             // V33 Power Logic: Use the same verified sign
             val powerAbs = (rt.voltage.toDouble() * kotlin.math.abs(rt.current.toDouble())) / 1000000.0
             val powerSign = if (rt.current < 0) -1 else 1 // rt.current is already set by V33 logic
             val powerW = powerAbs * powerSign
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

    private fun parse(dumpsys: String, capacityRaw: String, voltageVal: Int, currentVal: Int, tempVal: Int, statusVal: String, levelVal: Int): BatteryInfo {
        var level = levelVal
        var status = statusVal
        var health = "알 수 없음"
        var tech = ""
        var temp = tempVal
        var voltage = voltageVal
        var chargeCounter = 0
        var currentNow = currentVal
        
        var samsungUsage = -1
        
        dumpsys.lineSequence().forEach { line ->
            val trimmed = line.trim()
            val lower = trimmed.lowercase()

            when {
                trimmed.startsWith("health:") -> {
                     val healthInt = trimmed.substringAfter(":").trim().toIntOrNull() ?: 1
                     health = mapHealth(healthInt)
                }
                trimmed.startsWith("technology:") -> tech = trimmed.substringAfter(":").trim()
                lower.startsWith("charge counter:") -> {
                    chargeCounter = trimmed.substringAfter(":").trim().toIntOrNull() ?: 0
                }
                trimmed.contains("mSavedBatteryUsage") -> {
                     val match = Regex("""\d+""").find(trimmed.substringAfter("mSavedBatteryUsage"))
                     if (match != null) samsungUsage = match.value.toInt()
                }
            }
        }
        
        var designCap = -1
        if (capacityRaw.isNotEmpty()) {
            val match = Regex("""\d+""").find(capacityRaw)
            if (match != null) {
                designCap = match.value.toInt()
            }
        }

        var finalCycles = -1
        if (samsungUsage != -1) {
            finalCycles = samsungUsage / 100
        }

        val currentMah = chargeCounter / 1000.0
        var usableFullCap = 0.0
        
        if (level > 0 && currentMah > 0) {
            usableFullCap = (currentMah / level) * 100.0
        }
        
        var finalSoh = 0.0
        if (designCap > 0 && usableFullCap > 0) {
            finalSoh = (usableFullCap / designCap) * 100.0
        }
        
        val powerW = (voltage.toDouble() * currentNow.toDouble()) / 1000000.0

        return BatteryInfo(
            level = level,
            status = status,
            health = health,
            technology = tech,
            temperature = temp, 
            voltage = voltage,
            cycleCount = finalCycles,
            designCapacity = designCap, 
            chargeCounter = currentMah.toInt(), 
            currentAverage = usableFullCap.toInt(), 
            usableCapacityPercentage = finalSoh,
            currentNow = currentNow,
            power = powerW
        )
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
