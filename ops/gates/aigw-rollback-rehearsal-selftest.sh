#!/usr/bin/env bash
# 임시 복사본의 배포 stub·HTTPS handler fixture로 인증/redirect 경계를 검증한다. 모든 실제 전송 차단.
set -euo pipefail
export PYTHONDONTWRITEBYTECODE=1
exec python3 -B - "$@" <<'PY'
import json, os, pathlib, shutil, subprocess, tempfile

root = pathlib.Path.cwd()
A, B, C = 'sha256:' + 'a' * 64, 'sha256:' + 'b' * 64, 'sha256:' + 'c' * 64
LABELS = {A: '1' * 64, B: '1' * 64, C: '2' * 64}
TOKEN = 'registry-read-token-sentinel-value'
registry_mode = {}
checks = []
with tempfile.TemporaryDirectory(prefix='aigw-rollback-test-') as temporary:
    work = pathlib.Path(temporary)
    (work / 'ops/gates').mkdir(parents=True)
    (work / 'ops/deploy').mkdir(parents=True)
    shutil.copyfile(root / 'ops/gates/rollback_rehearsal.py', work / 'ops/gates/rollback_rehearsal.py')
    bin_dir = work / 'bin'; bin_dir.mkdir()
    state_path = work / 'state.json'
    registry_path = work / 'registry.json'
    # 실제 urllib opener의 HTTPError/redirect 처리를 통과시킨다. socket·HTTP(S) 연결·urlopen을 모두 봉쇄한다.
    fixture = r'''
import email.message, http.client, io, json, os, pathlib, runpy, socket, sys, urllib.request, urllib.response
state_path = pathlib.Path(os.environ['STUB_REGISTRY'])
def blocked(*args, **kwargs):
    raise AssertionError('ACTUAL_NETWORK_FORBIDDEN')
socket.create_connection = blocked
socket.socket.connect = blocked
socket.socket.connect_ex = blocked
http.client.HTTPConnection.connect = blocked
http.client.HTTPSConnection.connect = blocked
urllib.request.urlopen = blocked
urllib.request.HTTPHandler.http_open = blocked
def transport(self, request):
    s = json.loads(state_path.read_text())
    auth = request.get_header('Authorization', '')
    phase = 'token' if '/token' in request.full_url else ('bearer' if auth else 'initial')
    s.setdefault('calls', []).append({'url': request.full_url, 'auth': auth.split(' ', 1)[0] if auth else None, 'phase': phase})
    state_path.write_text(json.dumps(s))
    assert request.host in ('harbor.nori.local', 'harbor.nori.local:443'), 'UNAPPROVED_TRANSPORT'
    if s.get('error_phase') == phase:
        raise http.client.BadStatusLine('synthetic-sensitive-response')
    headers = email.message.Message()
    code, body = 200, {}
    if s.get('redirect_phase') == phase:
        code = s.get('redirect_code', 302)
        headers['Location'] = s.get('redirect_url', 'https://example.invalid/stolen')
    elif phase == 'token':
        if s.get('auth_fail'): code = 403
        else: body = s.get('token_body', {'token': 'bearer-x'})
    elif phase == 'initial':
        code = s.get('initial_code', 401)
        headers['WWW-Authenticate'] = s.get('challenge', 'Bearer realm="' + s.get('realm', 'https://harbor.nori.local/token') + '",service="harbor-registry",scope="repository:nori-tc/ai-gateway:pull"')
    elif '/manifests/' in request.full_url:
        digest = request.full_url.rsplit('/', 1)[1]
        body = {'config': {'digest': 'sha256:' + digest[-64:]}}
    else:
        digest = request.full_url.rsplit('/', 1)[1]
        label = '2' * 64 if digest == 'sha256:' + 'c' * 64 else '1' * 64
        body = {'config': {'Labels': {} if s.get('no_label') and digest == 'sha256:' + 'c' * 64 else {'aigw.migrations.sha256': label}}}
    payload = b'invalid-json' if s.get('bad_json_phase') == phase else json.dumps(body).encode()
    response = urllib.response.addinfourl(io.BytesIO(payload), headers, request.full_url, code)
    response.msg = 'fixture'
    return response
urllib.request.HTTPSHandler.https_open = transport
sys.argv = ['ops/gates/rollback_rehearsal.py', *sys.argv[1:]]
runpy.run_path(sys.argv[0], run_name='__main__')
'''
    (work / 'registry_fixture.py').write_text(fixture)
    (work / 'ops/gates/aigw-rollback-rehearsal.sh').write_text('#!/bin/sh\nexec python3 registry_fixture.py "$@"\n')
    stub = r'''#!/usr/bin/env python3
import json, os, sys
p = os.environ['STUB_STATE']; s = json.load(open(p)); a = sys.argv[1:]
s.setdefault('calls', []).append([os.path.basename(sys.argv[0])] + a)
def save(): json.dump(s, open(p, 'w'))
tool = os.path.basename(sys.argv[0])
if tool == 'aigw-helm':
    digest = json.load(open(a[a.index('--image-digests') + 1]))['app']
    if digest in s.get('fail_deploy', []): save(); sys.exit(1)
    s['revision'] += 1; s['history'][str(s['revision'])] = digest; s['deployed'] = digest
elif tool == 'helm' and a[0] == 'status':
    print(json.dumps({'info': {'status': 'deployed'}, 'version': s['revision']}))
elif tool == 'helm' and a[0] == 'rollback':
    if s.get('fail_rollback'): save(); sys.exit(1)
    s['revision'] += 1; s['deployed'] = s['history'][a[2]]; s['history'][str(s['revision'])] = s['deployed']
elif tool == 'kubectl':
    a = a[2:]
    if a[:2] == ['get', 'deployment']:
        print(json.dumps({'spec': {'template': {'spec': {'containers': [{'image': 'harbor.nori.local/nori-tc/ai-gateway@' + s['deployed']}]}}}}))
    elif a[:2] == ['get', 'pods']:
        ready = 'True' if s['deployed'] not in s.get('unready', []) else 'False'
        print(json.dumps({'items': [{'status': {'conditions': [{'type': 'Ready', 'status': ready}], 'containerStatuses': [{'ready': ready == 'True', 'imageID': 'docker-pullable://x@' + s['deployed']}]}}]}))
    elif a[:2] == ['get', 'secret']:
        print(json.dumps({'data': {'password': 'c2VjcmV0LWRiLXBhc3N3b3JkLXNlbnRpbmVs'}}))
    elif a[0] == 'exec':
        s['stdin'] = sys.stdin.read(); versions = s['flyway']; print(versions.pop(0) if len(versions) > 1 else versions[0])
    elif a[0] == 'rollout':
        pass
save()
'''
    for name in ('helm', 'kubectl'):
        (bin_dir / name).write_text(stub); (bin_dir / name).chmod(0o755)
    (work / 'ops/deploy/aigw-helm.sh').write_text('#!/bin/sh\nexec python3 ' + str(bin_dir / 'aigw-helm') + ' "$@"\n')
    (bin_dir / 'aigw-helm').write_text(stub); (bin_dir / 'aigw-helm').chmod(0o755)
    for name, digest in (('a.json', A), ('b.json', B), ('c.json', C)):
        (work / name).write_text(json.dumps({'app': digest}))
    env = dict(os.environ, PATH=str(bin_dir) + os.pathsep + os.environ['PATH'], STUB_STATE=str(state_path), STUB_REGISTRY=str(registry_path),
               AIGW_REGISTRY_URL='https://harbor.nori.local', AIGW_HARBOR_READ_USER='robot$reader', AIGW_HARBOR_READ_TOKEN=TOKEN)
    def reset(**extra):
        s = {'revision': 3, 'history': {'3': A}, 'deployed': A, 'flyway': ['7']}; s.update(extra); state_path.write_text(json.dumps(s))
        registry_path.write_text(json.dumps(registry_mode))
        shutil.rmtree(work / 'evidence', ignore_errors=True)
    def run(args, expected, reason=None, extra_env=None):
        result = subprocess.run(['bash', 'ops/gates/aigw-rollback-rehearsal.sh', *args], cwd=work, env=dict(env, **(extra_env or {})), capture_output=True, text=True)
        assert result.returncode == expected, (args, result.returncode, result.stdout, result.stderr)
        assert TOKEN not in result.stdout + result.stderr and 'c2VjcmV0' not in result.stdout + result.stderr
        evidence = [json.loads(p.read_text()) for p in (work / 'evidence').rglob('rollback-rehearsal-*.json')]
        if reason:
            assert result.stderr.strip() == reason and evidence[-1]['reasonCode'] == reason, (result.stderr, evidence)
        checks.append({'args': args[-4:], 'exit': expected, 'reason': reason})
        return evidence[-1] if evidence else None, json.loads(state_path.read_text())
    base = ['--exec-sha', '0' * 40, '--runconfig', 'main.json']
    reset(); run(base + ['--from-digests', 'a.json'], 2)
    assert not (work / 'evidence').exists()
    # 정상: 이미 A(rev 3) → B(4) → rollback 3(5), imageID = A, V 불변, 비밀 출력 0, 레지스트리 자격은 token 교환에만.
    reset()
    evidence, state = run(base + ['--from-digests', 'a.json', '--to-digests', 'b.json'], 0)
    assert evidence['result'] == 'PASS' and evidence['revisions'] == [3, 4, 5] and state['deployed'] == A
    assert (evidence['flywayLatestBefore'], evidence['flywayLatestAfter']) == ('7', '7')
    assert ['helm', 'rollback', 'ai-gateway', '3', '-n', 'tc', '--wait', '--timeout', '600s'] in state['calls']
    calls = json.loads(registry_path.read_text())['calls']
    assert [c['auth'] for c in calls] == [None, 'Basic', 'Bearer'] * 4
    assert all(c['url'].startswith('https://harbor.nori.local/') for c in calls)
    assert all(TOKEN not in ' '.join(c) for c in state['calls']) and 'c2VjcmV0' not in json.dumps(state['calls'])
    assert set(evidence) <= {'execSha', 'result', 'fromAppDigest', 'toAppDigest', 'migrationsSha256', 'revisions', 'flywayLatestBefore', 'flywayLatestAfter', 'reasonCode'}
    # A 가 배포돼 있지 않으면 먼저 A 배포.
    reset(deployed=B, history={'3': B})
    evidence, state = run(base + ['--from-digests', 'a.json', '--to-digests', 'b.json'], 0)
    assert evidence['revisions'] == [4, 5, 6]
    # NOT_RUN: 같은 이미지·다른 스키마·label 없음 → exit 2, 배포 호출 0.
    for to, reason, extra in (('a.json', 'SECOND_SAME_SCHEMA_IMAGE_MISSING', None), ('c.json', 'SCHEMA_DIFFERS', None), ('c.json', 'LABEL_MISSING', {'NO_LABEL': '1'})):
        registry_mode['no_label'] = bool(extra)
        reset(); evidence, state = run(base + ['--from-digests', 'a.json', '--to-digests', to], 2, reason)
        assert evidence['result'] == 'NOT_RUN' and not any(c[0] in ('aigw-helm', 'helm') for c in state.get('calls', []))
    # FAIL: B 배포 실패·B 미준비·rollback 실패·V 변경 → exit 1·FAIL 증거.
    for extra, reason in (({'fail_deploy': [B]}, 'DEPLOY_FAILED'), ({'unready': [B]}, 'UPGRADE_NOT_READY'), ({'fail_rollback': True}, 'ROLLBACK_FAILED'),
                          ({'flyway': ['7', '8']}, 'SCHEMA_VERSION_CHANGED')):
        reset(**extra); evidence, state = run(base + ['--from-digests', 'a.json', '--to-digests', 'b.json'], 1, reason)
        assert evidence['result'] == 'FAIL'
    reset(); run(base + ['--from-digests', 'a.json', '--to-digests', 'b.json'], 1, 'REGISTRY_READ_FAILED', {'AIGW_HARBOR_READ_TOKEN': ''})
    args = base + ['--from-digests', 'a.json', '--to-digests', 'b.json']
    # 임의 override는 읽기 요청 자체가 없어야 한다. userinfo·port·제어 문자·퍼센트 host·query도 우회 불가.
    for url in ('http://harbor.nori.local', 'https://example.invalid', 'https://harbor.nori.local:444',
                'https://user:synthetic@harbor.nori.local', 'https://harbor.nori.local@example.invalid',
                'https://harbor.nori.local.evil.invalid', 'https://harbor.nori.local/path', 'https://harbor.nori.local?x=1',
                'https://harbor.nori.local#x', 'https://harbor.nori.local\\@example.invalid',
                'https://harbor.nori.local\n', 'https://%68arbor.nori.local', 'https://harbor.nori.local:bad', ''):
        registry_mode.clear(); reset(); _, state = run(args, 1, 'REGISTRY_URL_REJECTED', {'AIGW_REGISTRY_URL': url})
        assert not json.loads(registry_path.read_text()).get('calls') and not state.get('calls')
    for realm in ('http://harbor.nori.local/token', 'https://example.invalid/token', 'https://harbor.nori.local:444/token',
                  'https://user:synthetic@harbor.nori.local/token', 'https://harbor.nori.local/token?x=1',
                  'https://harbor.nori.local/token#x', 'https://harbor.nori.local:bad/token',
                  'https://harbor.nori.local\\@example.invalid/token'):
        registry_mode.clear(); registry_mode['realm'] = realm; reset()
        _, state = run(args, 1, 'REGISTRY_AUTH_REALM_REJECTED')
        assert [c['auth'] for c in json.loads(registry_path.read_text())['calls']] == [None] and not state.get('calls')
    # 초기·Basic·Bearer 응답의 실제 표준 redirect 처리 모두 거부. 같은 origin/상대경로도 재전송 0.
    for phase in ('initial', 'token', 'bearer'):
        for code in (301, 302, 303, 307, 308):
            for target in ('https://example.invalid/stolen', 'http://harbor.nori.local/stolen', '/same-origin'):
                registry_mode.clear(); registry_mode.update(redirect_phase=phase, redirect_code=code, redirect_url=target); reset()
                _, state = run(args, 1, 'REGISTRY_REDIRECT_REJECTED')
                calls = json.loads(registry_path.read_text())['calls']
                assert len(calls) == ('initial', 'token', 'bearer').index(phase) + 1 and not state.get('calls')
    for mode in ({'auth_fail': True}, {'initial_code': 403}, {'challenge': 'Basic realm="https://harbor.nori.local/token"'},
                 {'challenge': 'Bearer service="harbor-registry"'}, {'token_body': []}, {'token_body': {}},
                 {'token_body': {'token': 42}}, {'token_body': {'token': 'bad\r\nheader'}},
                 {'error_phase': 'initial'}, {'error_phase': 'token'}, {'bad_json_phase': 'token'}, {'bad_json_phase': 'bearer'}):
        registry_mode.clear(); registry_mode.update(mode); reset(); run(args, 1, 'REGISTRY_READ_FAILED')
    registry_mode.clear(); registry_mode['token_body'] = {'access_token': 'bearer-x'}; reset(); run(args, 0)
    registry_mode.clear(); registry_mode['realm'] = 'https://harbor.nori.local:443/token'; reset()
    run(args, 0, extra_env={'AIGW_REGISTRY_URL': 'https://harbor.nori.local:443/'})
print(json.dumps({'status': 'PASS', 'boundary': 'LOCAL stub commands + actual urllib handlers; all socket/HTTP(S)/urlopen transmission blocked', 'actualNetworkCalls': 0, 'count': len(checks)}))
PY
