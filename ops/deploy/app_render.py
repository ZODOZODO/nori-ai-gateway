"""aigw-helm.sh 앱 release 보조: image digest·SPEC 입력 값 생성과 §11.9 렌더 행렬 대조. 출력은 고정 코드와 비밀 아닌 값뿐입니다."""
import base64
import gzip
import hashlib
import json
import re
import sys
from pathlib import Path

import yaml

HEX64 = re.compile(r'^[0-9a-f]{64}$')
COMMON_SECRETS = {'ai-gateway-service-tokens', 'ai-gateway-actor-jwks', 'ai-gateway-db-app', 'ai-gateway-gitlab-read', 'ai-gateway-test-hooks'}
SEAWEED_SECRET = 'ai-gateway-seaweedfs-read'
# §11.9 배포 행: (release, inference, srcRead, ingest, fixCat, fixture-writer). 재배포 0 행은 suite-only 로만 실행한다.
ROWS = {
    'none': ('ai-gateway', False, False, False, False, False),
    'F1': ('ai-gateway', False, False, False, False, False),
    'F2': ('ai-gateway', True, False, False, False, False),
    'F3-PROBE': ('ai-gateway', True, False, False, False, False),
    'F3': ('ai-gateway', True, True, True, True, True),
    'F3-ACL': ('ai-gateway', True, True, True, True, True),
    'F3-RULEVER': ('ai-gateway', True, True, True, True, True),
    'F4': ('ai-gateway', True, True, True, True, True),
    'F4-PERF': ('ai-gateway', True, True, True, True, True),
    'F4-RESTORE': ('ai-gateway-restore', False, True, False, True, True),
}
REUSE_SUITES = {'F1-RUBRIC', 'F2-OUTAGE', 'F2-RUBRIC', 'F4-RUBRIC', 'F4-OUTAGE', 'F4-CLOCK', 'F4-RESTORE-PREP', 'F4-RESTORE-MUTATE'}


class InputError(Exception):
    pass


class SpecMismatch(InputError):
    """실제 SPEC 바이트 SHA ≠ 기준선(M-10 exit 3)."""


def fail(code):
    raise InputError(code)


def digests(path, out):
    """image-digests.json → 차트 값(접두 없는 hex)과 수용 Job 빌드 신원 JSON."""
    try:
        data = json.loads(Path(path).read_text(encoding='utf-8'))
    except (OSError, ValueError):
        fail('IMAGE_DIGESTS_INVALID')
    if not isinstance(data, dict):
        fail('IMAGE_DIGESTS_INVALID')
    result = {}
    for key in ('app', 'backup', 'acceptance'):
        value = data.get(key)
        if not isinstance(value, str) or not value.startswith('sha256:') or not HEX64.match(value[7:]):
            fail('IMAGE_DIGESTS_INVALID:' + key)
        result[key] = value[7:]
    identity = data.get('buildIdentity')
    if not isinstance(identity, dict) or set(identity) != {'gitSha', 'appJarSha256', 'runtimeClasspathSha256'}:
        fail('IMAGE_DIGESTS_INVALID:buildIdentity')
    result['buildIdentityJson'] = json.dumps({'appImageDigest': data['app'], 'acceptanceImageDigest': data['acceptance'], 'buildIdentity': identity},
                                             sort_keys=True, separators=(',', ':'))
    Path(out).write_text(json.dumps(result), encoding='utf-8')


def baseline_sha(path):
    """기준선 문서의 첫 json 코드 블록 배열 마지막 항목 sha256."""
    try:
        text = Path(path).read_text(encoding='utf-8')
        block = re.search(r'```json\n(.*?)\n```', text, re.S)
        chain = json.loads(block.group(1)) if block else None
        sha = chain[-1]['sha256'] if isinstance(chain, list) and chain and isinstance(chain[-1], dict) else None
    except (OSError, ValueError, KeyError, TypeError):
        sha = None
    if not isinstance(sha, str) or not HEX64.match(sha):
        fail('SPEC_BASELINE_INVALID')
    return sha


