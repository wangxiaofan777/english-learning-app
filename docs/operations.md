# TalkBuddy 运维手册（上线后的日常运营）

> 本手册是上线后的**长期操作文档**：怎么发版本、怎么备份恢复、密钥怎么管、出故障怎么查、内容怎么运营、数据怎么看。
> 上线前的资质/部署/提审流程见 [launch-guide.md](launch-guide.md)；产品与 API 文档见 [product-design.md](product-design.md) / [api.md](api.md)。
> 约定：服务器上项目目录为 `/opt/talkbuddy`，所有命令在该目录执行；敏感值一律引用 `.env`，**不要把密钥写进任何文档/聊天/工单**。

## 0. 决策记录与凭据位置（新接手的人先读这里）

### 已确认的关键决策（2026-08）

| 决策 | 结论 | 原因与详情 |
| --- | --- | --- |
| 运营主体 | 个体工商户 | 深度合成类目需要非个人主体；个体户成本最低。若类目审核卡"企业"再升级公司（launch-guide §四.2） |
| 云厂商 | 阿里云轻量 2C4G | LLM 在百炼同生态；备案材料顺（launch-guide §一） |
| LLM | 阿里云百炼 DashScope（OpenAI 兼容） | 已备案大模型，小程序深度合成类目走合作协议路径 |
| 产品口径 | 「AI 学习工具」，不卖课、无"培训/课程"表述 | 英语属学科类培训管理，需办学许可（launch-guide §四.1） |
| 名称/包名 | 「英语搭子」/ `cn.talkbuddy.app` | 名称三一致原则；包名 App 备案后不可改 |
| 收费 | V1 不收费 | 规避资质与支付审查；后续如收费需重新评估类目与支付接入 |

### 凭据位置（只在服务器与密码管理器里存）

| 凭据 | 存放位置 | 用途 |
| --- | --- | --- |
| `.env` | 服务器 `/opt/talkbuddy/.env`（git 忽略） | 全部部署配置：DB_PASSWORD、JWT_SECRET、ADMIN_TOKEN、LLM_API_KEY、开关 |
| `certs/` | 服务器 `/opt/talkbuddy/certs/`（git 忽略） | TLS 证书 |
| Android keystore | 密码管理器 + 离线备份（**丢失 = App 无法再更新**） | 打包签名 |
| Apple 证书 .p12 | Apple Developer 后台可重发 | iOS 打包 |
| 百炼 API Key | `.env` 的 `LLM_API_KEY`；百炼控制台管理 | LLM 调用 |

---

## 1. 日常巡检 SOP

**每周 5 分钟**（建议固定周一上午）：
```bash
cd /opt/talkbuddy
docker compose ps                                   # 全部 Up (healthy)
docker compose logs server --since 24h | grep ERROR | tail -20
docker compose logs server --since 24h | grep "rate limited" | wc -l   # 突增=被刷信号
df -h /                                             # 磁盘 <80%
```
- 阿里云控制台看一眼：CPU/内存/磁盘曲线、拨测告警是否安静；
- 百炼控制台看 token 消耗与账单；
- 数据抽查（§6 SQL）：DAU、新增、对话量有没有异常波动（异常暴跌=故障没告警到；异常暴涨=被刷/病毒传播）。

**每月**：
- `apt update && apt upgrade`（内核更新后 `docker compose restart` 或择机重启）；
- 备份恢复演练（§4.3，用最近一份备份恢复到临时库验证）；
- 检查证书有效期：`openssl x509 -enddate -noout -in certs/fullchain.pem`（<30 天就去控制台换发）。

---

## 2. 版本发布流程（改了代码怎么发）

### 2.1 后端 + H5（一次完成）

```bash
# 本地：确认 CI 绿（mvn test + type-check），打 tag
git tag v1.0.1 && git push origin v1.0.1

# 服务器
cd /opt/talkbuddy && git pull
docker compose up -d --build          # 重建并滚动重启（healthcheck 挡住未就绪流量）
docker compose ps && docker compose logs server --since 5m   # 确认 healthy、无 ERROR
ADMIN_TOKEN=$(grep ^ADMIN_TOKEN .env | cut -d= -f2) bash scripts/smoke.sh   # 17/17 才算发布成功
```

