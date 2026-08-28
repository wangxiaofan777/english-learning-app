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

## 学习计划与统计

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/today` | 每日三件事 `{date, streakDays, cefrLevel, dueCount, todayMinutes, items[3]}` |
| GET | `/stats` | `{totalMinutes, totalDialogs, wordsTotal, wordsLearning, week[7]}` |

## 管理（X-Admin-Token 鉴权）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/admin/scenarios/generate` | body `{track, topic, cefr}`；配置 LLM 后按流水线生成入库，否则模板兜底 |
