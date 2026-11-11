"""검증 수용 자격(Plan §11.7·P15) provision/check/rotate/cleanup. 값은 이 프로세스 메모리와 kubectl stdin 으로만 다니며 출력·증거는 이름·상태·kid 뿐이다."""
import base64
import datetime
import hashlib
import json
import os
import pathlib
import re
import secrets
import subprocess
import sys
import tempfile
import uuid

NAMESPACE = 'tc'
JWKS, TOKENS, HOOKS = 'ai-gateway-actor-jwks', 'ai-gateway-service-tokens', 'ai-gateway-test-hooks'
SIGNER, CLIENT, DB_RO, WRITER = 'ai-gateway-acceptance-signer', 'ai-gateway-acceptance-client', 'ai-gateway-db-acceptance-ro', 'ai-gateway-acceptance-fixture-writer'
DB_RELEASES = {'ai-gateway-db': 'AIGW_RUNCONFIG', 'ai-gateway-db-restore': 'AIGW_RESTORE_RUNCONFIG'}
APP_RELEASES = ('ai-gateway', 'ai-gateway-restore')
STAGES = ('F1', 'F2', 'F3', 'F4')


class Failure(Exception):
    def __init__(self, code, exit_code):
        super().__init__(code)
        self.code, self.exit_code = code, exit_code


def kubectl(*args, stdin=None):
    """결과의 stdout 은 호출자 메모리에만 남고 stderr(서버 원문)는 버린다."""
    return subprocess.run(['kubectl', '-n', NAMESPACE, *args], input=stdin, capture_output=True)


def get_secret(name):
    """반환: 'MISSING' | 'QUERY_FAILED' | {키: 바이트}. 조회 오류를 부재로 취급하지 않는다."""
    result = kubectl('get', 'secret', name, '--ignore-not-found', '-o', 'json')
    if result.returncode != 0:
        return 'QUERY_FAILED'
    if not result.stdout.strip():
        return 'MISSING'
    try:
        data = json.loads(result.stdout).get('data') or {}
        return {key: base64.b64decode(value) for key, value in data.items()}
    except (ValueError, AttributeError, TypeError):
        return 'QUERY_FAILED'


def apply_secret(name, data):
    manifest = {'apiVersion': 'v1', 'kind': 'Secret', 'type': 'Opaque', 'metadata': {'name': name, 'namespace': NAMESPACE},
                'data': {key: base64.b64encode(value).decode('ascii') for key, value in data.items()}}
    if kubectl('apply', '-f', '-', stdin=json.dumps(manifest).encode()).returncode != 0:
        raise Failure('SECRET_APPLY_FAILED:' + name, 1)


def present(kind, name):
    result = kubectl('get', kind, name, '--ignore-not-found', '-o', 'name')
    if result.returncode != 0:
        raise Failure('RELEASE_QUERY_FAILED:' + name, 1)
    return bool(result.stdout.strip())


def b64url(raw):
    return base64.urlsafe_b64encode(raw).rstrip(b'=').decode('ascii')


def ec_key(kid):
    """openssl 로 P-256 키를 만들어 JWK 로 바꾼다(PEM·성분은 파이프와 메모리만)."""
    pem = subprocess.run(['openssl', 'ecparam', '-name', 'prime256v1', '-genkey', '-noout'], capture_output=True)
    text = subprocess.run(['openssl', 'ec', '-text', '-noout'], input=pem.stdout, capture_output=True)
    if pem.returncode != 0 or text.returncode != 0:
        raise Failure('KEY_GENERATION_FAILED', 1)
    blocks, current = {}, None
    for line in text.stdout.decode('ascii', 'replace').splitlines():
        if line.rstrip() in ('priv:', 'pub:'):
            current = line.strip()[:-1]; blocks[current] = ''
        elif current and line.startswith(' '):
            blocks[current] += line.strip().replace(':', '')
        else:
            current = None
    private, public = bytes.fromhex(blocks.get('priv', '')), bytes.fromhex(blocks.get('pub', ''))
    private = private[-32:].rjust(32, b'\0')
    if len(public) != 65 or public[0] != 4:
        raise Failure('KEY_GENERATION_FAILED', 1)
    return {'kty': 'EC', 'crv': 'P-256', 'kid': kid, 'x': b64url(public[1:33]), 'y': b64url(public[33:]), 'd': b64url(private)}


