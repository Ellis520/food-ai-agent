package com.foodmoment.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.foodmoment.app.ui.AppRoot
import com.foodmoment.app.ui.theme.FoodMomentTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as App).container

        // 启动时清理超过 7 天的回收站（12.4）
        startupScope.launch {
            runCatching { container.repo.purgeExpiredTrash() }
        }

        setContent {
            FoodMomentTheme {
                AppRoot(container)
            }
        }
    }

    override fun onDestroy() {
        startupScope.cancel()
        super.onDestroy()
    }
}
