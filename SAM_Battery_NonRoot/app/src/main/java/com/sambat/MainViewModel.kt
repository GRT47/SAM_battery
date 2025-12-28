package com.sambat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = BatteryRepository(application.applicationContext)

    private val _batteryInfo = MutableStateFlow<BatteryInfo?>(null)
    val batteryInfo: StateFlow<BatteryInfo?> = _batteryInfo

    private val _isShizukuPermissionGranted = MutableStateFlow(false)
    val isShizukuPermissionGranted: StateFlow<Boolean> = _isShizukuPermissionGranted
    
    private var isLoopRunning = false

    fun checkPermission() {
        _isShizukuPermissionGranted.value = ShizukuHelper.isShizukuAvailable() && ShizukuHelper.hasPermission()
    }

    // Called once manually or on start
    fun refreshData() {
        viewModelScope.launch {
            if (_isShizukuPermissionGranted.value) {
                // Full Fetch which includes API calls for V/I + Shizuku for others
                _batteryInfo.value = repository.getBatteryInfo()
            } else {
                // Non-Root / API Only
                _batteryInfo.value = repository.getBatteryInfoApiOnly()
            }
        }
    }
    
    // Called to start the 5s loop
    fun startRealtimeLoop() {
        if (isLoopRunning) return
        isLoopRunning = true
        
        viewModelScope.launch {
            while (true) {
                // Even if permission is lost or not needed for V/I, we check it because other stats rely on it.
                // Actually, V/I via API works WITHOUT Shizuku. 
                // However, the app structure relies on Shizuku for the main view.
                // We will keep the check for consistency, or we can relax it for V/I updates.
                // For now, let's keep it safe.
                if (_batteryInfo.value != null) {
                    val rt = repository.getRealtimeStats()
                    
                    // Power Calculation
                    val powerW = kotlin.math.abs((rt.voltage.toDouble() * rt.current.toDouble()) / 1000000.0)
                    
                    // Update V/I/W/T/Status/Level
                    _batteryInfo.value = _batteryInfo.value?.copy(
                        voltage = rt.voltage,
                        currentNow = rt.current,
                        power = powerW,
                        temperature = rt.temp,
                        status = rt.status,
                        level = rt.level
                    )
                }
                delay(1000) // 1 second
            }
        }
    }
}
