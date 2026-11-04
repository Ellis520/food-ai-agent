# FastAPI v2 接口

默认本机端口 8017，OpenAPI 文档 /docs。前端通过 /api 同源代理。所有响应信封为 {data, source}；错误返回标准 HTTP 状态和 detail。未配置第三方服务返回 503，超时/供应商错误返回 502，不冒充演示。

## 会话与数据库

GET /api/state：创建或读取匿名浏览器会话，设置 HttpOnly、SameSite=Lax 的 fm_session Cookie，返回 {revision, state}。

PUT /api/state：发送 {revision, state}。state 包含 profile、meals、plans、favorites、messages，与 static/src/api/types.ts 一致。服务端 Pydantic 验证食物 ID、克重、费用、日期与长度，SQLite 使用参数化 SQL。revision 不匹配返回 409，防止多个页面覆盖。

Meal现支持可选planRef，格式为 `YYYY-MM-DD:餐次`（例如 `2026-11-02:早餐`）。关联必须与该记录date/slot一致，同一会话的meals内不能重复关联；不符合返回422，整次状态写入不生效。旧记录可省略此字段。前端只从已保存且不晚于今天的计划带入，编辑时若更改日期或餐次则移除planRef。此字段用于关联和防重复，不代表后端核验真实用餐，也不自动写入记录。

所有业务接口要求先读取 state 建立会话。后端模式首次为空记录，demo 模式保留最初两条演示记录；不自动迁移浏览器 demo 数据。当前按会话保存完整 JSON，后续可迁移 users/diet_records 等规范化表。此版本不是账号认证系统，默认只监听 127.0.0.1；公网部署前需增加正式鉴权、限流、HTTPS 和反向代理。

## 已实现业务

- GET /api/health：数据库探测，以及 qwen/amap/campus/asr 配置存在标记，不返回密钥。
- POST /api/takeaway/recommend：{message, budget, preference?, history?, excludeKeywords?}。当前会话内按需求调用千问JSON模式，返回reply、cards（name/keyword/category/reason/difference/searchUrl/source）、provider、budgetTarget、note，source=qwen-meal-ideas。4–12个不同餐食方向；excludeKeywords最多60项，history最多8条。模型不输出价格/店铺/评分/链接字段；输出结构、卡片金额文案、餐食搜索词与去重由后端校验，URL由固定美团外卖结果页 /waimai/mindex/searchresults 编码构造（queryType=12002、keyword、entranceId=0、qwTypeId=0、mode=search），不采纳模型生成URL。无效输出最多重试一次；没有密钥503，输入无效422，模型结果仍无效或重复502。仅生成餐食方向，不查询实时外卖菜单、不创建订单或写入记录；链接为关键词搜索，平台可能要求登录。
- POST /api/diet/analysis：{days:7|30, revision}。前端先等待状态保存；后端读取当前会话SQLite快照，以北京时间今天为截止日聚合指定天数，排除未来/范围外记录。返回period、statistics（餐数/覆盖天数/早餐记录天数/花费/已记录日均花费/每日分布/样本食物频次）、summary、observations（带coverage/breakfast/spending/distribution依据键）、suggestions、provider、generatedAt、revision、dataNote。确定数值由程序计算，叙述来自千问JSON输出，source=qwen-sqlite-analysis；仅发送统计和目标/预算/偏好，不发送姓名、会话标识或原始逐条记录。无记录422、无密钥503、模型输出不合格502；请求版本不一致或生成期间版本改变409。不修改记录、不保存报告；TXT下载由前端生成。观察依据键和结构通过Pydantic校验，AI文字的事实准确性仍需用户核对。
- POST /api/chat：{message, history}，history 最多20条。服务端去除末尾重复当前消息，调用百炼兼容接口。返回 Message，包含 id/role/text/kind/source。成功生成计划时附 planItems（三餐）与 planDate（ISO日期），前端用这些数据渲染卡片并带入规划页。已支持受控的 get_diet_summary、get_user_preferences、generate_diet_plan、search_campus_restaurants 工具，最多4轮；只读记录/建议生成，不自动保存计划。工具结果含来源，缺少高德配置返回明确工具错误。
- POST /api/safety/ask：{message, history?}，message 去首尾空白后1–1600字；history 至多8条{role:"user"|"assistant",text}，每条至多2000字。需要匿名会话。返回{answer,steps,followUp,sources,kind,provider,reviewedAt,note}。普通问答以 qwen-curated-safety 为来源，仅发送问题和本页近期上下文，不读取档案或饮食记录；共享资料中的固定链接由后端解析，千问无联网检索工具。危险症状关键词触发条件式提醒，source=local-safety-rule，不调用千问。校验输入、输出结构和资料ID，并拦截部分超范围输出；不保证穷尽医疗风险。模型未配置503、输出不合格502、输入无效422、会话缺失401。回答不写入数据库。
- POST /api/language/translate：旧客户端兼容接口。{text, target:"en"}，千问翻译，仅返回字符串 data；新版界面已改为饮食小贴士。
- POST /api/diet/plan：{budget, preference, day}。调用千问JSON模式从示例目录生成三餐，响应 source=qwen-sample-catalog。后端验证早餐/午餐/晚餐各一项、允许餐次、已知食物ID、50–600g份量、奶蛋素偏好和每份示例价合计不超预算。目录最低三餐价为26元，更低预算返回422；模型输出不合格最多重试一次，仍无效返回502，不回退规则或自动保存。ID 沿用 oats/chicken/noodle/salad/rice/egg。读取当前会话的目标与禁忌供模型参考，配料资料不全，不保证过敏安全。保存仍须用户确认后PUT /api/state。
- GET /api/food/nearby：可接受 keyword/location，或使用后端校园地址地理编码查询中心。高德真实 POI，保留来源；未提供的价格、评分是 null。location 必须是中国境内 GCJ-02，经纬度不可直接用未经转换的浏览器 WGS-84。
- GET /api/amap/_AMapService/{path}：固定高德域名的 JS SDK 安全代理，不接受任意上游 URL；后端追加 AMAP_SECURITY_CODE。
- WS /api/asr/stream：匿名会话与 Origin 校验，向 FUNASR_WS_URL 转发。浏览器上传单声道16kHz PCM16 little-endian 二进制，{stop:true} 转为 FunASR 的停止标记，返回 mode/text/is_final；最多两分钟音频。客户端累加2pass-online增量片段，同步输入框；2pass-offline结果替换当前语音临时段。录音及校正期间不可发送，取消恢复原稿，连接失败保留已显示文字；校正后由用户确认发送。