**回滚**：`git checkout v1.0.0 && docker compose up -d --build`。
说明：`schema.sql` 全部是 `IF NOT EXISTS` 的幂等增量语句，新代码加列后回滚旧代码是安全的（旧代码不用新列）；**删列/改列类型必须避免**，要改就加新列。

### 2.2 微信小程序

1. `app/src/manifest.json`：`versionCode` +1、`versionName` 改版本号；
2. `npm run build:mp-weixin` → 微信开发者工具导入 `app/dist/build/mp-weixin` → 上传（填版本号+备注）→ 微信后台提交审核（引用 §launch-guide 3.3 的合规文案清单自检）→ 审核通过后**手动点发布**；
3. 发布后用线上版回归：登录→对话流式→跟读→复习。

### 2.3 App（iOS / Android）

1. manifest.json 版本号与小程序**保持一致**（三端对齐）；
2. HBuilderX 云打包出 APK + ipa（证书/密钥流程见 launch-guide §3.4.1）；
3. iOS：Transporter 上传 → App Store Connect 提审（版本备注写清本次改动）；
4. Android：华为/小米/应用宝逐家提交新 APK；
5. 提审期间用户仍用旧版，属正常；审核 1-3 天/家。

> 发布原则：**先服务器（三端共用），后小程序，再 App**——后端 API 向后兼容时小程序/App 旧版不受影响。删除接口字段前必须确认三端都已不调用。

---

## 3. 密钥轮换（泄露或有嫌疑时）

| 密钥 | 轮换步骤 | 影响 |
| --- | --- | --- |
| `JWT_SECRET` | 改 `.env` → `docker compose up -d server` | 所有用户登录态失效，重新登录（可接受） |
| `ADMIN_TOKEN` | 改 `.env` → 重启 server → 运营台里重新输入新 token | 管理员需重新登录运营台 |
| `LLM_API_KEY` | 百炼控制台禁用旧 key、创建新 key → 改 `.env` → 重启 server | 无感 |
| `DB_PASSWORD` | **注意顺序**：① `docker compose exec postgres psql -U lingo -c "ALTER USER lingo PASSWORD '新密码';"` ② 改 `.env` ③ 重启 server。直接改 `.env` 重启会连不上库（POSTGRES_PASSWORD 只在数据卷首次初始化时生效） | 无感 |
| TLS 证书 | 控制台换发免费 DV 证书 → 替换 `certs/` 两个文件 → `docker compose restart web` | 无感 |

生成强随机值：`openssl rand -hex 32`（JWT）、`openssl rand -hex 16`（ADMIN_TOKEN）、`openssl rand -base64 18`（DB 密码）。

**安全事件响应**：确认泄露 → 按上表轮换 → `docker compose logs server --since 72h | grep -E "rate limited|ERROR"` 排查是否有异常调用 → 百炼控制台核对 token 消耗是否被盗刷 → 在密码管理器更新记录。

---

## 4. 备份与恢复

### 4.1 备份机制（上线时已配置）

- **数据库**：cron 每日 03:00 `pg_dump` 到 `/opt/backup/`，保留 14 天，并同步 OSS（launch-guide §3.1 命令）；
- **服务器整机**：阿里云自动快照每日一档，保留 7 天；
- **代码**：GitHub 仓库本身；`.env` 与 keystore 在密码管理器 + 离线备份。

### 4.2 恢复步骤（数据误删/损坏时）

```bash
cd /opt/talkbuddy
docker compose stop server                                   # 停写入
docker compose exec postgres psql -U lingo -d postgres \
  -c "DROP DATABASE lingo;" -c "CREATE DATABASE lingo OWNER lingo;"
gunzip -c /opt/backup/lingo_2026-09-01.sql.gz | \
  docker compose exec -T postgres psql -U lingo -d lingo
docker compose start server
docker compose logs -f server --since 2m                     # 确认启动无错
curl -sk https://你的域名/api/v1/health                       # {"code":0,...}
```

### 4.3 恢复演练（每月一次，5 分钟）

