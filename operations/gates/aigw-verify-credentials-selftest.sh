#!/usr/bin/env bash
# aigw-verify-credentials.sh 셀프시험(Plan P15 ④): 임시 복사본 안에서 stub kubectl 로만 실행한다. 실제 클러스터·DB 0 — 운영 접근 PASS 가 아니다.
# Gateway 쪽 신뢰 성분은 Secret ai-gateway-config 의 키(jwks.json·service-tokens.json·hook-token-sha256)이고 그 밖 키(DB 설정·계정·GitLab·SeaweedFS)는 불변이어야 한다.
set -euo pipefail
export PYTHONDONTWRITEBYTECODE=1
exec python3 -B - "$@" <<'PY'
import base64, json, os, pathlib, shutil, subprocess, tempfile, time

root = pathlib.Path.cwd()
checks = []
UNRELATED_KEY = {'kty': 'EC', 'crv': 'P-256', 'kid': 'tc-system-2026', 'x': 'AAAA', 'y': 'BBBB'}
UNRELATED_HASH = 'f' * 64
b64 = lambda raw: base64.b64encode(raw).decode()
# 운영자가 만든 ai-gateway-config 의 그 밖 키(값은 시험 sentinel). 도구는 이 키들을 읽어도 바꾸거나 출력하지 않는다.
OPERATOR_KEYS = {'ai-gateway-db.properties': b64(b'spring.datasource.url=jdbc:postgresql://x/y\n'), 'AI_GATEWAY_DB_USER': b64(b'account-name-sentinel'),
                 'AI_GATEWAY_DB_PASSWORD': b64(b'db-password-sentinel-value-0123456789'), 'token': b64(b'gitlab-token-sentinel-value-0123456789')}
