#!/usr/bin/env bash
set -euo pipefail
set +x
export PYTHONDONTWRITEBYTECODE=1
# 차트는 이 checkout 의 operations/deploy/helm/** 에서 읽는다(Plan §4.1 2026-11-10). deploy·suite-only 는 operations/ 미커밋 변경 0 과
# HEAD = 앱 이미지 GIT_SHA(image-digests.json buildIdentity.gitSha)를 부작용 전에 확인하고, 증거에는 chartRepoSha·chartDigest 를 남긴다.
release= mode= runconfig= profile= expect= image_digests= suite= spec_file= baseline=
fixture_catalog= clock_initial= backup_point= prep_manifest= parent_run_id= outage_phase= set_extra= rubric_targets=
error() { printf '%s\n' "$1" >&2; exit "${2:-2}"; }
while (($#)); do
  (($# >= 2)) || error ARGUMENT_MISSING
  case "$1" in
    --release) release=$2;; --mode) mode=$2;; --runconfig) runconfig=$2;;
    --values-profile) profile=$2;; --expect-fail) expect=$2;;
    --image-digests) image_digests=$2;; --suite) suite=$2;; --spec-file) spec_file=$2;; --baseline) baseline=$2;;
    --fixture-catalog) fixture_catalog=$2;; --clock-initial) clock_initial=$2;; --backup-point) backup_point=$2;;
    --prep-manifest) prep_manifest=$2;; --parent-run-id) parent_run_id=$2;; --outage-phase) outage_phase=$2;; --set-extra) set_extra=$2;; --rubric-targets) rubric_targets=$2;;
    *) error ARGUMENT_INVALID;;
  esac
  shift 2
done
[[ -f operations/runconfig/verify.schema.json && -f operations/deploy/helm/ai-gateway/Chart.yaml ]] || error AI_ROOT_REQUIRED
app=0
case $release in
  ai-gateway-db-restore) ;;
  ai-gateway|ai-gateway-restore) app=1;;
  *) error RENDER_INVALID_RELEASE;;
esac
[[ $mode == lint || $mode == render || $mode == deploy || ($app == 1 && $mode == suite-only) ]] || error MODE_INVALID
[[ -n $runconfig ]] || error ARGUMENT_MISSING
# 앱 release 는 image-digests.json 이 필수다(앱·수용·백업 digest·빌드 신원).
[[ $app == 0 || -n $image_digests ]] || error ARGUMENT_MISSING
[[ -z $expect || $mode == render ]] || error EXPECT_FAIL_RENDER_ONLY
desired=verify
[[ $release != *-restore ]] || desired=restore
[[ -z $profile || $profile == "$desired" ]] || error RUNCONFIG_PROFILE_MISMATCH
profile=$desired
if [[ $app == 1 ]]; then
  [[ -n $suite ]] || error ARGUMENT_MISSING
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
for tool in python3 helm git sha256sum mktemp realpath; do command -v "$tool" >/dev/null || error "TOOL_MISSING:$tool"; done
chart_repo_sha=
if [[ $mode == deploy || $mode == suite-only ]]; then
  # 부작용 전 checkout 검사(exit 2): operations/ 미커밋·미추적 변경 0, 앱 release 는 HEAD = 이미지 GIT_SHA.
  chart_repo_sha=$(python3 -B operations/deploy/app_render.py checkout "${image_digests:--}") || exit 2
fi
tmp=$(mktemp -d)
deployment_started=0
cleanup() {
  code=$?
  if [[ $deployment_started == 1 && $code != 0 ]]; then
    python3 -B - "$release" "$chart_repo_sha" "$chart_digest" <<'PY'
import datetime,json,pathlib,sys,uuid
release,sha,digest=sys.argv[1:]
out=pathlib.Path('evidence/F1/gates'); out.mkdir(parents=True,exist_ok=True)
stamp=datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')+'-'+uuid.uuid4().hex[:8]
(out/f'deploy-{release}-{stamp}.json').write_text(json.dumps(dict(release=release,chartRepoSha=sha,chartDigest=digest,status='FAIL',reason='DEPLOYMENT_FAILED',operationalGate='BLOCKED'))+'\n')
PY
  fi
  chmod -R u+w "$tmp" 2>/dev/null || true
  rm -rf -- "$tmp"
}
trap cleanup EXIT
[[ -z $image_digests ]] || python3 -B operations/deploy/app_render.py digests "$image_digests" "$tmp/digests.json" || exit 2
digest() { python3 -B -c 'import json,sys; print(json.load(open(sys.argv[1]))[sys.argv[2]])' "$tmp/digests.json" "$1"; }
if ! python3 -B operations/deploy/runconfig.py "$runconfig" "$release" "$([[ $mode == suite-only ]] && echo deploy || echo "$mode")" "$tmp/values.json" 2>"$tmp/runconfig.err"; then
  reason=$(head -c 200 "$tmp/runconfig.err")
  # render 의 --expect-fail 은 입력 검증 사유(예: 복원 runconfig 의 RUNCONFIG_KEY_CONFLICT:<leaf>)도 정확히 같을 때만 성공으로 본다.
  if [[ -n $expect ]]; then
    [[ $reason == "$expect" ]] || error EXPECTED_FAILURE_MISMATCH 1
    printf 'EXPECTED_FAILURE:%s\n' "$expect"; exit 0
  fi
  printf '%s\n' "$reason" >&2; exit 2