def sha(value):
    return hashlib.sha256(value).hexdigest()


def required(stage):
    names = [JWKS, TOKENS, HOOKS, SIGNER, CLIENT, DB_RO]
    return names + [WRITER] if stage in ('F3', 'F4') else names


def evaluate(stage):
    """항목별 MATCH/MISSING 과 MISSING 의 고정 사유. Secret 조회 오류는 항목 상태가 아니라 실패(exit 1)다.
    자격 check 는 Secret 대조일 뿐 실제 DB 접근 PASS 가 아니다."""
    secrets_ = {name: get_secret(name) for name in required(stage)}
    failed = sorted(name for name, value in secrets_.items() if value == 'QUERY_FAILED')
    if failed:
        raise Failure('SECRET_QUERY_FAILED:' + ','.join(failed), 1)
    status, reasons = {}, {}
    def miss(item, reason):
        status[item] = 'MISSING'; reasons[item] = reason
    def absent(*names):
        return next((name for name in names if secrets_[name] == 'MISSING'), None)
    def load(name, key):
        return json.loads(secrets_[name][key])
    gone = absent(JWKS, SIGNER)
    if gone:
        miss('actor-signer', 'SECRET_ABSENT:' + gone)
    else:
        try:
            private, keys = load(SIGNER, 'signer-private.jwk'), load(JWKS, 'jwks.json')['keys']
            public = [k for k in keys if k.get('kid') == private.get('kid')]
            if not str(private.get('kid', '')).startswith('test-'):
                miss('actor-signer', 'KID_NOT_TEST_PREFIX')
            elif any('d' in k for k in keys):
                miss('actor-signer', 'JWKS_HAS_PRIVATE_MEMBER')
            elif 'd' not in private or len(public) != 1 or (public[0].get('x'), public[0].get('y'), public[0].get('crv')) != (private.get('x'), private.get('y'), private.get('crv')):
                miss('actor-signer', 'PUBLIC_KEY_MISMATCH')
            else:
                status['actor-signer'] = 'MATCH'
        except (KeyError, ValueError, TypeError, AttributeError):
            miss('actor-signer', 'FORMAT_INVALID')
    gone = absent(TOKENS, CLIENT)
    if gone:
        miss('service-token', 'SECRET_ABSENT:' + gone)
    else:
        try:
            ok = load(TOKENS, 'service-tokens.json').get(sha(secrets_[CLIENT]['service-token'].strip())) == 'TC'
            status['service-token'] = 'MATCH' if ok else None
            if not ok:
                miss('service-token', 'HASH_MISMATCH')
        except (KeyError, ValueError, TypeError, AttributeError):
            miss('service-token', 'FORMAT_INVALID')
    gone = absent(HOOKS, CLIENT)
    if gone:
        miss('hook-token', 'SECRET_ABSENT:' + gone)
    else:
        try:
            if secrets_[HOOKS]['hook-token-sha256'].decode().strip() == sha(secrets_[CLIENT]['hook-token'].strip()):
                status['hook-token'] = 'MATCH'
            else:
                miss('hook-token', 'HASH_MISMATCH')
        except (KeyError, UnicodeDecodeError):
            miss('hook-token', 'FORMAT_INVALID')
    if secrets_[DB_RO] == 'MISSING' or not secrets_[DB_RO].get('password', b'').strip():
        miss('db-acceptance-ro', 'SECRET_ABSENT:' + DB_RO)
    else:
        status['db-acceptance-ro'] = 'MATCH'
    if stage in ('F3', 'F4'):
        if secrets_[WRITER] == 'MISSING' or not {'access-key', 'secret-key'} <= set(secrets_[WRITER]):
            miss('fixture-writer', 'SECRET_ABSENT:' + WRITER)
        else:
            status['fixture-writer'] = 'MATCH'
    return status, reasons, secrets_


