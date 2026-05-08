package com.example.finalproject

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.finalproject.ui.theme.FinalProjectTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : AppCompatActivity() {

    private val isWakingUpState = mutableStateOf(false)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted
        } else {
            Toast.makeText(this, "需要通知權限才能接收呼叫", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 處理啟動時的 Intent
        handleIntent(intent)

        // 設定初始語言為繁體中文
        if (AppCompatDelegate.getApplicationLocales().isEmpty) {
            val appLocale: LocaleListCompat = LocaleListCompat.forLanguageTags("zh-TW")
            AppCompatDelegate.setApplicationLocales(appLocale)
        }

        // 讓 Activity 可以在鎖定畫面顯示並點亮螢幕
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
        
        askNotificationPermission()
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
                        WakeUpScreen(onDismiss = { isWakingUpState.value = false })
                    } else {
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra("from_notification", false) == true) {
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
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth().height(64.dp)
        ) {
            Text("我起床了！", fontSize = 24.sp)
        }
    }
}
