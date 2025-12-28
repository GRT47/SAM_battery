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
    
    private var isLoopRunning = false

    // Called once manually or on start
    fun refreshData() {
        viewModelScope.launch {
            try {
                _batteryInfo.value = repository.getBatteryInfo()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    // Called to start the 5s loop
    fun startRealtimeLoop() {
        if (isLoopRunning) return
        isLoopRunning = true
        
        viewModelScope.launch {
            while (true) {
                try {
                    if (_batteryInfo.value != null) {
                        val rt = repository.getRealtimeStats()
                        
                        // Use Centralized Power Logic (No local recalc)
                        _batteryInfo.value = _batteryInfo.value?.copy(
                            voltage = rt.voltage,
                            currentNow = rt.current,
                            power = rt.power,
                            temperature = rt.temp,
                            status = rt.status,
                            level = rt.level
                        )
                    }
                } catch (e: Exception) {
                     e.printStackTrace()
                }
                delay(1000) // 1 second
            }
        }
    }
}