def spec(spec_file, baseline, out):
    """SPEC 실제 바이트 SHA 가 기준선 마지막 항목과 같을 때만 spec-verify 값(gzip base64·SHA)을 쓴다. 본문 출력 0."""
    try:
        data = Path(spec_file).read_bytes()
    except OSError:
        fail('SPEC_INPUT_MISSING')
    expected = baseline_sha(baseline)
    actual = hashlib.sha256(data).hexdigest()
    if actual != expected:
        raise SpecMismatch('SPEC_BASELINE_MISMATCH')
    packed = base64.b64encode(gzip.compress(data, mtime=0)).decode('ascii')
    Path(out).write_text(json.dumps({'specVerify': {'gzipBase64': packed, 'sha256': actual, 'expectedSha256': expected}}), encoding='utf-8')
    print(actual)


def documents(render):
    return [d for d in yaml.safe_load_all(Path(render).read_text(encoding='utf-8')) if isinstance(d, dict)]


def one(docs, kind, name):
    found = [d for d in docs if d.get('kind') == kind and d.get('metadata', {}).get('name') == name]
    if len(found) != 1:
        fail('RENDER_MATRIX_MISMATCH:' + kind + '/' + name)
    return found[0]


def secrets(pod):
    return {v['secret']['secretName'] for v in pod['spec'].get('volumes', []) if 'secret' in v}


def env(container):
    return {e['name']: e.get('value') for e in container.get('env', [])}


def egress_rule(target):
    """runconfig.py 가 만든 netpol 대상 하나를 차트(`aigw.peer`·networkpolicy.yaml)와 같은 TCP egress 규칙으로: cidr 이면 ipBlock, 아니면 namespace·pod selector."""
    if target.get('cidr'):
        peer = {'ipBlock': {'cidr': target['cidr']}}
    else:
        peer = {'namespaceSelector': {'matchLabels': {'kubernetes.io/metadata.name': target['namespace']}}, 'podSelector': {'matchLabels': target['podSelector']}}
    return {'to': [peer], 'ports': [{'protocol': 'TCP', 'port': target['port']}]}


