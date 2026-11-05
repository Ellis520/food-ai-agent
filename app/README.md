# 食刻AI（FoodMoment）Android 客户端

按《食刻AI Android 版方案与功能需求说明书 V2.0》实现的本地优先饮食助手。

- 包名：`com.foodmoment.app`
- 形态：Kotlin + Jetpack Compose 单 APK
- 成本：0 元。不内置任何 Key、域名或第三方服务；AI 能力由用户自行配置 OpenAI 兼容端点
- 数据：全部存在设备本地，只有用户主动发布的社区帖子会离开设备

---

## 1. 构建

**环境要求**

| 项 | 版本 |
| --- | --- |
| JDK | 17 或以上（AGP 9 要求） |
| Gradle | 由 wrapper 提供（9.6.0） |
| AGP | 9.4.0（内置 Kotlin 2.2.10） |
| compileSdk / targetSdk | 37 |
| minSdk | 24（Android 7.0） |
| Compose BOM | 2026.02.01 |

**命令行**

```bash
./gradlew :app:assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

**Android Studio**：直接 Open 本目录（`app/settings.gradle.kts` 所在目录），等待同步完成后 Run。

> 首次构建需要联网下载依赖。若公司网络受限，请先配置 `~/.gradle/init.gradle` 镜像。

---

## 2. 目录结构

```
app/src/main/java/com/foodmoment/app/
├── App.kt                  Application + 手写依赖容器
├── MainActivity.kt         单 Activity 入口
├── core/                   加密、配置、金额、时间
│   ├── Crypto.kt           Keystore AES-256-GCM
│   ├── Settings.kt         本地配置（SharedPreferences）
│   ├── Money.kt            分↔元，禁止浮点
│   └── Time.kt             UTC 存储 + 固定 Asia/Shanghai 归属
├── data/                   数据层
│   ├── Models.kt           实体
│   ├── FoodDatabase.kt     SQLite 建表与 DAO
│   ├── Repository.kt       唯一数据出入口 + revision 刷新信号
│   ├── SeedFoods.kt        内置食物 + 离线模板
│   ├── ImageStore.kt       图片压缩落盘
│   └── BackupService.kt    zip 导出 / 导入
├── domain/Calc.kt          预算 / 营养 / 计划校验 / 习惯（纯函数）
├── ai/ChatEngine.kt        对话编排 + 卡片构造 + 事实包
├── net/
│   ├── LlmClient.kt        OpenAI 兼容，容错解析
│   ├── CommunityClient.kt  社区后端（匿名会话 cookie）
│   └── ImageLoader.kt      极简图片加载
└── ui/
    ├── AppRoot.kt          外壳：顶栏 + 底部导航 + 分发
    ├── Nav.kt              页面与回退栈
    ├── components/         通用组件、卡片渲染、Canvas 图表
    └── screens/            引导 / 首页 / 聊天 / 计划 / 记录 / 营养 / 分析 / 社区 / 设置
