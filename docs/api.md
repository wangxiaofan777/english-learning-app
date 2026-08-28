# API 契约 v0.1

Base URL：`/api/v1`。统一响应 `{"code":0,"message":"ok","data":...}`；业务错误 code=400/401/404/500。
鉴权：除 `health`、`auth/**`、`admin/**` 外均需 `Authorization: Bearer <token>`。
ID 均以字符串形式返回（防 JS 精度丢失）。

## 认证与用户

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/auth/guest` | 游客登录，body `{nickname?}` → `{token, onboardingStep, profile}` |
| POST | `/auth/wechat` | 微信登录，body `{code, nickname?}`；未配置 WX_APPID 时走 dev 兜底 |
| GET | `/me` | 当前用户 Profile |
| POST | `/onboarding` | body `{goalTrack: daily/work/travel/exam, dailyMinutes}` → `{nextStep, profile}` |
| GET | `/placement/questions` | 10 道测评题（不含答案） |
| POST | `/placement/submit` | body `{answers: {qid: key}, spokenText?}` → `{score, total, cefr, weakTags, spokenComment}` |

## 场景

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/scenarios?track=&page=&size=` | 场景卡片列表（含 lineCount/vocabCount/practiced） |
| GET | `/scenarios/recommended` | 今日推荐场景（当前轨道下未练过的第一个） |
| GET | `/scenarios/{id}` | 场景详情：roleSetting + lines + vocab |
| GET | `/scenarios/free-talk` | 自由聊天场景（不占课程，大厅直达入口） |

## 对话

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/conversations` | body `{scenarioId}` → 创建对话 + AI 开场白 |
| GET | `/conversations` | 最近 30 轮对话历史 |
| GET | `/conversations/{id}` | 对话详情（场景信息 + 全部消息） |
| POST | `/conversations/{id}/messages` | 非流式回复，body `{content}` → 助手 MessageView（含 feedback） |
| GET | `/conversations/{id}/messages/stream?text=` | **SSE 流式**，事件：`start` → `delta`* → `meta`（完整助手消息含反馈）→ `done`；出错推 `error` |
| POST | `/conversations/{id}/finish` | 结束本轮 → `{recap: {summary, strengths[], suggestions[]}, vocab}` |

`feedback` 结构：`{betterWay, grammarFix: {original, corrected, explain} | null, vocabHints: [{word, meaningZh}]}`。

## 生词与复习

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/vocab` | body `{word, meaningZh?, phonetic?, exampleEn?, exampleZh?, source?, scenarioId?}`（按 user+word 幂等） |
| GET | `/vocab?page=&size=` | 词库列表 |
| GET | `/vocab/queue?limit=15` | 到期复习卡 `{cards, dueCount}` |
| POST | `/vocab/{id}/grade` | body `{rating: 1忘了/2困难/3良好/4轻松}` → FSRS 排期 `{nextDueAt}` |

## 课程体系

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/courses` | 课程目录（18 门：6 学段方向 × 3 周期）：`{id, track(方向码), examTag, months, titleZh, lessonCount, doneCount, enrolled}` |
| GET | `/courses/current` | 当前课程详情 + 课时清单（status: done/current/locked）+ currentLessonId；未报名返回 null |
| GET | `/courses/{id}` | 指定课程详情 |
| POST | `/courses/{id}/enroll` | 报名/切换课程与周期（幂等） |
| POST | `/courses/complete` | 完课上报，body `{lessonType, scenarioId|null, score?}`（review 课时 scenarioId 传 null，仅推进当前课），幂等 |

课程制定规则：Onboarding 提交与测评定级时自动匹配「年龄段 + 目标 → 学段方向」的课程（默认 3 个月周期）：
child→小学(KET/PET)；teen 按等级→初中(中考)/高中(高考)；成人 work→职场(BEC)、exam→四六级、travel/daily→出境生活。

## 学习计划与统计

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/today` | 每日三件事 + 打卡：`{date, streakDays, cefrLevel, dueCount, todayMinutes, items[3], xp, dailySentence[en,zh]}`；每日首次调用自动打卡 +5 XP |
| GET | `/stats` | `{totalMinutes, totalDialogs, wordsTotal, wordsLearning, week[7], weekXp, totalXp}` |
| GET | `/achievements` | 成就徽章墙（实时计算）：`[{code, name, description, icon, earned}]` |
| GET | `/study/calendar?month=YYYY-MM` | 打卡月历：`{month, days:[{date, minutes, xp}], studiedCount, streakDays}` |
| POST | `/study/record` | 端上练习计时，body `{kind, minutes?, count?}`；XP 按行为类型由服务端结算（对话+30/精听+20/跟读+25/Boss+40/复习每词+2/速测与拼写每题+3/打卡+5） |

## 管理（X-Admin-Token 鉴权）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/admin/scenarios/generate` | body `{track, topic, cefr}`；配置 LLM 后按流水线生成入库，否则模板兜底 |
| GET | `/admin/content/status` | 内容分层统计 `{total, seed, ai, template}` |
| GET | `/admin/content/scenarios?source=template&page=` | 按来源列出场景（内容运营台用） |
| POST | `/admin/content/rewrite` | body `{scenarioId}`；LLM 重写单篇正文（id/标题不变，课时引用不断链） |
| POST | `/admin/content/rewrite-batch` | body `{limit}`；批量重写，返回 `{rewritten, remaining}` |
