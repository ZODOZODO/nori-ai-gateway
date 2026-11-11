"""검증 수용 자격(Plan §11.7·P15) provision/check/rotate/cleanup. 값은 이 프로세스 메모리와 kubectl stdin 으로만 다니며 출력·증거는 이름·상태·kid 뿐이다.

Gateway 쪽 신뢰 성분(jwks.json·service-tokens.json·hook-token-sha256)은 운영자가 만든 파일 Secret ai-gateway-config 의 키이고(Plan U6 ①),
이 도구는 그 키만 merge patch 로 바꾼다(그 밖 키 — DB 설정·계정·GitLab·SeaweedFS 자격 — 불변). 수용 하네스 쪽 Secret(signer·client·fixture-writer)은 별도다.
수용 oracle 의 DB 자격은 앱과 같은 단일 계정(ai-gateway-config 의 AI_GATEWAY_DB_USER·AI_GATEWAY_DB_PASSWORD)이라 이 도구가 만들거나 지우는 DB 역할·자격은 없다.
"""
import base64
import datetime
import hashlib
import json
import pathlib
import secrets
import subprocess
import sys
import uuid

NAMESPACE = 'tc'
CONFIG = 'ai-gateway-config'
SIGNER, CLIENT, WRITER = 'ai-gateway-acceptance-signer', 'ai-gateway-acceptance-client', 'ai-gateway-acceptance-fixture-writer'
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


def patch_config(changes):
    """ai-gateway-config 의 지정 키만 바꾼다(값 None = 키 삭제). merge patch 는 stdin 으로만 — 명령행·last-applied annotation 에 값 0."""
    body = {'data': {key: None if value is None else base64.b64encode(value).decode('ascii') for key, value in changes.items()}}
    if kubectl('patch', 'secret', CONFIG, '--type', 'merge', '--patch-file', '/dev/stdin', stdin=json.dumps(body).encode()).returncode != 0:
        raise Failure('SECRET_PATCH_FAILED:' + CONFIG, 1)


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
    names = [CONFIG, SIGNER, CLIENT]
    return names + [WRITER] if stage in ('F3', 'F4') else names


def evaluate(stage):
    """항목별 MATCH/MISSING 과 MISSING 의 고정 사유. Secret 조회 오류는 항목 상태가 아니라 실패(exit 1)다."""
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
    gone = absent(CONFIG, SIGNER)
    if gone:
        miss('actor-signer', 'SECRET_ABSENT:' + gone)
    else:
        try:
            private, keys = load(SIGNER, 'signer-private.jwk'), load(CONFIG, 'jwks.json')['keys']
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
    gone = absent(CONFIG, CLIENT)
    if gone:
        miss('service-token', 'SECRET_ABSENT:' + gone)
    else:
        try:
            ok = load(CONFIG, 'service-tokens.json').get(sha(secrets_[CLIENT]['service-token'].strip())) == 'TC'
            status['service-token'] = 'MATCH' if ok else None
            if not ok:
                miss('service-token', 'HASH_MISMATCH')
        except (KeyError, ValueError, TypeError, AttributeError):
            miss('service-token', 'FORMAT_INVALID')
    gone = absent(CONFIG, CLIENT)
    if gone:
        miss('hook-token', 'SECRET_ABSENT:' + gone)
    else:
        try:
            if secrets_[CONFIG]['hook-token-sha256'].decode().strip() == sha(secrets_[CLIENT]['hook-token'].strip()):
                status['hook-token'] = 'MATCH'
            else:
                miss('hook-token', 'HASH_MISMATCH')
        except (KeyError, UnicodeDecodeError):
            miss('hook-token', 'FORMAT_INVALID')
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


