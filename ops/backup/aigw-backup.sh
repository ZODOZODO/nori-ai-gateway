#!/usr/bin/env bash
# 검증 환경 일일 백업(Plan P15 M-6). 백업 이미지 안에서 db-chart CronJob 명령으로만 실행한다.
# 입력(env): PGHOST·PGUSER(aigw_backup)·PGPASSWORD(Secret)·PGDATABASE(ai_gateway)·PGPORT,
#   AIGW_BACKUP_DESTINATION, AIGW_BACKUP_AGE_RECIPIENT, AIGW_BACKUP_RETENTION_DAYS, AIGW_BACKUP_DEST_TYPE(이미지 고정),
#   AIGW_BACKUP_CREDENTIALS_DIR(목적지 Secret 마운트), 선택 AIGW_BACKUP_DEST_OVERRIDE=unreachable-probe(경보 주입 전용).
# 평문 덤프·Job 데이터는 파일로 쓰지 않고 파이프로만 age 에 넘긴다. 결과는 gw.backup_run 1행(SUCCESS|FAILED+단계 코드)이다.
set -euo pipefail
set +x
umask 077
PLAIN=/work/plain ENC=/work/enc COLUMNS_FILE=/opt/aigw/job-columns-v1.txt
stage=INPUT recorded=0 session_pid= bundle= remote_object=

die_input() { printf 'BACKUP_INPUT_INVALID:%s\n' "$1" >&2; exit 2; }
[[ ${AIGW_BACKUP_DEST_TYPE:-} == s3 || ${AIGW_BACKUP_DEST_TYPE:-} == sftp ]] || die_input dest_type
[[ ${AIGW_BACKUP_AGE_RECIPIENT:-} =~ ^age1[02-9ac-hj-np-z]{58}$ ]] || die_input age_recipient
[[ ${AIGW_BACKUP_RETENTION_DAYS:-} =~ ^[1-9][0-9]{0,2}$ ]] || die_input retention_days
[[ -n ${PGHOST:-} && ${PGUSER:-} == aigw_backup && ${PGDATABASE:-} == ai_gateway && -n ${PGPASSWORD:-} ]] || die_input pg_connection
[[ -d ${AIGW_BACKUP_CREDENTIALS_DIR:-} ]] || die_input credentials_dir
[[ -d $PLAIN && -d $ENC && -z $(ls -A "$PLAIN") && -z $(ls -A "$ENC") ]] || die_input work_dirs
destination=${AIGW_BACKUP_DESTINATION:-}
if [[ -n ${AIGW_BACKUP_DEST_OVERRIDE:-} ]]; then
  # 공유 Secret·CronJob 을 바꾸지 않고 이 Job 의 업로드만 존재하지 않는 고정 대상으로 돌린다.
  [[ $AIGW_BACKUP_DEST_OVERRIDE == unreachable-probe ]] || die_input dest_override
  case $AIGW_BACKUP_DEST_TYPE in
    s3) destination=http://127.0.0.1:9/aigw-unreachable-probe/probe ;;
    sftp) destination=sftp://aigw-probe@127.0.0.1:9/aigw-unreachable-probe ;;
  esac
