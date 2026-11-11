#!/usr/bin/env bash
# aigw-verify-credentials.sh 셀프시험(Plan P15 ④·승인 A/B): 임시 복사본 안에서 stub kubectl·stub aigw-helm.sh·stub hba_policy.py 로만 실행한다.
# 실제 클러스터·DB 0 — 운영 HBA·접근 PASS 가 아니다.
set -euo pipefail
export PYTHONDONTWRITEBYTECODE=1
exec python3 -B - "$@" <<'PY'
import base64, json, os, pathlib, shutil, subprocess, tempfile, time

root = pathlib.Path.cwd()
checks = []
OPEN = 'select OK\ntcp-postgres HBA_REJECT\ntcp-template1 HBA_REJECT\nunix-postgres HBA_REJECT\nunix-template1 HBA_REJECT\n'
UNRELATED_KEY = {'kty': 'EC', 'crv': 'P-256', 'kid': 'tc-system-2026', 'x': 'AAAA', 'y': 'BBBB'}
UNRELATED_HASH = 'f' * 64
ALLOWED_DELETES = {'ai-gateway-acceptance-signer', 'ai-gateway-acceptance-client', 'ai-gateway-db-acceptance-ro', 'ai-gateway-acceptance-fixture-writer',
                   'ai-gateway-test-hooks', 'ai-gateway-actor-jwks', 'ai-gateway-service-tokens', 'ai-gateway-acceptance', 'ai-gateway-restore-acceptance'}