fi
record_deploy() {  # $1 = 추가 필드 JSON. 성공 배포 증거(값 없는 신원·digest·revision 만).
  helm status "$release" -n tc -o json >"$tmp/status.json" 2>"$tmp/error" || error HELM_STATUS_FAILED 1
  python3 -B - "$release" "$chart_repo_sha" "$chart_digest" "$tmp/status.json" "$1" <<'PY'
import datetime,json,pathlib,sys,uuid
release,sha,digest,status,extra=sys.argv[1:]
data=json.loads(pathlib.Path(status).read_text())
if data.get('info',{}).get('status')!='deployed' or not isinstance(data.get('version'),int) or data['version']<1:
    sys.exit('HELM_NOT_DEPLOYED')
out=pathlib.Path('evidence/F1/gates'); out.mkdir(parents=True,exist_ok=True)
stamp=datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')+'-'+uuid.uuid4().hex[:8]
record=dict(release=release,chartRepoSha=sha,chartDigest=digest,helmRevision=data['version'],**json.loads(extra))
(out/f'deploy-{release}-{stamp}.json').write_text(json.dumps(record,indent=2)+'\n')
PY
}

# ── 앱 release(ai-gateway·ai-gateway-restore) ──────────────────────────────────────────────
if [[ $app == 1 ]]; then
  chart=operations/deploy/helm/ai-gateway
  helm package "$chart" -d "$tmp" >"$tmp/package.log" 2>&1 || error HELM_PACKAGE_FAILED
  package=$tmp/ai-gateway-0.1.0.tgz
  chart_digest=$(sha256sum "$package"); chart_digest=${chart_digest%% *}
  # 앱 자격·DB 설정은 Secret ai-gateway-config(차트 values 고정), 수용 하네스 Secret 이름은 §11.9 고정 정본이다(runconfig 로 바꾸지 않는다).
  common=(-n tc -f "$chart/values.yaml" -f "$chart/values-$profile.yaml" -f "$tmp/values.json"
    --set-string "image.digest=$(digest app)" --set-string "acceptance.image.digest=$(digest acceptance)"
    --set-string credentialsRef.acceptanceSigner=ai-gateway-acceptance-signer --set-string credentialsRef.acceptanceClient=ai-gateway-acceptance-client
    --set-string credentialsRef.acceptanceFixtureWriter=ai-gateway-acceptance-fixture-writer)
  # 백업 CronJob 이미지 = image-digests.json 의 backup(주 release 만 렌더).
  [[ $release != ai-gateway ]] || common+=(--set-string "backup.image.digest=$(digest backup)")
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
    catalog=operations/runconfig/fixtures/lint-fixture-catalog.yaml
    backup_point=${backup_point:-2026-01-01T00:00:00Z} clock_initial=${clock_initial:-2026-01-01T00:00:00Z}
    parent_run_id=${parent_run_id:-LINT-ONLY-PARENT} prep_manifest=${prep_manifest:-operations/runconfig/fixtures/lint-image-digests.json}
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
    spec_sha=$(python3 -B operations/deploy/app_render.py spec "$spec_file" "$baseline" "$tmp/spec.json") || exit $?
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
    helm upgrade "$release" "$package" -n tc --reuse-values --set acceptance.enabled=true --set-string "acceptance.suite=$suite" \
      --set-string "acceptance.parentRunId=$parent_run_id" --set-string "acceptance.outagePhase=$outage_phase" \
      --set-file "acceptance.buildIdentityJson=$tmp/build-identity.json" "${rubric_args[@]}" -f "$spec_values" --wait --timeout 600s >"$tmp/upgrade.log" 2>&1 || error HELM_UPGRADE_FAILED 1
    deployment_started=0
    printf 'SUITE_ONLY_OK release=%s suite=%s chartRepoSha=%s chartDigest=%s\n' "$release" "$suite" "$chart_repo_sha" "$chart_digest" >&2
    exit 0
  fi
  suite_args "$suite" extra "$spec_values" "$catalog"
  if [[ -n $expect ]]; then
    case $expect in INGEST_REQUIRES_SOURCE_READ|RESTORE_FLAGS_CONFLICT|RESTORE_BACKUP_POINT_REQUIRED|SPEC_VERIFY_SHA_MISMATCH|RENDER_REQUIRED_backup.type|RENDER_REQUIRED_backup.schedule|RENDER_REQUIRED_backup.destination|RENDER_REQUIRED_backup.ageRecipient|RENDER_REQUIRED_backup.retentionDays) ;; *) error EXPECT_FAIL_CODE_INVALID;; esac
    if helm template "$release" "$package" "${common[@]}" "${extra[@]}" >"$tmp/render.yaml" 2>"$tmp/error"; then error EXPECTED_FAILURE_ABSENT 1; fi
    grep -Fq -- "$expect" "$tmp/error" || error EXPECTED_FAILURE_MISMATCH 1
    printf 'EXPECTED_FAILURE:%s\n' "$expect"; exit 0
  fi
  helm lint "$package" "${common[@]}" "${extra[@]}" >"$tmp/lint.log" 2>&1 || error HELM_LINT_FAILED
  helm template "$release" "$package" "${common[@]}" "${extra[@]}" >"$tmp/render.yaml" 2>"$tmp/error" || error HELM_RENDER_FAILED
  python3 -B operations/deploy/app_render.py matrix "$tmp/render.yaml" "$release" "$suite" "${spec_sha:-}" "$tmp/values.json" || exit 1
  if [[ $mode == lint ]]; then
    # guard 음성: 각각 기대 사유로만 실패해야 한다(하나라도 다르면 exit 1).
    negative() {  # $1 = 기대 사유, 나머지 = 추가 helm 인자
      local want=$1; shift
      if helm template "$release" "$package" "${common[@]}" "${extra[@]}" "$@" >"$tmp/negative" 2>"$tmp/error"; then error "RENDER_GUARD_MISSING:$want" 1; fi
      grep -Fq -- "$want" "$tmp/error" || error "RENDER_GUARD_MISMATCH:$want" 1
    }
    # 용량 확장 값·Secret 생성은 차트 schema(const)가 먼저 거부한다.
    negative "at '/replicaCount': value must be 1" --set replicaCount=2
    negative "at '/capacity/maxRunningJobs': value must be 1" --set capacity.maxRunningJobs=2
    negative "at '/capacity/generationConcurrency': value must be 1" --set capacity.generationConcurrency=2
    negative "at '/configSecret/create'" --set configSecret.create=true
    negative "at '/configSecret/name'" --set-string configSecret.name=ai-gateway-db-app
    negative APP_IMAGE_DIGEST_REQUIRED --set-string image.digest=
    negative INGEST_REQUIRES_SOURCE_READ --set ingest.enabled=true --set knowledgeSource.read.enabled=false
    negative VERIFY_RULE_SET_SUITE_ONLY --set-string sanitize.verifyRuleSet=mask-v1r
    if [[ $release == ai-gateway ]]; then
      negative PRODUCTION_RENDER_BLOCKED -f "$chart/values-prod.yaml"
      negative MAIN_RELEASE_CONFLICT --set restoreMode=true
      # 추론 endpoint 직접 호출(SPEC §26.1): baseUrl 은 scheme://host:port 만, baseUrl 포트·inference.port·netpol 추론 대상 포트가 같아야 한다.
      negative INFERENCE_BASE_URL_REQUIRED --set inference.enabled=true --set-string inference.baseUrl=
      negative INFERENCE_BASE_URL_INVALID --set inference.enabled=true --set-string inference.baseUrl=http://lint-only-inference:1/v1
      negative INFERENCE_BASE_URL_INVALID --set inference.enabled=true --set-string inference.baseUrl=http://lint-only-inference:1/
      negative INFERENCE_BASE_URL_INVALID --set inference.enabled=true --set-string inference.baseUrl=http://lint-only-inference
      negative INFERENCE_PORT_REQUIRED --set inference.enabled=true --set-string inference.baseUrl=http://lint-only-inference:1 --set inference.port=0
      negative INFERENCE_PORT_CONFLICT --set inference.enabled=true --set-string inference.baseUrl=http://lint-only-inference:1 --set inference.port=2 --set netpol.targets.inference.port=2
      negative INFERENCE_PORT_CONFLICT --set inference.enabled=true --set-string inference.baseUrl=http://lint-only-inference:1 --set inference.port=1 --set netpol.targets.inference.port=2
      # 백업 CronJob 양성·음성(공유 서버·단일 계정): runconfig 의 backup 값으로 켰을 때 정확히 1개·backup digest·Forbid, 목적지 누락이면 그 사유로 실패.
      helm template "$release" "$package" "${common[@]}" "${extra[@]}" --set backup.enabled=true >"$tmp/backup.yaml" 2>"$tmp/error" || error BACKUP_RENDER_FAILED 1
      python3 -B operations/deploy/app_render.py backup "$tmp/backup.yaml" "$(digest backup)" || error BACKUP_RENDER_MISMATCH 1
      negative RENDER_REQUIRED_backup.destination --set backup.enabled=true --set-string backup.destination=
    else
      negative RESTORE_FLAGS_CONFLICT --set inference.enabled=true
      negative RESTORE_BACKUP_POINT_REQUIRED --set-string recovery.backupPointAt=
      negative RESTORE_RELEASE_CONFLICT --set restoreMode=false
      negative RENDER_RESTORE_BACKUP_FORBIDDEN --set backup.enabled=true
    fi
    if helm template invalid-release "$package" "${common[@]}" "${extra[@]}" >"$tmp/negative" 2>"$tmp/error"; then error RENDER_GUARD_MISSING 1; fi
    grep -Fq RENDER_INVALID_RELEASE "$tmp/error" || error RENDER_GUARD_MISMATCH 1
    # §11.9 렌더 행렬: lint 전용 SPEC 바이트·기준선으로 이 release 의 모든 배포 suite 를 렌더해 행과 대조한다.
    lint_spec_sha=$(python3 -B operations/deploy/app_render.py spec operations/runconfig/fixtures/lint-spec-bytes.md operations/runconfig/fixtures/lint-spec-baseline.md "$tmp/lint-spec.json") || exit 1
    rows=(F1 F2 F3-PROBE F3 F3-ACL F3-RULEVER F4 F4-PERF); [[ $release == ai-gateway ]] || rows=(F4-RESTORE)
    for row in "${rows[@]}"; do
      suite_args "$row" row_args "$tmp/lint-spec.json" "$catalog"
      helm template "$release" "$package" "${common[@]}" "${row_args[@]}" >"$tmp/row.yaml" 2>"$tmp/error" || error "RENDER_MATRIX_FAILED:$row" 1
      python3 -B operations/deploy/app_render.py matrix "$tmp/row.yaml" "$release" "$row" "$lint_spec_sha" "$tmp/values.json" || error "RENDER_MATRIX_MISMATCH:$row" 1
      if [[ $row == F1 || $row == F4 ]]; then
        # 재배포 0 suite(suite-only)는 수용 Job 값만 바꾼다: 같은 행의 Deployment 렌더가 바이트 그대로여야 한다.
        before=$(python3 -B operations/deploy/app_render.py deployment-sha "$tmp/row.yaml" "$release")
        reuse=(--set-string acceptance.suite=F1-RUBRIC --set-string 'acceptance.rubricTargets=[]'); [[ $row == F1 ]] || reuse=(--set-string acceptance.suite=F4-CLOCK)
        helm template "$release" "$package" "${common[@]}" "${row_args[@]}" "${reuse[@]}" >"$tmp/reuse.yaml" 2>"$tmp/error" || error SUITE_ONLY_RENDER_FAILED 1
        [[ $(python3 -B operations/deploy/app_render.py deployment-sha "$tmp/reuse.yaml" "$release") == "$before" ]] || error SUITE_ONLY_DEPLOYMENT_CHANGED 1
        [[ $row == F1 ]] && negative RUBRIC_TARGETS_REQUIRED "${row_args[@]}" --set-string acceptance.suite=F1-RUBRIC
      fi
    done
    suite_args "${rows[0]}" row_args "$tmp/lint-spec.json" "$catalog"
    negative SPEC_VERIFY_SHA_MISMATCH "${row_args[@]}" --set-string "specVerify.expectedSha256=$(printf '0%.0s' {1..64})"
  fi
  if [[ $mode == render ]]; then cat "$tmp/render.yaml"; fi
  if [[ $mode == deploy ]]; then
    case $suite in none|F1) stage=F1;; F2|F3-PROBE) stage=F2;; F3|F3-ACL|F3-RULEVER) stage=F3;; F4|F4-PERF) stage=F4;; F4-RESTORE) stage=RESTORE;; esac
    AIGW_RUNCONFIG="$runconfig" bash operations/gates/aigw-runtime-credentials.sh check --stage "$stage"
    deployment_started=1
    helm upgrade --install "$release" "$package" "${common[@]}" "${extra[@]}" --wait --timeout 600s >"$tmp/upgrade.log" 2>&1 || error HELM_UPGRADE_FAILED 1
    kubectl -n tc rollout status "deployment/$release" --timeout=300s >"$tmp/rollout.log" 2>&1 || error APP_ROLLOUT_FAILED 1
    record_deploy "$(python3 -B -c 'import json,sys; d=json.load(open(sys.argv[1])); print(json.dumps(dict(suite=sys.argv[2],appImageDigest=d["app"],acceptanceImageDigest=d["acceptance"])))' "$tmp/digests.json" "$suite")" || error HELM_NOT_DEPLOYED 1
    deployment_started=0
  fi
  printf 'LOCAL_CHART_OK release=%s suite=%s chartRepoSha=%s chartDigest=%s\n' "$release" "$suite" "${chart_repo_sha:-UNCHECKED}" "$chart_digest" >&2
  exit 0
