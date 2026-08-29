# 英语搭子 TalkBuddy

按你的目标定制的 AI 英语陪练：微信小程序 + iOS/Android App + H5，一套代码多端（uni-app）。
Spring Boot 3 后端，零配置即可完整体验（内置 Mock LLM 与 12 个精编场景）。

产品设计文档见 [docs/product-design.md](docs/product-design.md)，API 契约见 [docs/api.md](docs/api.md)，**上线手册（云选型 / 备案 / 小程序与 App 发布全流程）见 [docs/launch-guide.md](docs/launch-guide.md)**，**上线后的日常运维（发版 / 备份恢复 / 密钥轮换 / 故障排查 / 内容运营 / 数据查询）见 [docs/operations.md](docs/operations.md)**。

## 项目结构

```
english-learning-app/
├── server/          # Spring Boot 3 + MyBatis-Plus 后端（Java 17）
├── app/             # uni-app（Vue3 + TS）前端：微信小程序 / App / H5
├── docs/            # 产品设计文档、API 契约
├── scripts/         # 冒烟测试、TLS 证书生成、图标生成脚本
└── docker-compose.yml  # 生产部署：PostgreSQL + 后端 + Nginx(H5 + API + TLS)
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
ADMIN_TOKEN=$(grep ^ADMIN_TOKEN .env | cut -d= -f2) bash scripts/smoke.sh
# 目标不是本机 8088 时：SMOKE_BASE=http://<host>:<port>/api/v1 ADMIN_TOKEN=... bash scripts/smoke.sh
```

## 正式运行（PostgreSQL + 真实 LLM）

本地开发可直连 compose 里的数据库：

```bash
docker compose up -d postgres
cd server && mvn spring-boot:run   # 默认 postgres profile；密钥从 config/local.yml 读取
```

`server/config/local.yml`（不入库）承载本地私密配置：LLM 密钥、JWT_SECRET、ADMIN_TOKEN、wx-dev-fallback 等。真实 LLM（DeepSeek / GLM / Qwen / OpenAI 等 OpenAI 兼容服务均可）统一经 **LangChain4j** 接入，服务端不直接拼 HTTP 请求。

> 模板参考 `server/.env.example`。注意：从旧版本升级后 `local.yml` 需补 `jwt-secret` / `admin-token`，否则启动会 fail-fast（这是有意设计）。

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

Docker 部署读根目录 `.env`（模板见 `.env.example`）；裸机运行读 `server/config/local.yml` 或环境变量。**标注 [必填] 的项缺失时服务会拒绝启动**（不带默认凭据）。

| 变量 | 说明 | 默认 |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | `postgres` / `h2`（h2 仅本地体验，自动放宽 dev 开关） | postgres |
| `DB_PASSWORD` | PostgreSQL 密码 **[必填]** | 无（缺失拒绝启动） |
| `LLM_BASE_URL` `LLM_API_KEY` `LLM_MODEL` | OpenAI 兼容 LLM（经 LangChain4j 接入）**[必填]**；未配置且 `LLM_MOCK_ALLOWED=false` 时拒绝启动 | 无 |
| `JWT_SECRET` | 登录态签名密钥 **[必填]**（≥32 字节随机值） | 无（缺失拒绝启动） |
| `ADMIN_TOKEN` | 内容运营/生成接口的令牌 **[必填]** | 无（缺失拒绝启动） |
| `GUEST_ENABLED` | 游客登录开关（`false` 时仅允许微信登录） | true |
| `WX_APPID` `WX_SECRET` | 微信小程序登录凭据；未配置且 `WX_DEV_FALLBACK=false` 时微信登录不可用 | 空 |
| `WX_DEV_FALLBACK` | 微信 dev 兜底（code 直接派生身份），**生产必须 false** | false |
| `LLM_MOCK_ALLOWED` | 允许无密钥时降级 Mock LLM，**生产必须 false** | false |
| `CORS_ALLOWED_ORIGINS` | 跨域白名单（逗号分隔）；H5 同源反代时留空即可 | 空（不限制） |

## 管理接口（内容冷启动）

```bash
curl -X POST http://localhost:8088/api/v1/admin/scenarios/generate \
  -H "X-Admin-Token: $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{"track":"daily","topic":"机场值机","cefr":"A2"}'
```

配置 LLM 后该接口按设计文档 §7.3 的流水线生成场景（对话脚本 + 生词卡 + 校验）并入库；未配置时返回模板场景。浏览器版内容运营台：H5 访问 `/#/pages/admin/content`，输入 `.env` 中的 `ADMIN_TOKEN`。