with tempfile.TemporaryDirectory(prefix='aigw-verify-creds-test-') as temporary:
    work = pathlib.Path(temporary)
    shutil.copytree(root / 'operations', work / 'operations')
    bin_dir = work / 'bin'; bin_dir.mkdir()
    (bin_dir / 'kubectl').write_text('#!/bin/sh\nexec python3 -B ' + str(work / 'operations/gates/selftest_kubectl_stub.py') + ' "$@"\n'); (bin_dir / 'kubectl').chmod(0o755)
    # 같은 경로의 aigw-helm.sh·hba_policy.py 를 기록·종료 코드만 가진 stub 으로 바꾼다.
    (work / 'operations/deploy/aigw-helm.sh').write_text('#!/usr/bin/env bash\nprintf "%s\\n" "$*" >> "$STUB_HELM_LOG"\n'
        'for a in "$@"; do case $a in ai-gateway-db|ai-gateway-db-restore) r=$a;; esac; done\n'
        'eval "code=\\${STUB_HELM_RC_${r//-/_}:-0}"; exit "$code"\n')
    (work / 'operations/deploy/hba_policy.py').write_text('import os,sys\nopen(os.environ["STUB_HELM_LOG"],"a").write("hba "+" ".join(sys.argv[1:3])+"\\n")\n'
        'sys.exit(int(os.environ.get("STUB_HBA_RC_"+sys.argv[2].replace("-","_"),"0")))\n')
    # 부작용 전 입력 검사 stub: runconfig.py(<runconfig> <release> <mode> <out> <exec sha>)·app_render.py digests·런타임 자격 check.
    (work / 'operations/deploy/runconfig.py').write_text('import os,sys\nopen(os.environ["STUB_HELM_LOG"],"a").write("runconfig "+" ".join([sys.argv[1],sys.argv[2],sys.argv[3],sys.argv[5]])+"\\n")\n'
        'sys.exit(int(os.environ.get("STUB_RUNCONFIG_RC_"+sys.argv[2].replace("-","_"),"0")))\n')
    (work / 'operations/deploy/app_render.py').write_text('import os,sys\nopen(os.environ["STUB_HELM_LOG"],"a").write("digests "+sys.argv[2]+"\\n")\nsys.exit(int(os.environ.get("STUB_DIGESTS_RC","0")))\n')
    (work / 'operations/gates/aigw-runtime-credentials.sh').write_text('#!/usr/bin/env bash\nprintf "runtime %s %s\\n" "$*" "$AIGW_RUNCONFIG" >> "$STUB_HELM_LOG"\n'
        's=${3:-x}; eval "code=\\${STUB_RUNTIME_RC_$s:-0}"; exit "$code"\n')
    state_path, log_path, helm_log = work / 'state.json', work / 'calls.jsonl', work / 'helm.log'
    env = dict(os.environ, PATH=str(bin_dir) + os.pathsep + os.environ['PATH'], STUB_STATE=str(state_path), STUB_LOG=str(log_path), STUB_HELM_LOG=str(helm_log),
               AIGW_EXEC_CHART_SHA='a' * 40, AIGW_IMAGE_DIGESTS='image-digests.json', AIGW_RUNCONFIG='main.json', AIGW_RESTORE_RUNCONFIG='restore.json')

    def reset(statefulsets=('ai-gateway-db', 'ai-gateway-db-restore'), deployments=('ai-gateway',), **extra):
        state = {'secrets': {}, 'present': {'statefulset': list(statefulsets), 'deployment': list(deployments)}, 'fail': {},
                 'probe_outputs': {s + '-0': OPEN for s in statefulsets}}
        state.update(extra)
        state_path.write_text(json.dumps(state)); log_path.write_text(''); helm_log.write_text('')
    state = lambda: json.loads(state_path.read_text())
    calls = lambda: [json.loads(line) for line in log_path.read_text().splitlines()]
    decode = lambda s, name, key: base64.b64decode(s['secrets'][name][key])
    def run(args, expected, stderr=None, extra_env=None):
        result = subprocess.run(['bash', 'operations/gates/aigw-verify-credentials.sh', *args], cwd=work, env=dict(env, **(extra_env or {})), capture_output=True, text=True)
        assert result.returncode == expected, (args, result.returncode, result.stdout, result.stderr)
        if stderr is not None:
            assert result.stderr.strip() == stderr, (args, result.stderr)
        assert 'Traceback' not in result.stderr
        checks.append({'args': args, 'exit': result.returncode, 'stderr': stderr})
        return result
    def secret_values(s):
        found = []
        for data in s['secrets'].values():
            for value in data.values():
                raw = base64.b64decode(value).decode()
                found.append(raw.strip())
                try:
                    obj = json.loads(raw)
                    found += [k['d'] for k in obj.get('keys', [obj]) if isinstance(k, dict) and 'd' in k] if isinstance(obj, dict) else []
                except ValueError:
                    pass
        return [v for v in found if len(v) >= 20 and not v.startswith('{')]
    def evidence_text():
        return ''.join(p.read_text() for p in (work / 'evidence').rglob('*.json')) if (work / 'evidence').exists() else ''
    def item_lines(stdout):
        return [line.split() for line in stdout.splitlines()]

    # ── 입력 경계 ──
    reset()
    for bad in (['check', '--stage', 'F5'], ['check', '--stage', 'RESTORE'], ['provision', '--stage', 'x']):
        run(bad, 2, 'STAGE_INVALID')
    run(['check'], 2, 'ARGUMENT_INVALID'); run([], 2, 'ARGUMENT_MISSING')
    out = run(['check', '--stage', 'F1'], 12)
    assert {state for _, state in item_lines(out.stdout)} == {'MISSING'} and 'actor-signer SECRET_ABSENT:ai-gateway-actor-jwks' in out.stderr

    # ── provision: 부작용 전 HBA live 실패면 Secret 0 ──
    reset()
    run(['provision', '--stage', 'F1'], 1, 'HBA_LIVE_CHECK_FAILED:ai-gateway-db-restore', {'STUB_HBA_RC_ai_gateway_db_restore': '1'})
    assert state()['secrets'] == {} and not any(c['args'][0] == 'apply' for c in calls())
    reset(fail={'get-statefulset:ai-gateway-db-restore': 1})
    run(['provision', '--stage', 'F1'], 1, 'RELEASE_QUERY_FAILED:ai-gateway-db-restore'); assert state()['secrets'] == {}
    reset(); run(['provision', '--stage', 'F1'], 2, 'INPUT_MISSING:AIGW_RESTORE_RUNCONFIG', {'AIGW_RESTORE_RUNCONFIG': ''}); assert state()['secrets'] == {}
    # 입력 의미 검사(재배포가 쓸 검사를 부작용 전에): exec SHA 형식·digests·release 별 runconfig(deploy 모드)·런타임 자격 미준비 → Secret·apply·재배포 0.
    for code, stderr, extra_env in ((2, 'INPUT_INVALID:AIGW_EXEC_CHART_SHA', {'AIGW_EXEC_CHART_SHA': 'main'}),
                                    (2, 'INPUT_INVALID:AIGW_IMAGE_DIGESTS', {'STUB_DIGESTS_RC': '2'}),
                                    (2, 'INPUT_INVALID:AIGW_RESTORE_RUNCONFIG', {'STUB_RUNCONFIG_RC_ai_gateway_db_restore': '2'}),
                                    (1, 'DB_RUNTIME_CREDENTIALS_NOT_READY:ai-gateway-db', {'STUB_RUNTIME_RC_F1': '13'})):
        reset(); run(['provision', '--stage', 'F1'], code, stderr, extra_env)
        assert state()['secrets'] == {} and not any(c['args'][0] == 'apply' for c in calls()) and not [h for h in helm_log.read_text().splitlines() if h.startswith('--')], stderr
    reset(); run(['provision', '--stage', 'F1'], 1, 'HBA_LIVE_CHECK_FAILED:ai-gateway-db', {'STUB_HBA_RC_ai_gateway_db': '1'})
    checked = helm_log.read_text().splitlines()
    assert 'runconfig main.json ai-gateway-db deploy ' + 'a' * 40 in checked and 'runconfig restore.json ai-gateway-db-restore deploy ' + 'a' * 40 in checked
    assert 'runtime check --stage F1 main.json' in checked and 'runtime check --stage RESTORE restore.json' in checked
    # 접근 경계 실패(다른 DB TCP 허용·Unix 의 비 HBA 오류)는 성공이 아니다.
    reset(probe_outputs={'ai-gateway-db-0': OPEN.replace('tcp-postgres HBA_REJECT', 'tcp-postgres OK'), 'ai-gateway-db-restore-0': OPEN})
    run(['provision', '--stage', 'F1'], 1, 'ACCEPTANCE_ACCESS_BOUNDARY_FAILED:ai-gateway-db')
    reset(probe_outputs={'ai-gateway-db-0': OPEN, 'ai-gateway-db-restore-0': OPEN.replace('unix-template1 HBA_REJECT', 'unix-template1 FAILED')})
    run(['provision', '--stage', 'F1'], 1, 'ACCEPTANCE_ACCESS_BOUNDARY_FAILED:ai-gateway-db-restore')

    # ── provision 정상: 두 DB enabled 재배포·HBA live·접근 경계 OK·앱 재기동, 값 출력·명령행·증거 0 ──
    reset()
    out = run(['provision', '--stage', 'F1'], 0)
    saved = state(); values = secret_values(saved)
    assert len(values) >= 5
    for value in values:
        assert value not in out.stdout + out.stderr + evidence_text() + helm_log.read_text(), 'secret printed'
        assert all(value not in ' '.join(c['args']) for c in calls()), 'secret on command line'
    helm_calls = helm_log.read_text().splitlines()
    assert 'hba live ai-gateway-db' in helm_calls and 'hba live ai-gateway-db-restore' in helm_calls
    assert any('--release ai-gateway-db ' in h and '--acceptance-role enabled' in h and '--runconfig main.json' in h for h in helm_calls)
    assert any('--release ai-gateway-db-restore ' in h and '--runconfig restore.json' in h for h in helm_calls)
    assert ['rollout', 'restart', 'deployment/ai-gateway'] in [c['args'] for c in calls()]
    probes = [c for c in calls() if c['args'][0] == 'exec' and any('probe()' in a for a in c['args'])]
    assert len(probes) == 2 and all('POSTGRES_PASSWORD' not in ' '.join(p['args']) for p in probes)
    out = run(['check', '--stage', 'F1'], 0)
    assert {state for _, state in item_lines(out.stdout)} == {'MATCH'} and out.stderr == ''
    before = state()['secrets']; run(['provision', '--stage', 'F1'], 0); assert state()['secrets'] == before, '두 번째 provision 은 덮어쓰지 않음'
    out = run(['check', '--stage', 'F3'], 12); assert ['fixture-writer', 'MISSING'] in item_lines(out.stdout)

    # ── check 출력 계약: 항목은 MATCH/MISSING 뿐, 사유는 stderr 고정 코드, 조회 오류는 exit 1(항목 출력 0) ──
    def tampered(mutate):
        copy = json.loads(json.dumps(saved)); mutate(copy); state_path.write_text(json.dumps(copy))
    tampered(lambda s: s['secrets']['ai-gateway-test-hooks'].update({'hook-token-sha256': base64.b64encode(b'0' * 64).decode()}))
    out = run(['check', '--stage', 'F1'], 12); assert ['hook-token', 'MISSING'] in item_lines(out.stdout) and 'hook-token HASH_MISMATCH' in out.stderr
    def non_test_kid(s):
        signer = json.loads(decode(s, 'ai-gateway-acceptance-signer', 'signer-private.jwk')); jwks = json.loads(decode(s, 'ai-gateway-actor-jwks', 'jwks.json'))
        for key in jwks['keys']:
            key['kid'] = 'production-1'
        signer['kid'] = 'production-1'
        s['secrets']['ai-gateway-acceptance-signer']['signer-private.jwk'] = base64.b64encode(json.dumps(signer).encode()).decode()
        s['secrets']['ai-gateway-actor-jwks']['jwks.json'] = base64.b64encode(json.dumps(jwks).encode()).decode()
    tampered(non_test_kid)
    out = run(['check', '--stage', 'F1'], 12)
    assert ['actor-signer', 'MISSING'] in item_lines(out.stdout) and 'actor-signer KID_NOT_TEST_PREFIX' in out.stderr
    assert all(state in ('MATCH', 'MISSING') for _, state in item_lines(out.stdout)) and 'KID' not in evidence_text().split('"items"')[-1].split('"missingReasons"')[0]
    def private_in_jwks(s):
        jwks = json.loads(decode(s, 'ai-gateway-actor-jwks', 'jwks.json')); jwks['keys'][0]['d'] = 'x'
        s['secrets']['ai-gateway-actor-jwks']['jwks.json'] = base64.b64encode(json.dumps(jwks).encode()).decode()
    tampered(private_in_jwks); assert 'actor-signer JWKS_HAS_PRIVATE_MEMBER' in run(['check', '--stage', 'F1'], 12).stderr
    tampered(lambda s: s.update(fail={'get-secret:ai-gateway-acceptance-client': 1}))
    out = run(['check', '--stage', 'F1'], 1, 'SECRET_QUERY_FAILED:ai-gateway-acceptance-client'); assert out.stdout == ''

    # ── rotate: 이전 kid·해시 제거, 새 값 MATCH ──
    state_path.write_text(json.dumps(saved))
    old_kid = json.loads(decode(saved, 'ai-gateway-acceptance-signer', 'signer-private.jwk'))['kid']
    old_tokens = json.loads(decode(saved, 'ai-gateway-service-tokens', 'service-tokens.json'))
    time.sleep(1.1)
    run(['rotate', '--stage', 'F1'], 0)
    after = state()
    kids = [k['kid'] for k in json.loads(decode(after, 'ai-gateway-actor-jwks', 'jwks.json'))['keys']]
    assert old_kid not in kids and len(kids) == 1
    assert not set(old_tokens) & set(json.loads(decode(after, 'ai-gateway-service-tokens', 'service-tokens.json')))
    run(['check', '--stage', 'F1'], 0)
    partial = json.loads(json.dumps(saved)); del partial['secrets']['ai-gateway-acceptance-client']; state_path.write_text(json.dumps(partial))
    run(['provision', '--stage', 'F1'], 2, 'PROVISION_PARTIAL_STATE')
    reset(); run(['rotate', '--stage', 'F1'], 2, 'ROTATE_REQUIRES_EXISTING')

    # ── cleanup ──
    def with_unrelated(s):
        jwks = json.loads(decode(s, 'ai-gateway-actor-jwks', 'jwks.json')); jwks['keys'].append(UNRELATED_KEY)
        tokens = json.loads(decode(s, 'ai-gateway-service-tokens', 'service-tokens.json')); tokens[UNRELATED_HASH] = 'TC'
        s['secrets']['ai-gateway-actor-jwks']['jwks.json'] = base64.b64encode(json.dumps(jwks).encode()).decode()
        s['secrets']['ai-gateway-service-tokens']['service-tokens.json'] = base64.b64encode(json.dumps(tokens).encode()).decode()
        s['secrets']['unrelated-app-secret'] = {'k': base64.b64encode(b'keep').decode()}
        return s
    full = with_unrelated(json.loads(json.dumps(saved)))
    def cleanup_case(statefulsets, expected, stderr=None, extra_env=None, secrets=None, **extra):
        reset(statefulsets=statefulsets, secrets=json.loads(json.dumps((secrets or full)['secrets'])), **extra)
        if 'probe_outputs' not in extra:
            s = state(); s['probe_outputs'] = {}; state_path.write_text(json.dumps(s))   # 기본 = 역할 부재(재접속 실패)
        result = run(['cleanup'], expected, stderr, extra_env)
        return result, calls(), helm_log.read_text().splitlines()
    def deletes(log):
        return [c['args'][2] for c in log if c['args'][0] == 'delete']
    result, log, helm_calls = cleanup_case(('ai-gateway-db', 'ai-gateway-db-restore'), 0)
    ops_ = [c['args'][0] + ':' + (c['args'][1] if len(c['args']) > 1 else '') for c in log]
    first = lambda pred: next(i for i, c in enumerate(log) if pred(c))
    job_stop = first(lambda c: c['args'][:2] == ['delete', 'job'])
    nologin = first(lambda c: c['args'][0] == 'exec' and 'NOLOGIN' in c['stdin'])
    secret_delete = first(lambda c: c['args'][:2] == ['delete', 'secret'])
    last_probe = max(i for i, c in enumerate(log) if c['args'][0] == 'exec' and any('probe()' in a for a in c['args']))
    assert job_stop < nologin < last_probe < secret_delete, ops_
    assert set(d for d in deletes(log)) <= ALLOWED_DELETES and {'ai-gateway-acceptance', 'ai-gateway-restore-acceptance'} <= set(deletes(log))
    sql = [c['stdin'] for c in log if c['args'][0] == 'exec' and 'NOLOGIN' in c['stdin']]
    assert len(sql) == 2 and all('pg_terminate_backend' in s and "usename = 'aigw_acceptance_ro'" in s for s in sql)
    assert [h for h in helm_calls if h.startswith('--')] and all('--acceptance-role disabled' in h for h in helm_calls if h.startswith('--'))
    assert sum(1 for h in helm_calls if h.startswith('hba live')) == 2
    final = state()['secrets']
    assert not {'ai-gateway-acceptance-signer', 'ai-gateway-acceptance-client', 'ai-gateway-db-acceptance-ro', 'ai-gateway-test-hooks'} & set(final)
    keys = json.loads(base64.b64decode(final['ai-gateway-actor-jwks']['jwks.json']))['keys']
    assert keys == [UNRELATED_KEY], '시험 공개키 잔류 0·무관 키 보존'
    assert json.loads(base64.b64decode(final['ai-gateway-service-tokens']['service-tokens.json'])) == {UNRELATED_HASH: 'TC'}, '수용 해시 잔류 0·시스템 토큰 보존'
    assert 'unrelated-app-secret' in final
    assert 'ai-gateway RESTART_OR_UNINSTALL_REQUIRED' in result.stdout
    password = base64.b64decode(full['secrets']['ai-gateway-db-acceptance-ro']['password']).decode()
    assert all(password not in ' '.join(c['args']) for c in log) and password not in result.stdout + result.stderr
    # 2회 반복(이미 정리됨)도 성공, 남은 무관 항목 불변.
    snapshot = state()['secrets']; result = run(['cleanup'], 0); assert state()['secrets'] == snapshot
    # 시험 키만 있던 JWKS·수용 해시만 있던 토큰 Secret 은 통째로 지운다.
    result, log, helm_calls = cleanup_case(('ai-gateway-db',), 0, extra_env={'AIGW_RESTORE_RUNCONFIG': ''}, secrets=saved)
    assert not {'ai-gateway-actor-jwks', 'ai-gateway-service-tokens'} & set(state()['secrets'])
    assert len([h for h in helm_calls if h.startswith('--')]) == 1
    # 모두 없음: DB 작업 0, Secret 정리는 수행.
    result, log, helm_calls = cleanup_case((), 0, extra_env={'AIGW_RUNCONFIG': '', 'AIGW_RESTORE_RUNCONFIG': ''})
    assert not any(c['args'][0] == 'exec' for c in log) and not [h for h in helm_calls if h.startswith('--')]
    # 부작용 전 실패: 입력 누락·release 조회 오류·Secret 조회 오류·HBA live 실패 → delete/exec/helm 0.
    for statefulsets, code, stderr, extra_env, extra in (
            (('ai-gateway-db', 'ai-gateway-db-restore'), 2, 'INPUT_MISSING:AIGW_RESTORE_RUNCONFIG', {'AIGW_RESTORE_RUNCONFIG': ''}, {}),
            (('ai-gateway-db', 'ai-gateway-db-restore'), 1, 'RELEASE_QUERY_FAILED:ai-gateway-db-restore', None, {'fail': {'get-statefulset:ai-gateway-db-restore': 1}}),
            (('ai-gateway-db',), 1, 'SECRET_QUERY_FAILED:ai-gateway-actor-jwks', {'AIGW_RESTORE_RUNCONFIG': ''}, {'fail': {'get-secret:ai-gateway-actor-jwks': 1}}),
            (('ai-gateway-db',), 1, 'HBA_LIVE_CHECK_FAILED:ai-gateway-db', {'AIGW_RESTORE_RUNCONFIG': '', 'STUB_HBA_RC_ai_gateway_db': '1'}, {})):
        result, log, helm_calls = cleanup_case(statefulsets, code, stderr, extra_env, **extra)
        assert not any(c['args'][0] in ('exec', 'delete', 'apply') for c in log) and not [h for h in helm_calls if h.startswith('--')], stderr
    # 신뢰 Secret 형식 오류는 변경 전에 판정: 수용 kid·해시가 남아 있어 고친 뒤 재시도할 수 있다.
    for name, key, stderr in (('ai-gateway-actor-jwks', 'jwks.json', 'JWKS_FORMAT_INVALID'), ('ai-gateway-service-tokens', 'service-tokens.json', 'SERVICE_TOKENS_FORMAT_INVALID')):
        broken = json.loads(json.dumps(full)); broken['secrets'][name][key] = base64.b64encode(b'[not json').decode()
        result, log, helm_calls = cleanup_case(('ai-gateway-db',), 1, stderr, {'AIGW_RESTORE_RUNCONFIG': ''}, secrets=broken)
        assert not any(c['args'][0] in ('exec', 'delete', 'apply') for c in log) and state()['secrets'] == broken['secrets'], stderr
    # 한쪽 실패: 공유 Secret·신뢰 정리 0, 성공 표기 0.
    for statefulsets, code, stderr, extra_env, extra in (
            (('ai-gateway-db', 'ai-gateway-db-restore'), 1, 'DB_REDEPLOY_FAILED:ai-gateway-db-restore', {'STUB_HELM_RC_ai_gateway_db_restore': '1'}, {}),
            (('ai-gateway-db',), 1, 'ACCEPTANCE_ROLE_REMAINS:ai-gateway-db', {'AIGW_RESTORE_RUNCONFIG': ''}, {'sql_outputs': {'ai-gateway-db-0': '1'}}),
            (('ai-gateway-db', 'ai-gateway-db-restore'), 1, 'ACCEPTANCE_LOGIN_STILL_ALLOWED:ai-gateway-db-restore', None, {'probe_outputs': {'ai-gateway-db-restore-0': OPEN}}),
            (('ai-gateway-db',), 1, 'ACCEPTANCE_SESSION_CLEANUP_FAILED:ai-gateway-db', {'AIGW_RESTORE_RUNCONFIG': ''}, {'fail': {'exec:ai-gateway-db-0': 1}})):
        result, log, helm_calls = cleanup_case(statefulsets, code, stderr, extra_env, **extra)
        assert not any(c['args'][:2] == ['delete', 'secret'] for c in log) and not any(c['args'][0] == 'apply' for c in log), stderr
        assert state()['secrets'] == full['secrets'] and 'DELETED' not in result.stdout, stderr
        assert not any(line.endswith('ACCEPTANCE_ROLE_ABSENT') and line.startswith(stderr.split(':')[1]) for line in result.stdout.splitlines())
print(json.dumps({'status': 'PASS', 'boundary': 'LOCAL stub kubectl/aigw-helm/hba_policy only — 실제 자격·클러스터·DB 0', 'count': len(checks)}))
PY