def matrix(render, release, suite, spec_sha, values_file):
    """렌더 결과가 §11.9 그 행과 정확히 같은지: Deployment Secret volume·추론 모드 env·egress 대상·수용 Job Secret·spec-verify.
    egress 기대값은 그 실행의 runconfig 해석 결과(values_file = runconfig.py 출력)의 netpol 대상에서 계산한다."""
    if suite not in ROWS or ROWS[suite][0] != release and not (suite == 'none' and release == 'ai-gateway-restore'):
        fail('SUITE_UNSUPPORTED')
    row = ROWS['F4-RESTORE'] if release == 'ai-gateway-restore' else ROWS[suite]
    _, inference, src_read, ingest, fix_cat, writer = row
    docs = documents(render)
    deployment = one(docs, 'Deployment', release)
    pod = deployment['spec']['template']
    expected = set(COMMON_SECRETS)
    if src_read:
        expected.add(SEAWEED_SECRET)
    if secrets(pod) != expected:
        fail('RENDER_MATRIX_MISMATCH:deployment-secrets')
    values = env(pod['spec']['containers'][0])
    checks = {'AI_GATEWAY_INFERENCE_MODE': 'enabled' if inference else 'disabled', 'AI_GATEWAY_KNOWLEDGE_SOURCE_READ_ENABLED': str(src_read).lower(),
              'AI_GATEWAY_INGEST_ENABLED': str(ingest).lower(), 'AI_GATEWAY_INGEST_FIXTURE_CATALOG_ENABLED': str(fix_cat).lower(),
              'AI_GATEWAY_RECOVERY_RESTORE_MODE': str(release == 'ai-gateway-restore').lower(),
              'AI_GATEWAY_SANITIZE_VERIFY_RULE_SET': 'mask-v1r' if suite == 'F3-RULEVER' else 'mask-v1'}
    if any(values.get(key) != expected_value for key, expected_value in checks.items()):
        fail('RENDER_MATRIX_MISMATCH:deployment-env')
    # 추론 연결은 endpoint env 하나뿐이다(SPEC §26.1 — 추론 자격 Secret·토큰 파일 env 0).
    if {key for key in values if key.startswith('AI_GATEWAY_INFERENCE_')} != {'AI_GATEWAY_INFERENCE_MODE'} | ({'AI_GATEWAY_INFERENCE_BASEURL'} if inference else set()):
        fail('RENDER_MATRIX_MISMATCH:deployment-env')
    target = 'ai-gateway-db-restore' if release == 'ai-gateway-restore' else 'ai-gateway-db'
    if pod['metadata']['labels'].get('aigw.nori/db-target') != target:
        fail('RENDER_MATRIX_MISMATCH:db-target')
    targets = json.loads(Path(values_file).read_text(encoding='utf-8'))['netpol']['targets']
    rules = {key: egress_rule(targets[key]) for key in ('inference', 'seaweed', 'kafka') if key in targets}
    egress = one(docs, 'NetworkPolicy', release)['spec'].get('egress', [])
    for present, key in ((inference, 'inference'), (src_read, 'seaweed'), (ingest, 'kafka')):
        if (present and key not in rules) or (key in rules and (rules[key] in egress) != present):
            fail('RENDER_MATRIX_MISMATCH:egress')
    if any(d.get('kind') == 'CronJob' for d in docs):
        fail('RENDER_MATRIX_MISMATCH:cronjob')
    job = [d for d in docs if d.get('kind') == 'Job' and d['metadata']['name'] == release + '-acceptance']
    if suite == 'none':
        if job:
            fail('RENDER_MATRIX_MISMATCH:acceptance-job')
        return
    if len(job) != 1:
        fail('RENDER_MATRIX_MISMATCH:acceptance-job')
    # 수용 pod 는 어느 suite 에서도 추론 endpoint 에 닿지 않는다(SPEC §26.1 임차 원칙 — 수용 pod 추론 egress 0).
    if 'inference' in rules and rules['inference'] in one(docs, 'NetworkPolicy', release + '-acceptance')['spec'].get('egress', []):
        fail('RENDER_MATRIX_MISMATCH:acceptance-egress')
    job_pod = job[0]['spec']['template']
    job_secrets = secrets(job_pod)
    base = {'ai-gateway-acceptance-signer', 'ai-gateway-acceptance-client', 'ai-gateway-db-acceptance-ro'}
    if job_secrets != (base | {'ai-gateway-acceptance-fixture-writer'} if writer else base):
        fail('RENDER_MATRIX_MISMATCH:acceptance-secrets')
    if '-Daigw.acceptance.suite=' + suite + ' ' not in env(job_pod['spec']['containers'][0]).get('JAVA_OPTS', '') + ' ':
        fail('RENDER_MATRIX_MISMATCH:acceptance-suite')
    mounts = {m['name']: m for m in job_pod['spec']['containers'][0]['volumeMounts']}
    spec_volume = [v for v in job_pod['spec']['volumes'] if v['name'] == 'spec']
    if 'spec' not in mounts or not mounts['spec'].get('readOnly') or len(spec_volume) != 1 or spec_volume[0]['configMap']['name'] != release + '-spec-verify':
        fail('RENDER_MATRIX_MISMATCH:spec-mount')
    configmap = one(docs, 'ConfigMap', release + '-spec-verify')
    if hashlib.sha256(gzip.decompress(base64.b64decode(configmap['binaryData']['spec.md.gz']))).hexdigest() != spec_sha:
        fail('RENDER_MATRIX_MISMATCH:spec-verify')


def deployment_sha(render, release):
    print(hashlib.sha256(json.dumps(one(documents(render), 'Deployment', release), sort_keys=True).encode()).hexdigest())


def main():
    command, args = sys.argv[1], sys.argv[2:]
    try:
        {'digests': digests, 'spec': spec, 'matrix': matrix, 'deployment-sha': deployment_sha}[command](*args)
    except InputError as error:
        print(str(error), file=sys.stderr)
        if isinstance(error, SpecMismatch):
            return 3
        return 1 if str(error).startswith('RENDER_MATRIX') else 2
    return 0


if __name__ == '__main__':
    sys.exit(main())