```

---

## 3. 与方案文档的实现偏差（重要）

为在 **AGP 9 内置 Kotlin + 无注解处理器** 的前提下保证一次编译通过，以下四处做了等价替换，语义不变：

| 方案文档 | 本实现 | 原因 |
| --- | --- | --- |
| Room | 手写 `SQLiteOpenHelper` + DAO | Room 依赖 KSP；AGP 9 内置 Kotlin 下注解处理器存在兼容风险 |
| Hilt | `AppContainer` 手写依赖容器 | Hilt 同样依赖 KSP/kapt |
| DataStore | `SharedPreferences`（敏感项仍走 Keystore 加密） | 少一层协程依赖，语义等价 |
| navigation-compose | `Navigator` 手写回退栈（约 30 行） | 避免 Compose 1.10 与 navigation 版本的额外对齐面 |
| Coil | `ImageLoader`（OkHttp + BitmapFactory + LRU） | 减少三方依赖 |
| kotlinx.serialization | `org.json` | 序列化插件在内置 Kotlin 下需额外配置 |

多模块（`core/*`、`feature/*`）也收敛为单模块 + 包分层：模块拆分不改变代码组织，但会成倍增加构建配置面。若后续要拆，`domain/` 与 `data/` 已是独立包，迁移成本很低。

**没有妥协的部分**：金额一律 `Long` 分、营养与预算全部本地纯函数、模型不产出数值、卡片由本地构造、Keystore 加密、无任何埋点。

---

## 4. 首次运行

1. 启动后先看引导三页（可全程跳过）。
2. 不配置模型也能用：记录、营养换算、习惯统计、离线模板计划、数据导出。
3. 要启用 AI：设置 → 模型，填 Base URL / 模型名 / API Key，点「测试连接」。
   - 云厂商示例：`https://api.deepseek.com/v1` + `deepseek-chat`
   - 本地 Ollama：`http://<电脑局域网IP>:11434/v1`，并在设置里打开「允许明文 HTTP」
   - 手机上的 `127.0.0.1` 指向手机自身，填它一定连不上
4. 美食圈默认关闭。要启用需自建后端（见同级目录 `../app_backend/`），默认不发起任何网络请求。
5. 拍照识别菜品：底部「拍照」→ 拍摄或选图 → 识别 → 确认份数/克重 → 保存到记录。
   - 识别模型在「我的 → 菜品识别」配置，默认模型 `deepseek-flash`；Base URL / Key 留空则沿用文本模型配置
   - 菜品只从菜单里挑，菜单默认来自 `src/main/assets/menu.md`，可在「菜单管理」里增删
   - 营养值是模型按每 100g 的估算，保存的记录会标「拍照估算」

---

## 5. 拍照识别与菜单

| 事项 | 说明 |
| --- | --- |
| 入口 | 底部导航第 4 项「拍照」（原「营养」已移到首页快捷入口与记录页） |
| 识别模型 | `我的 → 菜品识别`：Base URL、API Key、模型名（默认 `deepseek-flash`） |
| 模型能力要求 | 必须支持图片输入（OpenAI 兼容的 `image_url` 消息格式） |
| 菜单来源 | `assets/menu.md`（4 列表格：名称 / 单位 / 克重 / 单价），首次建库时导入 |
| 用户自建 | 「菜单管理 → + 添加」，可填名称、分类、单位、克重、单价 |
| 防编造 | 模型返回的菜名必须与菜单逐字一致，匹配不上的直接丢弃并计入 warnings |
| 营养数据 | 由模型按每 100g 估算；缺失时显示「能量未知」，不按 0 计 |

数据库从 v1 升到 v2：`records` 表新增 `source` 列（`manual` / `plan` / `vision`），并新建 `menu_items` 表。升级时自动迁移，不会清空已有记录。

---

## 6. 已知限制

1. 无商家 / POI / 路线 / 地图能力。涉及「附近有什么」的问题不在能力范围内。
2. 无外卖下单、无支付、无平台跳转。
3. 不在 App 内采集音频；语音输入使用系统输入法的语音键。
4. 无账号体系，个人数据不跨设备同步（仅社区帖子）。
5. 社区服务端没有账号与内容审核，身份是服务端下发的会话 cookie；Cookie 丢失即失去旧帖所有权。
6. 内置营养数据目前标记为「待核验」，见 `docs/data-sources.md`。
7. 不提供自动更新，也不检查版本。
8. 拍照识别的营养值来自模型估算，不是称重或检测结果；识别准确率取决于照片质量与模型能力。
9. 拍照功能通过系统相机应用完成，App 本身不声明相机权限；没有相机应用时只能从相册选图。

---

## 7. 许可证

MIT。详见仓库根目录 `LICENSE`。

营养数据来源：U.S. Department of Agriculture, Agricultural Research Service, FoodData Central。
本项目与 USDA 无隶属关系，不使用其徽标，也不暗示其认可。