恢复到临时库验证备份可用（不动线上）：
```bash
gunzip -c $(ls -t /opt/backup/lingo_*.sql.gz | head -1) | \
  docker compose exec -T postgres psql -U lingo -d postgres \
  -c "CREATE DATABASE lingo_verify;" -d lingo_verify --set=ON_ERROR_STOP=1 \
  && docker compose exec postgres psql -U lingo -d lingo_verify \
  -c "SELECT COUNT(*) FROM t_user;"
docker compose exec postgres psql -U lingo -d postgres -c "DROP DATABASE lingo_verify;"
```
查询结果正常 = 备份有效。**没演练过的备份等于没有备份。**

---

## 5. 故障排查 Runbook

| 症状 | 排查 | 处理 |
| --- | --- | --- |
| 网站打不开 / 拨测告警 | `docker compose ps`（谁不 healthy）→ `docker compose logs --since 10m <服务>` | server 崩溃：看堆栈，改完 `up -d --build`；OOM：`docker stats` 看内存，考虑升配 |
| health 返回 503 | `docker compose logs postgres` | 多半是磁盘满（§下行）或 PG 崩溃 |
| 磁盘满 | `df -h` → `docker system df` | `docker system prune -f`（清理构建缓存）；确认日志卷没失控（logback 已按 50MB/天轮转，正常不失控） |
| 用户报「AI 服务暂时不可用」 | `docker compose logs server --since 30m \| grep -i "llm"` | 网络抖动会自动重试一次；持续失败：查百炼控制台（key 是否被禁用/欠费/限流），轮换 key 或等恢复 |
| AI 回复极慢 | 百炼控制台看延迟；`grep -c "stream error"` | 高峰期模型慢属正常；持续慢可在百炼换模型（`.env` 的 `LLM_MODEL`） |
| 疑似被刷（限流日志暴涨/账单异常） | `docker compose logs server --since 24h \| grep "rate limited" \| awk '{print $NF}' \| sort \| uniq -c \| sort -rn \| head` | 阿里云安全组/云防火墙封 IP 段；必要时临时 `GUEST_ENABLED=false` 关游客入口；限流阈值在 `RateLimitFilter.java` 的 `RULES` 常量 |
| 证书过期告警 | `openssl x509 -enddate -noout -in certs/fullchain.pem` | 控制台换发 → 替换 certs → `restart web` |
| 某用户异常行为/举报 | §6.3 查询定位 → 看对话记录 | 依规处理；需要封禁时目前只能改库（谨慎），V2 应加管理端封禁功能 |
| 服务器彻底挂（快照恢复） | 阿里云控制台用最近快照回滚磁盘 → 按重新部署流程 `git clone` + 恢复最新数据库备份（§4.2） | 半小时级恢复；所以 `.env` 和 keystore 必须在密码管理器里有副本 |

---

## 6. 数据查询（运营 SQL，只读）

进入方式：`docker compose exec postgres psql -U lingo -d lingo`。

### 6.1 增长与活跃
```sql
-- 近 7 日每日新增用户
SELECT created_at::date AS day, COUNT(*) FROM t_user
GROUP BY 1 ORDER BY 1 DESC LIMIT 7;

-- 近 7 日 DAU（有学习行为的用户）
SELECT study_date, COUNT(DISTINCT user_id) FROM t_study_log
GROUP BY 1 ORDER BY 1 DESC LIMIT 7;

-- 次周留存（把 :w1 和 :w2 换成具体日期，如 '2026-09-01'/'2026-09-07'）
SELECT ROUND(100.0*COUNT(DISTINCT b.user_id)/NULLIF(COUNT(DISTINCT a.user_id),0),1) AS wk_retention_pct
FROM (SELECT DISTINCT user_id FROM t_study_log WHERE study_date BETWEEN :w1 AND :w2) a
LEFT JOIN t_study_log b ON b.user_id=a.user_id
  AND b.study_date > :w2 AND b.study_date <= (:w2::date + 7);
```