def record(stage, command, items, reasons):
    stamp = datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%S%fZ') + '-' + uuid.uuid4().hex[:8]
    out = pathlib.Path('evidence') / stage / 'gates'
    out.mkdir(parents=True, exist_ok=True)
    (out / f'verify-credentials-{command}-{stamp}.json').write_text(json.dumps({'stage': stage, 'command': command, 'items': items, 'missingReasons': reasons}, indent=2) + '\n')


def check(stage):
    status, reasons, _ = evaluate(stage)
    for item, state in status.items():
        print(item, state)
    for item, reason in reasons.items():
        print(item, reason, file=sys.stderr)
    record(stage, 'check', status, reasons)
    return 0 if all(state == 'MATCH' for state in status.values()) else 12


def db_inputs(releases):
    """DB 재배포 입력을 부작용 전에 모두 확인한다. 재배포(aigw-helm.sh deploy)가 첫 단계에서 쓰는 같은 검사를 클러스터 변경 없이 먼저 돌린다:
    env 존재(없음 exit 2) → exec SHA 형식 → image-digests(app_render digests) → release 별 runconfig(runconfig.py deploy 모드·exec SHA 일치, 무효 exit 2)
    → 런타임 자격 check(읽기 전용, 미준비 exit 1). exec chart 읽기(git)는 재배포 때만 한다."""
    for name in ('AIGW_EXEC_CHART_SHA', 'AIGW_IMAGE_DIGESTS'):
        if not os.environ.get(name):
            raise Failure('INPUT_MISSING:' + name, 2)
    for release in releases:
        if not os.environ.get(DB_RELEASES[release]):
            raise Failure('INPUT_MISSING:' + DB_RELEASES[release], 2)
    exec_sha = os.environ['AIGW_EXEC_CHART_SHA']
    if not re.fullmatch(r'[0-9a-f]{40}', exec_sha):
        raise Failure('INPUT_INVALID:AIGW_EXEC_CHART_SHA', 2)
    with tempfile.TemporaryDirectory(prefix='aigw-inputs-') as directory:
        if subprocess.run(['python3', '-B', 'operations/deploy/app_render.py', 'digests', os.environ['AIGW_IMAGE_DIGESTS'], os.path.join(directory, 'digests.json')],
                          capture_output=True).returncode != 0:
            raise Failure('INPUT_INVALID:AIGW_IMAGE_DIGESTS', 2)
        for release in releases:
            runconfig = os.environ[DB_RELEASES[release]]
            if subprocess.run(['python3', '-B', 'operations/deploy/runconfig.py', runconfig, release, 'deploy', os.path.join(directory, 'values.json'), exec_sha],
                              capture_output=True).returncode != 0:
                raise Failure('INPUT_INVALID:' + DB_RELEASES[release], 2)
            stage = 'RESTORE' if release.endswith('-restore') else 'F1'
            if subprocess.run(['bash', 'operations/gates/aigw-runtime-credentials.sh', 'check', '--stage', stage], capture_output=True,
                              env=dict(os.environ, AIGW_RUNCONFIG=runconfig)).returncode != 0:
                raise Failure('DB_RUNTIME_CREDENTIALS_NOT_READY:' + release, 1)


