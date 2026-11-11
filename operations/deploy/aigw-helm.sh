#!/usr/bin/env bash
set -euo pipefail
set +x
export PYTHONDONTWRITEBYTECODE=1
release= mode= runconfig= exec_sha= exec_dir= profile= expect= acceptance_role= image_digests= suite= spec_file= baseline=
fixture_catalog= clock_initial= backup_point= prep_manifest= parent_run_id= outage_phase= set_extra= rubric_targets=
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
    --image-digests) image_digests=$2;; --suite) suite=$2;; --spec-file) spec_file=$2;; --baseline) baseline=$2;;
    --fixture-catalog) fixture_catalog=$2;; --clock-initial) clock_initial=$2;; --backup-point) backup_point=$2;;
    --prep-manifest) prep_manifest=$2;; --parent-run-id) parent_run_id=$2;; --outage-phase) outage_phase=$2;; --set-extra) set_extra=$2;; --rubric-targets) rubric_targets=$2;;
    *) error ARGUMENT_INVALID;;
  esac
  shift 2
done
[[ -f ops/postgres/bootstrap.sql && -f ops/runconfig/verify.schema.json ]] || error AI_ROOT_REQUIRED
app=0
case $release in
  ai-gateway-db|ai-gateway-db-restore) ;;
  ai-gateway|ai-gateway-restore) app=1;;
  *) error RENDER_INVALID_RELEASE;;
