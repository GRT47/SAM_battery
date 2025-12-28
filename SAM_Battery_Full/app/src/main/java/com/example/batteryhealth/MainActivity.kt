package com.example.batteryhealth

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {
    
    private val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            // Permission granted
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
         if (ShizukuHelper.isShizukuAvailable()) {
            Shizuku.addRequestPermissionResultListener(permissionListener)
        }
        
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color(0xFF121212),
                    surface = Color(0xFF1E1E1E),
                    onSurface = Color.White,
                    primary = Color(0xFF00E676)
                )
            ) {
                 MainScreen()
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (ShizukuHelper.isShizukuAvailable()) {
            Shizuku.removeRequestPermissionResultListener(permissionListener)
        }
    }
}

@Composable
fun MainScreen(viewModel: MainViewModel = viewModel()) {
    val hasPermission by viewModel.isShizukuPermissionGranted.collectAsState()
    val batteryInfo by viewModel.batteryInfo.collectAsState()
    
    // Initial Check & Start Loop
    LaunchedEffect(Unit) {
        viewModel.checkPermission()
        // Always load data. ViewModel handles API-only fallback if permission is missing.
        viewModel.refreshData() 
        viewModel.startRealtimeLoop()
    }
    
    // Watch permission changes
    LaunchedEffect(hasPermission) {
        if (hasPermission) {
             viewModel.refreshData()
             viewModel.startRealtimeLoop()
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
         DashboardScreen(
             info = batteryInfo,
             onRefresh = { viewModel.refreshData() } // Manual Refresh for Static Data
         )
    }
    }
}

@Composable
fun PermissionRequestScreen(onCheck: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Shizuku 권한 필요", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        Text(" 이 앱은 시스템 배터리 정보에 접근하기 위해 Shizuku 권한이 필요합니다. Shizuku 앱이 실행 중인지 확인하고 권한을 허용해주세요.", color = Color.Gray)
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = { 
            if (ShizukuHelper.isShizukuAvailable()) {
                 ShizukuHelper.requestPermission(0)
            }
            onCheck()
        }) {
            Text("권한 요청 / 확인")
        }
    }
}

@Composable
fun DashboardScreen(info: BatteryInfo?, onRefresh: () -> Unit) {
    var showInfoDialog by remember { mutableStateOf(false) }

    if (showInfoDialog) {
        CalculationInfoDialog(onDismiss = { showInfoDialog = false })
    }

    if (info == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
             Text("배터리 상태", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Center))
             IconButton(onClick = { showInfoDialog = true }, modifier = Modifier.align(Alignment.CenterEnd)) {
                 Icon(Icons.Default.Info, contentDescription = "Info", tint = Color.Gray)
             }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Circular Indicator
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(180.dp)) {
            CircularProgressbar(
                percentage = info.level / 100f,
                number = 100
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                 Text(text = "${info.level}%", fontSize = 42.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                 Text(text = info.status, color = Color.Gray, fontSize = 14.sp)
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Stats Grid
        
        // Group 1: Real-time Stats (전압, 전류, 전력, 온도)
        Text("실시간 모니터링", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.Start))
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // V36: Sync UI-Side Sign Enforcement from Non-Root V35
            val isDischargingUI = info.status.contains("방전")
            val signUI = if (isDischargingUI) -1 else 1
            
            val voltageV = info.voltage / 1000f
            
            // Current: Abs(Value) * UI_Sign
            val displayCurrent = kotlin.math.abs(info.currentNow) * signUI
            
            InfoCard(title = "전압", value = String.format("%.3f V", voltageV), modifier = Modifier.weight(1f), highlight = true)
            InfoCard(title = "전류", value = String.format("%+d mA", displayCurrent), modifier = Modifier.weight(1f), highlight = true)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val isDischargingUI = info.status.contains("방전")
            val signUI = if (isDischargingUI) -1 else 1

            // Power: Abs(Value) * UI_Sign
            val displayPower = kotlin.math.abs(info.power) * signUI

            InfoCard(title = "전력", value = String.format("%+.2f W", displayPower), modifier = Modifier.weight(1f), highlight = true)
            InfoCard(title = "온도", value = "${info.temperature / 10.0} °C", modifier = Modifier.weight(1f), highlight = true)
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Group 2: Battery Health & Spec (Synced Layout 3x2)
        Text("배터리 정보 (추정)", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Gray, modifier = Modifier.align(Alignment.Start))
        Spacer(modifier = Modifier.height(8.dp))
         
        // Row 1: SOH | Tech | Cycle
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val sohText = if (info.usableCapacityPercentage > 0) String.format("%.1f%%", info.usableCapacityPercentage) else "계산 중"
            InfoCard(title = "잔존수명(SOH)", value = sohText, modifier = Modifier.weight(1f))
            
            InfoCard(title = "종류", value = info.technology, modifier = Modifier.weight(1f))
            
            val cycleText = if (info.cycleCount != -1) "${info.cycleCount}회" else "N/A"
            InfoCard(title = "사이클", value = cycleText, modifier = Modifier.weight(1f))
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Row 2: Charge Counter | Estimated Cap | Design Cap
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
             // Charge Counter (mAh)
             // V37 Fix: Check magnitude to handle both uAh and mAh devices automatically.
             // If > 20,000 -> Likely uAh (e.g. 3,000,000 uAh) -> Divide by 1000
             // If < 20,000 -> Likely mAh (e.g. 3,000 mAh) -> Use as is
             val rawCounter = info.chargeCounter
             val chargeCounterMah = if (rawCounter > 20000) rawCounter / 1000 else rawCounter
             
             InfoCard(title = "현재 충전 용량", value = "$chargeCounterMah mAh", modifier = Modifier.weight(1f))
             
             InfoCard(title = "완충 추정 용량", value = "${info.currentAverage} mAh", modifier = Modifier.weight(1f))
             
             val designCapText = if (info.designCapacity != -1) "${info.designCapacity} mAh" else "Unknown"
             InfoCard(title = "설계 용량", value = designCapText, modifier = Modifier.weight(1f))
        }
        

        
        // Hide Cycles for Non-Root (or show if available later)
        // Old Cycles Row Removed (Moved to Grid)
    }
}