def hba_live(release):
    """P06 정본 검사 재사용: overlay 경로·SHA·기동 인자 결과 hba_file·pg_hba_file_rules 앞 4 규칙(정본 SHA·오류 0)."""
    with tempfile.TemporaryDirectory(prefix='aigw-hba-') as directory:
        if subprocess.run(['python3', '-B', 'operations/deploy/hba_policy.py', 'live', release, os.path.join(directory, 'hba.json')], capture_output=True).returncode != 0:
            raise Failure('HBA_LIVE_CHECK_FAILED:' + release, 1)
    print(release, 'HBA_LIVE_PASS')


def db_releases():
    """존재하는 DB release(StatefulSet). 조회 오류는 부재가 아니라 실패다."""
    return [release for release in DB_RELEASES if present('statefulset', release)]


def redeploy_db(release, role_state):
    command = ['bash', 'operations/deploy/aigw-helm.sh', '--release', release, '--mode', 'deploy', '--exec-sha', os.environ['AIGW_EXEC_CHART_SHA'],
               '--runconfig', os.environ[DB_RELEASES[release]], '--image-digests', os.environ['AIGW_IMAGE_DIGESTS'], '--acceptance-role', role_state]
    # aigw-helm.sh deploy 는 bootstrap hook Job complete·P06 HBA live 검사까지 확인한 뒤에만 0 이다.
    if subprocess.run(command, capture_output=True).returncode != 0:
        raise Failure('DB_REDEPLOY_FAILED:' + release, 1)
    print(release, 'ACCEPTANCE_ROLE_' + role_state.upper())


def admin_psql(release, sql):
    """P06 admin 경로(hba_policy.deployed 와 같음): Pod 안 superuser 자격 env + TCP. SQL 은 stdin 으로만."""
    return kubectl('exec', '-i', release + '-0', '--', 'sh', '-c',
                   'PGPASSWORD="$POSTGRES_PASSWORD" psql -X -q -w -A -t -h 127.0.0.1 -U postgres -d ai_gateway -v ON_ERROR_STOP=1 -f -', stdin=sql.encode())


PROBE = ('IFS= read -r PGPASSWORD; export PGPASSWORD; '
         'probe() { out=$(psql -X -w -U aigw_acceptance_ro "$@" -c "SELECT 1" 2>&1); rc=$?; '
         'if [ $rc -eq 0 ]; then echo OK; elif printf "%s" "$out" | grep -q "pg_hba.conf rejects connection"; then echo HBA_REJECT; else echo FAILED; fi; }; '
         'echo "select $(probe -h 127.0.0.1 -d ai_gateway)"; '
         'echo "tcp-postgres $(probe -h 127.0.0.1 -d postgres)"; echo "tcp-template1 $(probe -h 127.0.0.1 -d template1)"; '
         'echo "unix-postgres $(probe -d postgres)"; echo "unix-template1 $(probe -d template1)"')


def access_probe(release, password):
    """수용 역할 실제 접속: ai_gateway SELECT 와 postgres/template1 의 TCP·Unix socket. 비밀번호는 exec stdin 으로만, 출력은 고정 분류만."""
    result = kubectl('exec', '-i', release + '-0', '--', 'sh', '-c', PROBE, stdin=password.strip() + b'\n')
    if result.returncode != 0:
        raise Failure('ACCEPTANCE_PROBE_FAILED:' + release, 1)
    outcome = dict(line.split(' ', 1) for line in result.stdout.decode('ascii', 'replace').split('\n') if ' ' in line)
    if set(outcome) != {'select', 'tcp-postgres', 'tcp-template1', 'unix-postgres', 'unix-template1'}:
        raise Failure('ACCEPTANCE_PROBE_FAILED:' + release, 1)
    return outcome


