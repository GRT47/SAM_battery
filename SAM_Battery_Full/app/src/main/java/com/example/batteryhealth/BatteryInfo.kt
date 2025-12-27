package com.example.batteryhealth

data class BatteryInfo(
    val level: Int = 0,
    val status: String = "Unknown",
    val health: String = "Unknown",
    val technology: String = "",
    val temperature: Int = 0,
    val voltage: Int = 0,
    val cycleCount: Int = -1,
    val designCapacity: Int = -1, // mAh
    val chargeCounter: Int = -1, // usually uAh
    val currentAverage: Int = -1, // Full Charge Capacity (uAh)
    val usableCapacityPercentage: Double = 0.0,
    val currentNow: Int = 0,
    val power: Double = 0.0
)
