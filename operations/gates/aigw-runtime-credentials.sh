#!/usr/bin/env bash
# 런타임 자격 check(Plan U6 ③): Secret ai-gateway-config 의 존재와 키 이름만 확인한다(값 조회·출력 0). DB 역할 Secret provision·회전은 없다 —
# 운영 DB 는 공유 서버의 단일 계정이고 계정 값은 운영자가 ai-gateway-config 에 둔다(docs/runbooks/aigw-runtime-credentials.md).
set -euo pipefail
set +x
export PYTHONDONTWRITEBYTECODE=1
error() { printf '%s\n' "$1" >&2; exit "${2:-2}"; }
command=${1:-}; [[ -n $command ]] || error ARGUMENT_MISSING
shift
fields_template='{{range $k,$v := .data}}{{printf "%s\n" $k}}{{end}}'
case "$command" in
  check)
    [[ $# == 2 && $1 == --stage ]] || error ARGUMENT_MISSING
    stage=$2
    [[ $stage == F1 || $stage == F2 || $stage == F3 || $stage == F4 ]] || error STAGE_INVALID
    tmp=$(mktemp -d); trap 'rm -rf -- "$tmp"' EXIT
    # DB 설정 파일·단일 계정 두 키·GitLab 읽기 token. SeaweedFS 읽기 키는 F3·F4 행만(§11.9). 서비스 토큰·JWKS·hook 해시는 검증 자격 check 소관.
    config='ai-gateway-config:ai-gateway-db.properties,AI_GATEWAY_DB_USER,AI_GATEWAY_DB_PASSWORD,token'
    [[ $stage != F3 && $stage != F4 ]] || config+=',access-key,secret-key'
    required=("$config")
    missing=0
    for spec in "${required[@]}"; do
      name=${spec%%:*}; need=${spec#*:}; state=PRESENT
      if ! fields=$(kubectl -n tc get secret "$name" --ignore-not-found -o "go-template=$fields_template" 2>/dev/null); then
        # 조회 실패는 존재로 처리하지 않으며 원시 서버 오류를 기록하지 않는다.
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
  *) error COMMAND_INVALID;;
esac