## 仍未完成

权威营养数据、正式用户账号和跨设备同步、真实外卖平台菜单/交易、网页内路线折线与个人定位、用户实际麦克风和手机HTTPS环境测试。

## 官方依据

- 百炼密钥：https://help.aliyun.com/zh/model-studio/get-api-key/
- 高德 POI：https://lbs.amap.com/api/webservice/guide/api/search/
- 高德 JS：https://lbs.amap.com/api/javascript-api-v2/guide/abc/load
- FunASR 协议：https://github.com/modelscope/FunASR/blob/main/runtime/docs/websocket_protocol_zh.md


商家响应现包含可空的 photoUrl，直接使用该高德 POI 的照片字段；无图或加载失败明确显示占位，不替换成无关店铺照片。


营养搭配使用现有GET/PUT /api/state，不新增付费供应商接口。State增加nutritionRecipes（默认[]，最多30份）：每份{id,name,items}，items为1–20项{id,name,grams,source,foodId?,per100}。source为sample或label；sample需要已知foodId，label不接受foodId。per100含kcal/protein/carbs/fat/fiber/sodium，可为null（未知）；至少一项有值。每100g热量0–1000kcal，蛋白质/碳水/脂肪/纤维各0–100g，钠0–100000mg，份量>0且≤2000g；禁止非有限值、未知字段、重复搭配ID/条目ID。输入为用户数据或明确的人工样本，不声明其内容经过营养数据库核验。

保存时保留未知值null；旧客户端未发送nutritionRecipes字段时保留当前已保存值，显式传[]可清空。保存沿用会话隔离、2MB限制及revision校验。GET恢复后前端对未存在的新字段使用[]。保存、载入、修改或删除营养搭配均不自动写入meals。
