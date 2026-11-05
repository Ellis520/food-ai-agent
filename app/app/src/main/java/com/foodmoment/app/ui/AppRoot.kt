package com.foodmoment.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import com.foodmoment.app.AppContainer
import com.foodmoment.app.ui.screens.ChatScreen
import com.foodmoment.app.ui.screens.CommunityScreen
import com.foodmoment.app.ui.screens.HabitsScreen
import com.foodmoment.app.ui.screens.HomeScreen
import com.foodmoment.app.ui.screens.MenuScreen
import com.foodmoment.app.ui.screens.MapScreen
import com.foodmoment.app.ui.screens.NearbyScreen
import com.foodmoment.app.ui.screens.NutritionScreen
import com.foodmoment.app.ui.screens.OnboardingScreen
import com.foodmoment.app.ui.screens.PhotoScreen
import com.foodmoment.app.ui.screens.PlansScreen
import com.foodmoment.app.ui.screens.PostDetailScreen
import com.foodmoment.app.ui.screens.RecordsScreen
import com.foodmoment.app.ui.screens.SettingsScreen
import kotlinx.coroutines.launch

/**
 * 应用外壳：顶栏 + 底部导航 + 页面分发（7.1）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(container: AppContainer) {
    var onboarded by remember { mutableStateOf(container.settings.onboarded) }

    if (!onboarded) {
        OnboardingScreen(settings = container.settings) { onboarded = true }
        return
    }

    val nav = remember { Navigator(Screen.Home) }
    val scope = rememberCoroutineScope()
    var chatSessionId by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = nav.canGoBack) { nav.back() }

    val isTab = nav.current in BottomTabs

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (nav.current == Screen.Chat) "AI 聊天" else nav.current.label,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    if (!isTab) {
                        IconButton(onClick = { nav.back() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                },
                actions = {
                    if (nav.current == Screen.Chat) {
                        TextButton(onClick = {
                            scope.launch {
                                val session = container.repo.createSession()
                                chatSessionId = session.id
                            }
                        }) { Text("新会话") }
                    }
                }
            )
        },
        bottomBar = {
            if (isTab) {
                NavigationBar {
                    BottomTabs.forEach { screen ->
                        NavigationBarItem(
                            selected = nav.current == screen,
                            onClick = { nav.go(screen) },
                            icon = { Icon(iconFor(screen), contentDescription = screen.label) },
                            label = { Text(screen.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (val current = nav.current) {
                Screen.Home -> HomeScreen(
                    container = container,
                    nav = nav,
                    onOpenChat = { sessionId ->
                        chatSessionId = sessionId
                        nav.go(Screen.Chat)
                    },
                    onSessionDeleted = { deletedId ->
                        // 删掉的正好是聊天页挂着的会话时，清空引用，避免继续往已删除的会话写消息
                        if (chatSessionId == deletedId) chatSessionId = null
                    }
                )

                Screen.Chat -> ChatScreen(
                    container = container,
                    sessionId = chatSessionId,
                    onSessionChange = { chatSessionId = it }
                )

                Screen.Records -> RecordsScreen(container, nav)
                Screen.Nutrition -> NutritionScreen(container, nav)
                Screen.Photo -> PhotoScreen(container, nav)
                Screen.Menu -> MenuScreen(container, nav)
                Screen.Settings -> SettingsScreen(container, nav)
                Screen.Plans -> PlansScreen(container, nav)
                Screen.Habits -> HabitsScreen(container, nav)
                Screen.Community -> CommunityScreen(container, nav)
                is Screen.PostDetail -> PostDetailScreen(container, nav, current.postId)
                Screen.Nearby -> NearbyScreen(container, nav)
                is Screen.MapDetail -> MapScreen(container, nav, current.lng, current.lat, current.title)
                Screen.Onboarding -> OnboardingScreen(container.settings) { onboarded = true }
            }
        }
    }
}

private fun iconFor(screen: Screen): ImageVector = when (screen) {
    Screen.Home -> Icons.Filled.Home
    Screen.Chat -> Icons.Filled.Chat
    Screen.Records -> Icons.Filled.Receipt
    Screen.Photo -> Icons.Filled.PhotoCamera
    Screen.Settings -> Icons.Filled.Person
    else -> Icons.Filled.Home
}