esac
[[ $mode == lint || $mode == render || $mode == deploy || ($app == 1 && $mode == suite-only) ]] || error MODE_INVALID
[[ -n $runconfig ]] || error ARGUMENT_MISSING
# 앱 release 는 image-digests.json 이 필수다. DB release 는 백업 CronJob digest(backup)가 필요할 때만 쓴다.
[[ $app == 0 || -n $image_digests ]] || error ARGUMENT_MISSING
[[ -z $expect || $mode == render ]] || error EXPECT_FAIL_RENDER_ONLY
[[ -z $acceptance_role || $acceptance_role == enabled || $acceptance_role == disabled ]] || error ACCEPTANCE_ROLE_INVALID
desired=verify
[[ $release != *-restore ]] || desired=restore
[[ -z $profile || $profile == "$desired" ]] || error RUNCONFIG_PROFILE_MISMATCH
profile=$desired
if [[ $app == 1 ]]; then
  [[ -n $suite ]] || error ARGUMENT_MISSING
  [[ -z $acceptance_role ]] || error ARGUMENT_INVALID
  if [[ $mode == suite-only ]]; then
    case $suite in F1-RUBRIC|F2-OUTAGE|F2-RUBRIC|F4-RUBRIC|F4-OUTAGE|F4-CLOCK|F4-RESTORE-PREP|F4-RESTORE-MUTATE) ;; *) error SUITE_UNSUPPORTED;; esac
  elif [[ $release == ai-gateway-restore ]]; then
    [[ $suite == none || $suite == F4-RESTORE ]] || error SUITE_UNSUPPORTED
  else
    case $suite in none|F1|F2|F3-PROBE|F3|F3-ACL|F3-RULEVER|F4|F4-PERF) ;; *) error SUITE_UNSUPPORTED;; esac
  fi
  # --set-extra 는 §11.9 가 그 suite 에 허용한 verify 전용 키만(F3-RULEVER = sanitize.verifyRuleSet=mask-v1r). 그 suite 는 이 값이 필수다.
  if [[ -n $set_extra || $suite == F3-RULEVER ]]; then
    [[ $suite == F3-RULEVER && $set_extra == sanitize.verifyRuleSet=mask-v1r ]] || error SET_EXTRA_NOT_ALLOWED
  fi
  if [[ -n $fixture_catalog ]]; then
    # 허용 디렉터리(P23 corpus 목록) 밖 경로·심볼릭 링크는 거부한다.
    resolved=$(realpath -e -- "$fixture_catalog" 2>/dev/null) || error FIXTURE_CATALOG_INVALID
    allowed=$(realpath -m -- modules/apps/ai-gateway-app/src/main/resources/config/fixtures/corpus)
    [[ $resolved == "$allowed"/* && ! -L $fixture_catalog && -f $resolved ]] || error FIXTURE_CATALOG_INVALID
  fi
  if [[ -z $baseline ]]; then baseline=docs/gates/spec-baseline.md; fi
  [[ $mode != deploy || $baseline == docs/gates/spec-baseline.md ]] || error BASELINE_DEPLOY_FIXED
  [[ -n $spec_file ]] || spec_file=${AIGW_SPEC_FILE:-}
fi
if [[ -n $exec_dir ]]; then
  [[ $mode != deploy && $mode != suite-only && -z $exec_sha ]] || error EXEC_DIR_LOCAL_ONLY
else
  [[ $exec_sha =~ ^[a-f0-9]{40}$ ]] || error EXEC_SHA_REQUIRED
fi
for tool in python3 helm git sha256sum mktemp realpath; do command -v "$tool" >/dev/null || error "TOOL_MISSING:$tool"; done
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
[[ -z $image_digests ]] || python3 -B ops/deploy/app_render.py digests "$image_digests" "$tmp/digests.json" || exit 2
digest() { python3 -B -c 'import json,sys; print(json.load(open(sys.argv[1]))[sys.argv[2]])' "$tmp/digests.json" "$1"; }
if ! python3 -B ops/deploy/runconfig.py "$runconfig" "$release" "$([[ $mode == suite-only ]] && echo deploy || echo "$mode")" "$tmp/values.json" "$exec_sha" 2>"$tmp/runconfig.err"; then
  reason=$(head -c 200 "$tmp/runconfig.err")
  # 앱 render 의 --expect-fail 은 입력 검증 사유(예: 복원 runconfig 의 RUNCONFIG_KEY_CONFLICT:<leaf>)도 정확히 같을 때만 성공으로 본다.
  if [[ $app == 1 && -n $expect ]]; then
    [[ $reason == "$expect" ]] || error EXPECTED_FAILURE_MISMATCH 1
    printf 'EXPECTED_FAILURE:%s\n' "$expect"; exit 0
  fi
  printf '%s\n' "$reason" >&2; exit 2
fi
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

# ── 앱 release(ai-gateway·ai-gateway-restore) ──────────────────────────────────────────────
if [[ $app == 1 ]]; then
  chart=$exec_dir/deploy/helm/ai-gateway
  [[ -f $chart/Chart.yaml ]] || error EXEC_CHART_MISSING
  helm package "$chart" -d "$tmp" >"$tmp/package.log" 2>&1 || error HELM_PACKAGE_FAILED
  package=$tmp/ai-gateway-0.1.0.tgz
  chart_digest=$(sha256sum "$package"); chart_digest=${chart_digest%% *}
  # §11.9 공통·행별 Secret 이름은 고정 정본이다(runconfig 로 바꾸지 않는다).
  common=(-n tc -f "$chart/values.yaml" -f "$chart/values-$profile.yaml" -f "$tmp/values.json"
    --set-string "image.digest=$(digest app)" --set-string "acceptance.image.digest=$(digest acceptance)"
    --set-string credentialsRef.serviceTokens=ai-gateway-service-tokens --set-string credentialsRef.actorJwks=ai-gateway-actor-jwks
    --set-string credentialsRef.dbApp=ai-gateway-db-app --set-string credentialsRef.dbMigrator=ai-gateway-db-migrator
    --set-string credentialsRef.gitlabRead=ai-gateway-gitlab-read --set-string credentialsRef.testHooks=ai-gateway-test-hooks
    --set-string credentialsRef.seaweedRead=ai-gateway-seaweedfs-read
    --set-string credentialsRef.acceptanceSigner=ai-gateway-acceptance-signer --set-string credentialsRef.acceptanceClient=ai-gateway-acceptance-client
    --set-string credentialsRef.acceptanceDbRead=ai-gateway-db-acceptance-ro --set-string credentialsRef.acceptanceFixtureWriter=ai-gateway-acceptance-fixture-writer)
  # suite → 추론·Source 읽기·ingest·fixture catalog 플래그(§11.9). 복원 release 는 values-restore 가 정한다.
  flags() {
    case $1 in
      none|F1) printf '%s\n' false false false false;;
      F2|F3-PROBE) printf '%s\n' true false false false;;
      F3|F3-ACL|F3-RULEVER|F4|F4-PERF) printf '%s\n' true true true true;;
    esac
  }
  suite_args() {  # $1 = suite, $2 = 출력 배열 이름, $3 = spec 값 파일, $4 = catalog 파일
    local -n out=$2
    out=()
    if [[ $release == ai-gateway ]]; then
      mapfile -t f < <(flags "$1")
      out+=(--set "inference.enabled=${f[0]}" --set "knowledgeSource.read.enabled=${f[1]}" --set "ingest.enabled=${f[2]}" --set "ingest.fixtureCatalog.enabled=${f[3]}")
      [[ ${f[3]} == false ]] || out+=(--set-file "fixtureCatalog.content=$4")
    else
      out+=(--set-file "fixtureCatalog.content=$4" --set-string "recovery.backupPointAt=$backup_point" --set-string "testHooks.clock.initial=$clock_initial")
    fi
    if [[ $1 != none ]]; then
      [[ $1 != F3-RULEVER ]] || out+=(--set-string sanitize.verifyRuleSet=mask-v1r)
      out+=(--set acceptance.enabled=true --set-string "acceptance.suite=$1" --set-string "acceptance.parentRunId=$parent_run_id"
        --set-file "acceptance.buildIdentityJson=$tmp/build-identity.json" -f "$3")
      [[ $1 != F4-RESTORE ]] || out+=(--set-file "acceptance.prepManifest=$prep_manifest")
    fi
  }
  # 쉼표가 든 JSON 은 --set 으로 넘기면 값이 쪼개지므로 파일로 넘긴다.
  printf '%s' "$(digest buildIdentityJson)" > "$tmp/build-identity.json"
  catalog=${fixture_catalog:-modules/apps/ai-gateway-app/src/main/resources/config/fixtures/corpus/tc-fixture-corpus-v1.yaml}
  if [[ $mode == lint ]]; then
    # lint 는 비밀 아닌 lint 전용 입력만 쓴다(승인값 아님).
    catalog=ops/runconfig/fixtures/lint-fixture-catalog.yaml
    backup_point=${backup_point:-2026-01-01T00:00:00Z} clock_initial=${clock_initial:-2026-01-01T00:00:00Z}
    parent_run_id=${parent_run_id:-LINT-ONLY-PARENT} prep_manifest=${prep_manifest:-ops/runconfig/fixtures/lint-image-digests.json}
  fi
  if [[ $release == ai-gateway-restore && $mode != lint ]]; then
    [[ -n $backup_point && -n $clock_initial ]] || error RESTORE_INPUT_MISSING
  fi
  if [[ $suite != none && $mode != lint ]]; then
    [[ -n $parent_run_id ]] || error ACCEPTANCE_PARENT_REQUIRED
    [[ $suite != F4-RESTORE || -n $prep_manifest ]] || error RESTORE_PREP_MANIFEST_REQUIRED
  fi
  spec_values=$tmp/spec-none.json; printf '{}' > "$spec_values"
  if [[ $suite != none ]]; then
    # M-10: 실제 SPEC 바이트만 결속한다. 부재 exit 2·기준선 불일치 exit 3, 둘 다 렌더 전.
    [[ -n $spec_file && -f $spec_file ]] || error SPEC_INPUT_MISSING
    spec_sha=$(python3 -B ops/deploy/app_render.py spec "$spec_file" "$baseline" "$tmp/spec.json") || exit $?
    spec_values=$tmp/spec.json
  fi
  if [[ $mode == suite-only ]]; then
    # 재배포 0 suite: 기존 release 값을 그대로 두고 수용 Job 값만 바꾼다(앱 Deployment 템플릿 입력 불변).
    # 앱 Pod UID·pod-template-hash·restartCount 전후 비교와 RUN ABORTED(exit 11)는 stage gate 책임이다.
    [[ $suite != *-OUTAGE || $outage_phase == STOPPED || $outage_phase == LOADING ]] || error OUTAGE_PHASE_REQUIRED
    [[ $suite == *-OUTAGE || -z $outage_phase ]] || error OUTAGE_PHASE_FORBIDDEN
    # rubric suite 는 같은 부모 회차 앞 suite 의 RUN COMPLETED 레코드 파일을 수용 Job 에 읽기 전용으로 넘긴다(N16).
    rubric_args=()
    if [[ $suite == *-RUBRIC ]]; then
      [[ -n $rubric_targets && -f $rubric_targets ]] || error RUBRIC_TARGETS_MISSING
      rubric_args=(--set-file "acceptance.rubricTargets=$rubric_targets")
    fi
    deployment_started=1
    helm upgrade "$release" "$package" -n tc --reuse-values --set acceptance.enabled=true --set-string "acceptance.suite=$suite"       --set-string "acceptance.parentRunId=$parent_run_id" --set-string "acceptance.outagePhase=$outage_phase"       --set-file "acceptance.buildIdentityJson=$tmp/build-identity.json" "${rubric_args[@]}" -f "$spec_values" --wait --timeout 600s >"$tmp/upgrade.log" 2>&1 || error HELM_UPGRADE_FAILED 1
    deployment_started=0
    printf 'SUITE_ONLY_OK release=%s suite=%s chartDigest=%s
' "$release" "$suite" "$chart_digest" >&2
    exit 0
  fi
  suite_args "$suite" extra "$spec_values" "$catalog"
  if [[ -n $expect ]]; then
    case $expect in INGEST_REQUIRES_SOURCE_READ|RESTORE_FLAGS_CONFLICT|RESTORE_BACKUP_POINT_REQUIRED|SPEC_VERIFY_SHA_MISMATCH) ;; *) error EXPECT_FAIL_CODE_INVALID;; esac
    if helm template "$release" "$package" "${common[@]}" "${extra[@]}" >"$tmp/render.yaml" 2>"$tmp/error"; then error EXPECTED_FAILURE_ABSENT 1; fi
    grep -Fq -- "$expect" "$tmp/error" || error EXPECTED_FAILURE_MISMATCH 1
    printf 'EXPECTED_FAILURE:%s\n' "$expect"; exit 0
  fi
  helm lint "$package" "${common[@]}" "${extra[@]}" >"$tmp/lint.log" 2>&1 || error HELM_LINT_FAILED
  helm template "$release" "$package" "${common[@]}" "${extra[@]}" >"$tmp/render.yaml" 2>"$tmp/error" || error HELM_RENDER_FAILED
  python3 -B ops/deploy/app_render.py matrix "$tmp/render.yaml" "$release" "$suite" "${spec_sha:-}" "$tmp/values.json" || exit 1
  if [[ $mode == lint ]]; then
    # guard 음성: 각각 기대 사유로만 실패해야 한다(하나라도 다르면 exit 1).
    negative() {  # $1 = 기대 사유, 나머지 = 추가 helm 인자
      local want=$1; shift
      if helm template "$release" "$package" "${common[@]}" "${extra[@]}" "$@" >"$tmp/negative" 2>"$tmp/error"; then error "RENDER_GUARD_MISSING:$want" 1; fi
      grep -Fq -- "$want" "$tmp/error" || error "RENDER_GUARD_MISMATCH:$want" 1
    }
    # 용량 확장 값은 차트 schema(const 1)가 먼저 거부한다.
    negative "at '/replicaCount': value must be 1" --set replicaCount=2
    negative "at '/capacity/maxRunningJobs': value must be 1" --set capacity.maxRunningJobs=2
    negative "at '/capacity/generationConcurrency': value must be 1" --set capacity.generationConcurrency=2
    negative APP_IMAGE_DIGEST_REQUIRED --set-string image.digest=
    negative INGEST_REQUIRES_SOURCE_READ --set ingest.enabled=true --set knowledgeSource.read.enabled=false
    negative VERIFY_RULE_SET_SUITE_ONLY --set-string sanitize.verifyRuleSet=mask-v1r
    if [[ $release == ai-gateway ]]; then
      negative PRODUCTION_RENDER_BLOCKED -f "$chart/values-prod.yaml"
      negative MAIN_RELEASE_CONFLICT --set restoreMode=true
      negative MAIN_RELEASE_CONFLICT --set-string db.release=ai-gateway-db-restore
      # 추론 endpoint 직접 호출(SPEC §26.1): baseUrl 은 scheme://host:port 만, baseUrl 포트·inference.port·netpol 추론 대상 포트가 같아야 한다.
      negative INFERENCE_BASE_URL_REQUIRED --set inference.enabled=true --set-string inference.baseUrl=
      negative INFERENCE_BASE_URL_INVALID --set inference.enabled=true --set-string inference.baseUrl=http://lint-only-inference:1/v1
      negative INFERENCE_BASE_URL_INVALID --set inference.enabled=true --set-string inference.baseUrl=http://lint-only-inference:1/
      negative INFERENCE_BASE_URL_INVALID --set inference.enabled=true --set-string inference.baseUrl=http://lint-only-inference
      negative INFERENCE_PORT_REQUIRED --set inference.enabled=true --set-string inference.baseUrl=http://lint-only-inference:1 --set inference.port=0
      negative INFERENCE_PORT_CONFLICT --set inference.enabled=true --set-string inference.baseUrl=http://lint-only-inference:1 --set inference.port=2 --set netpol.targets.inference.port=2
      negative INFERENCE_PORT_CONFLICT --set inference.enabled=true --set-string inference.baseUrl=http://lint-only-inference:1 --set inference.port=1 --set netpol.targets.inference.port=2
    else
      negative RESTORE_FLAGS_CONFLICT --set inference.enabled=true
      negative RESTORE_BACKUP_POINT_REQUIRED --set-string recovery.backupPointAt=
      negative RESTORE_RELEASE_CONFLICT --set-string db.release=ai-gateway-db
    fi
    if helm template invalid-release "$package" "${common[@]}" "${extra[@]}" >"$tmp/negative" 2>"$tmp/error"; then error RENDER_GUARD_MISSING 1; fi
    grep -Fq RENDER_INVALID_RELEASE "$tmp/error" || error RENDER_GUARD_MISMATCH 1
    # §11.9 렌더 행렬: lint 전용 SPEC 바이트·기준선으로 이 release 의 모든 배포 suite 를 렌더해 행과 대조한다.
    lint_spec_sha=$(python3 -B ops/deploy/app_render.py spec ops/runconfig/fixtures/lint-spec-bytes.md ops/runconfig/fixtures/lint-spec-baseline.md "$tmp/lint-spec.json") || exit 1
    rows=(F1 F2 F3-PROBE F3 F3-ACL F3-RULEVER F4 F4-PERF); [[ $release == ai-gateway ]] || rows=(F4-RESTORE)
    for row in "${rows[@]}"; do
      suite_args "$row" row_args "$tmp/lint-spec.json" "$catalog"
      helm template "$release" "$package" "${common[@]}" "${row_args[@]}" >"$tmp/row.yaml" 2>"$tmp/error" || error "RENDER_MATRIX_FAILED:$row" 1
      python3 -B ops/deploy/app_render.py matrix "$tmp/row.yaml" "$release" "$row" "$lint_spec_sha" "$tmp/values.json" || error "RENDER_MATRIX_MISMATCH:$row" 1
      if [[ $row == F1 || $row == F4 ]]; then
        # 재배포 0 suite(suite-only)는 수용 Job 값만 바꾼다: 같은 행의 Deployment 렌더가 바이트 그대로여야 한다.
        before=$(python3 -B ops/deploy/app_render.py deployment-sha "$tmp/row.yaml" "$release")
        reuse=(--set-string acceptance.suite=F1-RUBRIC --set-string 'acceptance.rubricTargets=[]'); [[ $row == F1 ]] || reuse=(--set-string acceptance.suite=F4-CLOCK)
        helm template "$release" "$package" "${common[@]}" "${row_args[@]}" "${reuse[@]}" >"$tmp/reuse.yaml" 2>"$tmp/error" || error SUITE_ONLY_RENDER_FAILED 1
        [[ $(python3 -B ops/deploy/app_render.py deployment-sha "$tmp/reuse.yaml" "$release") == "$before" ]] || error SUITE_ONLY_DEPLOYMENT_CHANGED 1
        [[ $row == F1 ]] && negative RUBRIC_TARGETS_REQUIRED "${row_args[@]}" --set-string acceptance.suite=F1-RUBRIC
      fi
    done
    suite_args "${rows[0]}" row_args "$tmp/lint-spec.json" "$catalog"
    negative SPEC_VERIFY_SHA_MISMATCH "${row_args[@]}" --set-string "specVerify.expectedSha256=$(printf '0%.0s' {1..64})"
  fi
  if [[ $mode == render ]]; then cat "$tmp/render.yaml"; fi
  if [[ $mode == deploy ]]; then
    case $suite in none|F1) stage=F1;; F2|F3-PROBE) stage=F2;; F3|F3-ACL|F3-RULEVER) stage=F3;; F4|F4-PERF) stage=F4;; F4-RESTORE) stage=RESTORE;; esac
    AIGW_RUNCONFIG="$runconfig" bash ops/gates/aigw-runtime-credentials.sh check --stage "$stage"
    deployment_started=1
    helm upgrade --install "$release" "$package" "${common[@]}" "${extra[@]}" --wait --timeout 600s >"$tmp/upgrade.log" 2>&1 || error HELM_UPGRADE_FAILED 1
    kubectl -n tc rollout status "deployment/$release" --timeout=300s >"$tmp/rollout.log" 2>&1 || error APP_ROLLOUT_FAILED 1
    helm status "$release" -n tc -o json >"$tmp/status.json" 2>"$tmp/error" || error HELM_STATUS_FAILED 1
    python3 -B - "$release" "$actual_sha" "$chart_digest" "$tmp/status.json" "$tmp/digests.json" "$suite" <<'PY'
import datetime,json,pathlib,sys,uuid
release,sha,digest,status,digests,suite=sys.argv[1:]
data=json.loads(pathlib.Path(status).read_text())
if data.get('info',{}).get('status')!='deployed' or not isinstance(data.get('version'),int) or data['version']<1:
    sys.exit('HELM_NOT_DEPLOYED')
images=json.loads(pathlib.Path(digests).read_text())
out=pathlib.Path('evidence/F1/gates'); out.mkdir(parents=True,exist_ok=True)
stamp=datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')+'-'+uuid.uuid4().hex[:8]
record=dict(release=release,suite=suite,execSha=sha,chartDigest=digest,helmRevision=data['version'],appImageDigest=images['app'],acceptanceImageDigest=images['acceptance'])
(out/f'deploy-{release}-{stamp}.json').write_text(json.dumps(record,indent=2)+'\n')
PY
  fi
  printf 'LOCAL_CHART_OK release=%s suite=%s execSha=%s chartDigest=%s\n' "$release" "$suite" "$actual_sha" "$chart_digest" >&2
  exit 0
fi

# ── DB release(ai-gateway-db·ai-gateway-db-restore) ────────────────────────────────────────
chart=$exec_dir/deploy/helm/ai-gateway/db-chart
[[ -f $chart/Chart.yaml ]] || error EXEC_CHART_MISSING
args=(-n tc -f "$chart/values.yaml" -f "$chart/values-$profile.yaml" -f "$tmp/values.json" --set-file bootstrap.sql=ops/postgres/bootstrap.sql --set-file hba.overlay=ops/postgres/acceptance-pg_hba.conf)
# 백업 CronJob 이미지 = image-digests.json 의 backup(주 DB release 만 렌더).
[[ $release != ai-gateway-db || -z $image_digests ]] || args+=(--set-string "backup.image.digest=$(digest backup)")
if [[ -n $acceptance_role ]]; then
  enabled=true; [[ $acceptance_role != disabled ]] || enabled=false
  args+=(--set "bootstrap.acceptanceRole.enabled=$enabled")
fi
helm package "$chart" -d "$tmp" >"$tmp/package.log" 2>&1 || error HELM_PACKAGE_FAILED
package=$tmp/ai-gateway-db-0.1.0.tgz
chart_digest=$(sha256sum "$package"); chart_digest=${chart_digest%% *}
if [[ -n $expect ]]; then
  case $expect in RENDER_REQUIRED_persistence.size|RENDER_REQUIRED_backup.destination|RENDER_REQUIRED_backup.schedule|RENDER_REQUIRED_backup.ageRecipient|RENDER_REQUIRED_backup.type|RENDER_REQUIRED_backup.retentionDays) ;; *) error EXPECT_FAIL_CODE_INVALID;; esac
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
# 백업 CronJob: 주 DB 에 백업이 켜져 있으면 정확히 1개, 복원 release 에는 0개.
python3 -B - "$tmp/render.yaml" "$release" "$tmp/values.json" <<'PY' || error BACKUP_RENDER_MISMATCH 1
import json,sys,yaml
render,release,values=sys.argv[1:]
jobs=[d for d in yaml.safe_load_all(open(render,encoding='utf-8')) if isinstance(d,dict) and d.get('kind')=='CronJob']
enabled=release=='ai-gateway-db' and json.load(open(values,encoding='utf-8')).get('backup',{}).get('enabled') is True
sys.exit(0 if len(jobs)==(1 if enabled else 0) else 1)
PY
if [[ $mode == lint && $release == ai-gateway-db && -n $image_digests ]]; then
  # 백업 CronJob 양성·음성: runconfig 의 backup 값으로 켰을 때 정확히 1개·image-digests 의 backup digest, 목적지 누락이면 그 사유로 실패.
  helm template "$release" "$package" "${args[@]}" --set backup.enabled=true >"$tmp/backup.yaml" 2>"$tmp/error" || error BACKUP_RENDER_FAILED 1
  python3 -B -c '
import sys,yaml
render,digest=sys.argv[1:]
jobs=[d for d in yaml.safe_load_all(open(render,encoding="utf-8")) if isinstance(d,dict) and d.get("kind")=="CronJob"]
container=jobs[0]["spec"]["jobTemplate"]["spec"]["template"]["spec"]["containers"][0] if len(jobs)==1 else {}
sys.exit(0 if jobs and jobs[0]["spec"]["concurrencyPolicy"]=="Forbid" and container.get("image")=="harbor.nori.local/nori-tc/ai-gateway-backup@sha256:"+digest else 1)
' "$tmp/backup.yaml" "$(digest backup)" || error BACKUP_RENDER_MISMATCH 1
  if helm template "$release" "$package" "${args[@]}" --set backup.enabled=true --set-string backup.destination= >"$tmp/negative" 2>"$tmp/error"; then error RENDER_GUARD_MISSING 1; fi
  grep -Fq RENDER_REQUIRED_backup.destination "$tmp/error" || error RENDER_GUARD_MISMATCH 1
fi
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
