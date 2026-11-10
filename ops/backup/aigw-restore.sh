#!/usr/bin/env bash
# 격리 복원(Plan P15 M-2·M-6, §11.8). 백업 이미지 안에서만 실행한다:
#   docker run --rm --network host -v <bundle 디렉터리>:/in:ro -v <identity>:/run/age/identity:ro -v <출력 디렉터리>:/out \
#     -e AIGW_RESTORE_PGPASSWORD <backup image@digest> /opt/aigw/aigw-restore.sh --bundle /in/<bundle> \
#     --target-db-url postgresql://<단일 계정>@127.0.0.1:15432/nori_ai_gateway --expect-db-release ai-gateway-db-restore \
#     --age-identity-file /run/age/identity --receipt-dir /out
# 종료 코드: 2 입력 오류, 7 RESTORE_TARGET_REJECTED(쓰기 0), 8 RESTORE_STEP_FAILED(오류 1건이면 즉시 중단).
# 평문 덤프는 디스크에 쓰지 않고 복호 스트림을 pg_restore 에 파이프로만 넘긴다. 주 DB·주 앱 release 는 바꾸지 않는다.
# 대상은 리허설 전용 임시 인스턴스이고, 접속 계정은 bootstrap 이 env 로 받은 이름으로 만든 단일 계정(그 DB 의 소유자)이다.
set -euo pipefail
set +x
umask 077
FORWARD_PORT=15432 COLUMNS_FILE=/opt/aigw/job-columns-v1.txt
bundle= target= release= identity= receipt_dir=
die() { printf '%s\n' "$1" >&2; exit "$2"; }
while (($#)); do
  (($# >= 2)) || die ARGUMENT_MISSING 2
  case $1 in
    --bundle) bundle=$2 ;; --target-db-url) target=$2 ;; --expect-db-release) release=$2 ;;
    --age-identity-file) identity=$2 ;; --receipt-dir) receipt_dir=$2 ;;
    *) die ARGUMENT_INVALID 2 ;;
  esac
  shift 2