## 部署上线（Docker）

> 云服务器选购、ICP/App 备案、小程序与 App 发布的完整流程见 **[docs/launch-guide.md](docs/launch-guide.md)**。本节只讲部署本身。

```bash
cp .env.example .env && vim .env          # 填入全部 [必填] 项
./scripts/gen_certs.sh                    # 自签名证书（正式上线替换为 CA 证书）
docker compose up -d --build              # 构建 postgres + server + web
curl -k https://localhost:8443/api/v1/health
```

- 对外端口：`8443`（HTTPS 业务）、`8088`（HTTP，仅跳转到 HTTPS）；数据库与后端**不对宿主机暴露端口**
- HTTPS：nginx 监听 443，证书挂载自 `./certs/`；正式域名上线时把 CA 签发的 `fullchain.pem` / `privkey.pem` 放进 `./certs/` 并 `docker compose restart web`
- 日志：`docker compose logs -f server`，或落盘日志（数据卷 `lingo-logs`，按天轮转、保留 14 天）
- 健康检查：`/api/v1/health` 含数据库探活；compose 内置 server/web/postgres healthcheck + `restart: always`
- 限流：内置按 IP 的固定窗口限流（auth 10/分、admin 30/分、对话 60/分、其余 240/分），超限返回 429
- 已有旧数据卷时修改 `DB_PASSWORD`：新密码只对新库生效，需同步执行
  `docker compose exec postgres psql -U lingo -c "ALTER USER lingo PASSWORD '<新密码>';"`

### 上线 checklist

- [ ] `.env` 全部 `[必填]` 项已填，`ADMIN_TOKEN`/`JWT_SECRET`/`DB_PASSWORD` 为随机强值（非示例值）
- [ ] `LLM_API_KEY` 有效且额度充足；`LLM_MOCK_ALLOWED=false`
- [ ] `GUEST_ENABLED` 按产品决策设置；正式运营建议 `false`（仅微信登录）
- [ ] `WX_APPID`/`WX_SECRET` 已配置，`WX_DEV_FALLBACK=false`
- [ ] 已有正式域名与 CA 证书（Let's Encrypt 免费签发即可），替换 `./certs/`
- [ ] 小程序后台配置 request 合法域名（`https://你的域名`），`app/src/manifest.json` 填入真实 appid
- [ ] 小程序/App 生产构建前在 `app/.env.production` 设置 `VITE_API_BASE_URL=https://你的域名`
- [ ] 数据库定期备份：`docker compose exec postgres pg_dump -U lingo lingo > backup_$(date +%F).sql`
- [ ] 冒烟通过：`ADMIN_TOKEN=... bash scripts/smoke.sh`
- [ ] CI 绿：GitHub Actions 自动跑后端测试 + 前端类型检查

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
- ✅ **AI 陪练搭子（像真人）**：3 个有名字、有性格的人设陪练（阿乐/艾玛/小米），
  真人式聊天行为——先反应再回应、长短句混合、不再每句都反问、偶尔聊自己的小生活、
  说错时用 recast 技巧在回复里自然带正（可选「小声说」轻量纠正）；
  **跨会话长期记忆**：自动抽取你的身份/爱好/目标/计划并持久化，下次开场会接着上次的话题聊，
  记忆面板可查看/让陪练「忘掉」；回复自动 TTS 朗读（可关）+ 「正在输入…」拟真节奏
- ✅ **听力理解小测**：精听结尾自动出 3 道理解题（听英文选中文），听力不只跟读还要检验
- ✅ **打卡月历**：按月视图查看学习轨迹与连续天数
- ✅ **成绩分享海报**：Canvas 生成个人战绩卡，长按保存转发
- ✅ **内容真实化流水线**：admin 接口单篇/批量重写模板场景（需 LLM key），配套浏览器版内容运营台（H5 访问 `/#/pages/admin/content`）
- ✅ 每日三件事（跟随当前课时）、streak 打卡、学习统计周报
- ✅ H5 / 微信小程序构建通过（5 Tab）；App 端随 uni-app 输出
- ⏳ V1：音素级发音评测（腾讯智聆/讯飞）、微信订阅消息学习提醒、订阅支付、App 流式

> 内容分层说明：每个方向的前 6-12 个话题配有精编手写场景；其余话题由模板场景引擎自动生成（零 LLM 成本建课），配置 LLM 后可通过 admin 接口逐个重写为真实内容。