fi
case $AIGW_BACKUP_DEST_TYPE in
  s3) [[ $destination =~ ^(https?://[^/@]+)/([a-z0-9][a-z0-9.-]{1,61}[a-z0-9])(/[A-Za-z0-9._/-]*)?$ ]] || die_input destination
      s3_endpoint=${BASH_REMATCH[1]} s3_bucket=${BASH_REMATCH[2]} s3_prefix=${BASH_REMATCH[3]#/} ;;
  sftp) [[ $destination =~ ^sftp://([a-z_][a-z0-9_-]*)@([A-Za-z0-9.-]+)(:([0-9]{1,5}))?(/[A-Za-z0-9._/-]+)$ ]] || die_input destination
      sftp_user=${BASH_REMATCH[1]} sftp_host=${BASH_REMATCH[2]} sftp_port=${BASH_REMATCH[4]:-22} sftp_path=${BASH_REMATCH[5]} ;;
esac
mapfile -t columns < "$COLUMNS_FILE"
for column in "${columns[@]}"; do [[ $column =~ ^[a-z_]+$ ]] || die_input columns; done
column_list=$(IFS=,; printf '%s' "${columns[*]}")
column_list=${column_list//,/, }

utc() { date -u +%Y-%m-%dT%H:%M:%S.%6NZ; }
started_at=$(utc)
record() {
  # aigw_backup 의 유일한 쓰기 권한(V3). 값은 스크립트가 만든 시각·고정 코드뿐이다.
  local outcome=$1 detail=$2 point=$3
  psql -X -q -w -v ON_ERROR_STOP=1 -v started="$started_at" -v finished="$(utc)" -v outcome="$outcome" -v point="$point" -v detail="$detail" >/dev/null 2>&1 <<'SQL'
INSERT INTO gw.backup_run(started_at, finished_at, outcome, backup_point_at, detail_code)
VALUES (:'started'::timestamptz, :'finished'::timestamptz, :'outcome', NULLIF(:'point', '')::timestamptz, NULLIF(:'detail', ''));
SQL
}
remote_delete() {
  [[ -n $remote_object ]] || return 0
  case $AIGW_BACKUP_DEST_TYPE in
    s3) rclone deletefile "aigwdest:$remote_object" --retries 1 --low-level-retries 1 >/dev/null 2>&1 || true ;;
    sftp) printf 'rm %s\n' "$remote_object" | sftp_batch >/dev/null 2>&1 || true ;;
  esac
}
cleanup() {
  if [[ -n $session_pid ]]; then kill "$session_pid" 2>/dev/null || true; wait "$session_pid" 2>/dev/null || true; session_pid=; fi
  find "$PLAIN" "$ENC" -mindepth 1 -delete 2>/dev/null || true
}
residue_free() { [[ -z $(ls -A "$PLAIN") && -z $(ls -A "$ENC") ]]; }
on_exit() {
  local rc=$?
  trap - EXIT
  if (( rc != 0 && recorded == 0 )); then
    remote_delete
    cleanup
    record FAILED "$stage" '' || true
    printf 'BACKUP_FAILED:%s\n' "$stage" >&2
  fi
  cleanup
  if ! residue_free; then printf 'BACKUP_RESIDUE\n' >&2; (( rc != 0 )) || rc=9; fi
  exit "$rc"
}
trap on_exit EXIT

sftp_batch() {
  sftp -q -b - -P "$sftp_port" -i "$AIGW_BACKUP_CREDENTIALS_DIR/ssh-private-key" -o BatchMode=yes -o StrictHostKeyChecking=yes \
    -o UserKnownHostsFile="$AIGW_BACKUP_CREDENTIALS_DIR/known-hosts" -o ConnectTimeout=10 "$sftp_user@$sftp_host"
}
upload_s3() {
  local name=$1
  export RCLONE_CONFIG_AIGWDEST_TYPE=s3 RCLONE_CONFIG_AIGWDEST_PROVIDER=Other RCLONE_CONFIG_AIGWDEST_ENDPOINT="$s3_endpoint" RCLONE_CONFIG_AIGWDEST_FORCE_PATH_STYLE=true
  RCLONE_CONFIG_AIGWDEST_ACCESS_KEY_ID=$(<"$AIGW_BACKUP_CREDENTIALS_DIR/access-key")
  RCLONE_CONFIG_AIGWDEST_SECRET_ACCESS_KEY=$(<"$AIGW_BACKUP_CREDENTIALS_DIR/secret-key")
  export RCLONE_CONFIG_AIGWDEST_ACCESS_KEY_ID RCLONE_CONFIG_AIGWDEST_SECRET_ACCESS_KEY
  local base=$s3_bucket${s3_prefix:+/$s3_prefix}
  remote_object=$base/$name
  stage=UPLOAD
  rclone copyto "$ENC/$name" "aigwdest:$remote_object" --retries 1 --low-level-retries 2 --contimeout 10s --timeout 60s >/dev/null 2>&1
  stage=RETENTION
  rclone delete "aigwdest:$base" --include 'aigw-*.bundle' --min-age "${AIGW_BACKUP_RETENTION_DAYS}d" --retries 1 >/dev/null 2>&1
}
upload_sftp() {
  local name=$1
  remote_object=$sftp_path/$name
  stage=UPLOAD
  printf 'put %s %s\n' "$ENC/$name" "$remote_object" | sftp_batch >/dev/null 2>&1
  stage=RETENTION
  local cutoff listing old=()
  cutoff=$(date -u -d "-${AIGW_BACKUP_RETENTION_DAYS} days" +%Y%m%dT%H%M%SZ)
  listing=$(printf 'ls -1 %s\n' "$sftp_path" | sftp_batch 2>/dev/null)
  while read -r entry; do
    entry=${entry##*/}
    [[ $entry =~ ^aigw-([0-9]{8}T[0-9]{6}Z)\.bundle$ && ${BASH_REMATCH[1]} < $cutoff ]] && old+=("rm $sftp_path/$entry")
  done <<< "$listing"
  (( ${#old[@]} == 0 )) || printf '%s\n' "${old[@]}" | sftp_batch >/dev/null 2>&1
}

# 1. 일관 snapshot: 세션 A 는 \copy 까지 같은 거래를 열어 둔다(읽기 전용). 동기화는 /work/plain 의 제어 파일로만 한다.
stage=SNAPSHOT
psql -X -q -w -A -t -v ON_ERROR_STOP=1 >/dev/null 2>&1 <<SQL &
BEGIN ISOLATION LEVEL REPEATABLE READ READ ONLY;
\o $PLAIN/session.out
SELECT pg_export_snapshot();
SELECT to_char(now() AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"');
SELECT version FROM gw_flyway.flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1;
SELECT count(*) FROM gw.job;
\o
\! : > $PLAIN/session.ready; while [ ! -e $PLAIN/dump.done ]; do sleep 0.2; done
\copy (SELECT $column_list, NULL::text AS delegation_id FROM gw.job ORDER BY seq) TO PROGRAM 'age -r $AIGW_BACKUP_AGE_RECIPIENT > $ENC/job.copy.age' WITH (FORMAT binary)
COMMIT;
SQL
session_pid=$!
for _ in $(seq 1 600); do [[ -e $PLAIN/session.ready ]] && break; kill -0 "$session_pid" 2>/dev/null || break; sleep 0.2; done
[[ -e $PLAIN/session.ready ]]
mapfile -t session < "$PLAIN/session.out"
snapshot=${session[0]:-} snapshot_at=${session[1]:-} flyway_latest=${session[2]:-} job_rows=${session[3]:-}
[[ $snapshot =~ ^[0-9A-F-]+$ && $snapshot_at =~ ^[0-9T:.Z-]+$ && $flyway_latest =~ ^[0-9._]+$ && $job_rows =~ ^[0-9]+$ ]]

# 2. 본 덤프: gw.job 은 스키마·제약·sequence 를 유지하고 데이터만 뺀다.
stage=DUMP
pg_dump -w --snapshot="$snapshot" -Fc --exclude-table-data=gw.job -d "$PGDATABASE" | age -r "$AIGW_BACKUP_AGE_RECIPIENT" > "$ENC/main.dump.age"

# 3. 같은 snapshot 의 sanitised Job 데이터(세션 A 의 \copy). 인가 필드 delegation_id 는 항상 NULL 리터럴.
stage=JOB_COPY
: > "$PLAIN/dump.done"
wait "$session_pid"
session_pid=
[[ -s $ENC/job.copy.age ]]
rm -f "$PLAIN/session.out" "$PLAIN/session.ready" "$PLAIN/dump.done"

# 4. manifest(업무 값 0) 암호화 → 하나의 bundle.
stage=MANIFEST
main_sha=$(sha256sum "$ENC/main.dump.age" | cut -d ' ' -f 1)
job_sha=$(sha256sum "$ENC/job.copy.age" | cut -d ' ' -f 1)
columns_sha=$(sha256sum "$COLUMNS_FILE" | cut -d ' ' -f 1)
printf '{"formatVersion":"aigw-backup-v1","snapshotAt":"%s","flywayLatest":"%s","jobColumnsSha256":"%s","jobRowCount":%s,"delegationExcluded":true,"files":{"main.dump.age":"%s","job.copy.age":"%s"}}\n' \
  "$snapshot_at" "$flyway_latest" "$columns_sha" "$job_rows" "$main_sha" "$job_sha" > "$PLAIN/manifest.json"
age -r "$AIGW_BACKUP_AGE_RECIPIENT" -o "$ENC/manifest.json.age" "$PLAIN/manifest.json"
rm -f "$PLAIN/manifest.json"
stage=BUNDLE
bundle=aigw-$(date -u +%Y%m%dT%H%M%SZ).bundle
tar -C "$ENC" -cf "$ENC/$bundle" manifest.json.age main.dump.age job.copy.age
rm -f "$ENC/manifest.json.age" "$ENC/main.dump.age" "$ENC/job.copy.age"

# 5. 목적지 업로드·보관 기간 정리(승인 유형의 도구 하나).
"upload_$AIGW_BACKUP_DEST_TYPE" "$bundle"

# 6. 정리·잔류 확인 뒤에만 성공을 기록한다.
stage=RESIDUE
cleanup
residue_free
stage=RECORD
record SUCCESS '' "$snapshot_at"
recorded=1
printf 'BACKUP_SUCCESS bundle=%s\n' "$bundle"
