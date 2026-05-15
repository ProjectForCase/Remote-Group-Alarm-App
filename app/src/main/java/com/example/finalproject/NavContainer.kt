package com.example.finalproject

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel

sealed class Screen(val route: String, val titleResId: Int, val icon: ImageVector) {
    object Home : Screen("home_tab", R.string.tab_home, Icons.Default.Home)
    object Focus : Screen("focus_tab", R.string.tab_focus, Icons.Default.SelfImprovement)
    object Groups : Screen("groups_tab", R.string.tab_groups, Icons.Default.Groups)
    object Settings : Screen("settings_tab", R.string.tab_settings, Icons.Default.Settings)
}

/**
 * AppNavContainer (原名 MainAppContent)
 * 這是 App 的主要容器，負責底部導覽列、頂部標題列以及各個頁面 (Home, Focus, Groups, Settings) 的切換。
 * 它本身不包含具體的畫面內容，而是作為一個「外殼」來裝載不同功能的 Screen。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavContainer(userEmail: String, onLogout: () -> Unit) {
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

    // 當離開專注分頁時自動暫停計時器
    LaunchedEffect(selectedScreen) {
        if (selectedScreen != Screen.Focus && focusViewModel.isRunning) {
            focusViewModel.pauseTimer()
        }
    }

    // 生命週期監聽：當 App 進入後台時暫停計時
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
            // 群組頁面和頭貼選擇頁面不顯示預設標題列
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
            // 頭貼選擇頁面隱藏底部導覽列
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
                // 顯示頭貼選擇
                AvatarSelectionScreen(onBack = { isSelectingAvatar = false })
            } else {
                // 根據選中的分頁顯示對應內容
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
