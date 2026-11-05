#!/usr/bin/env bash
set -euo pipefail
set +x
export PYTHONDONTWRITEBYTECODE=1
release= mode= runconfig= exec_sha= exec_dir= profile= expect= acceptance_role=
error() { printf '%s\n' "$1" >&2; exit "${2:-2}"; }
while (($#)); do
  (($# >= 2)) || error ARGUMENT_MISSING
  case "$1" in
    --release) release=$2;; --mode) mode=$2;; --runconfig) runconfig=$2;;
    --exec-sha) exec_sha=$2;; --exec-dir) exec_dir=$2;;
    --values-profile) profile=$2;; --expect-fail) expect=$2;;
    --acceptance-role)
      [[ $2 == enabled || $2 == disabled ]] || error ACCEPTANCE_ROLE_INVALID
      acceptance_role=$2;;
    *) error ARGUMENT_INVALID;;
  esac
  shift 2
done
[[ -f ops/postgres/bootstrap.sql && -f ops/runconfig/verify.schema.json ]] || error AI_ROOT_REQUIRED
[[ $release == ai-gateway-db || $release == ai-gateway-db-restore ]] || error RENDER_INVALID_RELEASE
[[ $mode == lint || $mode == render || $mode == deploy ]] || error MODE_INVALID
[[ -n $runconfig ]] || error ARGUMENT_MISSING
[[ -z $expect || $mode == render ]] || error EXPECT_FAIL_RENDER_ONLY
[[ -z $acceptance_role || $acceptance_role == enabled || $acceptance_role == disabled ]] || error ACCEPTANCE_ROLE_INVALID
desired=verify
[[ $release != *-restore ]] || desired=restore
[[ -z $profile || $profile == "$desired" ]] || error RUNCONFIG_PROFILE_MISMATCH
profile=$desired
if [[ -n $exec_dir ]]; then
  [[ $mode != deploy && -z $exec_sha ]] || error EXEC_DIR_LOCAL_ONLY
else
  [[ $exec_sha =~ ^[a-f0-9]{40}$ ]] || error EXEC_SHA_REQUIRED
fi
for tool in python3 helm git sha256sum mktemp; do command -v "$tool" >/dev/null || error "TOOL_MISSING:$tool"; done
python3 -B ops/deploy/hba_policy.py source || error HBA_POLICY_INVALID
tmp=$(mktemp -d)
deployment_started=0
cleanup() {
  code=$?
  if [[ $deployment_started == 1 && $code != 0 ]]; then
    python3 -B - "$release" "$actual_sha" "$chart_digest" <<'PY'
import datetime,json,pathlib,sys,uuid
release,sha,digest=sys.argv[1:]
out=pathlib.Path('evidence/F1/gates'); out.mkdir(parents=True,exist_ok=True)
stamp=datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')+'-'+uuid.uuid4().hex[:8]
(out/f'deploy-{release}-{stamp}.json').write_text(json.dumps(dict(release=release,execSha=sha,chartDigest=digest,status='FAIL',reason='DEPLOYMENT_FAILED',operationalGate='BLOCKED'))+'\n')
PY
  fi
  chmod -R u+w "$tmp" 2>/dev/null || true
  rm -rf -- "$tmp"
}
trap cleanup EXIT
python3 -B ops/deploy/runconfig.py "$runconfig" "$release" "$mode" "$tmp/values.json" "$exec_sha"
if [[ -z $exec_dir ]]; then
  if [[ ${CI:-} == true ]]; then
    [[ -n ${CI_JOB_TOKEN:-} ]] || error EXEC_READ_CREDENTIAL_MISSING
    export AIGW_ASK_USER=gitlab-ci-token AIGW_ASK_TOKEN="$CI_JOB_TOKEN"
  else
    [[ -n ${AIGW_EXEC_READ_USER:-} && -n ${AIGW_EXEC_READ_TOKEN:-} ]] || error EXEC_READ_CREDENTIAL_MISSING
    export AIGW_ASK_USER="$AIGW_EXEC_READ_USER" AIGW_ASK_TOKEN="$AIGW_EXEC_READ_TOKEN"
  fi
  printf '%s\n' '#!/usr/bin/env bash' 'case "$1" in *[Uu]sername*) printf "%s\n" "$AIGW_ASK_USER";; *) printf "%s\n" "$AIGW_ASK_TOKEN";; esac' > "$tmp/askpass"
  chmod 700 "$tmp/askpass"
  export GIT_ASKPASS="$tmp/askpass" GIT_TERMINAL_PROMPT=0 GIT_CONFIG_NOSYSTEM=1 GIT_CONFIG_GLOBAL=/dev/null
  unset GIT_TRACE GIT_TRACE_CURL GIT_TRACE_PACKET GIT_TRACE_SETUP GIT_TRACE_PERFORMANCE GIT_CURL_VERBOSE GIT_TRACE2 GIT_TRACE2_EVENT GIT_TRACE2_PERF GIT_CONFIG_PARAMETERS
  export GIT_CONFIG_COUNT=1 GIT_CONFIG_KEY_0=credential.helper GIT_CONFIG_VALUE_0=''
  # 원시 오류를 파일에도 쓰지 않고 메모리에서 고정 오류 코드로 바꿉니다.
  python3 -B ops/deploy/read-exec.py "$tmp/exec" "$exec_sha" || exit 2
  actual_sha=$exec_sha
  rm -f "$tmp/askpass"
  unset AIGW_ASK_USER AIGW_ASK_TOKEN GIT_ASKPASS
  chmod -R a-w "$tmp/exec"
  exec_dir=$tmp/exec
else
  actual_sha=$(git -C "$exec_dir" rev-parse HEAD) || error EXEC_CHECKOUT_FAILED