fi

# ── 복원 리허설 DB release(ai-gateway-db-restore) ───────────────────────────────────────────
chart=operations/deploy/helm/ai-gateway-db-restore
args=(-n tc -f "$chart/values.yaml" -f "$tmp/values.json")
helm package "$chart" -d "$tmp" >"$tmp/package.log" 2>&1 || error HELM_PACKAGE_FAILED
package=$tmp/ai-gateway-db-restore-0.1.0.tgz
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
python3 -B operations/deploy/app_render.py restore-db "$tmp/render.yaml" "$chart/bootstrap.sql" || error RESTORE_DB_RENDER_MISMATCH 1
# 성공 경로에서도 두 필수 guard 가 실제로 작동하는지 확인한다.
if helm template "$release" "$package" "${args[@]}" --set-string persistence.size= >"$tmp/negative" 2>"$tmp/error"; then error RENDER_GUARD_MISSING 1; fi
grep -Fq RENDER_REQUIRED_persistence.size "$tmp/error" || error RENDER_GUARD_MISMATCH 1
if helm template invalid-release "$package" "${args[@]}" >"$tmp/negative" 2>"$tmp/error"; then error RENDER_GUARD_MISSING 1; fi
grep -Fq RENDER_INVALID_RELEASE "$tmp/error" || error RENDER_GUARD_MISMATCH 1
if [[ $mode == render ]]; then cat "$tmp/render.yaml"; fi
if [[ $mode == deploy ]]; then
  # 클러스터 변경은 승인 운영자가 이 모드를 실행할 때만 발생한다.
  AIGW_RUNCONFIG="$runconfig" bash operations/gates/aigw-runtime-credentials.sh check --stage RESTORE
  deployment_started=1
  helm upgrade --install "$release" "$package" "${args[@]}" --wait --timeout 600s >"$tmp/upgrade.log" 2>&1 || error HELM_UPGRADE_FAILED 1
  kubectl -n tc rollout status "statefulset/$release" --timeout=300s >"$tmp/rollout.log" 2>&1 || error DB_ROLLOUT_FAILED 1
  kubectl -n tc wait --for=condition=complete "job/$release-bootstrap" --timeout=300s >"$tmp/bootstrap.log" 2>&1 || error BOOTSTRAP_FAILED 1
  record_deploy "$(python3 -B -c 'import hashlib,json,sys; print(json.dumps(dict(bootstrapSqlSha256=hashlib.sha256(open(sys.argv[1],"rb").read()).hexdigest(),imageDigest=json.load(open(sys.argv[2]))["pgImage"]["digest"])))' "$chart/bootstrap.sql" "$tmp/values.json")" || error HELM_NOT_DEPLOYED 1
  deployment_started=0
fi
printf 'LOCAL_CHART_OK release=%s chartRepoSha=%s chartDigest=%s\n' "$release" "${chart_repo_sha:-UNCHECKED}" "$chart_digest" >&2