ALLOWED_DELETES = {'ai-gateway-acceptance-signer', 'ai-gateway-acceptance-client', 'ai-gateway-acceptance-fixture-writer', 'ai-gateway-acceptance'}
with tempfile.TemporaryDirectory(prefix='aigw-verify-creds-test-') as temporary:
    work = pathlib.Path(temporary)
    shutil.copytree(root / 'operations', work / 'operations')
    bin_dir = work / 'bin'; bin_dir.mkdir()
    (bin_dir / 'kubectl').write_text('#!/bin/sh\nexec python3 -B ' + str(work / 'operations/gates/selftest_kubectl_stub.py') + ' "$@"\n'); (bin_dir / 'kubectl').chmod(0o755)
    state_path, log_path = work / 'state.json', work / 'calls.jsonl'
    env = dict(os.environ, PATH=str(bin_dir) + os.pathsep + os.environ['PATH'], STUB_STATE=str(state_path), STUB_LOG=str(log_path))

    def reset(deployments=('ai-gateway',), config=True, **extra):
        state = {'secrets': {'ai-gateway-config': dict(OPERATOR_KEYS)} if config else {}, 'present': {'deployment': list(deployments)}, 'fail': {}}
        state.update(extra)
        state_path.write_text(json.dumps(state)); log_path.write_text('')
    state = lambda: json.loads(state_path.read_text())
    calls = lambda: [json.loads(line) for line in log_path.read_text().splitlines()]
    decode = lambda s, name, key: base64.b64decode(s['secrets'][name][key])
    def run(args, expected, stderr=None):
        result = subprocess.run(['bash', 'operations/gates/aigw-verify-credentials.sh', *args], cwd=work, env=env, capture_output=True, text=True)
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
    def operator_keys_unchanged():
        return all(state()['secrets']['ai-gateway-config'].get(k) == v for k, v in OPERATOR_KEYS.items())

    # ── 입력 경계 ──
    reset()
    for bad in (['check', '--stage', 'F5'], ['check', '--stage', 'RESTORE'], ['provision', '--stage', 'x']):
        run(bad, 2, 'STAGE_INVALID')
    run(['check'], 2, 'ARGUMENT_INVALID'); run([], 2, 'ARGUMENT_MISSING')
    out = run(['check', '--stage', 'F1'], 12)
    assert {s for _, s in item_lines(out.stdout)} == {'MISSING'} and 'actor-signer SECRET_ABSENT:ai-gateway-acceptance-signer' in out.stderr
    reset(config=False)
    out = run(['check', '--stage', 'F1'], 12); assert 'actor-signer SECRET_ABSENT:ai-gateway-config' in out.stderr

    # ── provision: ai-gateway-config 부재(운영자 미생성) → exit 2·Secret 변경 0 / 조회 오류 → exit 1 ──
    reset(config=False)
    run(['provision', '--stage', 'F1'], 2, 'CONFIG_SECRET_MISSING')
    assert state()['secrets'] == {} and not any(c['args'][0] in ('apply', 'patch') for c in calls())
    reset(fail={'get-secret:ai-gateway-config': 1})
    run(['provision', '--stage', 'F1'], 1, 'SECRET_QUERY_FAILED:ai-gateway-config'); assert not any(c['args'][0] in ('apply', 'patch') for c in calls())
    reset(fail={'patch-secret:ai-gateway-config': 1})
    run(['provision', '--stage', 'F1'], 1, 'SECRET_PATCH_FAILED:ai-gateway-config'); assert operator_keys_unchanged()

    # ── provision 정상: 수용 Secret apply → ai-gateway-config 신뢰 키만 merge patch → 앱 재기동. 값 출력·명령행·증거 0, 운영자 키 불변 ──
    reset()
    out = run(['provision', '--stage', 'F1'], 0)
    saved = state(); values = secret_values(saved)
    assert len(values) >= 4
    for value in values:
        assert value not in out.stdout + out.stderr + evidence_text(), 'secret printed'
        assert all(value not in ' '.join(c['args']) for c in calls()), 'secret on command line'
    log = calls()
    order = [c['args'][0] + ':' + c['args'][1] for c in log if c['args'][0] in ('apply', 'patch', 'rollout')]
    assert order[:4] == ['apply:-f', 'apply:-f', 'patch:secret', 'rollout:restart'], order
    patches = [json.loads(c['stdin']) for c in log if c['args'][0] == 'patch']
    assert len(patches) == 1 and set(patches[0]['data']) == {'jwks.json', 'service-tokens.json', 'hook-token-sha256'}
    assert [c['args'] for c in log if c['args'][0] == 'patch'][0] == ['patch', 'secret', 'ai-gateway-config', '--type', 'merge', '--patch-file', '/dev/stdin']
    assert not any(c['args'][0] == 'apply' and json.loads(c['stdin'])['metadata']['name'] == 'ai-gateway-config' for c in log), 'config Secret 는 apply 로 덮지 않음'
    assert operator_keys_unchanged() and 'db-password-sentinel' not in out.stdout + out.stderr + evidence_text()
    assert ['rollout', 'restart', 'deployment/ai-gateway'] in [c['args'] for c in log]
    out = run(['check', '--stage', 'F1'], 0)
    assert {s for _, s in item_lines(out.stdout)} == {'MATCH'} and out.stderr == ''
    before = state()['secrets']; run(['provision', '--stage', 'F1'], 0); assert state()['secrets'] == before, '두 번째 provision 은 덮어쓰지 않음'
    out = run(['check', '--stage', 'F3'], 12); assert ['fixture-writer', 'MISSING'] in item_lines(out.stdout)

    # ── check 출력 계약: 항목은 MATCH/MISSING 뿐, 사유는 stderr 고정 코드, 조회 오류는 exit 1(항목 출력 0) ──
    def tampered(mutate):
        copy = json.loads(json.dumps(saved)); mutate(copy); state_path.write_text(json.dumps(copy))
    tampered(lambda s: s['secrets']['ai-gateway-config'].update({'hook-token-sha256': b64(b'0' * 64)}))
    out = run(['check', '--stage', 'F1'], 12); assert ['hook-token', 'MISSING'] in item_lines(out.stdout) and 'hook-token HASH_MISMATCH' in out.stderr
    tampered(lambda s: s['secrets']['ai-gateway-config'].pop('hook-token-sha256'))
    out = run(['check', '--stage', 'F1'], 12); assert 'hook-token FORMAT_INVALID' in out.stderr
    def non_test_kid(s):
        signer = json.loads(decode(s, 'ai-gateway-acceptance-signer', 'signer-private.jwk')); jwks = json.loads(decode(s, 'ai-gateway-config', 'jwks.json'))
        for key in jwks['keys']:
            key['kid'] = 'production-1'
        signer['kid'] = 'production-1'
        s['secrets']['ai-gateway-acceptance-signer']['signer-private.jwk'] = b64(json.dumps(signer).encode())
        s['secrets']['ai-gateway-config']['jwks.json'] = b64(json.dumps(jwks).encode())
    tampered(non_test_kid)
    out = run(['check', '--stage', 'F1'], 12)
    assert ['actor-signer', 'MISSING'] in item_lines(out.stdout) and 'actor-signer KID_NOT_TEST_PREFIX' in out.stderr
    assert all(s in ('MATCH', 'MISSING') for _, s in item_lines(out.stdout)) and 'KID' not in evidence_text().split('"items"')[-1].split('"missingReasons"')[0]
    def private_in_jwks(s):
        jwks = json.loads(decode(s, 'ai-gateway-config', 'jwks.json')); jwks['keys'][0]['d'] = 'x'
        s['secrets']['ai-gateway-config']['jwks.json'] = b64(json.dumps(jwks).encode())
    tampered(private_in_jwks); assert 'actor-signer JWKS_HAS_PRIVATE_MEMBER' in run(['check', '--stage', 'F1'], 12).stderr
    tampered(lambda s: s.update(fail={'get-secret:ai-gateway-acceptance-client': 1}))
    out = run(['check', '--stage', 'F1'], 1, 'SECRET_QUERY_FAILED:ai-gateway-acceptance-client'); assert out.stdout == ''

    # ── rotate: 이전 kid·해시 제거, 새 값 MATCH, 운영자 키 불변 ──
    state_path.write_text(json.dumps(saved))
    old_kid = json.loads(decode(saved, 'ai-gateway-acceptance-signer', 'signer-private.jwk'))['kid']
    old_tokens = json.loads(decode(saved, 'ai-gateway-config', 'service-tokens.json'))
    time.sleep(1.1)
    run(['rotate', '--stage', 'F1'], 0)
    after = state()
    kids = [k['kid'] for k in json.loads(decode(after, 'ai-gateway-config', 'jwks.json'))['keys']]
    assert old_kid not in kids and len(kids) == 1
    assert not set(old_tokens) & set(json.loads(decode(after, 'ai-gateway-config', 'service-tokens.json')))
    assert operator_keys_unchanged()
    run(['check', '--stage', 'F1'], 0)
    partial = json.loads(json.dumps(saved)); del partial['secrets']['ai-gateway-acceptance-client']; state_path.write_text(json.dumps(partial))
    run(['provision', '--stage', 'F1'], 2, 'PROVISION_PARTIAL_STATE')
    reset(); run(['rotate', '--stage', 'F1'], 2, 'ROTATE_REQUIRES_EXISTING')

    # ── cleanup ──
    def with_unrelated(s):
        config = s['secrets']['ai-gateway-config']
        jwks = json.loads(base64.b64decode(config['jwks.json'])); jwks['keys'].append(UNRELATED_KEY)
        tokens = json.loads(base64.b64decode(config['service-tokens.json'])); tokens[UNRELATED_HASH] = 'TC'
        config['jwks.json'] = b64(json.dumps(jwks).encode()); config['service-tokens.json'] = b64(json.dumps(tokens).encode())
        s['secrets']['ai-gateway-acceptance-fixture-writer'] = {'access-key': b64(b'writer-access-sentinel'), 'secret-key': b64(b'writer-secret-sentinel')}
        s['secrets']['unrelated-app-secret'] = {'k': b64(b'keep')}
        return s
    full = with_unrelated(json.loads(json.dumps(saved)))
    def cleanup_case(expected, stderr=None, secrets=None, **extra):
        reset(secrets=json.loads(json.dumps((secrets or full)['secrets'])), **extra)
        result = run(['cleanup'], expected, stderr)
        return result, calls()
    deletes = lambda log: [c['args'][2] for c in log if c['args'][0] == 'delete']
    result, log = cleanup_case(0)
    first = lambda pred: next(i for i, c in enumerate(log) if pred(c))
    job_stop = first(lambda c: c['args'][:2] == ['delete', 'job'])
    secret_delete = first(lambda c: c['args'][:2] == ['delete', 'secret'])
    patch = first(lambda c: c['args'][0] == 'patch')
    assert job_stop < secret_delete < patch
    assert set(deletes(log)) <= ALLOWED_DELETES and 'ai-gateway-acceptance' in deletes(log)
    assert 'ai-gateway-config' not in deletes(log) and not any(c['args'][0] == 'apply' for c in log)
    final = state()['secrets']
    assert not {'ai-gateway-acceptance-signer', 'ai-gateway-acceptance-client', 'ai-gateway-acceptance-fixture-writer'} & set(final)
    config = final['ai-gateway-config']
    assert json.loads(base64.b64decode(config['jwks.json']))['keys'] == [UNRELATED_KEY], '시험 공개키 잔류 0·무관 키 보존'
    assert json.loads(base64.b64decode(config['service-tokens.json'])) == {UNRELATED_HASH: 'TC'}, '수용 해시 잔류 0·시스템 토큰 보존'
    assert 'hook-token-sha256' not in config and all(config.get(k) == v for k, v in OPERATOR_KEYS.items())
    assert 'unrelated-app-secret' in final and 'ai-gateway RESTART_OR_UNINSTALL_REQUIRED' in result.stdout
    assert 'writer-secret-sentinel' not in result.stdout + result.stderr and 'db-password-sentinel' not in result.stdout + result.stderr
    # 2회 반복(이미 정리됨)도 성공, 남은 항목 불변·patch 0.
    snapshot = state()['secrets']; log_path.write_text(''); run(['cleanup'], 0); assert state()['secrets'] == snapshot and not any(c['args'][0] == 'patch' for c in calls())
    # 시험 키만 있던 JWKS·수용 해시만 있던 토큰 키는 빈 집합으로 남긴다(키 투영이 깨지지 않게 키 자체는 유지).
    result, log = cleanup_case(0, secrets=saved)
    config = state()['secrets']['ai-gateway-config']
    assert json.loads(base64.b64decode(config['jwks.json']))['keys'] == [] and json.loads(base64.b64decode(config['service-tokens.json'])) == {}
    # ai-gateway-config 가 없어도 수용 Secret 정리는 수행한다(patch 0).
    result, log = cleanup_case(0, secrets={'secrets': {k: v for k, v in full['secrets'].items() if k != 'ai-gateway-config'}})
    assert not any(c['args'][0] == 'patch' for c in log) and 'ai-gateway-acceptance-signer' not in state()['secrets']
    # 부작용 전 실패: Secret 조회 오류·신뢰 키 형식 오류 → delete/patch 0, 수용 kid·해시가 남아 재시도 가능.
    result, log = cleanup_case(1, 'SECRET_QUERY_FAILED:ai-gateway-config', fail={'get-secret:ai-gateway-config': 1})
    assert not any(c['args'][0] in ('delete', 'patch', 'apply') for c in log)
    for key, stderr in (('jwks.json', 'JWKS_FORMAT_INVALID'), ('service-tokens.json', 'SERVICE_TOKENS_FORMAT_INVALID')):
        broken = json.loads(json.dumps(full)); broken['secrets']['ai-gateway-config'][key] = b64(b'[not json')
        result, log = cleanup_case(1, stderr, secrets=broken)
        assert not any(c['args'][0] in ('delete', 'patch', 'apply') for c in log) and state()['secrets'] == broken['secrets'], stderr
    # 수용 Secret 삭제 실패: Gateway 쪽 신뢰 정리 0.
    result, log = cleanup_case(1, 'SECRET_DELETE_FAILED:ai-gateway-acceptance-client', fail={'delete-secret:ai-gateway-acceptance-client': 1})
    assert not any(c['args'][0] == 'patch' for c in log) and state()['secrets']['ai-gateway-config'] == full['secrets']['ai-gateway-config']
print(json.dumps({'status': 'PASS', 'boundary': 'LOCAL stub kubectl only — 실제 자격·클러스터·DB 0', 'count': len(checks)}))
PY
