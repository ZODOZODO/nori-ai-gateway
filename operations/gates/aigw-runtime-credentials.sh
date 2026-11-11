#!/usr/bin/env bash
set -euo pipefail
set +x
export PYTHONDONTWRITEBYTECODE=1
error() { printf '%s\n' "$1" >&2; exit "${2:-2}"; }
command=${1:-}; [[ -n $command ]] || error ARGUMENT_MISSING
shift
fields_template='{{range $k,$v := .data}}{{printf "%s\n" $k}}{{end}}'
case "$command" in
  provision-db)
    (($# == 0)) || error ARGUMENT_INVALID
    for role in superuser migrator app backup; do
      name=ai-gateway-db-$role
      # NotFound만 생성 대상으로 인정하며 권한/통신 실패는 전파합니다.
      exists=$(kubectl -n tc get secret "$name" --ignore-not-found -o name) || error SECRET_QUERY_FAILED 1
      if [[ -n $exists ]]; then printf '%s PRESENT\n' "$name"; continue; fi
      { openssl rand -base64 32 | kubectl -n tc create secret generic "$name" --from-file=password=/dev/stdin >/dev/null; } 2>/dev/null || error SECRET_CREATE_FAILED 1
      printf '%s CREATED\n' "$name"
    done
    ;;
  check)
    [[ $# == 2 && $1 == --stage ]] || error ARGUMENT_MISSING
    stage=$2
    [[ $stage == F1 || $stage == F2 || $stage == F3 || $stage == F4 || $stage == RESTORE ]] || error STAGE_INVALID
    backup_fields=
    if [[ $stage != RESTORE ]]; then
      [[ -n ${AIGW_RUNCONFIG:-} ]] || error RUNCONFIG_MISSING:backup.type
      backup_fields=$(python3 -B - "$AIGW_RUNCONFIG" <<'PY'
import sys
sys.path.insert(0, 'operations/deploy')
from runconfig import InputError, item, load
try:
    data = load(sys.argv[1])
    if data.get('purpose') != 'deploy':
        raise InputError('RUNCONFIG_NOT_APPROVED:purpose')
    if data.get('profile') != 'main':
        raise InputError('RUNCONFIG_PROFILE_MISMATCH')
    kind = item(data, 'backup.type', True)
    if kind not in ('s3', 'sftp'):
        raise InputError('RUNCONFIG_INVALID:backup.type')
    print('access-key,secret-key' if kind == 's3' else 'ssh-private-key,known-hosts')
except (InputError, OSError, ValueError) as error:
    print(str(error) if isinstance(error, InputError) else 'RUNCONFIG_INVALID', file=sys.stderr)
    sys.exit(2)
PY
      ) || exit 2
    fi
    tmp=$(mktemp -d); trap 'rm -rf -- "$tmp"' EXIT
    required=('ai-gateway-db-superuser:password' 'ai-gateway-db-migrator:password' 'ai-gateway-db-app:password' 'ai-gateway-db-backup:password' 'ai-gateway-gitlab-read:token')
    [[ $stage == RESTORE ]] || required+=("ai-gateway-backup-destination:$backup_fields")
    [[ $stage != F3 && $stage != F4 && $stage != RESTORE ]] || required+=('ai-gateway-seaweedfs-read:access-key,secret-key')
    missing=0
    for spec in "${required[@]}"; do
      name=${spec%%:*}; need=${spec#*:}; state=PRESENT
      if ! fields=$(kubectl -n tc get secret "$name" --ignore-not-found -o "go-template=$fields_template" 2>/dev/null); then
        # 조회 실패는 존재로 처리하지 않으며 원시 서버 오류를 기록하지 않습니다.
        state=MISSING
      else
        IFS=',' read -ra keys <<<"$need"
        for key in "${keys[@]}"; do grep -qx "$key" <<<"$fields" || state=MISSING; done
      fi
      [[ $state == PRESENT ]] || missing=13
      printf '%s %s\n' "$name" "$state"
      printf '%s\t%s\t%s\n' "$name" "$state" "$need" >> "$tmp/records"
    done
    python3 -B - "$stage" "$tmp/records" <<'PY'
import datetime,json,pathlib,sys,uuid
stage,source=sys.argv[1:]
stamp=datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')+'-'+uuid.uuid4().hex[:8]
items=[dict(name=n,status=s,fields=f.split(',')) for n,s,f in (line.split('\t') for line in pathlib.Path(source).read_text().splitlines())]
out=pathlib.Path('evidence')/stage/'gates'; out.mkdir(parents=True,exist_ok=True)
(out/f'runtime-credentials-check-{stamp}.json').write_text(json.dumps(dict(stage=stage,items=items),indent=2)+'\n')
PY
    exit "$missing"
    ;;
  rotate-db)
    [[ $# == 2 && $1 == --role ]] || error ARGUMENT_MISSING
    role=$2
    [[ $role == app || $role == migrator || $role == backup ]] || error ROLE_INVALID
    releases=()
    for release in ai-gateway-db ai-gateway-db-restore; do
      found=$(kubectl -n tc get statefulset "$release" --ignore-not-found -o name) || error RELEASE_QUERY_FAILED 1
      [[ -z $found ]] || releases+=("$release")
    done
    ((${#releases[@]})) || error DB_RELEASE_MISSING
    # 실행 중인 SQL 작성자를 취소하지 않도록 모든 hook을 Secret 교체 전에 확인합니다.
    templates=()
    for release in "${releases[@]}"; do
      job=$(kubectl -n tc get job "$release-bootstrap" -o json 2>/dev/null) || error BOOTSTRAP_JOB_MISSING 1
      template=$(printf '%s' "$job" | python3 -B -c '
import json,sys
try:
    d=json.load(sys.stdin); release=sys.argv[1]
    if d["metadata"]["name"] != release+"-bootstrap" or d["metadata"].get("namespace") != "tc" or d["metadata"].get("labels",{}).get("aigw.nori/db-instance") != release:
        sys.exit("BOOTSTRAP_JOB_INVALID")
    status=d.get("status",{})
    terminal=any(c.get("type") in ("Complete","Failed") and c.get("status")=="True" for c in status.get("conditions",[]))
    if status.get("active",0) or not terminal:
        sys.exit("BOOTSTRAP_JOB_BUSY")
    d["metadata"]={k:v for k,v in d["metadata"].items() if k in ("name","namespace","labels","annotations")}
    d.pop("status",None)
    d["spec"].pop("selector",None); d["spec"].pop("manualSelector",None)
    labels=d["spec"]["template"]["metadata"].get("labels",{})
    d["spec"]["template"]["metadata"]["labels"]={k:v for k,v in labels.items() if k not in ("controller-uid","job-name","batch.kubernetes.io/controller-uid","batch.kubernetes.io/job-name")}
    json.dump(d,sys.stdout)
except (ValueError,KeyError,TypeError,AttributeError):
    sys.exit("BOOTSTRAP_JOB_INVALID")
' "$release") || exit 1
      templates+=("$template")
    done
    { openssl rand -base64 32 | kubectl -n tc create secret generic "ai-gateway-db-$role" --from-file=password=/dev/stdin --dry-run=client -o yaml | kubectl -n tc apply -f - >/dev/null; } 2>/dev/null || error ROTATION_SECRET_FAILED 1
    for index in "${!releases[@]}"; do
      release=${releases[$index]}
      kubectl -n tc delete job "$release-bootstrap" --wait=true >/dev/null 2>&1 || error ROTATION_BOOTSTRAP_FAILED_RECOVERY_REQUIRED 1
      printf '%s' "${templates[$index]}" | kubectl -n tc create -f - >/dev/null 2>&1 || error ROTATION_BOOTSTRAP_FAILED_RECOVERY_REQUIRED 1
      kubectl -n tc wait --for=condition=complete "job/$release-bootstrap" --timeout=300s >/dev/null || error ROTATION_BOOTSTRAP_FAILED_RECOVERY_REQUIRED 1
    done
    # 두 DB가 같은 새 자격으로 완료된 뒤에만 앱을 재시작합니다.
    for release in "${releases[@]}"; do
      if [[ $role == app ]]; then
        app=ai-gateway; [[ $release != *-restore ]] || app=ai-gateway-restore
        exists=$(kubectl -n tc get deployment "$app" --ignore-not-found -o name) || error ROTATION_RESTART_FAILED_RECOVERY_REQUIRED 1
        if [[ -n $exists ]]; then
          kubectl -n tc rollout restart "deployment/$app" >/dev/null || error ROTATION_RESTART_FAILED_RECOVERY_REQUIRED 1
          kubectl -n tc rollout status "deployment/$app" --timeout=300s >/dev/null || error ROTATION_RESTART_FAILED_RECOVERY_REQUIRED 1
        fi
      fi
    done
    # migrator/backup은 매 실행마다 Secret을 읽는 Job이므로 앱을 재시작하지 않습니다.
    printf 'ROTATED:%s\n' "$role"
    ;;
  *) error COMMAND_INVALID;;
esac