@Composable
fun CalculationInfoDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF252525))
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("시스템 권한 모드 안내", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(16.dp))
                
                Text("본 앱은 Shizuku/시스템 권한을 사용하여 정확한 하드웨어 정보를 불러옵니다.",
                     color = Color(0xFF00E676), fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
                     
                InfoItem("1. 실시간 모니터링", "전압, 전류, 전력, 온도는 시스템 센서값을 1초 간격으로 관리자 권한으로 조회합니다.")

                InfoItem("2. 설계 용량 (Design Capacity)", 
                         "Android 시스템(PowerProfile)에 정의된 기기의 공식 배터리 규격 용량입니다.")
                
                InfoItem("3. 완충 추정 용량 (Estimated)", 
                         "현재 배터리 잔량(%)과 누적 전하량(Charge Counter)을 기반으로 계산된 실질적인 완충 용량입니다.")
                
                InfoItem("4. 건강 상태 (SOH)", 
                         "[완충 추정 용량 ÷ 설계 용량 × 100] 공식으로 계산된 배터리 효율입니다. 충전 중 값이 보정됩니다.")
                         
                InfoItem("5. 충방전 사이클 (Cycle)", 
                         "루트/시스템 권한을 통해 기기에 기록된 정확한 충방전 횟수를 조회합니다.")

                Spacer(modifier = Modifier.height(24.dp))
                
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676), contentColor = Color.Black)
                ) {
                    Text("확인")
                }
            }
        }
    }
}

@Composable
fun InfoItem(title: String, desc: String) {
    Column(modifier = Modifier.padding(bottom = 12.dp)) {
        Text(title, fontWeight = FontWeight.Bold, color = Color(0xFF00E676), fontSize = 14.sp)
        Text(desc, color = Color.LightGray, fontSize = 13.sp)
    }
}

@Composable
fun InfoCard(title: String, value: String, modifier: Modifier = Modifier, highlight: Boolean = false) {
    val containerColor = if (highlight) Color(0xFF2A2A2A) else MaterialTheme.colorScheme.surface
    val textColor = if (highlight) Color(0xFF00E676) else Color.White

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontSize = 12.sp, color = Color.Gray)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = textColor)
        }
    }
}

@Composable
fun CircularProgressbar(
    percentage: Float,
    number: Int,
    fontSize: Int = 28,
    radius: Dp = 80.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    strokeWidth: Dp = 12.dp,
    animDuration: Int = 1000,
    animDelay: Int = 0
) {
    val curPercentage = animateFloatAsState(
        targetValue = percentage,
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = animDuration,
            delayMillis = animDelay
        ), label = "progress"
    )

    Canvas(modifier = Modifier.size(radius * 2f)) {
        drawArc(
            color = Color.DarkGray,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            style = Stroke(strokeWidth.toPx(), cap = StrokeCap.Round)
        )
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = 360 * curPercentage.value,
            useCenter = false,
            style = Stroke(strokeWidth.toPx(), cap = StrokeCap.Round)
        )
    }
}
