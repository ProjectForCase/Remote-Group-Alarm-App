package com.example.finalproject

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.res.stringResource
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import android.content.Intent
import coil.compose.AsyncImage
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.util.Calendar

sealed class Screen(val route: String, val titleResId: Int, val icon: ImageVector) {
    object Home : Screen("home_tab", R.string.tab_home, Icons.Default.Home)
    object Focus : Screen("focus_tab", R.string.tab_focus, Icons.Default.SelfImprovement)
    object Groups : Screen("groups_tab", R.string.tab_groups, Icons.Default.Groups)
    object Settings : Screen("settings_tab", R.string.tab_settings, Icons.Default.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContent(userEmail: String, onLogout: () -> Unit) {
    var selectedScreen by remember { mutableStateOf<Screen>(Screen.Home) }
    var isSelectingAvatar by remember { mutableStateOf(false) }
    val focusViewModel: FocusViewModel = viewModel()
    val themeViewModel: ThemeViewModel = viewModel()
    
    val items = listOf(
        Screen.Home,
        Screen.Focus,
        Screen.Groups,
        Screen.Settings
    )

    LaunchedEffect(selectedScreen) {
        if (selectedScreen != Screen.Focus && focusViewModel.isRunning) {
            focusViewModel.pauseTimer()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                if (focusViewModel.isRunning) {
                    focusViewModel.pauseTimer()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
            if (selectedScreen != Screen.Groups && !isSelectingAvatar) {
                CenterAlignedTopAppBar(
                    title = { 
                        Text(
                            text = stringResource(selectedScreen.titleResId),
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        ) 
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        },
        bottomBar = {
            if (!isSelectingAvatar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp
                ) {
                    items.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = stringResource(screen.titleResId)) },
                            label = { Text(stringResource(screen.titleResId)) },
                            selected = selectedScreen == screen,
                            onClick = { selectedScreen = screen },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                indicatorColor = Color.Transparent
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        val modifier = if (selectedScreen == Screen.Groups || isSelectingAvatar) {
            Modifier.fillMaxSize().padding(bottom = innerPadding.calculateBottomPadding())
        } else {
            Modifier.fillMaxSize().padding(innerPadding)
        }

        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            if (isSelectingAvatar) {
                AvatarSelectionScreen(onBack = { isSelectingAvatar = false })
            } else {
                when (selectedScreen) {
                    is Screen.Home -> HomeTabContent(userEmail)
                    is Screen.Focus -> FocusTabContent(focusViewModel)
                    is Screen.Groups -> GroupMainScreen(onBackToHome = { selectedScreen = Screen.Home })
                    is Screen.Settings -> SettingsTabContent(onLogout, themeViewModel, onNavigateToAvatar = { isSelectingAvatar = true })
                }
            }
        }
    }
}

@Composable
fun FocusTabContent(viewModel: FocusViewModel) {
    val options = listOf(
        FocusCategory(R.string.cat_work, "工作", Icons.Default.Assignment),
        FocusCategory(R.string.cat_read, "閱讀", Icons.Default.MenuBook),
        FocusCategory(R.string.cat_meditation, "冥想", Icons.Default.SelfImprovement),
        FocusCategory(R.string.cat_study, "學習", Icons.Default.School),
        FocusCategory(R.string.cat_exercise, "運動", Icons.Default.FitnessCenter),
        FocusCategory(R.string.cat_rest, "休息", Icons.Default.Bedtime)
    )
    val mainColor = Color(0xFF673AB7) // 紫色基調

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        
        // 1. 標籤式切換
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            options.forEach { category ->
                val isSelected = viewModel.selectedType == category.internalName
                val isInteractionDisabled = viewModel.isRunning || viewModel.timerSeconds > 0
                
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .clickable(enabled = !isInteractionDisabled) {
                            viewModel.onTypeChange(category.internalName)
                        },
                    color = if (isSelected) mainColor else Color.Transparent,
                    border = if (isSelected) null else BorderStroke(1.dp, Color.LightGray),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = category.icon,
                            contentDescription = null,
                            tint = if (isSelected) Color.White else Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(category.titleRes),
                            color = if (isSelected) Color.White else Color.Gray,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // 2. 圓形計時器區域
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(300.dp)
        ) {
            // 背景圓環
            Canvas(modifier = Modifier.size(280.dp)) {
                drawCircle(
                    color = mainColor.copy(alpha = 0.1f),
                    style = Stroke(width = 8.dp.toPx())
                )
            }
            
            // 進度圓環
            Canvas(modifier = Modifier.size(280.dp)) {
                drawArc(
                    color = mainColor,
                    startAngle = -90f,
                    sweepAngle = 360f * (viewModel.timerSeconds % 60 / 60f), 
                    useCenter = false,
                    style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            Text(
                text = viewModel.formatTime(viewModel.timerSeconds),
                fontSize = 80.sp,
                fontWeight = FontWeight.W200,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // 3. 控制按鈕
        if (viewModel.isRunning) {
            Button(
                onClick = { viewModel.toggleTimer() },
                modifier = Modifier
                    .width(120.dp)
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.8f))
            ) {
                Text("STOP", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
        } else if (viewModel.timerSeconds > 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(
                    onClick = { viewModel.startTimer() },
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.height(56.dp)
                ) {
                    Text("CONTINUE")
                }
                Button(
                    onClick = { viewModel.completeTimer() },
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
                ) {
                    Text("FINISH")
                }
            }
        } else {
            Button(
                onClick = { viewModel.toggleTimer() },
                modifier = Modifier
                    .width(150.dp)
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = mainColor.copy(alpha = 0.2f), contentColor = mainColor)
            ) {
                Text("START", fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            }
        }
        
        Spacer(modifier = Modifier.height(48.dp))
    }
}

data class FocusCategory(
    val titleRes: Int,
    val internalName: String,
    val icon: ImageVector
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

    DisposableEffect(uid) {
        val db = FirebaseFirestore.getInstance()
        var userListener: ListenerRegistration? = null
        var recordsListener: ListenerRegistration? = null

        if (uid != null) {
            // 1. 監聽個人資料
            userListener = db.collection("users").document(uid).addSnapshotListener { doc, _ ->
                if (doc != null && doc.exists()) {
                    username = doc.getString("username") ?: "使用者"
                    photoUrl = doc.getString("photoUrl")
                }
            }

            // 2. 監聽專注紀錄 (使用 in-memory 過濾避免索引問題)
            recordsListener = db.collection("focus_records")
                .whereEqualTo("userId", uid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e("Firestore", "Error fetching records: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot == null) return@addSnapshotListener
                    
                    val allRecords = snapshot.documents
                    Log.d("Firestore", "Fetched ${allRecords.size} records")

                    val calendar = Calendar.getInstance()
                    calendar.set(Calendar.HOUR_OF_DAY, 0)
                    calendar.set(Calendar.MINUTE, 0)
                    calendar.set(Calendar.SECOND, 0)
                    calendar.set(Calendar.MILLISECOND, 0)
                    val todayStart = calendar.time

                    val weekCalendar = Calendar.getInstance()
                    weekCalendar.time = todayStart
                    // 將週日(1) 視為一週最後一天，計算週一(2)
                    while (weekCalendar.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
                        weekCalendar.add(Calendar.DAY_OF_YEAR, -1)
                    }
                    val weekStart = weekCalendar.time

                    // 過濾紀錄
                    val todayRecords = allRecords.filter { 
                        val ts = it.getDate("timestamp")
                        ts != null && ts >= todayStart
                    }
                    val weekRecords = allRecords.filter {
                        val ts = it.getDate("timestamp")
                        ts != null && ts >= weekStart
                    }

                    // 數據彙整
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
        // 1. Greeting
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

        // 2. Today's Focus Duration Card
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

        // 3. Weekly Focus Trend
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

        // 4. Today's Category Stats
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
            Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                categoryData.forEach { (category, ratio) ->
                    CategoryStatsRow(category = category, ratio = ratio)
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
        
        Spacer(modifier = Modifier.height(100.dp))
    }
}

@Composable
fun CategoryStatsRow(category: String, ratio: Float) {
    val displayType = when(category) {
        "工作" -> stringResource(R.string.cat_work)
        "學習" -> stringResource(R.string.cat_study)
        "運動" -> stringResource(R.string.cat_exercise)
        "休息" -> stringResource(R.string.cat_rest)
        "閱讀" -> stringResource(R.string.cat_read)
        else -> category
    }
    
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = displayType, style = MaterialTheme.typography.bodyMedium)
            Text(text = "${(ratio * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { ratio },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        )
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
                // 長條圖部分，放在一個 weight(1f) 的 Box 中以確保標籤不會被擠出去
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

@Composable
fun SettingsTabContent(onLogout: () -> Unit, themeViewModel: ThemeViewModel, onNavigateToAvatar: () -> Unit) {
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val context = LocalContext.current
    val user = auth.currentUser

    var username by remember { mutableStateOf("...") }
    var photoUrl by remember { mutableStateOf<String?>(null) }
    var showNameDialog by remember { mutableStateOf(false) }
    var showPwdDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    LaunchedEffect(user?.uid) {
        if (user != null) {
            db.collection("users").document(user.uid).addSnapshotListener { doc, _ ->
                if (doc != null) {
                    username = doc.getString("username") ?: "使用者"
                    photoUrl = doc.getString("photoUrl")
                }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // 使用者資訊與頭貼
        Box(contentAlignment = Alignment.Center) {
            if (photoUrl != null) {
                AsyncImage(
                    model = photoUrl,
                    contentDescription = "頭貼",
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(60.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = username, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(text = user?.email ?: "", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)

        Spacer(modifier = Modifier.height(32.dp))

        // 修改頭貼功能
        OutlinedButton(
            onClick = onNavigateToAvatar,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.PhotoCamera, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("修改頭貼")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 修改暱稱按鈕
        OutlinedButton(
            onClick = { showNameDialog = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Edit, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.edit_nickname))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 修改密碼按鈕
        OutlinedButton(
            onClick = { showPwdDialog = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Lock, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.change_password))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 語言設定按鈕
        OutlinedButton(
            onClick = { showLanguageDialog = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Language, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.language_settings))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 深色模式切換
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Brightness4, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.dark_mode))
            }
            Switch(
                checked = themeViewModel.isDarkMode,
                onCheckedChange = { themeViewModel.toggleDarkMode() }
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // 登出按鈕
        Button(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (themeViewModel.isDarkMode) Color(0xFF333333) else Color(0xFFF5F5F5),
                contentColor = Color.Red
            ),
            elevation = null
        ) {
            Text(stringResource(R.string.logout))
        }
    }

    // 語言選擇彈窗
    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(stringResource(R.string.select_language)) },
            text = {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val appLocale: LocaleListCompat = LocaleListCompat.forLanguageTags("zh-TW")
                                AppCompatDelegate.setApplicationLocales(appLocale)
                                showLanguageDialog = false
                                // 重啟 App 以確保語言生效
                                val intent = Intent(context, MainActivity::class.java)
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                context.startActivity(intent)
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.lang_zh))
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val appLocale: LocaleListCompat = LocaleListCompat.forLanguageTags("en")
                                AppCompatDelegate.setApplicationLocales(appLocale)
                                showLanguageDialog = false
                                // 重啟 App 以確保語言生效
                                val intent = Intent(context, MainActivity::class.java)
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                context.startActivity(intent)
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.lang_en))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // 修改暱稱彈窗
    if (showNameDialog) {
        var newName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = { Text(stringResource(R.string.edit_nickname)) },
            text = {
                OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text(stringResource(R.string.new_nickname)) })
            },
            confirmButton = {
                Button(onClick = {
                    if (newName.isNotBlank() && user != null) {
                        db.collection("users").document(user.uid).update("username", newName)
                            .addOnSuccessListener {
                                Toast.makeText(context, context.getString(R.string.update_success), Toast.LENGTH_SHORT).show()
                                showNameDialog = false
                            }
                    }
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = { TextButton(onClick = { showNameDialog = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    // 修改密碼彈窗
    if (showPwdDialog) {
        var oldPwd by remember { mutableStateOf("") }
        var newPwd by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPwdDialog = false },
            title = { Text(stringResource(R.string.change_password)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = oldPwd, onValueChange = { oldPwd = it },
                        label = { Text(stringResource(R.string.old_password)) },
                        visualTransformation = PasswordVisualTransformation()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newPwd, onValueChange = { newPwd = it },
                        label = { Text(stringResource(R.string.new_password_hint)) },
                        visualTransformation = PasswordVisualTransformation()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (newPwd.length >= 6 && user?.email != null) {
                        val credential = EmailAuthProvider.getCredential(user.email!!, oldPwd)
                        user.reauthenticate(credential).addOnCompleteListener { reAuthTask ->
                            if (reAuthTask.isSuccessful) {
                                user.updatePassword(newPwd).addOnSuccessListener {
                                    Toast.makeText(context, context.getString(R.string.update_success), Toast.LENGTH_SHORT).show()
                                    photoUrl = null // 觸發頭貼重新整理(選用)
                                    showPwdDialog = false
                                }.addOnFailureListener {
                                    Toast.makeText(context, "${context.getString(R.string.update_failed)}: ${it.message}", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context, context.getString(R.string.wrong_password), Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        Toast.makeText(context, context.getString(R.string.invalid_password), Toast.LENGTH_SHORT).show()
                    }
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = { TextButton(onClick = { showPwdDialog = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}
