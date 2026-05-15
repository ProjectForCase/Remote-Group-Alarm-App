package com.example.finalproject

import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// 1. 資料結構 (Data Class)
data class Group(
    val id: String,
    val name: String,
    val memberCount: Int,
    val memberIds: List<String> = emptyList()
)

data class Member(
    val uid: String,
    val name: String,
    val fcmToken: String?,
    val photoUrl: String? = null
)

data class ScheduledGroupAlarm(
    val alarmId: String,
    val groupName: String,
    val hour: Int,
    val minute: Int,
    val scheduledAtMillis: Long,
    val repeatDays: List<Int> = emptyList(),
    val targetUids: List<String> = emptyList()
)

// 2. 群組導覽總管 (負責分發頁面)
@Composable
fun GroupMainScreen(onBackToHome: () -> Unit) {
    var selectedGroup by remember { mutableStateOf<Group?>(null) }
    var showWakeUpScreen by remember { mutableStateOf(false) }
    var showLeaderboard by remember { mutableStateOf(false) } // 👇 控制排行榜畫面顯示
    var showGroupAlarmsScreen by remember { mutableStateOf(false) }

    when {
        selectedGroup == null -> {
            GroupListScreen(
                onGroupClick = { clickedGroup -> selectedGroup = clickedGroup },
                onBackToHome = onBackToHome
            )
        }
        showWakeUpScreen -> {
            WakeUpCallScreen(
                group = selectedGroup!!,
                onBackClick = { showWakeUpScreen = false }
            )
        }
        showLeaderboard -> {
            // 👇 呼叫你在 LeaderboardScreen.kt 定義的元件
            LeaderboardScreen(
                group = selectedGroup!!,
                onBackClick = { showLeaderboard = false }
            )
        }
        showGroupAlarmsScreen -> {
            GroupAlarmScreen(
                group = selectedGroup!!,
                onBackClick = { showGroupAlarmsScreen = false }
            )
        }
        else -> {
            GroupDetailScreen(
                group = selectedGroup!!,
                onBackClick = { selectedGroup = null },
                onWakeUpClick = { showWakeUpScreen = true },
                onLeaderboardClick = { showLeaderboard = true }, // 👇 傳入點擊事件
                onSetGroupAlarmClick = { showGroupAlarmsScreen = true }
            )
        }
    }
}

// 3. 群組列表畫面 (即時讀取資料庫)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupListScreen(onGroupClick: (Group) -> Unit, onBackToHome: () -> Unit) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val currentUser = auth.currentUser

    var showCreateDialog by remember { mutableStateOf(false) }
    var showJoinDialog by remember { mutableStateOf(false) }

    val realGroups = remember { mutableStateListOf<Group>() }

    DisposableEffect(currentUser?.uid) {
        var listener: ListenerRegistration? = null
        if (currentUser != null) {
            listener = db.collection("groups")
                .whereArrayContains("members", currentUser.uid)
                .addSnapshotListener { snapshot, e ->
                    if (e != null) return@addSnapshotListener
                    if (snapshot != null) {
                        realGroups.clear()
                        for (doc in snapshot.documents) {
                            val id = doc.getString("groupId") ?: doc.id
                            val name = doc.getString("groupName") ?: context.getString(R.string.unknown_group)
                            val members = doc.get("members") as? List<String> ?: emptyList()
                            realGroups.add(Group(id, name, members.size, members))
                        }
                    }
                }
        }
        onDispose { listener?.remove() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.my_groups), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackToHome) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back_to_home))
                    }
                },
                actions = {
                    TextButton(onClick = { showJoinDialog = true }) {
                        Text(stringResource(R.string.join), color = MaterialTheme.colorScheme.primary)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }, containerColor = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_group), tint = Color.White)
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp)
        ) {
            if (realGroups.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.no_groups_yet), color = Color.Gray)
                    }
                }
            } else {
                items(realGroups) { group ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clickable { onGroupClick(group) },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    ) {
                        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = group.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text(text = stringResource(R.string.members_count, group.memberCount), style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray)
                        }
                    }
                }
            }
        }

        // 建立群組對話框
        if (showCreateDialog) {
            CreateGroupDialog(onDismiss = { showCreateDialog = false }, onCreate = { name ->
                val uid = currentUser?.uid ?: return@CreateGroupDialog
                val groupRef = db.collection("groups").document()
                val data = hashMapOf(
                    "groupId" to groupRef.id,
                    "groupName" to name,
                    "members" to listOf(uid),
                    "createdAt" to com.google.firebase.Timestamp.now()
                )

                val batch = db.batch()
                batch.set(groupRef, data)
                batch.update(db.collection("users").document(uid), "joinedGroups", FieldValue.arrayUnion(groupRef.id))

                batch.commit().addOnSuccessListener { showCreateDialog = false }
            })
        }

        // 加入群組對話框
        if (showJoinDialog) {
            JoinGroupDialog(onDismiss = { showJoinDialog = false }, onJoin = { code ->
                val uid = currentUser?.uid ?: return@JoinGroupDialog
                val batch = db.batch()
                batch.update(db.collection("groups").document(code), "members", FieldValue.arrayUnion(uid))
                batch.update(db.collection("users").document(uid), "joinedGroups", FieldValue.arrayUnion(code))

                batch.commit()
                    .addOnSuccessListener { showJoinDialog = false }
                    .addOnFailureListener { Toast.makeText(context, context.getString(R.string.join_failed), Toast.LENGTH_SHORT).show() }
            })
        }
    }
}