done
[[ -f $bundle && -f $identity && -d $receipt_dir && -n ${AIGW_RESTORE_PGPASSWORD:-} ]] || die ARGUMENT_MISSING 2
[[ $target =~ ^postgresql://([A-Za-z_][A-Za-z0-9_.-]{0,62})@([A-Za-z0-9.-]+):([0-9]+)/nori_ai_gateway$ ]] || die TARGET_URL_INVALID 2
export PGUSER=${BASH_REMATCH[1]} PGHOST=${BASH_REMATCH[2]} PGPORT=${BASH_REMATCH[3]} PGDATABASE=nori_ai_gateway PGPASSWORD=$AIGW_RESTORE_PGPASSWORD
unset AIGW_RESTORE_PGPASSWORD
work=$(mktemp -d)
trap 'rm -rf -- "$work"' EXIT
step=INPUT
fail_step() { printf 'RESTORE_STEP_FAILED:%s\n' "$step" >&2; exit 8; }
q() { psql -X -q -w -A -t -v ON_ERROR_STOP=1 -c "$1"; }

# 묶음: 암호문 3개와 manifest 를 확인한다(manifest 평문은 메모리 변수에만).
step=BUNDLE
# 묶음 = 정확히 세 일반 파일(이 순서)뿐이어야 추출한다. link·디렉터리·장치·중복·추가 항목은 추출 전에 거부한다.
listing=$(LC_ALL=C tar --numeric-owner -tvf "$bundle" 2>/dev/null) || fail_step
mapfile -t members <<< "$listing"
expected_members=(manifest.json.age main.dump.age job.copy.age)
(( ${#members[@]} == 3 )) || fail_step
for index in 0 1 2; do
  [[ ${members[$index]} =~ ^-[rwx-]{9}\ [0-9]+/[0-9]+\ +([0-9]+)\ [0-9-]+\ [0-9:]+\ ([^ ]+)$ && ${BASH_REMATCH[2]} == "${expected_members[$index]}" ]] || fail_step
  [[ $index != 0 ]] || (( BASH_REMATCH[1] <= 65536 )) || fail_step
done
tar -xf "$bundle" -C "$work" --no-same-owner --no-same-permissions "${expected_members[@]}" 2>/dev/null || fail_step
for member in "${expected_members[@]}"; do [[ -f $work/$member && ! -L $work/$member ]] || fail_step; done
# manifest 는 백업 스크립트가 쓰는 고정 형태 그대로여야 한다(필드 집합·순서·형식·크기 — 중복·추가 필드 0).
manifest=$(age -d -i "$identity" "$work/manifest.json.age" 2>/dev/null | head -c 4097) || fail_step
manifest_pattern='^\{"formatVersion":"aigw-backup-v1","snapshotAt":"([0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}\.[0-9]{6}Z)","flywayLatest":"([0-9][0-9._]{0,31})","jobColumnsSha256":"([0-9a-f]{64})","jobRowCount":(0|[1-9][0-9]{0,11}),"delegationExcluded":true,"files":\{"main\.dump\.age":"([0-9a-f]{64})","job\.copy\.age":"([0-9a-f]{64})"\}\}$'
(( ${#manifest} <= 4096 )) && [[ $manifest =~ $manifest_pattern ]] || fail_step
snapshot_at=${BASH_REMATCH[1]} flyway_latest=${BASH_REMATCH[2]} columns_sha=${BASH_REMATCH[3]} job_rows=${BASH_REMATCH[4]} main_sha=${BASH_REMATCH[5]} job_sha=${BASH_REMATCH[6]}
[[ $columns_sha == $(sha256sum "$COLUMNS_FILE" | cut -d ' ' -f 1) ]] || fail_step
[[ $main_sha == $(sha256sum "$work/main.dump.age" | cut -d ' ' -f 1) && $job_sha == $(sha256sum "$work/job.copy.age" | cut -d ' ' -f 1) ]] || fail_step

# 대상 검증: 하나라도 실패하면 쓰기 0.
reject() { printf 'RESTORE_TARGET_REJECTED:%s\n' "$1" >&2; exit 7; }
[[ $release == ai-gateway-db-restore ]] || reject release
[[ $PGPORT == "$FORWARD_PORT" ]] || reject port
schemas=$(q "SELECT count(*) FROM pg_namespace WHERE nspname IN ('gw','kn','gw_flyway')" 2>/dev/null) || reject connect
tables=$(q "SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace WHERE c.relkind IN ('r','p','v','m','S') AND n.nspname NOT IN ('pg_catalog','information_schema') AND n.nspname NOT LIKE 'pg_toast%'") || reject connect
# bootstrap 된 대상 = 접속 계정이 그 DB 의 소유자(서버 선행조건과 같은 형태 — 스키마를 만들 수 있다).
roles=$(q "SELECT count(*) FROM pg_database WHERE datname = current_database() AND pg_get_userbyid(datdba) = current_user") || reject connect
[[ $schemas == 0 && $tables == 0 ]] || reject not_empty
[[ $roles == 1 ]] || reject bootstrap_roles

# TOC 필터(N15): bootstrap(superuser)이 소유한 항목만 뺀다. 그 밖(gw·kn·gw_flyway·SEQUENCE SET 포함)은 그대로 복원한다.
step=TOC
decrypt() { age -d -i "$identity" "$work/main.dump.age"; }
decrypt | pg_restore -l > "$work/toc.full" || fail_step
# bootstrap.sql(superuser)이 만든 정확한 항목만: vector 확장·그 COMMENT, public 스키마 ACL/COMMENT, pg_control_system() 의 EXECUTE ACL.
# 다른 확장·pg_catalog ACL 은 빼지 않는다 — 대상에 없으면 pg_restore 오류로 exit 8 이 된다.
excluded_pattern='^[0-9]+; [0-9]+ [0-9]+ (EXTENSION - vector|COMMENT - EXTENSION vector|ACL - SCHEMA public|COMMENT - SCHEMA public|ACL pg_catalog FUNCTION pg_control_system\(\)) '
grep -E "$excluded_pattern" "$work/toc.full" > "$work/toc.excluded" || true
grep -vE "$excluded_pattern" "$work/toc.full" > "$work/restore.list"
restore_section() {
  step=$1
  decrypt | pg_restore --section="$1" --no-owner --role="$PGUSER" --exit-on-error -L "$work/restore.list" -d "$PGDATABASE" >/dev/null 2>"$work/restore.err" || fail_step
}
restore_section pre-data
step=JOB_DATA
columns=$(paste -sd, "$COLUMNS_FILE")
psql -X -q -w -v ON_ERROR_STOP=1 -c "\\copy gw.job (${columns//,/, }, delegation_id) FROM PROGRAM 'age -d -i $identity $work/job.copy.age' WITH (FORMAT binary)" >/dev/null 2>&1 || fail_step
restore_section data
restore_section post-data

# 검증: manifest 행 수·인가 필드·Flyway.
step=VERIFY
[[ $(q "SELECT count(*) FROM gw.job") == "$job_rows" ]] || fail_step
[[ $(q "SELECT count(*) FROM gw.job WHERE delegation_id IS NOT NULL") == 0 ]] || fail_step
[[ $(q "SELECT version FROM gw_flyway.flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1") == "$flyway_latest" ]] || fail_step
# 표별 행 수(건수만): 식별자는 카탈로그에서만 얻는다.
counts=$(psql -X -q -w -A -t -v ON_ERROR_STOP=1 <<'SQL'
SELECT string_agg(format('"%s":%s', t, (xpath('/row/c/text()', query_to_xml(format('SELECT count(*) AS c FROM %s', t), false, true, '')))[1]::text), ',' ORDER BY t)
FROM (SELECT format('%I.%I', schemaname, relname) AS t FROM pg_stat_user_tables WHERE schemaname IN ('gw','kn','gw_flyway')) tables;
SQL
) || fail_step
excluded=$(sed -E 's/^[0-9]+; [0-9]+ [0-9]+ //; s/"/\\"/g; s/.*/"&"/' "$work/toc.excluded" | paste -sd, -)
bundle_sha=$(sha256sum "$bundle" | cut -d ' ' -f 1)
printf '{"bundleSha256":"%s","snapshotAt":"%s","flywayLatest":"%s","jobRowCount":%s,"tableRowCounts":{%s},"tocExcluded":[%s]}\n' \
  "$bundle_sha" "$snapshot_at" "$flyway_latest" "$job_rows" "$counts" "$excluded" > "$receipt_dir/restore-receipt.json"
printf 'RESTORE_SUCCESS snapshotAt=%s\n' "$snapshot_at"
