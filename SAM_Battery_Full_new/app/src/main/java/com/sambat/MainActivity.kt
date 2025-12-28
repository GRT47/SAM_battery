package com.sambat

import android.os.Bundle
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import rikka.shizuku.Shizuku
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
    val batteryInfo by viewModel.batteryInfo.collectAsState()
    
    // Listen for permission grant
    DisposableEffect(Unit) {
        val listener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                viewModel.refreshData() // Immediate refresh
            }
        }
        
        try {
            if (Shizuku.pingBinder()) {
                Shizuku.addRequestPermissionResultListener(listener)
            }
        } catch (e: Exception) {}

        onDispose {
            try {
                if (Shizuku.pingBinder()) {
                    Shizuku.removeRequestPermissionResultListener(listener)
                }
            } catch (e: Exception) {}
        }
    }

    LaunchedEffect(Unit) {
        // Request Shizuku Permission
        try {
            if (Shizuku.pingBinder()) {
                if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                    Shizuku.requestPermission(0)
                }
            }
        } catch (e: Exception) {
            // Shizuku not installed or failed
        }
        
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
    var showDetailDialog by remember { mutableStateOf(false) }

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

    if (showDetailDialog && stats.details.isNotEmpty()) {
        DetailInfoDialog(details = stats.details, liveStats = stats) {
            showDetailDialog = false
        }
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
        
        // Title Row (Reorganized)
        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Title (Centered)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.align(Alignment.Center)
            ) {
                Text(
                    text = "SAM 배터리",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.9f)
                )
                Text(
                    text = "Full 에디션 (Shizuku)",
                    fontSize = 12.sp,
                    color = Color(0xFF00E676)
                )
            }
            
            // Buttons (Right Aligned)
            Row(
                modifier = Modifier.align(Alignment.CenterEnd),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                
                Spacer(modifier = Modifier.width(4.dp))
                
                // Detail Button
                TextButton(
                    onClick = { showDetailDialog = true },
                    enabled = stats.details.isNotEmpty(),
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Text("자세히", color = if (stats.details.isNotEmpty()) Color(0xFF00B0FF) else Color.DarkGray, fontSize = 12.sp)
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
fun WaveProgress(level: Float, phase: Float, isCharging: Boolean, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
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

@Composable
fun DetailInfoDialog(details: String, liveStats: BatteryInfo, onDismiss: () -> Unit) {
    val parsedData = remember(details) { parseDumpsysFriendly(details) }
    
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1E1E1E),
            modifier = Modifier.fillMaxWidth().heightIn(max = 650.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "상세 배터리 정보", 
                        fontSize = 20.sp, 
                        fontWeight = FontWeight.Bold, 
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                         Text("✕", color = Color.Gray)
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (parsedData.isEmpty()) {
                         Text("데이터를 분석할 수 없습니다.", color = Color.Gray)
                    }
                    
                    parsedData.forEach { (category, items) ->
                        // Collapsible Logic
                        val isCollapsible = category in listOf("수면/충전 패턴 학습", "최근 배터리 변화 (History)", "전원 연결/해제 이력 (Power Events)", "기타 시스템 로우 데이터")
                        var isExpanded by remember { mutableStateOf(!isCollapsible) } // Default collapsed if in list
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isExpanded = !isExpanded }
                                .padding(vertical = 8.dp)
                        ) {
                             Text(
                                 text = category, 
                                 fontSize = 16.sp, 
                                 fontWeight = FontWeight.Bold, 
                                 color = Color(0xFF00E676)
                             )
                             if (isCollapsible) {
                                 Spacer(modifier = Modifier.width(8.dp))
                                 Text(
                                     text = if(isExpanded) "▲" else "▼", 
                                     color = Color.Gray, 
                                     fontSize = 12.sp
                                 )
                             }
                        }
                        
                        if (isExpanded) {
                            items.forEach { item ->
                                val finalValue = if (category == "현재 상태 상세" && liveStats != null) {
                                    when (item.label) {
                                        "전압" -> "${liveStats.voltage / 1000.0} V"
                                        "온도" -> "${liveStats.temperature / 10.0} °C"
                                        "전류 흐름" -> "${liveStats.currentNow} mA"
                                        else -> item.value
                                    }
                                } else {
                                    item.value
                                }
                                val finalColor = if (category == "현재 상태 상세" && item.label in listOf("전압", "온도", "전류 흐름")) Color(0xFF00E676) else Color(0xFF81C784)

                                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp, start = 8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(), 
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(item.label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                        Text(finalValue, color = finalColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    }
                                    if (item.desc.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(item.desc, color = Color.Gray, fontSize = 12.sp, lineHeight = 14.sp)
                                    }
                                }
                                Divider(color = Color(0xFF333333), thickness = 1.dp)
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Button(
                    onClick = onDismiss, 
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676))
                ) {
                    Text("닫기", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

data class FriendlyItem(val label: String, val value: String, val desc: String)

fun parseDumpsysFriendly(raw: String): Map<String, List<FriendlyItem>> {
    val result = mutableMapOf<String, MutableList<FriendlyItem>>()
    
    fun add(cat: String, label: String, value: String, desc: String = "") {
        result.computeIfAbsent(cat) { mutableListOf() }
            .add(FriendlyItem(label, value, desc))
    }
    
    val lines = raw.lines().map { it.trim() }
    fun findVal(key: String): String? {
        val prefix = "$key:"
        return lines.firstOrNull { it.startsWith(prefix) }?.substringAfter(":")?.trim()
    }

    // ----------------------------------------------------------------
    // 1. 핵심 정보 (Core Info)
    // ----------------------------------------------------------------
    val catMain = "핵심 정보"
    
    findVal("mSavedBatteryUsage")?.let {
        // [28600] -> 286
        val cycle = it.substringAfter("[").substringBefore("]").toIntOrNull()?.div(100)
        if (cycle != null) {
            add(catMain, "사이클 (Usage)", "${cycle}회", "삼성 기기의 실제 배터리 사용 사이클입니다 (Usage/100). 가장 정확합니다.")
        }
    }
    
    // Health (ASOC/BSOH)
    findVal("mSavedBatteryAsoc")?.let { 
        val cleanVal = it.replace("[", "").replace("]", "")
        add(catMain, "배터리 효율 (ASOC)", "$cleanVal%", "설계 용량 대비 현재 실제 용량 비율입니다. (Absolute SOC)")
    }
    findVal("mSavedBatteryBsoh")?.let { 
        add(catMain, "배터리 성능 (BSOH)", "$it%", "배터리 성능 상태입니다.")
    }
    
    // ----------------------------------------------------------------
    // 2. 관리 및 날짜 (Dates & Management)
    // ----------------------------------------------------------------
    val catDate = "관리 및 이력"
    
    // Dates
    findVal("LLB CAL")?.let { add(catDate, "캘리브레이션 날짜", it, "마지막으로 배터리 게이지 보정이 수행된 날짜입니다.") }
    findVal("LLB MAN")?.let { add(catDate, "제조일자", it, "배터리가 제조된 날짜입니다.") }
    findVal("battery FirstUseDate")?.let { 
        val cleanVal = it.replace("[", "").replace("]", "")
        add(catDate, "초기화 이후 사용 날짜", cleanVal, "기기가 마지막으로 초기화된 날짜입니다.") 
    }
    
    // Protection
    findVal("mProtectBatteryMode")?.let { 
        val mode = if (it == "1") "켜짐 (80~85% 제한)" else "꺼짐"
        add(catDate, "배터리 보호 모드", mode, "설정한 충전 한도 제한 기능 동작 여부입니다.")
    }
    
    // ----------------------------------------------------------------
    // 3. 현재 상태 (Current Status)
    // ----------------------------------------------------------------
    val catStatus = "현재 상태 상세"
    
    findVal("level")?.let { add(catStatus, "현재 잔량", "$it%", "") }
    findVal("voltage")?.let { add(catStatus, "전압", "${it.toFloat()/1000} V", "") }
    findVal("temperature")?.let { add(catStatus, "온도", "${it.toFloat()/10} °C", "") }
    
    // Current - need to handle uAh/formatted
    lines.firstOrNull { it.startsWith("Charge counter:") }?.let {
        val cc = it.substringAfter(":").trim()
        add(catStatus, "충전 카운터", cc, "누적 전하량(Charge Counter)입니다.")
    }
    
    // Check various current keys
    listOf("ITEM_CURRENT_NOW", "current now", "Current now").forEach { key ->
        findVal(key)?.let { add(catStatus, "전류 흐름", "$it", "현재 충/방전 전류량입니다 (mA).") }
    }

    // ----------------------------------------------------------------
    // 4. 수면/충전 패턴 학습 (Sleep & Learning)
    // ----------------------------------------------------------------
    val catSleep = "수면/충전 패턴 학습"
    findVal("mSleepModeBlockOnOff")?.let { add(catSleep, "수면 모드 차단", it, "-1이면 학습 안됨, 0/1로 상태 표시") }
    
    // Additional sleep Time keys
    lines.filter { it.contains("SleepTime") || it.contains("SleepPattern") }.forEach { 
        val key = it.substringBefore(":").trim()
        val value = it.substringAfter(":").trim()
        if (value.isNotBlank()) {
            add(catSleep, key, value, "수면 충전 패턴 관련 학습 데이터입니다.")
        }
    }

    // ----------------------------------------------------------------
    // 5. 최근 배터리 변화 (History)
    // ----------------------------------------------------------------
    val catHistory = "최근 배터리 변화 (History)"
    val historyStart = raw.indexOf("Battery History:")
    if (historyStart != -1) {
        // Extract ~10 lines
        val historyPart = raw.substring(historyStart).lines().take(15)
        historyPart.drop(1).forEach { line ->
            if (line.isNotBlank()) {
                val time = line.substringBefore(" ").trim()
                val content = line.substringAfter(" ").trim()
                add(catHistory, time, content, "시간별 배터리 상태 변화 기록")
            }
        }
    }

    // ----------------------------------------------------------------
    // 7. 전원 연결 이력 (Power Events) - EventLogBuffer
    // ----------------------------------------------------------------
    val catEvent = "전원 연결/해제 이력 (Power Events)"
    // 12-27 16:32:18.436  android.intent.action.ACTION_POWER_CONNECTED
    val eventBufferStart = raw.indexOf("[EventLogBuffer]")
    if (eventBufferStart != -1) {
        val eventLines = raw.substring(eventBufferStart).lines()
            .drop(1) // Drop header
            .takeWhile { !it.startsWith("[") && it.isNotBlank() } // Read until next section or empty
            
        val recentEvents = eventLines.takeLast(10).reversed()
        
        recentEvents.forEach { line ->
            val time = line.substringBefore("android.intent").trim()
            val action = if (line.contains("ACTION_POWER_CONNECTED")) "충전 연결됨 (Connected)" 
                         else if (line.contains("ACTION_POWER_DISCONNECTED")) "충전 해제됨 (Disconnected)"
                         else "기타 이벤트"
                         
            if (time.isNotBlank()) {
                add(catEvent, time, action, "전원 케이블 연결/해제 로그")
            }
        }
    }

    // ----------------------------------------------------------------
    // 6. 기타 시스템 로우 데이터 (Raw)
    // ----------------------------------------------------------------
    val catRaw = "기타 시스템 로우 데이터"
    val usedKeys = setOf("level", "voltage", "temperature", "mSavedBatteryUsage", 
                         "mSavedBatteryAsoc", "mSavedBatteryBsoh", "LLB CAL", "LLB MAN", 
                         "battery FirstUseDate", "mProtectBatteryMode")
                         
    val skipKeys = listOf("mSleep", "History", "EventLog", "BackupOnOff", "ACTION_", "[")
                         
    lines.forEach { line ->
        if (line.contains(":")) {
            val key = line.substringBefore(":").trim()
            val value = line.substringAfter(":").trim()
            
            val isCore = usedKeys.contains(key)
            val isSkip = skipKeys.any { line.contains(it) } || key.startsWith("Date") || key.matches(Regex("\\d{4}-\\d{2}-\\d{2}.*"))

            if (!isCore && !isSkip && value.isNotEmpty()) {
                // Friendly Mapper
                val desc = when {
                    key.contains("dwState") -> "무선 충전 패드 상태 코드입니다."
                    key.contains("tx_id") -> "충전기(TX) 고유 ID입니다."
                    key.contains("cc_current_limit") && value != "0" -> "전류 제한 설정값입니다."
                    key.contains("high_voltage") -> "고전압 보호가 작동 중인지 나타냅니다."
                    key.contains("online") -> "전원이 연결되어 있는지 여부 (1=Yes)"
                    key.contains("present") -> "배터리가 장착되어 있는지 여부"
                     key.contains("status") -> "충전 상태 코드 (2=Charging, 3=Discharging...)"
                     key.contains("health") -> "건강 상태 코드 (2=Good...)"
                    else -> "시스템 내부 다이그노스틱 데이터입니다."
                }
                add(catRaw, key, value, desc)
            }
        }
    }
    
    return result
}