fi
chart=$exec_dir/deploy/helm/ai-gateway/db-chart
[[ -f $chart/Chart.yaml ]] || error EXEC_CHART_MISSING
args=(-n tc -f "$chart/values.yaml" -f "$chart/values-$profile.yaml" -f "$tmp/values.json" --set-file bootstrap.sql=ops/postgres/bootstrap.sql --set-file hba.overlay=ops/postgres/acceptance-pg_hba.conf)
if [[ -n $acceptance_role ]]; then
  enabled=true; [[ $acceptance_role != disabled ]] || enabled=false
  args+=(--set "bootstrap.acceptanceRole.enabled=$enabled")
fi
helm package "$chart" -d "$tmp" >"$tmp/package.log" 2>&1 || error HELM_PACKAGE_FAILED
package=$tmp/ai-gateway-db-0.1.0.tgz
chart_digest=$(sha256sum "$package"); chart_digest=${chart_digest%% *}
if [[ -n $expect ]]; then
  case $expect in RENDER_REQUIRED_persistence.size) ;; *) error EXPECT_FAIL_CODE_INVALID;; esac
  if helm template "$release" "$package" "${args[@]}" >"$tmp/render.yaml" 2>"$tmp/error"; then
    error EXPECTED_FAILURE_ABSENT 1
  elif grep -Fq "$expect" "$tmp/error"; then
    printf 'EXPECTED_FAILURE:%s\n' "$expect"; exit 0
  else
    error EXPECTED_FAILURE_MISMATCH 1
  fi
fi
helm lint "$package" "${args[@]}" >"$tmp/lint.log" 2>&1 || error HELM_LINT_FAILED
helm template "$release" "$package" "${args[@]}" >"$tmp/render.yaml" 2>"$tmp/error" || error HELM_RENDER_FAILED
python3 -B ops/deploy/hba_policy.py render "$tmp/render.yaml" "$release" || error HBA_POLICY_INVALID
# 성공 경로에서도 두 필수 guard가 실제로 작동하는지 확인합니다.
if helm template "$release" "$package" "${args[@]}" --set-string persistence.size= >"$tmp/negative" 2>"$tmp/error"; then error RENDER_GUARD_MISSING 1; fi
grep -Fq RENDER_REQUIRED_persistence.size "$tmp/error" || error RENDER_GUARD_MISMATCH 1
if helm template invalid-release "$package" "${args[@]}" >"$tmp/negative" 2>"$tmp/error"; then error RENDER_GUARD_MISSING 1; fi
grep -Fq RENDER_INVALID_RELEASE "$tmp/error" || error RENDER_GUARD_MISMATCH 1
if [[ $mode == render ]]; then cat "$tmp/render.yaml"; fi
if [[ $mode == deploy ]]; then
  # 클러스터 변경은 승인 운영자가 이 모드를 실행할 때만 발생합니다.
  stage=F1; [[ $release != *-restore ]] || stage=RESTORE
  AIGW_RUNCONFIG="$runconfig" bash ops/gates/aigw-runtime-credentials.sh check --stage "$stage"
  if [[ $acceptance_role != disabled ]]; then
    kubectl -n tc get secret ai-gateway-db-acceptance-ro -o 'go-template={{range $k,$v := .data}}{{printf "%s\n" $k}}{{end}}' 2>/dev/null | grep -qx password || error RUNTIME_CREDENTIALS_MISSING 13
  fi
  deployment_started=1
  helm upgrade --install "$release" "$package" "${args[@]}" --wait --timeout 600s >"$tmp/upgrade.log" 2>&1 || error HELM_UPGRADE_FAILED 1
  kubectl -n tc rollout status "statefulset/$release" --timeout=300s >"$tmp/rollout.log" 2>&1 || error HBA_ROLLOUT_FAILED 1
  kubectl -n tc wait --for=condition=complete "job/$release-bootstrap" --timeout=300s >"$tmp/bootstrap.log" 2>&1 || error BOOTSTRAP_FAILED 1
  python3 -B ops/deploy/hba_policy.py live "$release" "$tmp/hba-check.json" || error HBA_DEPLOY_CHECK_FAILED 1
  helm status "$release" -n tc -o json >"$tmp/status.json" 2>"$tmp/error" || error HELM_STATUS_FAILED 1
  python3 -B - "$release" "$actual_sha" "$chart_digest" "$tmp/status.json" "$tmp/values.json" "$tmp/hba-check.json" <<'PY'
import datetime,hashlib,json,pathlib,sys,uuid
release,sha,digest,status,values,hba=sys.argv[1:]
data=json.loads(pathlib.Path(status).read_text())
if data.get('info',{}).get('status')!='deployed' or not isinstance(data.get('version'),int) or data['version']<1:
    sys.exit('HELM_NOT_DEPLOYED')
out=pathlib.Path('evidence/F1/gates'); out.mkdir(parents=True,exist_ok=True)
stamp=datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')+'-'+uuid.uuid4().hex[:8]
record=dict(release=release,execSha=sha,chartDigest=digest,bootstrapSqlSha256=hashlib.sha256(pathlib.Path('ops/postgres/bootstrap.sql').read_bytes()).hexdigest(),helmRevision=data['version'],imageDigest=json.loads(pathlib.Path(values).read_text())['pgImage']['digest'])
record.update(json.loads(pathlib.Path(hba).read_text()))
(out/f'deploy-{release}-{stamp}.json').write_text(json.dumps(record,indent=2)+'\n')
PY
fi
printf 'LOCAL_CHART_OK release=%s execSha=%s chartDigest=%s\n' "$release" "$actual_sha" "$chart_digest" >&2
