#!/bin/bash
# 学习计划结构化拆解 · 业务链路冒烟脚本
# 用法: bash scripts/study_goal_smoke.sh [backend_base] [username] [password]
# 验证: save(4任务) -> 勾选2个(progress=50/in_progress) -> 剩余清单 -> 全勾完(progress=100/completed) -> 取消勾选回退 -> 归属校验
set -e

BASE_ARG="${1:-http://127.0.0.1:8080}"
USER="${2:-smoke_goal_user}"
PASS="${3:-Smoke#12345}"
B="$BASE_ARG/api/study-goal"

TOKEN=$(curl -s -X POST "$BASE_ARG/api/auth/applogin" -H "Content-Type: application/json" \
  -d "{\"username\":\"$USER\",\"password\":\"$PASS\"}" \
  | python -c "import json,sys;print(json.load(sys.stdin)['data']['token'])")
A="Authorization: Bearer $TOKEN"
echo "== token acquired"

echo "== 1) save goal with 4 tasks"
RESP=$(curl -s -X POST "$B/save" -H "$A" -H "Content-Type: application/json" -d '{
 "goal":{"title":"30天学会Python爬虫","description":"从零基础到完成一个简单爬虫项目"},
 "tasks":[
  {"taskName":"学习Python基础语法","stage":"基础阶段","estimatedDays":10,"priority":"高","orderNum":1,"isCompleted":false,"description":"掌握变量循环函数"},
  {"taskName":"学习请求库","stage":"基础阶段","estimatedDays":5,"priority":"中","orderNum":2,"isCompleted":false,"description":""},
  {"taskName":"解析HTML","stage":"进阶阶段","estimatedDays":7,"priority":"中","orderNum":3,"isCompleted":false,"description":"BeautifulSoup"},
  {"taskName":"完成爬虫项目","stage":"冲刺阶段","estimatedDays":8,"priority":"低","orderNum":4,"isCompleted":false,"description":"实战"}
 ]}')
export SMOKE_RESP="$RESP"
GID=$(python -c "import json,os;d=json.loads(os.environ['SMOKE_RESP'])['data'];g=d['goal'];assert g['progress']==0 and g['status']=='pending',g;assert len(d['tasks'])==4;print(g['id'])")
IDS=$(python -c "import json,os;print(' '.join(str(t['id']) for t in json.loads(os.environ['SMOKE_RESP'])['data']['tasks']))")
read -r T1 T2 T3 T4 <<< "$IDS"
echo "goalId=$GID taskIds=$T1,$T2,$T3,$T4"

toggle() { curl -s -X PUT "$B/tasks/$1/completion" -H "$A" -H "Content-Type: application/json" -d "{\"isCompleted\":$2}"; }

echo "== 2) complete task1&2 -> expect progress=50 status=in_progress"
toggle "$T1" true > /dev/null
SMOKE_RESP=$(toggle "$T2" true) python -c "
import json,os
g=json.loads(os.environ['SMOKE_RESP'])['data']
assert g['progress']==50 and g['status']=='in_progress',g
print('after 2 completions: progress=%s status=%s'%(g['progress'],g['status']))"

echo "== 3) detail filter=pending -> only last 2 tasks"
PENDING_NAMES=$(curl -s "$B/$GID?filter=pending" -H "$A" | python -c "
import json,sys
d=json.load(sys.stdin)['data']
names=[t['taskName'] for t in d['tasks']]
assert len(names)==2 and d['goal']['progress']==50,(d['goal'],names)
print(','.join(names))")
echo "pending filter ok: $PENDING_NAMES"

echo "== 4) remaining-tasks endpoint -> 2 items"
curl -s "$B/$GID/remaining-tasks" -H "$A" | python -c "
import json,sys
rows=json.load(sys.stdin)['data']
assert all(not t['isCompleted'] for t in rows),rows
assert len(rows)==2,len(rows)
print('remaining tasks:',[t['taskName'] for t in rows])"

echo "== 5) complete task3&4 -> expect progress=100 status=completed"
toggle "$T3" true > /dev/null
SMOKE_RESP=$(toggle "$T4" true) python -c "
import json,os
g=json.loads(os.environ['SMOKE_RESP'])['data']
assert g['progress']==100 and g['status']=='completed',g
print('final progress=%s status=%s'%(g['progress'],g['status']))"

echo "== 6) uncheck one -> back to in_progress 75"
SMOKE_RESP=$(toggle "$T4" false) python -c "
import json,os
g=json.loads(os.environ['SMOKE_RESP'])['data']
assert g['progress']==75 and g['status']=='in_progress',g
print('after uncheck progress=%s status=%s'%(g['progress'],g['status']))"

echo "== 7) ownership guard: nonexistent goal -> expect http!=200"
CODE=$(curl -s -o /dev/null -w '%{http_code}' "$B/999999999/remaining-tasks" -H "$A")
echo "GET /999999999/remaining-tasks http=$CODE"
[ "$CODE" != "200" ] || { echo 'FAIL: should not return 200'; exit 1; }

echo "ALL SMOKE CHECKS PASSED"
