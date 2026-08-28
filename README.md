# Lingo 英语陪练（工作名未定）

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
- ✅ **课程体系**：按登记自动制定并报名课程；课程 → 课时（对话实战/听力精听/跟读评分交替编排）→ 线性解锁；完课自动推进；可随时切换课程
- ✅ 首发两门 10 课时课程（职场英语实战课 / 日常生活英语课）
- ✅ AI 对话：SSE 流式回复、更地道说法、语法纠错、生词提示
- ✅ **听力精听**：逐句盲听 → 显示字幕 → 逐句推进，TTS 自动连播，练完计入学习时长与课时
- ✅ **跟读评分**：示范播放 → 端上语音识别 → 词级 LCS 比对打分，逐词高亮；无识别环境自动降级自评
- ✅ 生词本 + FSRS-5 复习调度（内置算法，含单元测试）
- ✅ 每日三件事（跟随当前课时）、streak 打卡、学习统计周报
- ✅ H5 / 微信小程序构建通过（5 Tab）；App 端随 uni-app 输出
- ⏳ V1：音素级发音评测（腾讯智聆/讯飞）、少儿专属课程内容、订阅支付、App 流式
