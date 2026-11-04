# 食刻 AI

面向校园的饮食助手。包含千问聊天、早午晚餐规划、饮食记录和打卡、营养分析、高德找店与导航、美团外卖搜索、美食圈和语音输入。

## 目录

- `main.py` 是 FastAPI 启动入口，业务路由在 `backend/app/main.py`。
- `requirements.txt` 引用后端依赖锁文件。
- `static/index.html` 是 Vite 网页入口。页面在 `static/src/views`，组件在 `static/src/components`，样式在 `static/src/style.css`，网页启动逻辑在 `static/src/main.ts`。API 适配在 `static/src/api`，静态资源放在 `static/public`。
- `core/config.py` 读取本地环境配置。聊天与营养估算提示词在 `core/prompts.py`。
- `services/llm_service.py` 封装千问请求。`services/map_service.py` 封装高德周边地点查询，网页端根据地点坐标生成导航链接。`services/nutrition_service.py` 校验千问估算，网页端在 `static/src/api/nutrition.ts` 按份量换算。
- `data/user_history.json` 是空白数据示例。运行时 SQLite 数据库默认写入 `data/foodmoment.db`。
- `shared/meal-options.json` 是聊天和饮食规划共用的餐食名单。
- `asr` 包含 FunASR 服务代码。模型由服务配置和安装流程准备。
- `backend/tests` 和 `static/tests` 保存后端与前端测试。

## 本地启动

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

## 测试

```powershell
npm --prefix static test
.venv/Scripts/python.exe -c "import sys; sys.path.insert(0, 'backend'); import pytest; raise SystemExit(pytest.main(['-q', 'backend/tests']))"
```

## 组员分工

| 模块 | 修改位置 | 负责人 |
| --- | --- | --- |
| 页面和交互 | `static/src/views`、`static/src/components` | 待团队填写 |
| 千问与对话 | `core/prompts.py`、`services/llm_service.py` | 待团队填写 |
| 地图和导航 | `services/map_service.py`、`static/src/api/restaurantActions.ts` | 待团队填写 |
| 营养与记录 | `services/nutrition_service.py`、`static/src/api/nutrition.ts` | 待团队填写 |
| 语音和验收 | `asr`、`backend/tests`、`static/tests` | 待团队填写 |

[接口说明](docs/API.md) · [Gitee 协作流程](docs/GITEE.md)
