#!/usr/bin/env bash
# 백업 실패 경보 주입(Plan P15 N1, AC-24-BACKUP-ALERT — OPS producer). 운영자 호스트·namespace tc 의 Job 생성 권한만 쓴다.
# ① CronJob 에서 만든 수동 Job 정의의 컨테이너 env 에만 AIGW_BACKUP_DEST_OVERRIDE=unreachable-probe 를 넣어 생성(공유 Secret·CronJob 변경 0)
#    → 실패 대기 → 주입 전 gw.backup_run 최대 id 이후 새 행이 정확히 1건이고 FAILED:UPLOAD·aigw_backup_last_run_failed=1·AIGW-BACKUP-FAILED firing 을 읽기 전용 확인
#    (gw.backup_run 은 공유 서버 192.168.0.150:15432/nori_ai_gateway 를 운영자 호스트 psql 로 읽는다 — 자격 = Secret ai-gateway-config 의 단일 계정 두 키 → psql env)
# ② override 없는 수동 백업 1회 → 그 뒤 새 행 정확히 1건 SUCCESS·지표 0 복귀 ③ OPS 레코드를 evidence/F4/ops/ 에 쓰고 eval ingest(--source ops).
# Prometheus firing 은 수신 확인이 아니다. G-ALERT 수신처 확인 경로가 승인되기 전(docs/gates/f1/alerting.md NOT_RUN)에는 관측이 모두 참이어도
# 결과 = NOT_RUN(ALERT_RECEIPT_PATH_UNAPPROVED)·exit 2 이고, 관측이 하나라도 거짓이면 FAIL·exit 1 이다(PASS 경로 없음).
# 사용: aigw-backup-alert-injection.sh --parent-run-id <p> --prometheus-url <Prometheus API> --wait-seconds <n> [--spec <SPEC>]
set -euo pipefail
set +x
export PYTHONDONTWRITEBYTECODE=1
error() { printf '%s\n' "$1" >&2; exit "${2:-2}"; }
parent= prometheus= wait_seconds= spec=${AIGW_SPEC_FILE:-}
while (($#)); do
  (($# >= 2)) || error ARGUMENT_MISSING
  case $1 in
    --parent-run-id) parent=$2;; --prometheus-url) prometheus=$2;; --wait-seconds) wait_seconds=$2;; --spec) spec=$2;;
    *) error ARGUMENT_INVALID;;
  esac
  shift 2