// 4. 群組詳細頁面 (包含排行榜按鈕與成員列表)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(
    group: Group,
    onBackClick: () -> Unit,
    onWakeUpClick: () -> Unit,
    onLeaderboardClick: () -> Unit, // 👇 排行榜點擊回呼
    onSetGroupAlarmClick: () -> Unit
) {
    val db = FirebaseFirestore.getInstance()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val members = remember { mutableStateListOf<Member>() }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(group.id) {
        if (group.memberIds.isNotEmpty()) {
            db.collection("users")
                .whereIn("uid", group.memberIds.take(10))
                .get()
                .addOnSuccessListener { snapshot ->
                    members.clear()
                    for (doc in snapshot.documents) {
                        members.add(Member(
                            uid = doc.getString("uid") ?: "",
                            name = doc.getString("username") ?: context.getString(R.string.anonymous_member),
                            fcmToken = doc.getString("fcmToken"),
                            photoUrl = doc.getString("photoUrl")
                        ))
                    }
                    isLoading = false
                }
                .addOnFailureListener { isLoading = false }
        } else {
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(group.name, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) { Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.cancel)) }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 邀請碼卡片 (點擊複製)
            Card(
                modifier = Modifier.fillMaxWidth().clickable {
                    clipboardManager.setText(AnnotatedString(group.id))
                    Toast.makeText(context, context.getString(R.string.invitation_code_copied), Toast.LENGTH_SHORT).show()
                },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.invitation_code_title), style = MaterialTheme.typography.labelSmall)
                        Text(group.id, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onSetGroupAlarmClick,
                modifier = Modifier.fillMaxWidth().height(56.dp).padding(bottom = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63))
            ) {
                Icon(Icons.Default.Alarm, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.set_group_alarm), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }

            // 👇 新增：排行榜進入按鈕
            Button(
                onClick = onLeaderboardClick,
                modifier = Modifier.fillMaxWidth().height(56.dp).padding(bottom = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Leaderboard, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.view_leaderboard), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(stringResource(R.string.member_list), modifier = Modifier.fillMaxWidth(), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(members) { member ->
                            ListItem(
                                headlineContent = { Text(member.name) },
                                leadingContent = {
                                    if (member.photoUrl != null) {
                                        AsyncImage(
                                            model = member.photoUrl,
                                            contentDescription = null,
                                            modifier = Modifier.size(40.dp).clip(CircleShape),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Box(modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                                            Text(member.name.take(1).uppercase(), fontWeight = FontWeight.Bold)
                                        }
                                    }
                                },
                                trailingContent = {
                                    if (member.fcmToken != null) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = "已連線", tint = Color.Green, modifier = Modifier.size(16.dp))
                                    }
                                }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                        }
                    }
                }
            }

            // 呼叫起床按鈕
            Button(
                onClick = onWakeUpClick,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
            ) {
                Icon(Icons.Default.NotificationsActive, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.call_to_wake_up), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// 5. 呼叫起床畫面 (保留原本邏輯)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WakeUpCallScreen(group: Group, onBackClick: () -> Unit) {
    val db = FirebaseFirestore.getInstance()
    val context = LocalContext.current
    val members = remember { mutableStateListOf<Member>() }
    val selectedMembers = remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(group.id) {
        if (group.memberIds.isNotEmpty()) {
            db.collection("users")
                .whereIn("uid", group.memberIds.take(10))
                .get()
                .addOnSuccessListener { snapshot ->
                    members.clear()
                    for (doc in snapshot.documents) {
                        members.add(Member(
                            uid = doc.getString("uid") ?: "",
                            name = doc.getString("username") ?: context.getString(R.string.anonymous_member),
                            fcmToken = doc.getString("fcmToken"),
                            photoUrl = doc.getString("photoUrl")
                        ))
                    }
                    isLoading = false
                }
                .addOnFailureListener { isLoading = false }
        }
    }

    val isAllSelected = members.isNotEmpty() && selectedMembers.value.size == members.size

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.select_call_targets), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) { Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.cancel)) }
                },
                actions = {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
                        Text(stringResource(R.string.select_all), style = MaterialTheme.typography.bodyMedium)
                        Checkbox(
                            checked = isAllSelected,
                            onCheckedChange = { checked ->
                                if (checked) selectedMembers.value = members.map { it.uid }
                                else selectedMembers.value = emptyList()
                            }
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp)) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (isLoading) CircularProgressIndicator(Modifier.align(Alignment.Center))
                else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(members) { member ->
                            val isSelected = selectedMembers.value.contains(member.uid)
                            ListItem(
                                headlineContent = { Text(member.name) },
                                leadingContent = {
                                    if (member.photoUrl != null) {
                                        AsyncImage(
                                            model = member.photoUrl,
                                            contentDescription = null,
                                            modifier = Modifier.size(40.dp).clip(CircleShape),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Box(modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                                            Text(member.name.take(1).uppercase(), fontWeight = FontWeight.Bold)
                                        }
                                    }
                                },
                                trailingContent = {
                                    Checkbox(checked = isSelected, onCheckedChange = { checked ->
                                        if (checked) selectedMembers.value = selectedMembers.value + member.uid
                                        else selectedMembers.value = selectedMembers.value - member.uid
                                    })
                                },
                                modifier = Modifier.clickable {
                                    if (isSelected) selectedMembers.value = selectedMembers.value - member.uid
                                    else selectedMembers.value = selectedMembers.value + member.uid
                                }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                        }
                    }
                }
            }

            Button(
                onClick = {
                    if (selectedMembers.value.isEmpty()) {
                        Toast.makeText(context, context.getString(R.string.select_at_least_one), Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val auth = FirebaseAuth.getInstance()
                    val uid = auth.currentUser?.uid ?: return@Button

                    // 👇 修正：先抓取發送者在 Firestore 裡的 username
                    db.collection("users").document(uid).get().addOnSuccessListener { userDoc ->
                        val realSenderName = userDoc.getString("username") ?: "群組成員"

                        val callData = hashMapOf(
                            "senderId" to uid,
                            "senderName" to realSenderName,
                            "groupId" to group.id,
                            "groupName" to group.name,
                            "targetUids" to selectedMembers.value,
                            "timestamp" to FieldValue.serverTimestamp(),
                            "status" to "pending"
                        )

                        // 寫入 Firestore 觸發雲端函數
                        db.collection("calls").add(callData)
                            .addOnSuccessListener {
                                Toast.makeText(context, "呼叫請求已發送至伺服器！", Toast.LENGTH_SHORT).show()
                                onBackClick()
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(context, "發送失敗: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
            ) {
                Icon(Icons.Default.Send, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.confirm_send, selectedMembers.value.size), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// 6. 對話框 (Dialogs)
@Composable
fun CreateGroupDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.create_group)) },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.group_name)) }) },
        confirmButton = { Button(onClick = { if(name.isNotEmpty()) onCreate(name) }) { Text(stringResource(R.string.create)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupAlarmScreen(group: Group, onBackClick: () -> Unit) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val uid = FirebaseAuth.getInstance().currentUser?.uid
    val alarms = remember { mutableStateListOf<ScheduledGroupAlarm>() }
    var isLoading by remember { mutableStateOf(true) }
    var showAddDialog by remember { mutableStateOf(false) }

    DisposableEffect(group.id, uid) {
        var listener: ListenerRegistration? = null
        if (uid != null) {
            listener = db.collection("groupAlarms")
                .whereEqualTo("groupId", group.id)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        isLoading = false
                        Toast.makeText(
                            context,
                            context.getString(R.string.group_alarm_load_failed, error.message ?: ""),
                            Toast.LENGTH_SHORT
                        ).show()
                        return@addSnapshotListener
                    }

                    alarms.clear()
                    snapshot?.documents
                        ?.filter { doc ->
                            doc.getString("createdBy") == uid && doc.getString("status") == "scheduled"
                        }
                        ?.mapNotNull { doc ->
                            val alarmId = doc.getString("alarmId") ?: doc.id
                            val hour = doc.getLong("hour")?.toInt() ?: return@mapNotNull null
                            val minute = doc.getLong("minute")?.toInt() ?: return@mapNotNull null
                            val scheduledAtMillis = doc.getLong("scheduledAtMillis") ?: 0L
                            val repeatDays = (doc.get("repeatDays") as? List<*>)
                                ?.mapNotNull { (it as? Number)?.toInt() }
                                .orEmpty()
                            val targetUids = (doc.get("targetUids") as? List<*>)
                                ?.mapNotNull { it as? String }
                                .orEmpty()
                            ScheduledGroupAlarm(
                                alarmId = alarmId,
                                groupName = doc.getString("groupName") ?: group.name,
                                hour = hour,
                                minute = minute,
                                scheduledAtMillis = scheduledAtMillis,
                                repeatDays = repeatDays,
                                targetUids = targetUids
                            )
                        }
                        ?.sortedBy { it.scheduledAtMillis }
                        ?.let { alarms.addAll(it) }
                    isLoading = false
                }
        } else {
            isLoading = false
        }
        onDispose { listener?.remove() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.group_alarms), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.cancel))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.AddAlarm, contentDescription = stringResource(R.string.add_group_alarm))
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp)) {
            when {
                isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                alarms.isEmpty() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.AlarmOff, contentDescription = null, modifier = Modifier.size(48.dp), tint = Color.Gray)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(stringResource(R.string.no_group_alarms), color = Color.Gray)
                    }
                }
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(alarms, key = { it.alarmId }) { alarm ->
                            GroupAlarmCard(
                                alarm = alarm,
                                onDelete = {
                                    cancelGroupAlarm(context, alarm.alarmId)
                                    db.collection("groupAlarms").document(alarm.alarmId)
                                        .update(
                                            mapOf(
                                                "status" to "cancelled",
                                                "cancelledAt" to FieldValue.serverTimestamp()
                                            )
                                        )
                                        .addOnSuccessListener {
                                            Toast.makeText(context, context.getString(R.string.group_alarm_deleted), Toast.LENGTH_SHORT).show()
                                        }
                                }
                            )
                        }
                    }
                }
            }
        }

        if (showAddDialog) {
            GroupAlarmDialog(
                group = group,
                onDismiss = { showAddDialog = false }
            )
        }
    }
}

