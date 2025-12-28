package com.example.batteryhealth

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

    // Lightweight fetch (Voltage/Current/Temp/Status) - API Version with AIDA64 Logic
    suspend fun getRealtimeStats(): RealtimeData {
        return withContext(Dispatchers.IO) {
             var voltage = 0
             var current = 0
             var temp = 0
             var statusStr = "알 수 없음"
             var level = 0
             
             // 1. Get Voltage, Temp, Status, Plugged via Intent (Sticky Broadcast)
             val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
             val batteryStatus = context.registerReceiver(null, intentFilter)
             
             if (batteryStatus != null) {
                 // Voltage (mV)
                 voltage = batteryStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
                 
                 // Temperature (tenths of °C)
                 temp = batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
                 
                 // Level (%)
                 level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
                 
                 // Status & Plugged -> AIDA64 Style String
                 val status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                 val plugged = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
                 statusStr = mapStatusAida64(status, plugged)
             }
             
             // 2. Get Current via BatteryManager API
             val batteryManager = context.getSystemService(BATTERY_SERVICE) as BatteryManager
             
             // AIDA64 Logic: Check both NOW and AVERAGE
             var currentRaw = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
             
             // V20 Strict Mode: Read CURRENT_NOW only. No Average fallback.
             // If CURRENT_NOW is 0, we report 0. (Prevents positive average polluting negative instant)
             
             if (currentRaw != Int.MIN_VALUE && currentRaw != Int.MAX_VALUE) {
                 current = if (abs(currentRaw) > 10000) {
                     currentRaw / 1000
                 } else {
                     currentRaw
                 }
             }
            
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

    // API-Only Fetch (No Shizuku)
    suspend fun getBatteryInfoApiOnly(): BatteryInfo {
        return withContext(Dispatchers.IO) {
            val rt = getRealtimeStats()
            
             // Get other props (Health, Tech) - Level/Status already in rt
             val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
             val batteryStatus = context.registerReceiver(null, intentFilter)
             
             var health = "알 수 없음"
             var tech = ""
             
             if (batteryStatus != null) {
                val healthInt = batteryStatus.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)
                health = mapHealth(healthInt)
                tech = batteryStatus.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "Li-ion"
             }
             
             val batteryManager = context.getSystemService(BATTERY_SERVICE) as BatteryManager
             val chargeCounter = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) // uAh
             val currentAverage = 0 // Calculated below
             
             // Design Capacity via PowerProfile (Reflection) - "Reported by Android" check
             var designCap = -1
             try {
                 val powerProfileClass = Class.forName("com.android.internal.os.PowerProfile")
                 val powerProfile = powerProfileClass.getConstructor(Context::class.java).newInstance(context)
                 val getAvailableCapacity = powerProfileClass.getMethod("getBatteryCapacity")
                 val capacityDouble = getAvailableCapacity.invoke(powerProfile) as Double
                 designCap = capacityDouble.toInt() // Usually returns mAh
             } catch (e: Exception) {
                 e.printStackTrace()
             }
             
             // Cycles not available via standard API
             val finalCycles = -1
             
             // Calc usable cap based on Charge Counter & Level
             // If ChargeCounter (uAh) is valid and Level > 0
             var usableFullCap = 0.0
             if (rt.level > 0 && chargeCounter > 0) {
                 val currentMah = chargeCounter / 1000.0
                 usableFullCap = (currentMah / rt.level) * 100.0
             }
             
             // Power
             val powerW = kotlin.math.abs((rt.voltage.toDouble() * rt.current.toDouble()) / 1000000.0)

             // Calculate SOH
             // Usable Full Cap (Estimated) / Design Cap * 100
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
                chargeCounter = (chargeCounter / 1000), 
                currentAverage = usableFullCap.toInt(), 
                usableCapacityPercentage = soh, 
                currentNow = rt.current,
                power = powerW
            )
        }
    }

    private fun parse(dumpsys: String, capacityRaw: String, voltageVal: Int, currentVal: Int, tempVal: Int, statusVal: String, levelVal: Int): BatteryInfo {
        var level = levelVal
        var status = statusVal
        var health = "알 수 없음"
        var tech = ""
        var temp = tempVal // Use API value
        var voltage = voltageVal // Use API value
        var chargeCounter = 0
        var currentNow = currentVal // Use API value
        
        // Samsung Specifics
        var samsungUsage = -1
        
        // Parse dumpsys battery
        dumpsys.lineSequence().forEach { line ->
            val trimmed = line.trim()
            val lower = trimmed.lowercase()

            when {
                // Level and Status already from API (Realtime), but dumpsys might have other info
                // We prefer API for real-time status as per user request
                trimmed.startsWith("level:") -> {
                    // level = trimmed.substringAfter(":").trim().toIntOrNull() ?: 0 
                    // Using API level instead
                }
                trimmed.startsWith("status:") -> {
                    // val statusInt = trimmed.substringAfter(":").trim().toIntOrNull() ?: 1
                    // status = mapStatus(statusInt)
                    // Using API status instead
                }
                trimmed.startsWith("health:") -> {
                     val healthInt = trimmed.substringAfter(":").trim().toIntOrNull() ?: 1
                     health = mapHealth(healthInt)
                }
                trimmed.startsWith("technology:") -> tech = trimmed.substringAfter(":").trim()
                // dumpsys temperature ignored in favor of API
                
                // Voltage from dumpsys ignored in favor of API if available (but it's static parse here)
                
                lower.startsWith("charge counter:") -> {
                    chargeCounter = trimmed.substringAfter(":").trim().toIntOrNull() ?: 0
                }
                
                trimmed.contains("mSavedBatteryUsage") -> {
                     val match = Regex("""\d+""").find(trimmed.substringAfter("mSavedBatteryUsage"))
                     if (match != null) samsungUsage = match.value.toInt()
                }
            }
        }
        
        // Parse batterystats for Capacity
        var designCap = -1
        if (capacityRaw.isNotEmpty()) {
            val match = Regex("""\d+""").find(capacityRaw)
            if (match != null) {
                designCap = match.value.toInt()
            }
        }

        // Logic Calculation
        
        // 1. Cycles
        var finalCycles = -1
        if (samsungUsage != -1) {
            finalCycles = samsungUsage / 100
        }

        // 2. Usable Capacity (Full Charge Capacity)
        val currentMah = chargeCounter / 1000.0
        var usableFullCap = 0.0
        
        if (level > 0 && currentMah > 0) {
            usableFullCap = (currentMah / level) * 100.0
        }
        
        // 3. SOH (Calculated Only)
        var finalSoh = 0.0
        if (designCap > 0 && usableFullCap > 0) {
            finalSoh = (usableFullCap / designCap) * 100.0
        }
        
        // 4. Power (Watts)
        // Voltage (mV) * Current (mA) / 1,000,000
        val powerW = kotlin.math.abs((voltage.toDouble() * currentNow.toDouble()) / 1000000.0)

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

    private fun mapStatus(i: Int): String {
        return when(i) {
            2 -> "충전 중"
            3 -> "방전 중"
            4 -> "충전 안 함"
            5 -> "배터리 가득 참"
            else -> "알 수 없음"
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
