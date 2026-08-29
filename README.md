# 英语搭子 TalkBuddy

按你的目标定制的 AI 英语陪练：微信小程序 + iOS/Android App + H5，一套代码多端（uni-app）。
Spring Boot 3 后端，零配置即可完整体验（内置 Mock LLM 与 12 个精编场景）。

产品设计文档见 [docs/product-design.md](docs/product-design.md)，API 契约见 [docs/api.md](docs/api.md)。

## 项目结构

```
english-learning-app/
├── server/          # Spring Boot 3 + MyBatis-Plus 后端（Java 17）
├── app/             # uni-app（Vue3 + TS）前端：微信小程序 / App / H5
├── docs/            # 产品设计文档、API 契约
├── scripts/         # 冒烟测试、图标生成脚本
└── docker-compose.yml  # PostgreSQL + Redis（本地开发）
```

## 快速开始（零配置体验）

### 1. 启动后端（H2 内存库模式，无需任何依赖）

```bash
cd server
SPRING_PROFILES_ACTIVE=h2 mvn spring-boot:run
```

后端跑在 `http://localhost:8080`，启动时自动建表并种入 12 个场景。

### 2. 启动 H5 前端

```bash
cd app
npm install
npm run dev:h5
```

打开 `http://localhost:5173`（已配置 `/api` 代理到 8080）。

「游客身份快速体验」→ 选目标 → 测评定级 → 开始学习。全流程无需任何密钥。

### 3. 跑端到端冒烟（可选）

```bash
bash scripts/smoke.sh   # 11 项 API 冒烟，全部 PASS 即健康
```

## 正式运行（PostgreSQL + 真实 LLM）

```bash
docker compose up -d        # 启动 PostgreSQL 16 + Redis 7
cd server && mvn spring-boot:run   # 默认 postgres profile
```

配置真实 LLM（DeepSeek / GLM / Qwen / OpenAI 等 OpenAI 兼容服务均可）：

```bash
export LLM_BASE_URL="https://api.deepseek.com"
export LLM_API_KEY="sk-xxx"
export LLM_MODEL="deepseek-chat"
# JWT_SECRET / WX_APPID / WX_SECRET / ADMIN_TOKEN 见 .env.example
```

配置后：对话走真实大模型（流式 SSE）、admin 生成接口批量产出新场景。

## 微信小程序

```bash
cd app && npm run dev:mp-weixin
# 用微信开发者工具导入 app/dist/dev/mp-weixin
```

- 在 `app/src/manifest.json` 填入你的小程序 `appid`
- 想启用语音对话（按住说话 + AI 语音回复）：在微信公众平台添加「微信同声传译」插件（wx069ba97219f66d99），manifest 中已预声明；未添加时应用自动降级为纯文本，不影响其他功能
- 开发者工具里请关闭「校验合法域名」（或把 localhost:8080 加入合法域名）

## App（iOS / Android）

用 HBuilderX 打开 `app/` 目录 → 运行到手机或模拟器；Android 权限与 manifest 已配置。
App 端 v1 对话降级为非流式，语音走文本（原生语音评测为 V1 规划，见设计文档）。

## 配置项（环境变量）

| 变量 | 说明 | 默认 |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | `postgres` / `h2` | postgres |
| `DB_HOST/DB_PORT/DB_NAME/DB_USER/DB_PASSWORD` | PostgreSQL 连接 | localhost:5432/lingo |
| `LLM_BASE_URL` `LLM_API_KEY` `LLM_MODEL` | OpenAI 兼容 LLM；留空走 Mock | 空（Mock） |
| `WX_APPID` `WX_SECRET` | 微信小程序登录；留空走 dev 兜底 | 空 |
| `JWT_SECRET` | 登录态签名密钥（≥32 字节） | 内置 dev 值（上线必须改） |
| `ADMIN_TOKEN` | 内容生成管理接口的令牌 | dev-admin |

## 管理接口（内容冷启动）

```bash
curl -X POST http://localhost:8080/api/v1/admin/scenarios/generate \
  -H "X-Admin-Token: dev-admin" -H "Content-Type: application/json" \
  -d '{"track":"daily","topic":"机场值机","cefr":"A2"}'
```

配置 LLM 后该接口按设计文档 §7.3 的流水线生成场景（对话脚本 + 生词卡 + 校验）并入库；未配置时返回模板场景。

## 已实现范围（对应设计文档 §6 MVP+）

- ✅ **登记建档**：年龄段（少儿/青少年/成人/银发）+ 目标 + 每日时长 + 10 题测评定级（CEFR）
- ✅ **课程目录（对标考试等级）**：6 个学段方向 × 3 档周期 = 18 门课——
  小学(KET/PET) · 初中(中考) · 高中(高考) · 大学(CET-4/6) · 职场(BEC 商务) · 出境生活口语；
  每方向 24 个话题大纲，3 个月 36 课时 / 6 个月 72 课时 / 12 个月 96 课时（含单元复习课），每周 2-3 课时
- ✅ **自动制定课程**：登记后按「年龄段 + 目标 + 等级」派课（如青少年 A2 → 初中中考班），可手动切换方向与周期
- ✅ **课时编排**：每话题「对话实战 → 听力精听 → 跟读评分 → ⚔️ Boss 挑战赛」循环（12 个月班每单元附单元复习课，对接 FSRS 生词复习）；完课自动推进解锁
- ✅ **Boss 挑战赛**：60 秒限时混合关卡（词汇突击/听音辨义/句子反击），答对 6 题通关解锁下一单元，+40 经验
- ✅ AI 对话：SSE 流式回复、更地道说法、语法纠错、生词提示
- ✅ **听力精听**：逐句盲听 → 显示字幕 → 逐句推进，TTS 自动连播，计时入统计与课时
- ✅ **跟读评分**：示范播放 → 端上语音识别 → 词级 LCS 比对打分逐词高亮；无识别环境降级自评
- ✅ 生词本 + FSRS-5 复习调度（内置算法，含单元测试）
- ✅ **成长体系**：全行为产出经验值（XP）——每日打卡 +5、对话 +30、精听 +20、跟读 +25、Boss +40、复习每词 +2、速测/拼写每题 +3；等级头衔从「英语新手」到「英语大师」；11 枚成就徽章成就墙
- ✅ **每日一句**：每天一句金句带发音，给用户每天打开的理由
- ✅ **词汇双玩法**：词汇速测（中译英 4 选 1）+ 拼写挑战（听音拼词、首字母提示）
- ✅ **自由聊天**：不限场景的 AI 聊伴模式，练「敢说」
- ✅ **听力理解小测**：精听结尾自动出 3 道理解题（听英文选中文），听力不只跟读还要检验
- ✅ **打卡月历**：按月视图查看学习轨迹与连续天数
- ✅ **成绩分享海报**：Canvas 生成个人战绩卡，长按保存转发
- ✅ **内容真实化流水线**：admin 接口单篇/批量重写模板场景（需 LLM key），配套浏览器版内容运营台（H5 访问 `/#/pages/admin/content`）
- ✅ 每日三件事（跟随当前课时）、streak 打卡、学习统计周报
- ✅ H5 / 微信小程序构建通过（5 Tab）；App 端随 uni-app 输出
- ⏳ V1：音素级发音评测（腾讯智聆/讯飞）、微信订阅消息学习提醒、订阅支付、App 流式

> 内容分层说明：每个方向的前 6-12 个话题配有精编手写场景；其余话题由模板场景引擎自动生成（零 LLM 成本建课），配置 LLM 后可通过 admin 接口逐个重写为真实内容。
