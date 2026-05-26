package com.example.finalproject

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.util.*
import android.util.Log
import java.text.SimpleDateFormat

data class FocusRecord(
    val id: String = "",
    val type: String = "",
    val durationSeconds: Long = 0,
    val timestamp: Date? = null
)

@Composable
fun HomeTabContent(userEmail: String) {
    val loadingText = stringResource(R.string.loading)
    var username by remember { mutableStateOf(loadingText) }
    var photoUrl by remember { mutableStateOf<String?>(null) }
    val uid = FirebaseAuth.getInstance().currentUser?.uid

    // 動態數據狀態
    var todayFocusSeconds by remember { mutableStateOf(0L) }
    var weeklyTrends by remember { mutableStateOf(listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f)) }
    var weeklyTotalHours by remember { mutableStateOf(0) }
    var categoryData by remember { mutableStateOf<List<Pair<String, Float>>>(emptyList()) }
    var lastSevenDaysRecords by remember { mutableStateOf<List<FocusRecord>>(emptyList()) }

    DisposableEffect(uid) {
        val db = FirebaseFirestore.getInstance()
        var userListener: ListenerRegistration? = null
        var recordsListener: ListenerRegistration? = null

        if (uid != null) {
            userListener = db.collection("users").document(uid).addSnapshotListener { doc, _ ->
                if (doc != null && doc.exists()) {
                    username = doc.getString("username") ?: "使用者"
                    photoUrl = doc.getString("photoUrl")
                }
            }

            recordsListener = db.collection("focus_records")
                .whereEqualTo("userId", uid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e("Firestore", "Error fetching records: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot == null) return@addSnapshotListener
                    
                    val allRecords = snapshot.documents
                    
                    // 取得近七天的紀錄
                    val calendar7 = Calendar.getInstance()
                    calendar7.add(Calendar.DAY_OF_YEAR, -7)
                    calendar7.set(Calendar.HOUR_OF_DAY, 0)
                    calendar7.set(Calendar.MINUTE, 0)
                    calendar7.set(Calendar.SECOND, 0)
                    calendar7.set(Calendar.MILLISECOND, 0)
                    val sevenDaysAgo = calendar7.time

                    lastSevenDaysRecords = allRecords.mapNotNull { doc ->
                        val record = doc.toObject(FocusRecord::class.java)
                        record?.copy(id = doc.id)
                    }.filter { it.timestamp != null && it.timestamp >= sevenDaysAgo }
                     .sortedByDescending { it.timestamp }

                    val calendar = Calendar.getInstance()
                    calendar.set(Calendar.HOUR_OF_DAY, 0)
                    calendar.set(Calendar.MINUTE, 0)
                    calendar.set(Calendar.SECOND, 0)
                    calendar.set(Calendar.MILLISECOND, 0)
                    val todayStart = calendar.time

                    val weekCalendar = Calendar.getInstance()
                    weekCalendar.time = todayStart
                    while (weekCalendar.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
                        weekCalendar.add(Calendar.DAY_OF_YEAR, -1)
                    }
                    val weekStart = weekCalendar.time

                    val todayRecords = allRecords.filter { 
                        val ts = it.getDate("timestamp")
                        ts != null && ts >= todayStart
                    }
                    val weekRecords = allRecords.filter {
                        val ts = it.getDate("timestamp")
                        ts != null && ts >= weekStart
                    }

                    todayFocusSeconds = todayRecords.sumOf { it.getLong("durationSeconds") ?: 0L }

                    val weekData = MutableList(7) { 0L }
                    weekRecords.forEach { doc ->
                        val ts = doc.getDate("timestamp")
                        if (ts != null) {
                            val cal = Calendar.getInstance()
                            cal.time = ts
                            val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
                            val index = when (dayOfWeek) {
                                Calendar.MONDAY -> 0
                                Calendar.TUESDAY -> 1
                                Calendar.WEDNESDAY -> 2
                                Calendar.THURSDAY -> 3
                                Calendar.FRIDAY -> 4
                                Calendar.SATURDAY -> 5
                                Calendar.SUNDAY -> 6
                                else -> -1
                            }
                            if (index != -1) {
                                weekData[index] += doc.getLong("durationSeconds") ?: 0L
                            }
                        }
                    }
                    
                    val maxVal = weekData.maxOrNull()?.toFloat() ?: 0f
                    weeklyTrends = weekData.map { if (maxVal > 0) it.toFloat() / maxVal else 0f }
                    weeklyTotalHours = (weekData.sum() / 3600).toInt()

                    val catMap = mutableMapOf<String, Long>()
                    todayRecords.forEach { doc ->
                        val type = doc.getString("type") ?: "其他"
                        val dur = doc.getLong("durationSeconds") ?: 0L
                        catMap[type] = catMap.getOrDefault(type, 0L) + dur
                    }
                    val totalToday = catMap.values.sum().toFloat()
                    categoryData = if (totalToday > 0) {
                        catMap.map { (type, dur) -> type to (dur.toFloat() / totalToday) }
                    } else {
                        emptyList()
                    }
                }
        }

        onDispose {
            userListener?.remove()
            recordsListener?.remove()
        }
    }

    val todayHours = (todayFocusSeconds / 3600).toInt()
    val todayMinutes = ((todayFocusSeconds % 3600) / 60).toInt()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.Start
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (photoUrl != null) {
                AsyncImage(
                    model = photoUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.primary, CircleShape),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(12.dp))
            }
            Text(
                text = stringResource(R.string.hello_user, username),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (MaterialTheme.colorScheme.surface == Color.Black) Color(0xFF1A1A1A) else Color.White
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.today_focus_duration),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "%02d : %02d".format(todayHours, todayMinutes),
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 72.sp,
                        fontWeight = FontWeight.W300,
                        fontFamily = FontFamily.Monospace
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.weekly_focus_trend),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        WeeklyTrendChart(trends = weeklyTrends)
        
        Text(
            text = stringResource(R.string.weekly_total, weeklyTotalHours),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = stringResource(R.string.today_category_stats),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (categoryData.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = stringResource(R.string.no_data_today), color = Color.Gray)
            }
        } else {
            CategoryPieChart(categoryData = categoryData)
        }

        Spacer(modifier = Modifier.height(32.dp))

        // 專注歷史紀錄部分 (近七天)
        Text(
            text = stringResource(R.string.focus_history),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (lastSevenDaysRecords.isEmpty()) {
            Text(
                text = stringResource(R.string.no_history),
                color = Color.Gray,
                modifier = Modifier.padding(8.dp)
            )
        } else {
            lastSevenDaysRecords.forEach { record ->
                HomeHistoryItem(record)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
        
        Spacer(modifier = Modifier.height(100.dp))
    }
}

@Composable
fun HomeHistoryItem(record: FocusRecord) {
    val dateFormat = remember { SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()) }
    val dateString = record.timestamp?.let { dateFormat.format(it) } ?: ""
    
    val minutes = record.durationSeconds / 60
    val seconds = record.durationSeconds % 60
    val durationString = stringResource(R.string.duration_format, minutes, seconds)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                val displayType = when(record.type) {
                    "工作" -> stringResource(R.string.cat_work)
                    "學習" -> stringResource(R.string.cat_study)
                    "運動" -> stringResource(R.string.cat_exercise)
                    "休息" -> stringResource(R.string.cat_rest)
                    "閱讀" -> stringResource(R.string.cat_read)
                    "冥想" -> stringResource(R.string.cat_meditation)
                    else -> record.type
                }
                Text(
                    text = displayType,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = dateString,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            Text(
                text = durationString,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun CategoryPieChart(categoryData: List<Pair<String, Float>>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(
            modifier = Modifier
                .size(150.dp)
                .padding(8.dp)
        ) {
            var startAngle = -90f
            categoryData.forEach { (category, ratio) ->
                val sweepAngle = ratio * 360f
                drawArc(
                    color = getCategoryColor(category),
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = true
                )
                startAngle += sweepAngle
            }
        }

        Spacer(modifier = Modifier.width(24.dp))

        Column {
            categoryData.forEach { (category, ratio) ->
                val displayType = when(category) {
                    "工作" -> stringResource(R.string.cat_work)
                    "學習" -> stringResource(R.string.cat_study)
                    "運動" -> stringResource(R.string.cat_exercise)
                    "休息" -> stringResource(R.string.cat_rest)
                    "閱讀" -> stringResource(R.string.cat_read)
                    "冥想" -> stringResource(R.string.cat_meditation)
                    else -> category
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(getCategoryColor(category))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "$displayType: ${(ratio * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

fun getCategoryColor(category: String): Color {
    return when (category) {
        "工作" -> Color(0xFF64B5F6)
        "學習" -> Color(0xFF81C784)
        "運動" -> Color(0xFFFFB74D)
        "休息" -> Color(0xFFBA68C8)
        "閱讀" -> Color(0xFF4DB6AC)
        "冥想" -> Color(0xFFF06292)
        else -> Color(0xFFBDBDBD)
    }
}

@Composable
fun WeeklyTrendChart(trends: List<Float>) {
    val days = listOf(
        R.string.mon, R.string.tue, R.string.wed, R.string.thu, R.string.fri, R.string.sat, R.string.sun
    )
    val barColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        trends.forEachIndexed { index, value ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Bottom
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        modifier = Modifier
                            .width(18.dp)
                            .fillMaxHeight(fraction = if (value <= 0.05f) 0.05f else value)
                            .background(barColor, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(days[index]), 
                    fontSize = 11.sp, 
                    color = Color.Gray,
                    maxLines = 1
                )
            }
        }
    }
}