def material(stage_secrets, replace):
    """새 자격 한 벌. replace 가 있으면 그 이전 kid·해시를 Gateway 쪽에서 뺀다(rotate)."""
    signer = ec_key('test-' + datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ'))
    service, hook, password = (secrets.token_urlsafe(32).encode() for _ in range(3))
    try:
        jwks = json.loads(stage_secrets[JWKS]['jwks.json']) if isinstance(stage_secrets[JWKS], dict) else {'keys': []}
        if not isinstance(jwks, dict) or not isinstance(jwks.get('keys'), list):
            raise TypeError
        if replace:
            jwks['keys'] = [k for k in jwks['keys'] if k.get('kid') != replace['kid']]
    except (KeyError, ValueError, TypeError, AttributeError):
        raise Failure('JWKS_FORMAT_INVALID', 1)
    try:
        tokens = json.loads(stage_secrets[TOKENS]['service-tokens.json']) if isinstance(stage_secrets[TOKENS], dict) else {}
        if not isinstance(tokens, dict):
            raise TypeError
        if replace:
            tokens.pop(replace['service'], None)
    except (KeyError, ValueError, TypeError):
        raise Failure('SERVICE_TOKENS_FORMAT_INVALID', 1)
    jwks['keys'].append({k: v for k, v in signer.items() if k != 'd'})
    tokens[sha(service)] = 'TC'
    return {
        JWKS: {'jwks.json': json.dumps(jwks).encode()}, TOKENS: {'service-tokens.json': json.dumps(tokens).encode()},
        HOOKS: {'hook-token-sha256': sha(hook).encode()}, SIGNER: {'signer-private.jwk': json.dumps(signer).encode()},
        CLIENT: {'service-token': service, 'hook-token': hook}, DB_RO: {'password': password}}


def install(stage, current, replace):
    # 부작용 전 판정: release 조회·입력·P06 HBA live. 하나라도 실패하면 Secret 을 바꾸지 않는다.
    releases = db_releases()
    if not releases:
        raise Failure('DB_RELEASE_MISSING', 2)
    db_inputs(releases)
    for release in releases:
        hba_live(release)
    bundle = material(current, replace)
    # 수용 쪽 Secret 을 먼저, Gateway 쪽 공개 성분·해시를 나중에 둔다. 이후 DB 역할 비밀번호를 bootstrap 으로 반영하고 실제 접속을 확인한 뒤 앱을 재기동한다.
    for name in (SIGNER, CLIENT, DB_RO, HOOKS, JWKS, TOKENS):
        apply_secret(name, bundle[name])
        print(name, 'APPLIED')
    for release in releases:
        redeploy_db(release, 'enabled')
        outcome = access_probe(release, bundle[DB_RO]['password'])
        expected = {'select': 'OK', 'tcp-postgres': 'HBA_REJECT', 'tcp-template1': 'HBA_REJECT', 'unix-postgres': 'HBA_REJECT', 'unix-template1': 'HBA_REJECT'}
        if outcome != expected:
            raise Failure('ACCEPTANCE_ACCESS_BOUNDARY_FAILED:' + release, 1)
        print(release, 'ACCEPTANCE_SELECT_OK_OTHER_DB_HBA_REJECTED')
    restart_apps()
    return check(stage)


def provision(stage):
    _, _, current = evaluate(stage)
    states = {name: 'ABSENT' if current[name] == 'MISSING' else 'PRESENT' for name in (SIGNER, CLIENT, DB_RO)}
    if set(states.values()) == {'PRESENT'}:
        # 이미 있으면 덮어쓰지 않는다. 불일치면 rotate 로만 바꾼다.
        return check(stage)
    if 'PRESENT' in states.values():
        raise Failure('PROVISION_PARTIAL_STATE', 2)
    return install(stage, current, None)


def rotate(stage):
    _, _, current = evaluate(stage)
    if not all(isinstance(current[name], dict) for name in (SIGNER, CLIENT)):
        raise Failure('ROTATE_REQUIRES_EXISTING', 2)
    try:
        replace = {'kid': json.loads(current[SIGNER]['signer-private.jwk'])['kid'], 'service': sha(current[CLIENT]['service-token'].strip())}
    except (KeyError, ValueError):
        raise Failure('ROTATE_REQUIRES_EXISTING', 2)
    return install(stage, current, replace)


def cleanup():
    """Plan P15 승인 A 정리 순서. 존재하는 DB release 각각 결과가 있어야 완료이고, 한쪽이라도 실패하면 공유 Secret 을 지우지 않는다.
    ① 부작용 전: release 조회·입력(db_inputs)·Secret 조회·형식 검사와 신뢰 제거 결과 계산·P06 HBA live ② 수용 Job 종료(새 수용 연결 차단) ③ 각 DB NOLOGIN·그 역할 세션 종료
    ④ 각 DB 비활성 bootstrap 재배포·역할 0·기존 자격 재접속 거부 ⑤ 공유 수용 Secret 삭제 ⑥ Gateway 쪽 test- 공개키·수용 토큰 해시·hook 해시 제거."""
    releases = db_releases()
    db_inputs(releases)
    current = {name: get_secret(name) for name in (SIGNER, CLIENT, DB_RO, WRITER, JWKS, TOKENS, HOOKS)}
    failed = sorted(name for name, value in current.items() if value == 'QUERY_FAILED')
    if failed:
        raise Failure('SECRET_QUERY_FAILED:' + ','.join(failed), 1)
    acceptance_hash = sha(current[CLIENT]['service-token'].strip()) if isinstance(current[CLIENT], dict) and 'service-token' in current[CLIENT] else None
    acceptance_kid = None
    if isinstance(current[SIGNER], dict):
        try:
            acceptance_kid = json.loads(current[SIGNER]['signer-private.jwk'])['kid']
        except (KeyError, ValueError):
            raise Failure('SIGNER_FORMAT_INVALID', 1)
    # 신뢰 제거 결과(남길 공개키·토큰)를 변경 전에 계산한다: 형식 오류로 중간에 멈추면 재시도에 필요한 kid·해시를 이미 지운 뒤가 되기 때문이다.
    jwks_after = tokens_after = None
    if isinstance(current[JWKS], dict):
        try:
            jwks = json.loads(current[JWKS]['jwks.json'])
            kept = [k for k in jwks['keys'] if not str(k.get('kid', '')).startswith('test-') and k.get('kid') != acceptance_kid]
        except (KeyError, ValueError, TypeError, AttributeError):
            raise Failure('JWKS_FORMAT_INVALID', 1)
        if len(kept) != len(jwks['keys']):
            jwks_after = dict(jwks, keys=kept)
    if isinstance(current[TOKENS], dict) and acceptance_hash:
        try:
            tokens = json.loads(current[TOKENS]['service-tokens.json'])
            removed = tokens.pop(acceptance_hash, None) is not None
        except (KeyError, ValueError, TypeError, AttributeError):
            raise Failure('SERVICE_TOKENS_FORMAT_INVALID', 1)
        if removed:
            tokens_after = tokens
    for release in releases:
        hba_live(release)
    for app in APP_RELEASES:
        # helm test 수용 Job(<앱 release>-acceptance)만 끝낸다. 다른 workload 는 건드리지 않는다.
        if kubectl('delete', 'job', app + '-acceptance', '--ignore-not-found', '--wait=true').returncode != 0:
            raise Failure('ACCEPTANCE_JOB_STOP_FAILED:' + app, 1)
    for release in releases:
        if admin_psql(release, "SELECT 'ALTER ROLE aigw_acceptance_ro NOLOGIN' WHERE EXISTS (SELECT FROM pg_roles WHERE rolname = 'aigw_acceptance_ro') \\gexec\n"
                               "SELECT count(pg_terminate_backend(pid)) FROM pg_stat_activity WHERE usename = 'aigw_acceptance_ro' AND pid <> pg_backend_pid();\n").returncode != 0:
            raise Failure('ACCEPTANCE_SESSION_CLEANUP_FAILED:' + release, 1)
        print(release, 'ACCEPTANCE_NOLOGIN_SESSIONS_TERMINATED')
    for release in releases:
        redeploy_db(release, 'disabled')
        roles = admin_psql(release, "SELECT count(*) FROM pg_roles WHERE rolname = 'aigw_acceptance_ro';\n")
        if roles.returncode != 0 or roles.stdout.strip() != b'0':
            raise Failure('ACCEPTANCE_ROLE_REMAINS:' + release, 1)
        if isinstance(current[DB_RO], dict) and current[DB_RO].get('password', b'').strip():
            # 기존 자격의 ai_gateway 재접속은 실패해야 한다(역할 부재). 다른 DB 는 계속 HBA 거부.
            outcome = access_probe(release, current[DB_RO]['password'])
            if outcome['select'] == 'OK' or any(outcome[key] == 'OK' for key in outcome):
                raise Failure('ACCEPTANCE_LOGIN_STILL_ALLOWED:' + release, 1)
        print(release, 'ACCEPTANCE_ROLE_ABSENT')
    for name in (SIGNER, CLIENT, DB_RO, WRITER):
        if kubectl('delete', 'secret', name, '--ignore-not-found').returncode != 0:
            raise Failure('SECRET_DELETE_FAILED:' + name, 1)
        print(name, 'DELETED')
    # Gateway 쪽 신뢰 제거: test- kid 공개키, 수용 토큰 해시만 뺀다(그 밖 키·시스템 토큰 보존). hook 해시 Secret 은 수용 전용이라 지운다.
    if jwks_after is not None:
        if jwks_after['keys']:
            apply_secret(JWKS, {'jwks.json': json.dumps(jwks_after).encode()})
        elif kubectl('delete', 'secret', JWKS, '--ignore-not-found').returncode != 0:
            raise Failure('SECRET_DELETE_FAILED:' + JWKS, 1)
        print(JWKS, 'TEST_KEYS_REMOVED')
    if tokens_after is not None:
        if tokens_after:
            apply_secret(TOKENS, {'service-tokens.json': json.dumps(tokens_after).encode()})
        elif kubectl('delete', 'secret', TOKENS, '--ignore-not-found').returncode != 0:
            raise Failure('SECRET_DELETE_FAILED:' + TOKENS, 1)
        print(TOKENS, 'ACCEPTANCE_HASH_REMOVED')
    if kubectl('delete', 'secret', HOOKS, '--ignore-not-found').returncode != 0:
        raise Failure('SECRET_DELETE_FAILED:' + HOOKS, 1)
    print(HOOKS, 'DELETED')
    # 실행 중 Gateway 는 재기동해야 제거된 신뢰가 메모리에서도 빠진다. 수용 종료 뒤 앱 release 정리(재배포·uninstall)는 runbook 절차다.
    for app in APP_RELEASES:
        if present('deployment', app):
            print(app, 'RESTART_OR_UNINSTALL_REQUIRED')
    return 0


def restart_apps():
    for app in APP_RELEASES:
        if present('deployment', app):
            if kubectl('rollout', 'restart', 'deployment/' + app).returncode != 0 or kubectl('rollout', 'status', 'deployment/' + app, '--timeout=300s').returncode != 0:
                raise Failure('APP_RESTART_FAILED:' + app, 1)
            print(app, 'RESTARTED')


def main(argv):
    try:
        if not argv:
            raise Failure('ARGUMENT_MISSING', 2)
        command, rest = argv[0], argv[1:]
        if command == 'cleanup':
            if rest:
                raise Failure('ARGUMENT_INVALID', 2)
            return cleanup()
        if command not in ('provision', 'check', 'rotate') or len(rest) != 2 or rest[0] != '--stage':
            raise Failure('ARGUMENT_INVALID', 2)
        if rest[1] not in STAGES:
            raise Failure('STAGE_INVALID', 2)
        return {'provision': provision, 'check': check, 'rotate': rotate}[command](rest[1])
    except Failure as failure:
        print(failure.code, file=sys.stderr)
        return failure.exit_code


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
