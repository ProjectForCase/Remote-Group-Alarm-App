package com.example.finalproject

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
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
    var expanded by remember { mutableStateOf(false) }
    val options = listOf(
        R.string.cat_work to "工作",
        R.string.cat_study to "學習",
        R.string.cat_exercise to "運動",
        R.string.cat_rest to "休息",
        R.string.cat_read to "閱讀"
    )
    val mainColor = MaterialTheme.colorScheme.primary

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(BorderStroke(1.dp, Color.LightGray), RoundedCornerShape(12.dp))
                .clickable(enabled = !viewModel.isRunning && viewModel.timerSeconds == 0) { expanded = true }
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val isInteractionDisabled = viewModel.isRunning || viewModel.timerSeconds > 0
                val currentDisplayType = when(viewModel.selectedType) {
                    "工作" -> stringResource(R.string.cat_work)
                    "學習" -> stringResource(R.string.cat_study)
                    "運動" -> stringResource(R.string.cat_exercise)
                    "休息" -> stringResource(R.string.cat_rest)
                    "閱讀" -> stringResource(R.string.cat_read)
                    else -> viewModel.selectedType
                }
                Text(
                    text = currentDisplayType,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isInteractionDisabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) else MaterialTheme.colorScheme.onSurface
                )
                Icon(
                    Icons.Default.ArrowDropDown, 
                    contentDescription = null, 
                    tint = if (isInteractionDisabled) Color.Gray.copy(alpha = 0.38f) else Color.Gray
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (resId, internalName) ->
                    DropdownMenuItem(text = { Text(stringResource(resId)) }, onClick = {
                        viewModel.onTypeChange(internalName)
                        expanded = false
                    })
                }
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = viewModel.formatTime(viewModel.timerSeconds),
                fontSize = 88.sp,
                fontWeight = FontWeight.W200,
                fontFamily = FontFamily.Monospace
            )
            Box(modifier = Modifier.width(60.dp).height(2.dp).background(mainColor.copy(alpha = 0.3f)))
        }

        // 3. 按鈕區域
        if (viewModel.isRunning) {
            // 執行中：顯示 STOP 鍵
            Surface(
                modifier = Modifier
                    .size(180.dp)
                    .clip(CircleShape)
                    .clickable { viewModel.toggleTimer() },
                color = Color.Red
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "STOP",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        color = Color.White
                    )
                }
            }
        } else if (viewModel.timerSeconds > 0) {
            // 暫停中：顯示 CONTINUE 與 COMPLETE 鍵
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // CONTINUE 鍵
                Surface(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .clickable { viewModel.startTimer() },
                    color = mainColor
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "CONTINUE",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                // COMPLETE 鍵
                Surface(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .clickable { viewModel.completeTimer() },
                    color = Color.Gray // 使用灰色或另一個顏色區分
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "COMPLETE",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        } else {
            // 未開始：顯示 START 鍵
            Surface(
                modifier = Modifier
                    .size(180.dp)
                    .clip(CircleShape)
                    .clickable { viewModel.toggleTimer() },
                color = mainColor
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "START",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        color = Color.White
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
fun HomeTabContent(userEmail: String) {
    val loadingText = stringResource(R.string.loading)
    var username by remember { mutableStateOf(loadingText) }
    var photoUrl by remember { mutableStateOf<String?>(null) }
    val uid = FirebaseAuth.getInstance().currentUser?.uid

    // Placeholder data (will be dynamic in future)
    val todayHours = 0
    val todayMinutes = 0
    val weeklyTotalHours = 0
    val weeklyTrends = listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f) // Mon to Sun
    val categoryData = emptyList<Pair<String, Float>>() // Empty for now

    LaunchedEffect(uid) {
        if (uid != null) {
            FirebaseFirestore.getInstance().collection("users").document(uid).get()
                .addOnSuccessListener { doc ->
                    username = doc.getString("username") ?: "使用者"
                    photoUrl = doc.getString("photoUrl")
                }
        }
    }

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
            // Category Stats Chart logic would go here
        }
        
        Spacer(modifier = Modifier.height(100.dp)) // Extra space for bottom nav
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
            .height(120.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        trends.forEachIndexed { index, value ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .width(16.dp)
                        .fillMaxHeight(fraction = if (value <= 0.05f) 0.05f else value)
                        .background(barColor, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = stringResource(days[index]), fontSize = 10.sp, color = Color.Gray)
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
