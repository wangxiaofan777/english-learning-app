#!/usr/bin/env bash
# Lingo API 端到端冒烟：mock 模式下跑通全流程
set -e
BASE="http://localhost:8080/api/v1"
PASS=0; FAIL=0

check() { # name, exit-code (0 = pass)
  if [ "$2" = "0" ]; then PASS=$((PASS+1)); echo "  PASS $1"; else FAIL=$((FAIL+1)); echo "  FAIL $1"; fi
}
jqpy() { python3 -c "import sys,json;d=json.load(sys.stdin);print(eval(sys.argv[1]))" "$1"; }

echo "== 1. 健康检查 =="
curl -s "$BASE/health" | grep -q '"status":"up"'; check "health" $?

echo "== 2. 游客登录 =="
LOGIN=$(curl -s -X POST "$BASE/auth/guest" -H 'Content-Type: application/json' -d '{"nickname":"冒烟测试"}')
TOKEN=$(echo "$LOGIN" | jqpy "d['data']['token']")
STEP=$(echo "$LOGIN" | jqpy "d['data']['onboardingStep']")
[ -n "$TOKEN" ] && [ "$STEP" = "goal" ]; check "guest login → token + step=goal" $?
AUTH="Authorization: Bearer $TOKEN"

echo "== 3. Onboarding 选目标 =="
STEP=$(curl -s -X POST "$BASE/onboarding" -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"goalTrack":"work","dailyMinutes":15}' | jqpy "d['data']['nextStep']")
[ "$STEP" = "placement" ]; check "onboarding → next=placement" $?

echo "== 4. 测评 =="
QCOUNT=$(curl -s "$BASE/placement/questions" -H "$AUTH" | jqpy "len(d['data'])")
ANSWERS=$(curl -s "$BASE/placement/questions" -H "$AUTH" | python3 -c "
import sys, json
qs = json.load(sys.stdin)['data']
keys = ['A','B','C','D']
print(json.dumps({q['id']: keys[i % 4] for i, q in enumerate(qs)}))")
CEFR=$(curl -s -X POST "$BASE/placement/submit" -H "$AUTH" -H 'Content-Type: application/json' \
  -d "{\"answers\":$ANSWERS,\"spokenText\":\"I am a data analyst and I like charts.\"}" | jqpy "d['data']['cefr']")
[ "$QCOUNT" = "10" ] && [ -n "$CEFR" ]; check "placement 10题 → 定级 $CEFR" $?

echo "== 5. 场景列表与推荐 =="
SCOUNT=$(curl -s "$BASE/scenarios?track=work" -H "$AUTH" | jqpy "len(d['data'])")
SCID=$(curl -s "$BASE/scenarios/recommended" -H "$AUTH" | jqpy "d['data']['id']")
[ "$SCOUNT" -ge 6 ] && [ -n "$SCID" ]; check "work 场景 $SCOUNT 个，推荐 id=$SCID" $?

echo "== 6. 创建对话（非流式一轮） =="
CONV=$(curl -s -X POST "$BASE/conversations" -H "$AUTH" -H 'Content-Type: application/json' \
  -d "{\"scenarioId\":$SCID}")
CVID=$(echo "$CONV" | jqpy "d['data']['conversationId']")
TITLE=$(echo "$CONV" | jqpy "d['data']['titleZh']")
R1=$(curl -s -X POST "$BASE/conversations/$CVID/messages" -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"content":"I want to introduce myself to the team."}')
FB=$(echo "$R1" | jqpy "bool(d['data']['feedback'])")
[ -n "$CVID" ] && [ -n "$TITLE" ] && [ "$FB" = "True" ]; check "对话 id=$CVID「$TITLE」，带反馈" $?

echo "== 7. SSE 流式一轮 =="
SSE=$(curl -s -N --max-time 15 "$BASE/conversations/$CVID/messages/stream?text=My%20name%20is%20Wang%20Lei." -H "$AUTH" | tr -d '\n')
echo "$SSE" | grep -q "event:start" && echo "$SSE" | grep -q "event:delta" && echo "$SSE" | grep -q "event:meta" && echo "$SSE" | grep -q "event:done"
check "SSE start→delta→meta→done" $?

echo "== 8. 结束对话拿复盘 =="
RECAP=$(curl -s -X POST "$BASE/conversations/$CVID/finish" -H "$AUTH" | jqpy "len(d['data']['recap']['strengths'])")
[ "$RECAP" -ge 3 ]; check "复盘含 $RECAP 条优点" $?

echo "== 9. 生词本 + 复习 =="
curl -s -X POST "$BASE/vocab" -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"word":"onboard","meaningZh":"使加入；熟悉环境","source":"dialog"}' >/dev/null
VOCABID=$(curl -s "$BASE/vocab/queue?limit=5" -H "$AUTH" | jqpy "d['data']['cards'][0]['id']")
DUE=$(curl -s "$BASE/vocab/queue?limit=5" -H "$AUTH" | jqpy "d['data']['dueCount']")
NEXT=$(curl -s -X POST "$BASE/vocab/$VOCABID/grade" -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"rating":3}' | jqpy "d['data']['nextDueAt']")
[ "$DUE" -ge 1 ] && [ -n "$NEXT" ]; check "生词 id=$VOCABID 评分后下次到期 $NEXT" $?

echo "== 10. 今日计划与统计 =="
TODAY=$(curl -s "$BASE/today" -H "$AUTH")
STREAK=$(echo "$TODAY" | jqpy "d['data']['streakDays']")
ITEMS=$(echo "$TODAY" | jqpy "len(d['data']['items'])")
MINUTES=$(curl -s "$BASE/stats" -H "$AUTH" | jqpy "d['data']['totalMinutes']")
[ "$ITEMS" = "3" ] && [ "$STREAK" -ge 1 ] && [ "$MINUTES" -ge 1 ]; check "streak=$STREAK 计划$ITEMS项 已学${MINUTES}分钟" $?

echo "== 11. 听说练习计时 =="
curl -s -X POST "$BASE/study/record" -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"kind":"listening","minutes":5,"count":8}' >/dev/null
NEWMIN=$(curl -s "$BASE/stats" -H "$AUTH" | jqpy "d['data']['totalMinutes']")
[ "$NEWMIN" -ge "$((MINUTES+5))" ]; check "精听计时已计入统计（$MINUTES → $NEWMIN 分钟）" $?

echo "== 12. Admin 生成场景（mock） =="
GEN=$(curl -s -X POST "$BASE/admin/scenarios/generate" -H "X-Admin-Token: dev-admin" \
  -H 'Content-Type: application/json' -d '{"track":"travel","topic":"机场值机","cefr":"A2"}')
GTITLE=$(echo "$GEN" | jqpy "d['data']['titleZh']")
[ -n "$GTITLE" ]; check "AI 生成场景「$GTITLE」" $?

echo ""
echo "结果：$PASS 通过 / $FAIL 失败"
[ "$FAIL" = "0" ]
