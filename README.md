# 食刻 AI

面向校园的饮食助手。仓库里有两套客户端，共用同一套网页后端。

网页端包含千问聊天、早午晚餐规划、饮食记录和打卡、饮食习惯、营养分析、高德找店与导航、美团外卖搜索、美食圈、饮食小贴士和语音输入。Android 端在手机上本地记录、计划和换算营养；模型、高德和美食圈都由使用者自己配置，美食圈可以接到这套网页后端。

## 目录

- `main.py` 是网页后端的 FastAPI 启动入口，业务路由在 `backend/app/main.py`。
- `requirements.txt` 引用后端依赖锁文件。
- `static/index.html` 是 Vite 网页入口。页面在 `static/src/views`，组件在 `static/src/components`，样式在 `static/src/style.css`，网页启动逻辑在 `static/src/main.ts`。API 适配在 `static/src/api`，静态资源放在 `static/public`。
- `core/config.py` 读取本地环境配置。聊天与营养估算提示词在 `core/prompts.py`。
- `services/llm_service.py` 封装千问请求。`services/map_service.py` 封装高德周边地点查询，网页端根据地点坐标生成导航链接。`services/nutrition_service.py` 校验千问估算，网页端在 `static/src/api/nutrition.ts` 按份量换算。
- `data/user_history.json` 是空白数据示例。运行时 SQLite 数据库默认写入 `data/foodmoment.db`。
- `shared/meal-options.json` 是聊天和饮食规划共用的餐食名单。
- `asr` 包含 FunASR 服务代码。模型由服务配置和安装流程准备。
- `backend/tests` 和 `static/tests` 保存后端与网页测试。
- `app` 是 Android 工程。应用代码在 `app/app`，构建说明在 `app/README.md`。

## 网页

### 本地启动

需要 Python 3.12 和 Node.js 22。Windows 可在项目根目录运行 `./Start.ps1`，脚本会准备依赖、构建网页并启动本地服务。

手动启动时，在根目录执行：

```powershell
python -m venv .venv
.venv/Scripts/python.exe -m pip install -r requirements.txt
Copy-Item backend/.env.example backend/.env
Copy-Item static/.env.example static/.env
npm --prefix static ci
npm --prefix static run build
.venv/Scripts/python.exe -m uvicorn main:app --host 127.0.0.1 --port 8017
```

打开 [食刻 AI 本地页面](http://127.0.0.1:8017/)。维护前端时可另开窗口运行 `npm --prefix static run dev`，Vite 会把接口请求转发到 8017。

千问、高德和语音服务按 [服务配置](docs/SERVICES.md) 填写本地配置。纯前端演示可在 `static/.env` 设置 `VITE_DATA_MODE=demo`。

### 测试

```powershell
npm --prefix static test
.venv/Scripts/python.exe -c "import sys; sys.path.insert(0, 'backend'); import pytest; raise SystemExit(pytest.main(['-q', 'backend/tests']))"
```

## Android

Kotlin + Jetpack Compose，包名 `com.foodmoment.app`，最低 Android 7.0。记录、计划、营养换算和备份存在手机本地。需要 JDK 17。在 `app` 目录执行：

```bash
./gradlew :app:assembleDebug
```

调试包在 `app/app/build/outputs/apk/debug/app-debug.apk`。Android Studio 打开 `app` 目录即可运行。页面包括首页、聊天、计划、记录、拍照识菜、附近商家、地图、习惯分析和设置。模型使用使用者自己填写的 OpenAI 兼容接口；高德 Key 也写在应用设置里，不经过网页后端。美食圈默认关闭，启用时把服务端地址指到上面的网页服务，例如 `http://<电脑局域网 IP>:8017`。构建细节、首次运行和已知限制见 [app/README.md](app/README.md)。

## 组员分工

| 成员 | 角色与职责 |
| --- | --- |
| 吴炫璋 | 负责主要前后端开发、AI 调试与服务器全流程搭建、Bug 修复与 UI 美化 |
| 陈泫颖 | 队长，统筹项目安排，负责前端页面与交互开发 |
| 樊响 | 负责前端设计优化 |
| 易乐怡 | 负责 UI 优化、演示视频录制与海报设计 |
| 赖纳 | 负责后端开发优化 |
| 黄嘉鑫 | 负责 APP 开发、双端互联与应用优化 |

[接口说明](docs/API.md) · [Gitee 协作流程](docs/GITEE.md)

## 开源许可

本项目采用 [MIT 许可证](LICENSE)。
