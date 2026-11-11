#!/usr/bin/env bash
# aigw-stage-gate.sh 셀프시험(Plan P15 ③): 임시 복사본에서 stub helm·kubectl·gradlew·aigw-helm.sh·자격 스크립트로 실행한다. 실제 배포·클러스터·eval 0 — 운영 게이트 PASS 가 아니다.
# 선택 env AIGW_STAGE_GATE_EXPORT=<쓰기 가능 디렉터리> + AIGW_STAGE_GATE_REAL_SPEC=<정본 SPEC 파일>: 정본 SPEC 바이트로 대표 시나리오를 다시 돌려
# 생성된 OPS/RUN 파일·정제 로그·eval 호출 순서를 내보낸다(호스트에서 실제 EvalCli ingest·gate 로 재생 — stage gate 레코드의 실제 eval 수용 확인용).
set -euo pipefail
export PYTHONDONTWRITEBYTECODE=1
exec python3 -B - "$@" <<'PY'
import base64, hashlib, json, os, pathlib, shutil, subprocess, tempfile, time

root = pathlib.Path.cwd()
checks = []
APP, ACC = 'sha256:' + '1' * 64, 'sha256:' + '3' * 64
IDENTITY = {'gitSha': '0' * 40, 'appJarSha256': '4' * 64, 'runtimeClasspathSha256': '5' * 64}
def pod(uid, restarts, digest=APP, ready='2026-11-07T01:00:30Z'):
    return {'metadata': {'uid': uid, 'labels': {'pod-template-hash': 'h1'}},
            'status': {'conditions': [{'type': 'Ready', 'status': 'True', 'lastTransitionTime': ready}],
                       'containerStatuses': [{'restartCount': restarts, 'imageID': 'docker-pullable://x@' + digest}]}}
ACCEPTANCE_PODS = {'job-name=ai-gateway-acceptance': [pod('a1', 0, ACC)], 'job-name=ai-gateway-restore-acceptance': [pod('a2', 0, ACC)]}

