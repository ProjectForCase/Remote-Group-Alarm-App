package com.example.finalproject

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Calendar

data class RankedMember(val uid: String, val name: String, val totalTime: Long)

private enum class LeaderboardMode {
    History,
    Weekly
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderboardScreen(group: Group, onBackClick: () -> Unit) {
    val db = FirebaseFirestore.getInstance()
    val historyMembers = remember { mutableStateListOf<RankedMember>() }
    val weeklyMembers = remember { mutableStateListOf<RankedMember>() }
    var selectedMode by remember { mutableStateOf(LeaderboardMode.History) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(group.id) {
        val memberIds = group.memberIds.take(10)
        if (memberIds.isEmpty()) {
            isLoading = false
            return@LaunchedEffect
        }

        db.collection("users")
            .whereIn("uid", memberIds)
            .get()
            .addOnSuccessListener { usersSnapshot ->
                val namesByUid = mutableMapOf<String, String>()
                val historyList = usersSnapshot.documents.map { doc ->
                    val uid = doc.getString("uid") ?: ""
                    val name = doc.getString("username") ?: "匿名"
                    namesByUid[uid] = name
                    RankedMember(
                        uid = uid,
                        name = name,
                        totalTime = doc.getLong("totalFocusTime") ?: 0L
                    )
                }.sortedByDescending { it.totalTime }

                historyMembers.clear()
                historyMembers.addAll(historyList)

                db.collection("focus_records")
                    .whereIn("userId", memberIds)
                    .get()
                    .addOnSuccessListener { recordsSnapshot ->
                        val weekStart = getCurrentWeekStartMillis()
                        val weeklyTotals = memberIds.associateWith { 0L }.toMutableMap()

                        recordsSnapshot.documents.forEach { doc ->
                            val uid = doc.getString("userId") ?: return@forEach
                            val timestamp = doc.getDate("timestamp")?.time ?: return@forEach
                            if (timestamp >= weekStart) {
                                weeklyTotals[uid] = weeklyTotals.getOrDefault(uid, 0L) + (doc.getLong("durationSeconds") ?: 0L)
                            }
                        }

                        val weeklyList = memberIds.map { uid ->
                            RankedMember(
                                uid = uid,
                                name = namesByUid[uid] ?: "匿名",
                                totalTime = weeklyTotals[uid] ?: 0L
                            )
                        }.sortedByDescending { it.totalTime }

                        weeklyMembers.clear()
                        weeklyMembers.addAll(weeklyList)
                        isLoading = false
                    }
                    .addOnFailureListener { isLoading = false }
            }
            .addOnFailureListener { isLoading = false }
    }

    val rankedMembers = if (selectedMode == LeaderboardMode.History) historyMembers else weeklyMembers

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("群組排行榜", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            TabRow(selectedTabIndex = if (selectedMode == LeaderboardMode.History) 0 else 1) {
                Tab(
                    selected = selectedMode == LeaderboardMode.History,
                    onClick = { selectedMode = LeaderboardMode.History },
                    text = { Text("歷史總和") }
                )
                Tab(
                    selected = selectedMode == LeaderboardMode.Weekly,
                    onClick = { selectedMode = LeaderboardMode.Weekly },
                    text = { Text("本週總和") }
                )
            }

            Box(modifier = Modifier.fillMaxSize()) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else if (rankedMembers.isEmpty()) {
                    Text("目前沒有資料", modifier = Modifier.align(Alignment.Center))
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        itemsIndexed(rankedMembers) { index, member ->
                            LeaderboardRow(rank = index + 1, member = member)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LeaderboardRow(rank: Int, member: RankedMember) {
    val cardColor = when (rank) {
        1 -> Color(0xFFFFF8E1)
        2 -> Color(0xFFF5F5F5)
        3 -> Color(0xFFFFF0E6)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }

    val rankIconColor = when (rank) {
        1 -> Color(0xFFFFD700)
        2 -> Color(0xFFC0C0C0)
        3 -> Color(0xFFCD7F32)
        else -> Color.Transparent
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (rank <= 3) 4.dp else 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center
            ) {
                if (rank <= 3) {
                    Icon(Icons.Default.Star, contentDescription = "Top 3", tint = rankIconColor, modifier = Modifier.size(36.dp))
                    Text("$rank", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = Color.White)
                } else {
                    Text("$rank", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.Gray)
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(text = member.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            Text(
                text = formatLeaderboardTime(member.totalTime),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = if (rank <= 3) MaterialTheme.colorScheme.primary else Color.Gray
            )
        }
    }
}

private fun getCurrentWeekStartMillis(): Long {
    val calendar = Calendar.getInstance()
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    while (calendar.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
        calendar.add(Calendar.DAY_OF_YEAR, -1)
    }
    return calendar.timeInMillis
}

private fun formatLeaderboardTime(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val mins = (totalSeconds % 3600) / 60
    val secs = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, mins, secs)
    } else {
        String.format("%02d:%02d", mins, secs)
    }
}
