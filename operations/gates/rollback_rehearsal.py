"""배포 rollback 리허설(Plan P15 L-6, P16 사용). 같은 스키마(aigw.migrations.sha256 label 동일)의 서로 다른 두 앱 이미지 A·B 로
A 배포(revision N) → B 업그레이드(N+1) → helm rollback N(N+2) → readiness·Pod imageID = A·Flyway 최신 V 불변을 확인한다.
차트는 이 checkout 에서 읽으므로(§4.1 2026-11-10) checkout 은 B 의 commit 이어야 한다(operations/ 미커밋 0·HEAD = B 의 gitSha, 아니면 부작용 전 exit 2).
A 는 이 checkout 의 차트로 배포하지 않는다 — A 의 commit checkout 에서 먼저 배포돼 있어야 하며 아니면 NOT_RUN(FROM_NOT_DEPLOYED).
Flyway 최신 V 는 공유 서버 nori_ai_gateway 를 운영자 호스트 psql 로 읽는다(자격 = Secret ai-gateway-config 의 단일 계정 두 키 → psql 프로세스 env, 명령행·출력 0).
증거 = evidence/F1/gates/rollback-rehearsal-<UTC>.json(chartRepoSha·revision 3개·digest 2개·V 값·판정만). 자격 값은 env·stdin·메모리만.

종료: 0 PASS, 1 FAIL(단계 실패 — 증거 기록), 2 입력 오류(checkout 포함 — 증거 0)·NOT_RUN(SECOND_SAME_SCHEMA_IMAGE_MISSING·SCHEMA_DIFFERS·LABEL_MISSING·FROM_NOT_DEPLOYED — 증거 기록).
"""
import base64
import datetime
import http.client
import json
import os
import pathlib
import re
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request

REGISTRY, REPOSITORY = 'harbor.nori.local', 'nori-tc/ai-gateway'
MANIFEST_TYPES = 'application/vnd.oci.image.manifest.v1+json, application/vnd.docker.distribution.manifest.v2+json'
FLYWAY_QUERY = 'SELECT version FROM gw_flyway.flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1'


class Stop(Exception):
    def __init__(self, code, exit_code):
        super().__init__(code)
        self.code, self.exit_code = code, exit_code


def run(command, stdin=None):
    return subprocess.run(command, input=stdin, capture_output=True)


def digests(path):
    try:
        data = json.loads(pathlib.Path(path).read_text(encoding='utf-8'))
        app = data['app']
    except (OSError, ValueError, KeyError, TypeError):
        raise Stop('IMAGE_DIGESTS_INVALID', 2)
    if not isinstance(app, str) or not re.fullmatch(r'sha256:[0-9a-f]{64}', app):
        raise Stop('IMAGE_DIGESTS_INVALID', 2)
    return app


def registry_base():
    base = os.environ.get('AIGW_REGISTRY_URL', 'https://' + REGISTRY)
    parsed = registry_url(base, 'REGISTRY_URL_REJECTED')
    if parsed.path not in ('', '/') or parsed.query:
        raise Stop('REGISTRY_URL_REJECTED', 1)
    return 'https://' + REGISTRY


def registry_url(url, reason):
    # urlsplit의 관대한 제어 문자 제거·userinfo 해석 전에 승인 origin만 허용한다.
    if not isinstance(url, str) or any(ord(c) <= 32 or ord(c) >= 127 for c in url) or '\\' in url:
        raise Stop(reason, 1)
    try:
        parsed = urllib.parse.urlsplit(url)
        if (parsed.scheme != 'https' or parsed.netloc not in (REGISTRY, REGISTRY + ':443')
                or parsed.fragment or parsed.port not in (None, 443)):
            raise ValueError
    except ValueError:
        raise Stop(reason, 1)
    return parsed


class RegistryNoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, response, code, message, headers, new_url):
        # 인증 전·후 모두 redirect를 거부해 Basic/Bearer가 다른 요청으로 복제되지 않게 한다.
        raise Stop('REGISTRY_REDIRECT_REJECTED', 1)


def registry_open(url, headers):
    registry_url(url, 'REGISTRY_URL_REJECTED')
    opener = urllib.request.build_opener(RegistryNoRedirect())
    return opener.open(urllib.request.Request(url, headers=headers), timeout=30)


