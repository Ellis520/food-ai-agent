package com.foodmoment.app

import android.app.Application
import com.foodmoment.app.ai.ChatEngine
import com.foodmoment.app.core.Settings
import com.foodmoment.app.data.BackupService
import com.foodmoment.app.data.FoodDatabase
import com.foodmoment.app.data.Repository
import com.foodmoment.app.net.CommunityClient
import com.foodmoment.app.net.LlmClient
import com.foodmoment.app.net.VisionClient

/**
 * 应用入口。采用手写依赖容器（不引入 Hilt），
 * 因为本工程在 AGP 9 的内置 Kotlin 环境下，注解处理器（KSP/kapt）风险较高。
 */
class App : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(app: Application) {

    val settings: Settings = Settings(app)

    val db: FoodDatabase = FoodDatabase(app)

    val llm: LlmClient = LlmClient(settings)

    val vision: VisionClient = VisionClient(settings)

    val community: CommunityClient = CommunityClient(settings)

    val amap: com.foodmoment.app.net.AmapClient = com.foodmoment.app.net.AmapClient(settings)

    val repo: Repository = Repository(app, db, settings, llm, community)

    val chat: ChatEngine = ChatEngine(repo, settings)

    val backup: BackupService = BackupService(app, db, repo.images)
}
