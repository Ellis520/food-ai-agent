# 服务账号与本地配置

## 百炼

登录 https://bailian.console.aliyun.com/ ，进入顶部“模型”，使用北京地域与默认业务空间。进入 API-KEY 管理，创建 API-KEY。描述可填“食刻AI校园项目”，自定义权限中限定所用模型，例如 Qwen3.8-Flash。创建密钥和开通服务由账号持有人完成。

明文密钥只在创建后显示一次，请直接保存到 backend/.env 的 DASHSCOPE_API_KEY。API Key 页面显示的 compatible-mode/v1 Base URL 填到 QWEN_BASE_URL，模型 ID 与所选权限保持一致。额度与计费规则以账号控制台为准。不要将密钥发送至聊天或提交仓库。

## 高德

进入 https://console.amap.com/ 注册/登录，完成控制台要求的开发者认证。应用管理中创建“食刻AI”应用；分别添加 Web 服务类型 Key 和 Web 端 JS API 类型 Key。

Web 服务 Key 填 backend/.env 的 AMAP_WEB_SERVICE_KEY；JS Key 填 static/.env 的 VITE_AMAP_JS_KEY；JS Key 对应安全码填 backend/.env 的 AMAP_SECURITY_CODE。配置合法域名/来源并按账号权限启用所需 POI 和地理编码服务。修改后重启并重新构建前端。

中山大学南校区地址已配置为广州市海珠区新港西路135号中山大学南校园。地图查询中心由高德地址解析返回；不是自动读取用户 GPS。Key 未配置时显示明确提示，不显示虚构实时商家。

## 语音

本地无需购买 ASR 云账号。运行 Start-ASR.ps1，等待依赖和两个 Paraformer 模型完成下载、服务启动；再设置 FUNASR_WS_URL=ws://127.0.0.1:10095。服务启动失败时不要填写后宣称可用。浏览器录音需用户授权并在安全上下文运行。

模型下载与 Python 生态兼容性仍需实际验证；本次已完成本机CPU环境和两个模型部署，真实音频链路验证通过。已有云端 FunASR 也可配置，但应使用受控私有服务及合适的传输加密与认证。