done
[[ -f operations/runconfig/verify.schema.json ]] || error AI_ROOT_REQUIRED
[[ $parent =~ ^[A-Za-z0-9_.:-]{1,128}$ ]] || error PARENT_RUN_ID_MISSING
[[ -n $spec && -f $spec ]] || error SPEC_INPUT_MISSING
[[ $prometheus =~ ^https?://[^[:space:]]+$ ]] || error PROMETHEUS_URL_MISSING
[[ $wait_seconds =~ ^[1-9][0-9]{0,4}$ ]] || error WAIT_SECONDS_INVALID
for tool in kubectl python3 bash psql; do command -v "$tool" >/dev/null || error "TOOL_MISSING:$tool"; done
stamp=$(date -u +%Y%m%dT%H%M%SZ)
probe_job=ai-gateway-db-backup-alertprobe-$stamp recovery_job=ai-gateway-db-backup-alertprobe-$stamp-recovery
tmp=$(mktemp -d); trap 'rm -rf -- "$tmp"' EXIT

create_job() {  # $1 = Job 이름, $2 = override 값(빈 값이면 넣지 않음). Job 정의는 파이프로만 다닌다(디스크 파일 0).
  local transform='import json,sys
override=sys.argv[1]
job=json.load(sys.stdin)
if job.get("kind")!="Job": sys.exit(1)
if override:
    for container in job["spec"]["template"]["spec"]["containers"]:
        env=[e for e in container.get("env",[]) if e.get("name")!="AIGW_BACKUP_DEST_OVERRIDE"]
        container["env"]=env+[{"name":"AIGW_BACKUP_DEST_OVERRIDE","value":override}]
json.dump(job,sys.stdout)'
  kubectl -n tc create job --from=cronjob/ai-gateway-db-backup "$1" --dry-run=client -o json 2>/dev/null |
    python3 -B -c "$transform" "$2" | kubectl -n tc create -f - >/dev/null 2>&1 || error JOB_CREATE_FAILED 1
}
db_sql() {  # stdin = 고정 SQL. 자격 값은 셸 변수 → psql 프로세스 env 로만(명령행·출력·파일 0), 출력 공백 제거
  local account secret
  account=$(kubectl -n tc get secret ai-gateway-config -o 'go-template={{index .data "AI_GATEWAY_DB_USER" | base64decode}}' 2>/dev/null) || return 1
  secret=$(kubectl -n tc get secret ai-gateway-config -o 'go-template={{index .data "AI_GATEWAY_DB_PASSWORD" | base64decode}}' 2>/dev/null) || return 1
  [[ -n $account && -n $secret ]] || return 1
  PGHOST=192.168.0.150 PGPORT=15432 PGDATABASE=nori_ai_gateway PGUSER=$account PGPASSWORD=$secret psql -X -q -w -A -t -v ON_ERROR_STOP=1 -f - 2>/dev/null | tr -d '[:space:]'
}
rows_after() {  # $1 = 기준 id(숫자). 출력 = <새 행 수>:<최대 id>:<최신 outcome>:<detail_code>
  printf '%s\n' "SELECT count(*) || ':' || coalesce(max(id), 0) || ':' || coalesce((SELECT outcome || ':' || coalesce(detail_code, '') FROM gw.backup_run WHERE id > $1 ORDER BY id DESC LIMIT 1), '') FROM gw.backup_run WHERE id > $1;" | db_sql
}
prom() {  # $1 = PromQL. 결과 = 숫자 값 첫 개 또는 빈 값(조회 실패는 빈 값 — 판정은 FAIL 로만)
  python3 -B - "$prometheus" "$1" <<'PY' 2>/dev/null || true
import json,sys,urllib.parse,urllib.request
base,query=sys.argv[1:]
with urllib.request.urlopen(base.rstrip('/')+'/api/v1/query?'+urllib.parse.urlencode({'query':query}),timeout=10) as r:
    data=json.load(r)
result=data.get('data',{}).get('result',[])
print(result[0]['value'][1] if result else '')
PY
}
wait_until() {  # $1 = 비교 함수 이름. wait_seconds 안에서 최대 15초 간격
  local deadline=$((SECONDS + wait_seconds))
  while ((SECONDS < deadline)); do "$1" && return 0; sleep $((wait_seconds < 15 ? wait_seconds : 15)); done
  "$1"
}

# 기준 id: 조회 실패는 0 행이 아니라 중단(Job 생성 0).
base_id=$(printf '%s\n' "SELECT coalesce(max(id), 0) FROM gw.backup_run;" | db_sql) || error BACKUP_RUN_QUERY_FAILED 1
[[ $base_id =~ ^[0-9]{1,19}$ ]] || error BACKUP_RUN_QUERY_FAILED 1
# ① 실패 주입
create_job "$probe_job" unreachable-probe
kubectl -n tc wait --for=condition=failed "job/$probe_job" --timeout="${wait_seconds}s" >/dev/null 2>&1 && probe_failed=true || probe_failed=false
injected=$(rows_after "$base_id" || true)
probe_id=$(cut -d: -f2 <<<"$injected"); [[ $probe_id =~ ^[0-9]{1,19}$ ]] || probe_id=$base_id
metric_failed() { [[ $(prom 'max(aigw_backup_last_run_failed)') == 1 ]]; }
alert_firing() { [[ -n $(prom 'max(ALERTS{alertname="AIGW-BACKUP-FAILED",alertstate="firing"})') ]]; }
wait_until metric_failed && metric_one=true || metric_one=false
wait_until alert_firing && alert_seen=true || alert_seen=false
# ② 복귀(override 없음)
create_job "$recovery_job" ''
kubectl -n tc wait --for=condition=complete "job/$recovery_job" --timeout="${wait_seconds}s" >/dev/null 2>&1 && recovered=true || recovered=false
recovered_row=$(rows_after "$probe_id" || true)
metric_cleared() { [[ $(prom 'max(aigw_backup_last_run_failed)') == 0 ]]; }
wait_until metric_cleared && metric_zero=true || metric_zero=false

# ③ OPS 레코드(건수·시각·상태만) → ingest
mkdir -p evidence/F4/ops
out=evidence/F4/ops/backup-alert-injection-$stamp.json
python3 -B - "$tmp/records.json" "$parent" "$stamp" "$probe_failed" "$injected" "$metric_one" "$alert_seen" "$recovered" "$recovered_row" "$metric_zero" <<'PY'
import json,sys,uuid
out,parent,stamp,probe_failed,injected,metric_one,alert_seen,recovered,recovered_row,metric_zero=sys.argv[1:]
# 새 행 결속: 주입 뒤 새 행 정확히 1건 = FAILED:UPLOAD, 복귀 뒤 새 행 정확히 1건 = SUCCESS(동시 CronJob 실행 등 다른 행이 끼면 거짓).
flags=dict(probeJobFailed=probe_failed=='true',failedRowObserved=injected.startswith('1:') and injected.endswith(':FAILED:UPLOAD'),failedMetricOne=metric_one=='true',
           alertFiring=alert_seen=='true',recoveryJobComplete=recovered=='true',recoveredRowSuccess=recovered_row.startswith('1:') and recovered_row.endswith(':SUCCESS:'),
           failedMetricZero=metric_zero=='true',alertReceiptConfirmed=False)
observed=all(v for k,v in flags.items() if k!='alertReceiptConfirmed')
record=dict(kind='LEAF',stage='F4',leafId='AC-24-BACKUP-ALERT',producer='OPS',componentId='OP:backup-alert',parentRunId=parent,
            runId='backup-alert-'+stamp+'-'+uuid.uuid4().hex[:8],labels=['MOCK_API'],environment='verify',result='NOT_RUN' if observed else 'FAIL',values=flags)
if observed: record['reasonCode']='ALERT_RECEIPT_PATH_UNAPPROVED'
json.dump(record,open(out,'w',encoding='utf-8'))
PY
python3 -B operations/gates/evidence_record.py seal "$spec" "$tmp/records.json" "$out" || error EVIDENCE_RECORD_INVALID 1
bash ./gradlew :gateway-eval:run --args="ingest --stage F4 --parent-run-id $parent --input $out --source ops --spec $spec" --no-daemon >"$tmp/ingest.log" 2>&1 || error INGEST_FAILED 1
python3 -B -c 'import json,sys; r=json.load(open(sys.argv[1])); print("BACKUP_ALERT_INJECTION", r["result"], r.get("reasonCode", "-"), sys.argv[1]); sys.exit({"NOT_RUN": 2}.get(r["result"], 1))' "$out"