### 6.2 学习与内容
```sql
-- 昨日总学习分钟数与练习次数
SELECT kind, SUM(minutes) mins, SUM(count) cnt FROM t_study_log
WHERE study_date = (CURRENT_DATE - 1)::text GROUP BY kind ORDER BY mins DESC;

-- 累计完课数 / 活跃课程
SELECT c.title_zh, COUNT(*) FROM t_lesson_progress p
JOIN t_course c ON c.id = p.course_id
WHERE p.status = 'done' GROUP BY 1 ORDER BY 2 DESC LIMIT 10;

-- 生词总量与到期待复习量
SELECT COUNT(*) FROM t_vocab_entry;
SELECT COUNT(*) FROM t_vocab_entry WHERE due_at < now();

-- 昨日对话量与消息量（scene=场景陪练 / companion=AI 搭子 / free=自由聊天）
SELECT mode, COUNT(*) convs, COALESCE(SUM(msg_count),0) msgs FROM t_conversation
WHERE created_at::date = CURRENT_DATE - 1 GROUP BY mode;
```

### 6.3 处理举报/查用户记录
```sql
-- 按昵称找用户
SELECT id, nickname, created_at FROM t_user WHERE nickname LIKE '%关键字%';
-- 某用户最近对话与消息（举报取证）
SELECT id, mode, status, created_at FROM t_conversation WHERE user_id = :uid ORDER BY created_at DESC LIMIT 10;
SELECT role, content, created_at FROM t_message
WHERE conversation_id = :cid ORDER BY idx;
```

---

## 7. 内容运营 SOP

内容冷启动目标已达成（场景库 145+ 篇，模板重写为 AI 篇）。日常内容动作：

1. **新增话题场景**（想扩话题时）：
   ```bash
   ADMIN_TOKEN=$(grep ^ADMIN_TOKEN .env | cut -d= -f2)
   curl -sk -X POST https://你的域名/api/v1/admin/scenarios/generate \
     -H "X-Admin-Token: $ADMIN_TOKEN" -H 'Content-Type: application/json' \
     -d '{"track":"travel","topic":"酒店入住","cefr":"A2"}'
   ```
   （track 可选 daily/travel/work/study/life/exam，以课程方向为准；接口会走 LLM 生成对话脚本+生词卡并入库）
2. **质量抽检**：每批生成/重写后抽 5 篇，在 H5 端实际开对话听 TTS 顺一遍——重点看 AI 首句是否符合人设、生词卡释义是否准确；
3. **运营台**：H5 访问 `/#/pages/admin/content`，输入 `.env` 的 ADMIN_TOKEN，可看各来源篇数、按模板来源筛选、单篇/批量重写；
4. **节奏建议**：每周固定看一次「模板剩余量」（`/admin/content/status` 的 `template` 字段），新增话题优先走 generate 保证内容质量。

---

## 8. 成本与扩容

**每月 1 号看一次**（10 分钟）：

| 项 | 在哪看 | 预警线 |
| --- | --- | --- |
| 服务器账单 | 阿里云费用中心 | 固定包年，无常变 |
| LLM token | 百炼控制台账单 | 月消费环比 +100% 时查原因（§5 被刷排查） |
| OSS 备份存储 | OSS 控制台 | <10 元/月，忽略 |
| Apple Developer / 微信认证 | 年付 | 每年一次 |

**扩容触发条件（出现任一再动，不提前优化）**：
- 服务器 CPU 持续 >70% 或内存 >85%（连续一周）→ 升 4C8G 或加 swap 过渡；
- 数据库单表 >500 万行或备份 >1GB → 迁 RDS PG（compose 里 server 的 DB_HOST 指向 RDS 即可，其他不用改）；
- 需要多实例部署时：限流器要从内存实现换 Redis（`RateLimitFilter` 注释里有说明），SSE 前加 sticky session。

---

## 9. 用户反馈与合规动作

- **反馈渠道**：小程序“反馈与投诉”自带入口 + 鼓励用户加反馈群（V1 简单做）；每周巡检时统一看；
- **举报处理**：按 §6.3 取证 → 内容确有违规（涉政/涉黄/辱骂）→ 处理对应会话并留存记录 → 涉及模型生成内容的问题，同步在百炼侧确认护栏策略；
- **隐私政策**：`https://域名/privacy.html` 上线时发布；收集项变更（新增权限/字段）时**先改政策再发版**；
- **版本同步纪律**：任何提审文案改动（名称、简介、截图）以 launch-guide §四的合规口径为准。