@Composable
fun GroupAlarmCard(alarm: ScheduledGroupAlarm, onDelete: () -> Unit) {
    val timeText = String.format(Locale.getDefault(), "%02d:%02d", alarm.hour, alarm.minute)
    val nextText = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date(alarm.scheduledAtMillis))
    val weekdayLabels = mapOf(
        Calendar.MONDAY to stringResource(R.string.weekday_monday),
        Calendar.TUESDAY to stringResource(R.string.weekday_tuesday),
        Calendar.WEDNESDAY to stringResource(R.string.weekday_wednesday),
        Calendar.THURSDAY to stringResource(R.string.weekday_thursday),
        Calendar.FRIDAY to stringResource(R.string.weekday_friday),
        Calendar.SATURDAY to stringResource(R.string.weekday_saturday),
        Calendar.SUNDAY to stringResource(R.string.weekday_sunday)
    )
    val repeatText = if (alarm.repeatDays.isEmpty()) {
        stringResource(R.string.once_alarm)
    } else {
        alarm.repeatDays.sorted().joinToString(" ") { weekdayLabels[it].orEmpty() }
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(timeText, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                Text(repeatText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.next_alarm_time, nextText), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                Text(stringResource(R.string.call_member_count, alarm.targetUids.size), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_alarm), tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupAlarmDialog(group: Group, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val calendar = remember { Calendar.getInstance().apply { add(Calendar.MINUTE, 5) } }
    var selectedTimeMillis by remember { mutableStateOf(calendar.timeInMillis) }
    var repeatDays by remember { mutableStateOf<List<Int>>(emptyList()) }
    var isScheduling by remember { mutableStateOf(false) }
    val dateFormatter = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()) }

    fun refreshSelectedTime() {
        selectedTimeMillis = calendar.timeInMillis
    }

    AlertDialog(
        onDismissRequest = { if (!isScheduling) onDismiss() },
        title = { Text(stringResource(R.string.set_group_alarm)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.group_alarm_description, group.memberCount),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = dateFormatter.format(Date(selectedTimeMillis)),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            DatePickerDialog(
                                context,
                                { _, year, month, dayOfMonth ->
                                    calendar.set(Calendar.YEAR, year)
                                    calendar.set(Calendar.MONTH, month)
                                    calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                                    refreshSelectedTime()
                                },
                                calendar.get(Calendar.YEAR),
                                calendar.get(Calendar.MONTH),
                                calendar.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.DateRange, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.choose_date))
                    }
                    OutlinedButton(
                        onClick = {
                            TimePickerDialog(
                                context,
                                { _, hourOfDay, minute ->
                                    calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                                    calendar.set(Calendar.MINUTE, minute)
                                    calendar.set(Calendar.SECOND, 0)
                                    calendar.set(Calendar.MILLISECOND, 0)
                                    refreshSelectedTime()
                                },
                                calendar.get(Calendar.HOUR_OF_DAY),
                                calendar.get(Calendar.MINUTE),
                                true
                            ).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Schedule, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.choose_time))
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.repeat_weekdays), fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                WeekdaySelector(
                    selectedDays = repeatDays,
                    onToggleDay = { day ->
                        repeatDays = if (repeatDays.contains(day)) {
                            repeatDays - day
                        } else {
                            (repeatDays + day).sorted()
                        }
                    }
                )
            }
        },
        confirmButton = {
            Button(
                enabled = !isScheduling,
                onClick = {
                    val uid = FirebaseAuth.getInstance().currentUser?.uid
                    if (uid == null) {
                        Toast.makeText(context, context.getString(R.string.auth_failed), Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (group.memberIds.isEmpty()) {
                        Toast.makeText(context, context.getString(R.string.no_members_to_call), Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    isScheduling = true
                    db.collection("users").document(uid).get()
                        .addOnSuccessListener { userDoc ->
                            val senderName = userDoc.getString("username") ?: context.getString(R.string.group_member)
                            val alarmId = db.collection("groupAlarms").document().id
                            val triggerAtMillis = calculateNextAlarmTime(selectedTimeMillis, repeatDays)
                            val scheduled = scheduleGroupAlarm(
                                context = context,
                                group = group,
                                senderId = uid,
                                senderName = senderName,
                                triggerAtMillis = triggerAtMillis,
                                alarmId = alarmId,
                                repeatDays = repeatDays
                            )

                            if (scheduled) {
                                val alarmData = hashMapOf(
                                    "alarmId" to alarmId,
                                    "createdBy" to uid,
                                    "senderName" to senderName,
                                    "groupId" to group.id,
                                    "groupName" to group.name,
                                    "targetUids" to group.memberIds,
                                    "hour" to calendar.get(Calendar.HOUR_OF_DAY),
                                    "minute" to calendar.get(Calendar.MINUTE),
                                    "repeatDays" to repeatDays,
                                    "scheduledAtMillis" to triggerAtMillis,
                                    "scheduledAt" to Date(triggerAtMillis),
                                    "status" to "scheduled",
                                    "createdAt" to FieldValue.serverTimestamp()
                                )
                                db.collection("groupAlarms").document(alarmId).set(alarmData)
                                    .addOnSuccessListener {
                                        isScheduling = false
                                        Toast.makeText(context, context.getString(R.string.group_alarm_scheduled), Toast.LENGTH_SHORT).show()
                                        onDismiss()
                                    }
                                    .addOnFailureListener { e ->
                                        cancelGroupAlarm(context, alarmId)
                                        isScheduling = false
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.group_alarm_schedule_failed, e.message ?: ""),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                            } else {
                                isScheduling = false
                            }
                        }
                        .addOnFailureListener { e ->
                            isScheduling = false
                            Toast.makeText(context, context.getString(R.string.group_alarm_schedule_failed, e.message ?: ""), Toast.LENGTH_SHORT).show()
                        }
                }
            ) {
                Text(stringResource(R.string.schedule_alarm))
            }
        },
        dismissButton = {
            TextButton(enabled = !isScheduling, onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun WeekdaySelector(selectedDays: List<Int>, onToggleDay: (Int) -> Unit) {
    val days = listOf(
        Calendar.MONDAY,
        Calendar.TUESDAY,
        Calendar.WEDNESDAY,
        Calendar.THURSDAY,
        Calendar.FRIDAY,
        Calendar.SATURDAY,
        Calendar.SUNDAY
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        days.chunked(4).forEach { rowDays ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowDays.forEach { day ->
                    FilterChip(
                        selected = selectedDays.contains(day),
                        onClick = { onToggleDay(day) },
                        label = { Text(weekdayLabel(day)) }
                    )
                }
            }
        }
    }
}

@Composable
fun weekdayLabel(day: Int): String {
    return when (day) {
        Calendar.MONDAY -> stringResource(R.string.weekday_monday)
        Calendar.TUESDAY -> stringResource(R.string.weekday_tuesday)
        Calendar.WEDNESDAY -> stringResource(R.string.weekday_wednesday)
        Calendar.THURSDAY -> stringResource(R.string.weekday_thursday)
        Calendar.FRIDAY -> stringResource(R.string.weekday_friday)
        Calendar.SATURDAY -> stringResource(R.string.weekday_saturday)
        Calendar.SUNDAY -> stringResource(R.string.weekday_sunday)
        else -> ""
    }
}

private fun calculateNextAlarmTime(selectedTimeMillis: Long, repeatDays: List<Int>): Long {
    val selected = Calendar.getInstance().apply {
        timeInMillis = selectedTimeMillis
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    if (repeatDays.isEmpty()) {
        return selected.timeInMillis
    }

    val now = Calendar.getInstance()
    return repeatDays.map { day ->
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, selected.get(Calendar.HOUR_OF_DAY))
            set(Calendar.MINUTE, selected.get(Calendar.MINUTE))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            set(Calendar.DAY_OF_WEEK, day)
            if (!after(now)) {
                add(Calendar.WEEK_OF_YEAR, 1)
            }
        }.timeInMillis
    }.minOrNull() ?: selected.timeInMillis
}

private fun scheduleGroupAlarm(
    context: Context,
    group: Group,
    senderId: String,
    senderName: String,
    triggerAtMillis: Long,
    alarmId: String,
    repeatDays: List<Int> = emptyList()
): Boolean {
    if (triggerAtMillis <= System.currentTimeMillis()) {
        Toast.makeText(context, context.getString(R.string.group_alarm_time_in_past), Toast.LENGTH_SHORT).show()
        return false
    }

    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
        Toast.makeText(context, context.getString(R.string.exact_alarm_permission_needed), Toast.LENGTH_LONG).show()
        context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return false
    }

    val intent = Intent(context, GroupAlarmReceiver::class.java).apply {
        putExtra(GroupAlarmReceiver.EXTRA_ALARM_ID, alarmId)
        putExtra(GroupAlarmReceiver.EXTRA_SENDER_ID, senderId)
        putExtra(GroupAlarmReceiver.EXTRA_SENDER_NAME, senderName)
        putExtra(GroupAlarmReceiver.EXTRA_GROUP_ID, group.id)
        putExtra(GroupAlarmReceiver.EXTRA_GROUP_NAME, group.name)
        putExtra(GroupAlarmReceiver.EXTRA_HOUR, Calendar.getInstance().apply { timeInMillis = triggerAtMillis }.get(Calendar.HOUR_OF_DAY))
        putExtra(GroupAlarmReceiver.EXTRA_MINUTE, Calendar.getInstance().apply { timeInMillis = triggerAtMillis }.get(Calendar.MINUTE))
        putStringArrayListExtra(GroupAlarmReceiver.EXTRA_TARGET_UIDS, ArrayList(group.memberIds))
        putIntegerArrayListExtra(GroupAlarmReceiver.EXTRA_REPEAT_DAYS, ArrayList(repeatDays))
    }
    val pendingIntent = PendingIntent.getBroadcast(
        context,
        alarmId.hashCode(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
    } else {
        alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
    }
    return true
}

private fun cancelGroupAlarm(context: Context, alarmId: String) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val pendingIntent = PendingIntent.getBroadcast(
        context,
        alarmId.hashCode(),
        Intent(context, GroupAlarmReceiver::class.java),
        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
    )
    if (pendingIntent != null) {
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }
}

@Composable
fun JoinGroupDialog(onDismiss: () -> Unit, onJoin: (String) -> Unit) {
    var code by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.join_group)) },
        text = { OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text(stringResource(R.string.invite_code)) }) },
        confirmButton = { Button(onClick = { if(code.isNotEmpty()) onJoin(code) }) { Text(stringResource(R.string.join)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