def registry_get(url, accept):
    """Harbor v2 읽기 전용 조회. 401 이면 WWW-Authenticate realm 으로 bearer token 을 받는다(자격 = env 의 읽기 전용 계정).
    승인 HTTPS Harbor origin만 조회하며 모든 redirect를 거부한다."""
    registry_base()
    registry_url(url, 'REGISTRY_URL_REJECTED')
    user, token = os.environ.get('AIGW_HARBOR_READ_USER', ''), os.environ.get('AIGW_HARBOR_READ_TOKEN', '')
    try:
        with registry_open(url, {'Accept': accept}) as response:
            return response.read()
    except urllib.error.HTTPError as error:
        header = error.headers.get('WWW-Authenticate', '')
        error.close()
        if error.code != 401 or not user or not token:
            raise Stop('REGISTRY_READ_FAILED', 1)
        challenge = dict(re.findall(r'(\w+)="([^"]*)"', header))
        if not header.startswith('Bearer ') or 'realm' not in challenge:
            raise Stop('REGISTRY_READ_FAILED', 1)
        realm = registry_url(challenge['realm'], 'REGISTRY_AUTH_REALM_REJECTED')
        if realm.query:
            raise Stop('REGISTRY_AUTH_REALM_REJECTED', 1)
        query = urllib.parse.urlencode({k: v for k, v in challenge.items() if k in ('service', 'scope')})
        basic = base64.b64encode(f'{user}:{token}'.encode()).decode()
        try:
            with registry_open(challenge['realm'] + '?' + query, {'Authorization': 'Basic ' + basic}) as response:
                granted = json.loads(response.read())
            bearer = granted.get('token') or granted.get('access_token')
            if not isinstance(bearer, str) or not bearer or any(ord(c) <= 32 or ord(c) >= 127 for c in bearer):
                raise ValueError
            with registry_open(url, {'Accept': accept, 'Authorization': 'Bearer ' + bearer}) as response:
                return response.read()
        except (OSError, http.client.HTTPException, ValueError, TypeError, AttributeError):
            raise Stop('REGISTRY_READ_FAILED', 1)
    except (OSError, http.client.HTTPException, ValueError):
        raise Stop('REGISTRY_READ_FAILED', 1)


def migrations_label(digest):
    base = registry_base() + '/v2/' + REPOSITORY
    try:
        manifest = json.loads(registry_get(f'{base}/manifests/{digest}', MANIFEST_TYPES))
        config = json.loads(registry_get(f"{base}/blobs/{manifest['config']['digest']}", '*/*'))
        label = (config.get('config') or {}).get('Labels', {}).get('aigw.migrations.sha256')
    except (KeyError, ValueError, TypeError, AttributeError):
        raise Stop('REGISTRY_READ_FAILED', 1)
    if not isinstance(label, str) or not re.fullmatch(r'[0-9a-f]{64}', label):
        raise Stop('LABEL_MISSING', 2)
    return label


def deploy(arguments, digests_file):
    result = run(['bash', 'operations/deploy/aigw-helm.sh', '--release', 'ai-gateway', '--mode', 'deploy',
                  '--runconfig', arguments['runconfig'], '--image-digests', digests_file, '--suite', 'none'])
    if result.returncode != 0:
        raise Stop('DEPLOY_FAILED', 1)


def revision():
    result = run(['helm', 'status', 'ai-gateway', '-n', 'tc', '-o', 'json'])
    try:
        data = json.loads(result.stdout)
        if result.returncode != 0 or data['info']['status'] != 'deployed' or not isinstance(data['version'], int):
            raise ValueError
        return data['version']
    except (ValueError, KeyError, TypeError):
        raise Stop('HELM_STATUS_FAILED', 1)


def kubectl_json(*args):
    result = run(['kubectl', '-n', 'tc', *args, '-o', 'json'])
    try:
        if result.returncode != 0:
            raise ValueError
        return json.loads(result.stdout)
    except ValueError:
        raise Stop('KUBECTL_QUERY_FAILED', 1)


def deployed_digest():
    deployment = kubectl_json('get', 'deployment', 'ai-gateway')
    image = deployment['spec']['template']['spec']['containers'][0]['image']
    return image.split('@', 1)[1] if '@' in image else ''


def pods_ready_with(digest):
    """모든 앱 Pod 가 Ready 이고 실행 중 컨테이너 imageID 가 그 digest 인지(SchemaVersionGuard 통과 = 기동 성공)."""
    pods = kubectl_json('get', 'pods', '-l', 'app=ai-gateway')['items']
    if not pods:
        return False
    for pod in pods:
        ready = any(c.get('type') == 'Ready' and c.get('status') == 'True' for c in pod.get('status', {}).get('conditions', []))
        statuses = pod.get('status', {}).get('containerStatuses', [])
        if not ready or not statuses or not all(s.get('imageID', '').endswith(digest) and s.get('ready') for s in statuses):
            return False
    return True