with tempfile.TemporaryDirectory(prefix='aigw-stage-gate-test-') as temporary:
    work = pathlib.Path(temporary)
    shutil.copytree(root / 'operations', work / 'operations')
    (work / 'docs/gates').mkdir(parents=True)
    shutil.copy(root / 'docs/gates/spec-baseline.md', work / 'docs/gates/spec-baseline.md')
    bin_dir = work / 'bin'; bin_dir.mkdir()
    calls_log, gradle_log, helm_args = work / 'calls.log', work / 'gradle.jsonl', work / 'helm-args.json'
    def script(path, body):
        path.write_text(body); path.chmod(0o755)
    script(bin_dir / 'kubectl', '#!/bin/sh\nexec python3 -B ' + str(work / 'operations/gates/selftest_kubectl_stub.py') + ' "$@"\n')
    # helm test stub: 상태 줄·블록 밖 원문(버려야 함)·STUB_BLOCK=valid 면 aigw-helm 이 받은 부모 회차·SPEC 으로 봉인한 RUN COMPLETED 블록.
    script(bin_dir / 'helm', '#!/bin/sh\nexec python3 -B ' + str(bin_dir / 'helm_stub.py') + ' "$@"\n')
    (bin_dir / 'helm_stub.py').write_text(r'''
import base64, hashlib, json, os, sys, uuid
sys.path.insert(0, 'operations/gates')
from evidence_record import seal
from stage_gate import MATRIX
open(os.environ['STUB_CALLS'], 'a').write('helm ' + ' '.join(sys.argv[1:]) + '\n')
print('NAME: stub'); print('STATUS: deployed'); print('RAW_NARRATIVE_SENTINEL 서술 원문'); sys.stdout.flush()
if os.environ.get('STUB_TEST_SLEEP'):
    os.execvp('sleep', ['sleep', os.environ['STUB_TEST_SLEEP']])
if os.environ.get('STUB_BLOCK', 'valid') == 'valid':
    deployed = json.load(open(os.environ['STUB_HELM_ARGS']))
    suite, parent, spec = (deployed[deployed.index(f) + 1] for f in ('--suite', '--parent-run-id', '--spec-file'))
    record = seal(spec, {'kind': 'RUN', 'status': 'COMPLETED', 'stage': MATRIX[suite][4], 'suite': suite, 'parentRunId': parent, 'runId': str(uuid.uuid4()),
                         'producer': 'ACCEPTANCE', 'buildIdentity': json.loads(os.environ['STUB_IDENTITY'])})
    block = json.dumps([record]).encode()
    print('AIGW-EVIDENCE-BEGIN ' + hashlib.sha256(block).hexdigest()); print(base64.b64encode(block).decode()); print('AIGW-EVIDENCE-END')
print('Phase: Succeeded')
sys.exit(int(os.environ.get('STUB_TEST_RC', '0')))
''')
    # stage_gate 는 실제처럼 `bash ./gradlew` 로 부른다 → sh 래퍼가 python stub 으로 넘긴다. ops 입력은 레코드 객체 파일이어야 한다(배열이면 실패).
    script(work / 'gradlew', '#!/bin/sh\nexec python3 -B ' + str(bin_dir / 'gradlew_stub.py') + ' "$@"\n')
    (bin_dir / 'gradlew_stub.py').write_text(r'''
import hashlib, json, os, pathlib, sys
arg = next(a for a in sys.argv[1:] if a.startswith('--args='))[7:]
open(os.environ['STUB_GRADLE_LOG'], 'a').write(json.dumps(arg) + '\n')
words = arg.split(); opt = lambda k: words[words.index(k) + 1]
if words[0] == 'spec-guard':
    sys.exit(0 if hashlib.sha256(open(opt('--spec'), 'rb').read()).hexdigest() == os.environ['STUB_SPEC_SHA'] else 3)
if words[0] == 'ingest':
    if opt('--source') == 'ops':
        record = json.loads(pathlib.Path(opt('--input')).read_text())
        if not isinstance(record, dict):
            sys.exit(2)
        d = pathlib.Path('evidence', opt('--stage'), {'RUN': 'runs', 'MEASUREMENT': 'measurements'}[record['kind']]); d.mkdir(parents=True, exist_ok=True)
        (d / (record['contentSha256'] + '.json')).write_text(json.dumps(record))
    sys.exit(int(os.environ.get('STUB_INGEST_RC', '0')))
if words[0] == 'gate':
    p = pathlib.Path('evidence', opt('--stage'), 'GATE-REPORT.md'); p.parent.mkdir(parents=True, exist_ok=True); p.write_text('stub report\n')
    sys.exit(int(os.environ.get('STUB_GATE_RC', '0')))
sys.exit(9)
''')
    script(work / 'operations/deploy/aigw-helm.sh', '#!/usr/bin/env bash\nprintf "aigw-helm %s\\n" "$*" >> "$STUB_CALLS"\n'
           'python3 -B -c "import json,sys; json.dump(sys.argv[1:], open(\'$STUB_HELM_ARGS\', \'w\'))" "$@"\nexit "${STUB_DEPLOY_RC:-0}"\n')
    script(work / 'operations/gates/aigw-verify-credentials.sh', '#!/usr/bin/env bash\nprintf "verify-credentials %s\\n" "$*" >> "$STUB_CALLS"\nexit "${STUB_VERIFY_RC:-0}"\n')
    script(work / 'operations/gates/aigw-runtime-credentials.sh', '#!/usr/bin/env bash\nprintf "runtime-credentials %s runconfig=%s\\n" "$*" "$AIGW_RUNCONFIG" >> "$STUB_CALLS"\nexit "${STUB_RUNTIME_RC:-0}"\n')
    spec = work / 'spec.md'; spec.write_bytes('# 시험 SPEC\n'.encode())
    tampered = work / 'tampered.md'; tampered.write_bytes('# 변조 SPEC\n'.encode())
    (work / 'main.json').write_text(json.dumps({'purpose': 'deploy', 'profile': 'main'}))
    (work / 'restore.json').write_text(json.dumps({'purpose': 'deploy', 'profile': 'restore'}))
    (work / 'digests.json').write_text(json.dumps({'app': APP, 'backup': 'sha256:' + '2' * 64, 'acceptance': ACC, 'buildIdentity': IDENTITY}))
    (work / 'other-digests.json').write_text(json.dumps({'app': 'sha256:' + '9' * 64, 'backup': 'sha256:' + '2' * 64, 'acceptance': ACC, 'buildIdentity': IDENTITY}))
    for name in ('targets.json', 'prep.json', 'catalog.yaml'):
        (work / name).write_text('{}\n')
    state = work / 'state.json'
    base_env = dict(os.environ, PATH=str(bin_dir) + os.pathsep + os.environ['PATH'], STUB_CALLS=str(calls_log), STUB_STATE=str(state), STUB_LOG=str(work / 'kubectl.jsonl'),
                    STUB_GRADLE_LOG=str(gradle_log), STUB_HELM_ARGS=str(helm_args), STUB_SPEC_SHA=hashlib.sha256(spec.read_bytes()).hexdigest(),
                    STUB_IDENTITY=json.dumps({'appImageDigest': APP, 'acceptanceImageDigest': ACC, 'buildIdentity': IDENTITY}))
    for key in ('AIGW_SPEC_FILE', 'AIGW_STAGE_GATE_EXPORT', 'AIGW_STAGE_GATE_REAL_SPEC'):
        base_env.pop(key, None)
    common = ['--exec-sha', 'a' * 40, '--image-digests', 'digests.json', '--test-timeout-seconds', '60']
    def run(args, expected, stderr=None, pods_seq=None, selector_pods=None, keep_evidence=False, **extra_env):
        if not keep_evidence:
            shutil.rmtree(work / 'evidence', ignore_errors=True)
        for path in (calls_log, gradle_log, work / 'kubectl.jsonl'):
            path.write_text('')
        state.write_text(json.dumps({'pods': pods_seq or [[pod('u1', 0)]], 'selector_pods': ACCEPTANCE_PODS if selector_pods is None else selector_pods}))
        result = subprocess.run(['bash', 'operations/gates/aigw-stage-gate.sh', *args], cwd=work, env=dict(base_env, **extra_env), capture_output=True, text=True)
        assert result.returncode == expected, (args, result.returncode, result.stdout, result.stderr)
        if stderr is not None:
            assert result.stderr.strip().splitlines()[0] == stderr, result.stderr
        assert 'Traceback' not in result.stderr
        checks.append({'args': [a for a in args if not a.endswith('.json')][:2], 'exit': expected})
        return result, calls_log.read_text().splitlines()
    def records(stage, kind='runs'):
        directory = work / 'evidence' / stage / kind
        return [json.loads(p.read_text()) for p in directory.glob('*.json')] if directory.exists() else []
    deployed = lambda calls: [c for c in calls if c.startswith('aigw-helm')]
    gradle_calls = lambda: [json.loads(line) for line in gradle_log.read_text().splitlines()]
    ingests = lambda: [c for c in gradle_calls() if c.startswith('ingest ')]
    gates = lambda: [c for c in gradle_calls() if c.startswith('gate ')]
    tests = lambda calls: [c for c in calls if c.startswith('helm test')]
    report = lambda stage: (work / 'evidence' / stage / 'GATE-REPORT.md').exists()
    aborted = lambda stage: [r['reasonCode'] for r in records(stage) if r['status'] == 'ABORTED']

    # ── 입력: SPEC 둘 다 없음 → exit 2·배포·ingest 0 / 같은 기준선에 변조 SPEC → exit 3·배포 0 ──
    result, calls = run(['--stage', 'F1', '--runconfig', 'main.json'] + common, 2, 'SPEC_INPUT_MISSING')
    assert calls == [] and gradle_calls() == []
    result, calls = run(['--stage', 'F1', '--runconfig', 'main.json', '--spec', str(tampered)] + common, 3, 'SPEC_GUARD_FAILED')
    assert not deployed(calls) and not ingests()
    result, calls = run(['--stage', 'F9', '--runconfig', 'main.json', '--spec', str(spec)] + common, 2, 'STAGE_INVALID'); assert calls == []
    run(['--stage', 'F1', '--runconfig', 'main.json', '--spec', str(spec), '--exec-sha', 'a' * 40, '--image-digests', 'digests.json'], 2, 'ARGUMENT_MISSING:test-timeout-seconds')

    # ── 정상 F1(새 부모): stdout 에 AIGW_PARENT_RUN_ID= 정확히 1줄, 그 값 = PARENT runId = parentRunId, 실제 앱·수용 Pod digest ──
    result, calls = run(['--stage', 'F1', '--runconfig', 'main.json', '--spec', str(spec)] + common, 0)
    lines = [l for l in result.stdout.splitlines() if l.startswith('AIGW_PARENT_RUN_ID=')]
    assert len(lines) == 1 and result.stdout.strip() == lines[0]
    parent = lines[0].split('=', 1)[1]
    assert parent.startswith('F1-') and len(parent.rsplit('-', 1)[1]) == 8
    manifests = [r for r in records('F1') if r['status'] == 'PARENT']
    assert len(manifests) == 1 and manifests[0]['parentRunId'] == parent and manifests[0]['runId'] == parent
    assert manifests[0]['buildIdentity'] == {'appImageDigest': APP, 'acceptanceImageDigest': ACC, 'buildIdentity': IDENTITY}
    assert manifests[0]['baselineSeq'] >= 1 and manifests[0]['specShaSource'] == 'FILE_HASHED' and manifests[0]['suite'] == 'F1'
    assert all(isinstance(json.loads(p.read_text()), dict) for p in (work / 'evidence/F1/ops').glob('*.json'))
    helm_call = deployed(calls)[0]
    assert '--mode deploy' in helm_call and f'--spec-file {spec}' in helm_call and f'--parent-run-id {parent}' in helm_call and '--suite F1' in helm_call
    assert any(f'--spec {spec}' in c and '--source acceptance' in c for c in ingests()) and gates() and f'--spec {spec}' in gates()[0]
    assert ['verify-credentials check --stage F1'] == [c for c in calls if c.startswith('verify-credentials')]
    assert tests(calls) == ['helm test ai-gateway -n tc --timeout 60s --logs']
    saved = sorted((work / 'evidence/F1').glob('acceptance-*.log'))
    assert len(saved) == 1
    text = saved[0].read_text()
    assert 'RAW_NARRATIVE_SENTINEL' not in text and 'AIGW-EVIDENCE-BEGIN' in text and 'STATUS: deployed' in text and 'Phase: Succeeded' in text
    assert (work / 'evidence/F1' / (saved[0].name + '.sha256')).read_text().split()[0] == hashlib.sha256(saved[0].read_bytes()).hexdigest()
    assert report('F1')
    # 수용 Pod digest 를 못 읽으면 입력값으로 채우지 않고 UNVERIFIED(gate 가 IDENTITY_MISMATCH 로 판정).
    run(['--stage', 'F1', '--runconfig', 'main.json', '--spec', str(spec)] + common, 0, selector_pods={'job-name=ai-gateway-acceptance': []})
    assert [r['buildIdentity']['acceptanceImageDigest'] for r in records('F1') if r['status'] == 'PARENT'] == ['UNVERIFIED']

    # ── 같은 부모로 다른 앱 digest → exit 2 PARENT_IDENTITY_MISMATCH·배포 0 / 같은 digest 는 진행 ──
    run(['--stage', 'F1', '--runconfig', 'main.json', '--spec', str(spec)] + common, 0)
    parent = next(r['parentRunId'] for r in records('F1') if r['status'] == 'PARENT')
    result, calls = run(['--stage', 'F1', '--runconfig', 'main.json', '--spec', str(spec), '--parent-run-id', parent, '--exec-sha', 'a' * 40,
                         '--image-digests', 'other-digests.json', '--test-timeout-seconds', '60'], 2, 'PARENT_IDENTITY_MISMATCH', keep_evidence=True)
    assert not deployed(calls)
    run(['--stage', 'F1', '--runconfig', 'main.json', '--spec', str(spec), '--parent-run-id', 'F1-unknown'] + common, 2, 'PARENT_MANIFEST_MISSING', keep_evidence=True)
    result, calls = run(['--stage', 'F1-RUBRIC', '--runconfig', 'main.json', '--spec', str(spec), '--parent-run-id', parent, '--rubric-targets', 'targets.json'] + common, 0,
                        pods_seq=[[pod('u1', 0)], [pod('u1', 0)]], keep_evidence=True)
    assert 'AIGW_PARENT_RUN_ID=' not in result.stdout and '--mode suite-only' in deployed(calls)[0] and '--rubric-targets targets.json' in deployed(calls)[0]
    assert len([r for r in records('F1') if r['status'] == 'PARENT']) == 1
    # 재배포 0 suite 에서 Pod 변화 → RUN ABORTED·exit 11·helm test 0.
    result, calls = run(['--stage', 'F1-RUBRIC', '--runconfig', 'main.json', '--spec', str(spec), '--parent-run-id', parent, '--rubric-targets', 'targets.json'] + common, 11,
                        pods_seq=[[pod('u1', 0)], [pod('u1', 1)]], keep_evidence=True)
    assert 'APP_POD_CHANGED' in aborted('F1') and not tests(calls) and report('F1')
    # 기존 부모라도 배포 뒤 실제 앱 Pod digest ≠ 입력 → DEPLOYED_DIGEST_MISMATCH·exit 11·helm test 0.
    result, calls = run(['--stage', 'F1', '--runconfig', 'main.json', '--spec', str(spec), '--parent-run-id', parent] + common, 11,
                        pods_seq=[[pod('u2', 0, 'sha256:' + '8' * 64)]], keep_evidence=True)
    assert 'DEPLOYED_DIGEST_MISMATCH' in aborted('F1') and not tests(calls)
    run(['--stage', 'F1-RUBRIC', '--runconfig', 'main.json', '--spec', str(spec), '--parent-run-id', parent] + common, 2, 'RUBRIC_TARGETS_MISSING', keep_evidence=True)

    # ── 배포 실패 → RUN ABORTED(DEPLOY_FAILED) 1건만·leaf 0·exit 11 ──
    result, calls = run(['--stage', 'F1', '--runconfig', 'main.json', '--spec', str(spec)] + common, 11, STUB_DEPLOY_RC='1')
    assert aborted('F1') == ['DEPLOY_FAILED'] and not (work / 'evidence/F1/AC-24-JOB').exists() and report('F1') and not tests(calls)
    # ── helm test 실패 → 로그·.sha256·ingest·gate 뒤 exit 10 ──
    result, calls = run(['--stage', 'F1', '--runconfig', 'main.json', '--spec', str(spec)] + common, 10, STUB_TEST_RC='1')
    assert list((work / 'evidence/F1').glob('acceptance-*.log.sha256')) and any('--source acceptance' in c for c in ingests()) and gates()
    # ── helm test 무응답 → --test-timeout-seconds+120 초에 강제 종료, 로그·ingest·gate 뒤 exit 10 ──
    started = time.monotonic()
    run(['--stage', 'F1', '--runconfig', 'main.json', '--spec', str(spec), '--exec-sha', 'a' * 40, '--image-digests', 'digests.json', '--test-timeout-seconds', '1'], 10, STUB_TEST_SLEEP='600')
    assert 120 <= time.monotonic() - started < 200
    assert list((work / 'evidence/F1').glob('acceptance-*.log.sha256')) and any('--source acceptance' in c for c in ingests()) and gates() and report('F1')
    # ── 자격: 검증 MISSING → 배포 0·12, 런타임 MISSING → 배포 0·13 ──
    result, calls = run(['--stage', 'F1', '--runconfig', 'main.json', '--spec', str(spec)] + common, 12, STUB_VERIFY_RC='12')
    assert not deployed(calls) and aborted('F1') == ['VERIFY_CREDENTIALS_MISSING'] and report('F1')
    result, calls = run(['--stage', 'F1', '--runconfig', 'main.json', '--spec', str(spec)] + common, 13, STUB_RUNTIME_RC='13')
    assert not deployed(calls) and aborted('F1') == ['RUNTIME_CREDENTIALS_MISSING']
    # ── suite → 자격 단계(N2): F3-PROBE 는 verify F2·ingest F3·gate 생략 ──
    result, calls = run(['--stage', 'F3-PROBE', '--runconfig', 'main.json', '--spec', str(spec)] + common, 0)
    assert 'verify-credentials check --stage F2' in calls and any(c.startswith('runtime-credentials check --stage F2') for c in calls)
    assert not gates() and list((work / 'evidence/F3/gates').glob('acceptance-probe-*.log')) and all(' --stage F3 ' in c for c in ingests())
    # ── F4-RESTORE: 필수 인자·profile·RESTORE 런타임 단계·RTO 측정·gate 실행(Plan 의 gate 생략 = F3-PROBE·F4-PERF·F4-RESTORE-PREP·F4-RESTORE-MUTATE 만) ──
    restore_args = ['--stage', 'F4-RESTORE', '--spec', str(spec), '--prep-manifest', 'prep.json', '--backup-point', '2026-11-06T00:00:00Z',
                    '--clock-initial', '2026-11-06T00:00:00Z', '--restore-started-at', '2026-11-07T01:00:00Z', '--fixture-catalog', 'catalog.yaml'] + common
    for drop in ('--prep-manifest', '--backup-point', '--clock-initial', '--restore-started-at'):
        index = restore_args.index(drop)
        result, calls = run(restore_args[:index] + restore_args[index + 2:] + ['--runconfig', 'restore.json'], 2, 'ARGUMENT_MISSING:' + drop[2:]); assert calls == []
    result, calls = run(restore_args + ['--runconfig', 'main.json'], 2, 'RUNCONFIG_PROFILE_MISMATCH'); assert not deployed(calls) and not any(c.startswith('helm') for c in calls)
    result, calls = run(['--stage', 'F4', '--runconfig', 'restore.json', '--spec', str(spec)] + common, 2, 'RUNCONFIG_PROFILE_MISMATCH'); assert not deployed(calls)
    result, calls = run(restore_args + ['--runconfig', 'restore.json'], 0, pods_seq=[[pod('r1', 0, ready='2026-11-07T01:07:30Z')]])
    assert any(c.startswith('runtime-credentials check --stage RESTORE') for c in calls) and 'verify-credentials check --stage F4' in calls
    helm_call = deployed(calls)[0]
    assert '--release ai-gateway-restore' in helm_call and '--fixture-catalog catalog.yaml' in helm_call and '--backup-point 2026-11-06T00:00:00Z' in helm_call
    rto = records('F4', 'measurements')
    assert len(rto) == 1 and rto[0]['measurementId'] == 'ops:restore-rto' and rto[0]['values'] == {'restoreStartedAt': '2026-11-07T01:00:00Z', 'readyAt': '2026-11-07T01:07:30Z', 'seconds': 450}
    assert gates() and ' --stage F4 ' in gates()[0] and report('F4')
    assert [r['buildIdentity']['acceptanceImageDigest'] for r in records('F4') if r['status'] == 'PARENT'] == [ACC]
    # ── ingest·gate 실패 rc 전파(수집 뒤) ──
    run(['--stage', 'F1', '--runconfig', 'main.json', '--spec', str(spec)] + common, 5, STUB_INGEST_RC='5')
    assert gates()
    run(['--stage', 'F1', '--runconfig', 'main.json', '--spec', str(spec)] + common, 4, STUB_GATE_RC='4')
    # ── F3-RULEVER 의 --set-extra 는 그대로 aigw-helm 으로 ──
    result, calls = run(['--stage', 'F3-RULEVER', '--runconfig', 'main.json', '--spec', str(spec), '--set-extra', 'sanitize.verifyRuleSet=mask-v1r'] + common, 0)
    assert '--set-extra sanitize.verifyRuleSet=mask-v1r' in deployed(calls)[0]

    # ── 선택: 정본 SPEC 으로 대표 시나리오를 내보내 호스트의 실제 EvalCli 로 재생 ──
    export, real_spec = os.environ.get('AIGW_STAGE_GATE_EXPORT'), os.environ.get('AIGW_STAGE_GATE_REAL_SPEC')
    if export and real_spec:
        shutil.copy(real_spec, work / 'real-spec.md')
        real_sha = hashlib.sha256((work / 'real-spec.md').read_bytes()).hexdigest()
        restore_real = [a for a in restore_args if a not in ('--spec', str(spec))] + ['--runconfig', 'restore.json']
        scenarios = {
            'f1-normal': (['--stage', 'F1', '--runconfig', 'main.json'] + common, 0, {}, None),
            'f1-verify-missing': (['--stage', 'F1', '--runconfig', 'main.json'] + common, 12, {'STUB_VERIFY_RC': '12'}, None),
            'f1-no-block': (['--stage', 'F1', '--runconfig', 'main.json'] + common, 0, {'STUB_BLOCK': 'none'}, None),
            'f4-restore': (restore_real, 0, {}, [[pod('r1', 0, ready='2026-11-07T01:07:30Z')]]),
        }
        for name, (args, expected, extra, pods_seq) in scenarios.items():
            run(args + ['--spec', 'real-spec.md'], expected, pods_seq=pods_seq, STUB_SPEC_SHA=real_sha, **extra)
            target = pathlib.Path(export) / name
            shutil.rmtree(target, ignore_errors=True)
            for path in (work / 'evidence').rglob('*'):
                relative = path.relative_to(work)
                if path.is_file() and ('ops' in relative.parts or path.suffix in ('.log', '.sha256')):
                    (target / relative).parent.mkdir(parents=True, exist_ok=True)
                    shutil.copy(path, target / relative)
            (target / 'calls.json').write_text(json.dumps([c for c in gradle_calls() if c.split()[0] in ('ingest', 'gate')]))
        checks.append({'export': sorted(scenarios)})
print(json.dumps({'status': 'PASS', 'boundary': 'LOCAL stub helm/kubectl/gradlew/aigw-helm/자격 스크립트 only', 'count': len(checks)}))
PY