def material(config, replace):
    """새 자격 한 벌. replace 가 있으면 그 이전 kid·해시를 Gateway 쪽에서 뺀다(rotate)."""
    signer = ec_key('test-' + datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ'))
    service, hook = (secrets.token_urlsafe(32).encode() for _ in range(2))
    try:
        jwks = json.loads(config['jwks.json']) if 'jwks.json' in config else {'keys': []}
        if not isinstance(jwks, dict) or not isinstance(jwks.get('keys'), list):
            raise TypeError
        if replace:
            jwks['keys'] = [k for k in jwks['keys'] if k.get('kid') != replace['kid']]
    except (KeyError, ValueError, TypeError, AttributeError):
        raise Failure('JWKS_FORMAT_INVALID', 1)
    try:
        tokens = json.loads(config['service-tokens.json']) if 'service-tokens.json' in config else {}
        if not isinstance(tokens, dict):
            raise TypeError
        if replace:
            tokens.pop(replace['service'], None)
    except (KeyError, ValueError, TypeError):
        raise Failure('SERVICE_TOKENS_FORMAT_INVALID', 1)
    jwks['keys'].append({k: v for k, v in signer.items() if k != 'd'})
    tokens[sha(service)] = 'TC'
    return {
        CONFIG: {'jwks.json': json.dumps(jwks).encode(), 'service-tokens.json': json.dumps(tokens).encode(), 'hook-token-sha256': sha(hook).encode()},
        SIGNER: {'signer-private.jwk': json.dumps(signer).encode()}, CLIENT: {'service-token': service, 'hook-token': hook}}


def install(stage, current, replace):
    # 부작용 전 판정: Gateway 설정 Secret 은 운영자가 먼저 만든다(차트·이 도구는 만들지 않음). 없으면 Secret 변경 0.
    if current[CONFIG] == 'MISSING':
        raise Failure('CONFIG_SECRET_MISSING', 2)
    bundle = material(current[CONFIG], replace)
    # 수용 쪽 Secret 을 먼저, Gateway 쪽 공개 성분·해시를 나중에 둔 뒤 앱을 재기동한다.
    for name in (SIGNER, CLIENT):
        apply_secret(name, bundle[name])
        print(name, 'APPLIED')
    patch_config(bundle[CONFIG])
    print(CONFIG, 'TRUST_KEYS_APPLIED')
    restart_apps()
    return check(stage)


def provision(stage):
    _, _, current = evaluate(stage)
    states = {name: 'ABSENT' if current[name] == 'MISSING' else 'PRESENT' for name in (SIGNER, CLIENT)}
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
    """Plan P15 검증 자격 정리 순서(승인 A 의 수용 DB 역할 NOLOGIN·재배포 단계는 단일 계정 결정으로 없음 — Plan §11.7 2026-11-10).
    ① 부작용 전: Secret 조회·형식 검사와 신뢰 제거 결과 계산 ② 수용 Job 종료 ③ 수용 쪽 Secret 삭제
    ④ ai-gateway-config 에서 test- 공개키·수용 토큰 해시를 빼고 hook 해시 키를 지운다(그 밖 키·시스템 신뢰 보존)."""
    current = {name: get_secret(name) for name in (CONFIG, SIGNER, CLIENT, WRITER)}
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
    changes = {}
    config = current[CONFIG] if isinstance(current[CONFIG], dict) else {}
    if 'jwks.json' in config:
        try:
            jwks = json.loads(config['jwks.json'])
            kept = [k for k in jwks['keys'] if not str(k.get('kid', '')).startswith('test-') and k.get('kid') != acceptance_kid]
        except (KeyError, ValueError, TypeError, AttributeError):
            raise Failure('JWKS_FORMAT_INVALID', 1)
        if len(kept) != len(jwks['keys']):
            changes['jwks.json'] = json.dumps(dict(jwks, keys=kept)).encode()
    if 'service-tokens.json' in config and acceptance_hash:
        try:
            tokens = json.loads(config['service-tokens.json'])
            removed = tokens.pop(acceptance_hash, None) is not None
        except (KeyError, ValueError, TypeError, AttributeError):
            raise Failure('SERVICE_TOKENS_FORMAT_INVALID', 1)
        if removed:
            changes['service-tokens.json'] = json.dumps(tokens).encode()
    if 'hook-token-sha256' in config:
        changes['hook-token-sha256'] = None
    for app in APP_RELEASES:
        # helm test 수용 Job(<앱 release>-acceptance)만 끝낸다. 다른 workload 는 건드리지 않는다.
        if kubectl('delete', 'job', app + '-acceptance', '--ignore-not-found', '--wait=true').returncode != 0:
            raise Failure('ACCEPTANCE_JOB_STOP_FAILED:' + app, 1)
    for name in (SIGNER, CLIENT, WRITER):
        if kubectl('delete', 'secret', name, '--ignore-not-found').returncode != 0:
            raise Failure('SECRET_DELETE_FAILED:' + name, 1)
        print(name, 'DELETED')
    if changes:
        patch_config(changes)
        print(CONFIG, 'ACCEPTANCE_TRUST_REMOVED')
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