def flyway_latest():
    secret = kubectl_json('get', 'secret', 'ai-gateway-config')
    try:
        user, password = (base64.b64decode(secret['data'][key]).decode().strip() for key in ('AI_GATEWAY_DB_USER', 'AI_GATEWAY_DB_PASSWORD'))
    except (KeyError, ValueError, TypeError):
        raise Stop('DB_ACCOUNT_SECRET_MISSING', 1)
    environment = dict(os.environ, PGHOST='192.168.0.150', PGPORT='15432', PGDATABASE='nori_ai_gateway', PGUSER=user, PGPASSWORD=password)
    try:
        result = subprocess.run(['psql', '-X', '-q', '-w', '-A', '-t', '-c', FLYWAY_QUERY], capture_output=True, env=environment)
    except OSError:
        raise Stop('FLYWAY_QUERY_FAILED', 1)
    version = result.stdout.decode('ascii', 'replace').strip()
    if result.returncode != 0 or not re.fullmatch(r'[0-9][0-9._]{0,31}', version):
        raise Stop('FLYWAY_QUERY_FAILED', 1)
    return version


def write(evidence):
    out = pathlib.Path('evidence/F1/gates')
    out.mkdir(parents=True, exist_ok=True)
    path = out / ('rollback-rehearsal-' + datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ') + '.json')
    path.write_text(json.dumps(evidence, indent=2) + '\n', encoding='utf-8')
    return path


def rehearse(arguments, evidence):
    a, b = digests(arguments['from']), digests(arguments['to'])
    evidence.update(fromAppDigest=a, toAppDigest=b)
    if a == b:
        raise Stop('SECOND_SAME_SCHEMA_IMAGE_MISSING', 2)
    label_a, label_b = migrations_label(a), migrations_label(b)
    evidence['migrationsSha256'] = label_a
    if label_a != label_b:
        raise Stop('SCHEMA_DIFFERS', 2)
    # ① A 가 이미 배포돼 있어야 한다(현재 revision = N). 이 checkout(B commit)의 차트로 A 를 배포하지 않는다.
    if deployed_digest() != a:
        raise Stop('FROM_NOT_DEPLOYED', 2)
    if not pods_ready_with(a):
        raise Stop('FROM_NOT_READY', 1)
    base, version_before = revision(), flyway_latest()
    evidence.update(revisions=[base], flywayLatestBefore=version_before)
    # ② B 로 업그레이드
    deploy(arguments, arguments['to'])
    upgraded = revision()
    evidence['revisions'].append(upgraded)
    if upgraded != base + 1 or not pods_ready_with(b):
        raise Stop('UPGRADE_NOT_READY', 1)
    # ③ helm rollback N
    if run(['helm', 'rollback', 'ai-gateway', str(base), '-n', 'tc', '--wait', '--timeout', '600s']).returncode != 0:
        raise Stop('ROLLBACK_FAILED', 1)
    rolled = revision()
    evidence['revisions'].append(rolled)
    if rolled != base + 2:
        raise Stop('ROLLBACK_REVISION_UNEXPECTED', 1)
    if run(['kubectl', '-n', 'tc', 'rollout', 'status', 'deployment/ai-gateway', '--timeout=300s']).returncode != 0 or not pods_ready_with(a):
        raise Stop('ROLLBACK_NOT_READY', 1)
    version_after = flyway_latest()
    evidence['flywayLatestAfter'] = version_after
    if version_after != version_before:
        raise Stop('SCHEMA_VERSION_CHANGED', 1)


def chart_checkout(digests_path):
    """aigw-helm.sh 와 같은 checkout 검사(app_render.py checkout). stdout = chartRepoSha."""
    result = run(['python3', '-B', 'operations/deploy/app_render.py', 'checkout', digests_path])
    if result.returncode != 0:
        raise Stop(result.stderr.decode('ascii', 'replace').strip() or 'CHART_CHECKOUT_UNREADABLE', 2)
    return result.stdout.decode('ascii', 'replace').strip()


def main(argv):
    names = {'--runconfig': 'runconfig', '--from-digests': 'from', '--to-digests': 'to'}
    arguments = {}
    if len(argv) % 2:
        print('ARGUMENT_MISSING', file=sys.stderr); return 2
    for flag, value in zip(argv[::2], argv[1::2]):
        if flag not in names:
            print('ARGUMENT_INVALID', file=sys.stderr); return 2
        arguments[names[flag]] = value
    missing = [flag for flag, key in names.items() if not arguments.get(key)]
    if missing:
        print('ARGUMENT_MISSING', file=sys.stderr); return 2
    try:
        chart_repo_sha = chart_checkout(arguments['to'])
    except Stop as stop:
        print(stop.code, file=sys.stderr); return stop.exit_code
    evidence = {'chartRepoSha': chart_repo_sha, 'result': 'PASS'}
    code = 0
    try:
        rehearse(arguments, evidence)
    except Stop as stop:
        evidence.update(result='NOT_RUN' if stop.exit_code == 2 else 'FAIL', reasonCode=stop.code)
        print(stop.code, file=sys.stderr)
        code = stop.exit_code
    print('ROLLBACK_REHEARSAL', evidence['result'], write(evidence))
    return code


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
