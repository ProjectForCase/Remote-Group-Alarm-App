package com.example.finalproject

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.finalproject.ui.theme.FinalProjectTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging

class MainActivity : AppCompatActivity() {

    // 觀察是否處於被叫醒的狀態
    private val isWakingUpState = mutableStateOf(false)

    // 通知權限請求器
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) {
            Toast.makeText(this, "需要通知權限才能接收呼叫", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. 處理啟動時的 Intent (判斷是否從鬧鐘推播進來)
        handleIntent(intent)

        // 設定語言
        if (AppCompatDelegate.getApplicationLocales().isEmpty) {
            val appLocale: LocaleListCompat = LocaleListCompat.forLanguageTags("zh-TW")
            AppCompatDelegate.setApplicationLocales(appLocale)
        }

        // 2. 讓 Activity 可以在鎖定畫面顯示並點亮螢幕
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        enableEdgeToEdge()

        // 3. 檢查權限
        askNotificationPermission()
        checkOverlayPermission() // 檢查「顯示在其他應用程式上層」權限
        updateFcmToken()

        setContent {
            val themeViewModel: ThemeViewModel = viewModel()
            FinalProjectTheme(darkTheme = themeViewModel.isDarkMode) {
                val auth = FirebaseAuth.getInstance()
                val isWakingUp by isWakingUpState

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (isWakingUp) {
                        // 顯示鬧鐘叫醒畫面
                        WakeUpScreen(onDismiss = {
                            isWakingUpState.value = false
                        })
                    } else {
                        // 正常的導覽邏輯
                        var currentScreen by remember {
                            mutableStateOf(if (auth.currentUser != null) "home" else "login")
                        }

                        when (currentScreen) {
                            "login" -> {
                                LoginScreen(
                                    onLoginSuccess = {
                                        currentScreen = "home"
                                        updateFcmToken()
                                    },
                                    onNavigateToSignUp = { currentScreen = "signup" }
                                )
                            }
                            "signup" -> {
                                SignUpScreen(
                                    onSignUpSuccess = {
                                        currentScreen = "home"
                                        updateFcmToken()
                                    },
                                    onBackToLogin = { currentScreen = "login" }
                                )
                            }
                            "home" -> {
                                MainAppContent(
                                    userEmail = auth.currentUser?.email ?: "使用者",
                                    onLogout = {
                                        auth.signOut()
                                        currentScreen = "login"
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 當 App 已經開啟時，收到新的 Intent (例如點擊推播)
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    // 核心邏輯：判斷 Intent 是否帶有鬧鐘觸發標記
    private fun handleIntent(intent: Intent?) {
        val fromNotification = intent?.getBooleanExtra("from_notification", false) ?: false
        if (fromNotification) {
            isWakingUpState.value = true
        }
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "請開啟「顯示在上方」權限以確保鬧鐘彈出", Toast.LENGTH_LONG).show()
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
        }
    }

    private fun updateFcmToken() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                FirebaseFirestore.getInstance().collection("users").document(uid)
                    .update("fcmToken", token)
            }
        }
    }
}

@Composable
fun WakeUpScreen(onDismiss: () -> Unit) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "⏰ 呼叫起床！！",
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.Black
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "有人在遠方叫你，快起來專注！",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = {
                // 👇 重點：停止背景的鬧鐘服務，音樂才會停
                context.stopService(Intent(context, AlarmService::class.java))

                // 清除通知欄位上的通知
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(1001) // 服務用的 ID
                notificationManager.cancel(1002) // 推播用的 ID

                onDismiss()
            },
            modifier = Modifier.fillMaxWidth().height(64.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Text("我起床了！", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
    }
}