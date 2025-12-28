package com.sambat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.PI
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
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
}

@Composable
fun MainScreen(viewModel: MainViewModel = viewModel()) {
    // We can assume usage is safe now
    val batteryInfo by viewModel.batteryInfo.collectAsState()
    
    LaunchedEffect(Unit) {
        viewModel.refreshData() 
        viewModel.startRealtimeLoop()
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
         PremiumDashboard(batteryInfo)
    }
}

@Composable
fun PremiumDashboard(info: BatteryInfo?) {
    val stats = info ?: BatteryInfo()
    
    // Wave Animation State
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2 * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing)
        ),
        label = "phase"
    )

    var showInfoDialog by remember { mutableStateOf(false) }

    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = { Text("안내", fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Column {
                    Text("• 수명 (SOH): 현재 배터리 상태에서 추정된 완충 용량을 설계 용량과 비교한 효율입니다.\n(산출식: 완충 추정 용량 / 설계 용량 × 100)", color = Color.LightGray, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("• 사이클: 배터리 용량을 100%만큼 소모한 누적 횟수입니다. 안드로이드 시스템(BMS) 내부에 기록된 정밀한 값을 직접 읽어옵니다.", color = Color.LightGray, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("• 완충 추정: 현재 충전 카운터(mAh)와 배터리 잔량(%)을 기반으로 역산하여, 100% 충전 시 예상되는 실사용 가능 용량을 추정합니다.", color = Color.LightGray, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("• 전력 (W): 실시간 전압(V) × 전류(A)로 계산됩니다.\n(+: 충전 중 / -: 방전 중)", color = Color.LightGray, fontSize = 13.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text("확인", color = Color(0xFF00E676))
                }
            },
            containerColor = Color(0xFF1E1E1E),
            textContentColor = Color.LightGray
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF121212), Color.Black)
                )
            )
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        
        // Title Row (Compact)
        Row(
            verticalAlignment = Alignment.CenterVertically, 
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "SAM 배터리",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.9f)
                )
                Text(
                    text = "Non-Root 에디션",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = { showInfoDialog = true }, modifier = Modifier.size(24.dp)) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(20.dp)
                        .background(Color.Transparent, shape = androidx.compose.foundation.shape.CircleShape)
                        .border(1.dp, Color.Gray, androidx.compose.foundation.shape.CircleShape)
                ) {
                    Text("i", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Main Circle (Compact: 240 -> 180)
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(180.dp)) {
            // Background Circle
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = Color(0xFF2A2A2A),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                )
            }
            
            // Wave Content
            val isCharging = stats.status.contains("충전")
            val waveColor = if (isCharging) Color(0xFF00E676) else Color(0xFF00B0FF)
            
            WaveProgress(
                level = stats.level.toFloat(),
                phase = wavePhase,
                isCharging = isCharging,
                color = waveColor
            )
            
            // Text Content
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${stats.level}%",
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = stats.status,
                    fontSize = 14.sp,
                    color = Color.LightGray
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Glassmorphism Grid (Compact Spacing & Card)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassCard(
                title = "전압",
                value = String.format("%.3f V", stats.voltage / 1000f),
                icon = "⚡",
                modifier = Modifier.weight(1f)
            )
            GlassCard(
                title = "전류",
                value = String.format("%+d mA", stats.currentNow),
                icon = "Hz",
                modifier = Modifier.weight(1f)
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassCard(
                title = "전력",
                value = String.format("%+.2f W", stats.power),
                icon = "⚡",
                modifier = Modifier.weight(1f)
            )
            GlassCard(
                title = "온도",
                value = "${stats.temperature / 10.0}°C",
                icon = "🌡️",
                modifier = Modifier.weight(1f)
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "배터리 건강 상태",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.align(Alignment.Start)
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Health Stats (Compact Height)
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max), 
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val sohText = if (stats.usableCapacityPercentage > 0) String.format("%.1f%%", stats.usableCapacityPercentage) else "--"
            GlassCard(
                title = "수명",
                value = sohText,
                icon = "❤️",
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            GlassCard(
                title = "사이클",
                value = if (stats.cycleCount > 0) "${stats.cycleCount}회" else "--",
                icon = "🔄",
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            GlassCard(
                title = "상태",
                value = stats.health,
                icon = "🏥",
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Capacity Stats
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassCard(
                title = "설계 용량",
                value = "${stats.designCapacity} mAh",
                icon = "📏",
                modifier = Modifier.weight(1f)
            )
            GlassCard(
                title = "완충 추정",
                value = "${stats.currentAverage} mAh",
                icon = "🔮",
                modifier = Modifier.weight(1f)
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Charge Counter
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
             GlassCard(
                title = "충전 카운터",
                value = "${stats.chargeCounter} mAh",
                icon = "🔋",
                modifier = Modifier.weight(1f)
            )
             GlassCard(
                title = "종류",
                value = stats.technology,
                icon = "🧪",
                modifier = Modifier.weight(1f)
            )
        }
        
        if (stats.debugLog.isNotEmpty()) {
             Spacer(modifier = Modifier.height(12.dp))
             Text("Debug: ${stats.debugLog}", color = Color.Gray, fontSize = 8.sp)
        }
    }
}

@Composable
fun GlassCard(title: String, value: String, icon: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E).copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(icon, fontSize = 14.sp, color = Color(0xFF00E676))
                Spacer(modifier = Modifier.width(6.dp))
                Text(title, fontSize = 12.sp, color = Color.Gray)
            }
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

@Composable
fun WaveProgress(level: Float, phase: Float, isCharging: Boolean, color: Color) {
    Canvas(modifier = Modifier.size(240.dp)) {
        val radius = size.minDimension / 2
        val clipPath = Path().apply {
            addOval(Rect(0f, 0f, size.width, size.height))
        }

        clipPath(clipPath) {
            // Draw Background Liquid (Darker)
            drawRect(color = color.copy(alpha = 0.2f))

            // Wave Logic
            val wavePath = Path()
            val waveHeight = 15.dp.toPx()
            val waterLevel = size.height * (1 - level / 100f)
            
            wavePath.moveTo(0f, size.height)
            
            // Draw Sine Wave
            for (x in 0..size.width.toInt() step 10) {
                val y = waterLevel + sin((x / 60f) + phase) * waveHeight
                if (x == 0) wavePath.moveTo(x.toFloat(), y)
                else wavePath.lineTo(x.toFloat(), y)
            }
            
            wavePath.lineTo(size.width, size.height)
            wavePath.lineTo(0f, size.height)
            wavePath.close()

            drawPath(
                path = wavePath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        color.copy(alpha = 0.8f),
                        color.copy(alpha = 0.4f)
                    ),
                    startY = waterLevel,
                    endY = size.height
                )
            )
        }
    }
}
