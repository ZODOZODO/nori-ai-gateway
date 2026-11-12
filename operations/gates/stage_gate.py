"""단계 게이트(Plan P15 `aigw-stage-gate.sh`, §4.1 단계 게이트 명령 정본·§11.9 행렬). P16·P21·P22·P25·P28·P29 가 운영자 호스트에서 쓴다.

순서: 입력·SPEC 실제 바이트·runconfig profile·차트 checkout(operations/ 미커밋 0·HEAD = 이미지 GIT_SHA, 실패 exit 2 — §4.1 2026-11-10)·부모 회차 신원 확인 → ① spec-guard(exit≠0 → 3) → ② 검증 자격 check(MISSING → RUN ABORTED·12)
→ ②' 런타임 자격 check(MISSING → RUN ABORTED·13) → ③ aigw-helm deploy|suite-only(실패·재배포 0 suite 의 앱 Pod 변화 → RUN ABORTED·11)
→ ④ helm test --logs(성공·실패 무관 저장 — 블록·helm 상태 줄만, .sha256) → ⑤ ingest(acceptance) → ⑥ gate(행렬의 생략 suite 제외)
→ ⑦ 종료 코드(수집·보고 뒤에만 전파): deploy 11, test 10, 자격 12/13, ingest·gate 실패는 그 rc, 그 밖 0(게이트 PASS 아님 — 판정은 GATE-REPORT.md).
"""
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

# suite: (release, 모드, 검증 자격 단계, 런타임 자격 단계, ingest 단계, 로그 경로 형식, gate 실행)
MATRIX = {
    'F1': ('ai-gateway', 'deploy', 'F1', 'F1', 'F1', 'evidence/F1/acceptance-{utc}.log', True),
    'F1-RUBRIC': ('ai-gateway', 'suite-only', 'F1', 'F1', 'F1', 'evidence/F1/rubric-{utc}.log', True),
    'F2': ('ai-gateway', 'deploy', 'F2', 'F2', 'F2', 'evidence/F2/acceptance-{utc}.log', True),
    'F2-OUTAGE': ('ai-gateway', 'suite-only', 'F2', 'F2', 'F2', 'evidence/F2/outage-{phase}-{utc}.log', True),
    'F2-RUBRIC': ('ai-gateway', 'suite-only', 'F2', 'F2', 'F2', 'evidence/F2/rubric-{utc}.log', True),
    'F3-PROBE': ('ai-gateway', 'deploy', 'F2', 'F2', 'F3', 'evidence/F3/gates/acceptance-probe-{utc}.log', False),
    'F3': ('ai-gateway', 'deploy', 'F3', 'F3', 'F3', 'evidence/F3/acceptance-{utc}.log', True),
    'F3-ACL': ('ai-gateway', 'deploy', 'F3', 'F3', 'F3', 'evidence/F3/acceptance-acl-{utc}.log', True),
    'F3-RULEVER': ('ai-gateway', 'deploy', 'F3', 'F3', 'F3', 'evidence/F3/acceptance-rulever-{utc}.log', True),
    'F4': ('ai-gateway', 'deploy', 'F4', 'F4', 'F4', 'evidence/F4/acceptance-{utc}.log', True),
    'F4-RUBRIC': ('ai-gateway', 'suite-only', 'F4', 'F4', 'F4', 'evidence/F4/rubric-{utc}.log', True),
    'F4-OUTAGE': ('ai-gateway', 'suite-only', 'F4', 'F4', 'F4', 'evidence/F4/outage-{phase}-{utc}.log', True),
    'F4-CLOCK': ('ai-gateway', 'suite-only', 'F4', 'F4', 'F4', 'evidence/F4/clock-{utc}.log', True),
    'F4-PERF': ('ai-gateway', 'deploy', 'F4', 'F4', 'F4', 'evidence/F4-PERF/acceptance-{utc}.log', False),
}
VALUE_FLAGS = ('--stage', '--image-digests', '--runconfig', '--test-timeout-seconds', '--spec', '--release', '--parent-run-id',
               '--rubric-targets', '--outage-phase', '--fixture-catalog', '--set-extra', '--clock-initial')
HELM_STATUS = re.compile(r'^(NAME|LAST DEPLOYED|NAMESPACE|STATUS|REVISION|TEST SUITE|Last Started|Last Completed|Phase):')


class Stop(Exception):
    def __init__(self, code, exit_code):
        super().__init__(code)
        self.code, self.exit_code = code, exit_code


def run(command, timeout=None, env=None):
    try:
        return subprocess.run(command, capture_output=True, timeout=timeout, env=env)
    except subprocess.TimeoutExpired as expired:
        return subprocess.CompletedProcess(command, 124, expired.stdout or b'', b'')


def gradle(arguments):
    return run(['bash', './gradlew', ':gateway-eval:run', '--args=' + arguments, '--no-daemon']).returncode


def parse(argv, environ):
    if len(argv) % 2:
        raise Stop('ARGUMENT_MISSING', 2)
    options = {}
    for flag, value in zip(argv[::2], argv[1::2]):
        if flag not in VALUE_FLAGS or flag in options:
            raise Stop('ARGUMENT_INVALID', 2)
        options[flag] = value
    suite = options.get('--stage')
    if suite not in MATRIX:
        raise Stop('STAGE_INVALID', 2)
    for flag in ('--image-digests', '--runconfig', '--test-timeout-seconds'):
        if not options.get(flag):
            raise Stop('ARGUMENT_MISSING:' + flag[2:], 2)
    if not re.fullmatch(r'[1-9][0-9]{0,4}', options['--test-timeout-seconds']):
        raise Stop('ARGUMENT_INVALID', 2)
    spec = options.get('--spec') or environ.get('AIGW_SPEC_FILE')
    if not spec or not pathlib.Path(spec).is_file():
        raise Stop('SPEC_INPUT_MISSING', 2)
    options['--spec'] = spec
    release = MATRIX[suite][0]
    if options.get('--release', release) != release:
        raise Stop('RELEASE_SUITE_CONFLICT', 2)
    if suite.endswith('-RUBRIC') and not options.get('--rubric-targets'):
        raise Stop('RUBRIC_TARGETS_MISSING', 2)
    if suite.endswith('-OUTAGE') != bool(options.get('--outage-phase')) or options.get('--outage-phase', 'STOPPED') not in ('STOPPED', 'LOADING'):
        raise Stop('OUTAGE_PHASE_INVALID', 2)
    if options.get('--parent-run-id') is not None and not re.fullmatch(r'[A-Za-z0-9_.:-]{1,128}', options['--parent-run-id']):
        raise Stop('ARGUMENT_INVALID', 2)
    return suite, options


def runconfig_profile(path):
    try:
        profile = json.loads(pathlib.Path(path).read_text(encoding='utf-8')).get('profile')
    except (OSError, ValueError, AttributeError):
        raise Stop('RUNCONFIG_INVALID', 2)
    if profile != 'main':
        raise Stop('RUNCONFIG_PROFILE_MISMATCH', 2)


def image_identity(path):
    with tempfile.TemporaryDirectory(prefix='aigw-gate-') as directory:
        out = pathlib.Path(directory) / 'digests.json'
        if run(['python3', '-B', 'operations/deploy/app_render.py', 'digests', path, str(out)]).returncode != 0:
            raise Stop('IMAGE_DIGESTS_INVALID', 2)
        return json.loads(json.loads(out.read_text())['buildIdentityJson'])


def chart_checkout(digests):
    """배포 checkout 검사(aigw-helm.sh 와 같은 app_render.py checkout). 실패는 부작용(부모 회차 발급·증거) 전 exit 2."""
    result = run(['python3', '-B', 'operations/deploy/app_render.py', 'checkout', digests])
    if result.returncode != 0:
        raise Stop(result.stderr.decode('ascii', 'replace').strip() or 'CHART_CHECKOUT_UNREADABLE', 2)
    return result.stdout.decode('ascii', 'replace').strip()


def parent_manifest(stage, parent):
    for path in sorted(pathlib.Path('evidence', stage, 'runs').glob('*.json')):
        try:
            node = json.loads(path.read_text(encoding='utf-8'))
        except ValueError:
            continue
        if node.get('kind') == 'RUN' and node.get('status') == 'PARENT' and node.get('parentRunId') == parent:
            return node
    raise Stop('PARENT_MANIFEST_MISSING', 2)


def baseline_seq():
    text = pathlib.Path('docs/gates/spec-baseline.md').read_text(encoding='utf-8')
    return json.loads(re.search(r'```json\n(.*?)\n```', text, re.S).group(1))[-1]['seq']


def pods(release):
    result = run(['kubectl', '-n', 'tc', 'get', 'pods', '-l', 'app=' + release, '-o', 'json'])
    if result.returncode != 0:
        raise Stop('POD_QUERY_FAILED', 11)
    items = json.loads(result.stdout)['items']
    return sorted((p['metadata']['uid'], p['metadata'].get('labels', {}).get('pod-template-hash', ''),
                   sum(c.get('restartCount', 0) for c in p.get('status', {}).get('containerStatuses', []))) for p in items)


def pod_digest(selector):
    """selector 의 Pod 실행 컨테이너 imageID digest 가 정확히 하나면 그 값, 아니면(조회 실패·0개·여러 개) None."""
    result = run(['kubectl', '-n', 'tc', 'get', 'pods', '-l', selector, '-o', 'json'])
    digests_ = {s['imageID'].rsplit('@', 1)[-1] for p in json.loads(result.stdout or b'{"items":[]}')['items']
                for s in p.get('status', {}).get('containerStatuses', []) if '@' in s.get('imageID', '')} if result.returncode == 0 else set()
    return digests_.pop() if len(digests_) == 1 else None



class Gate:
    def __init__(self, suite, options):
        self.suite, self.options = suite, options
        self.release, self.mode, self.verify_stage, self.runtime_stage, self.stage, self.log_format, self.run_gate = MATRIX[suite]
        self.spec, self.utc = options['--spec'], datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ')
        self.parent, self.ingest_rc = options.get('--parent-run-id'), 0

    def ops_record(self, record, name):
        """OPS·RUN 레코드 하나를 봉인해 evidence/<stage>/ops/ 에 객체 파일로 쓰고 --source ops 로 ingest 한다."""
        out = pathlib.Path('evidence', self.stage, 'ops'); out.mkdir(parents=True, exist_ok=True)
        path = out / f'{name}-{self.utc}-{uuid.uuid4().hex[:8]}.json'
        with tempfile.NamedTemporaryFile('w', suffix='.json', delete=False, encoding='utf-8') as raw:
            json.dump(dict(record, stage=self.stage, parentRunId=self.parent, suite=self.suite), raw)
        if run(['python3', '-B', 'operations/gates/evidence_record.py', 'seal', self.spec, raw.name, str(path)]).returncode != 0:
            raise Stop('EVIDENCE_RECORD_INVALID', 1)
        pathlib.Path(raw.name).unlink()
        rc = gradle(f'ingest --stage {self.stage} --parent-run-id {self.parent} --input {path} --source ops --spec {self.spec}')
        self.ingest_rc = self.ingest_rc or rc

    def aborted(self, reason, exit_code):
        self.ops_record({'kind': 'RUN', 'status': 'ABORTED', 'producer': 'OPS', 'runId': str(uuid.uuid4()), 'reasonCode': reason}, 'run-aborted')
        return self.finish(exit_code)

    def finish(self, exit_code):
        gate_rc = gradle(f'gate --stage {self.stage} --parent-run-id {self.parent} --spec {self.spec}') if self.run_gate else 0
        print(f'STAGE_GATE suite={self.suite} parent={self.parent} exit={exit_code or self.ingest_rc or gate_rc}', file=sys.stderr)
        return exit_code or self.ingest_rc or gate_rc

    def parent_manifest(self, identity, app_digest, acceptance_digest):
        """새 부모 회차 manifest(RUN PARENT, runId = parentRunId — GateEvaluator 의 부모 조회 키). 실제 Pod digest 를 못 읽었으면 UNVERIFIED 로 남겨
        gate 가 모든 component 를 IDENTITY_MISMATCH 로 판정하게 한다(입력값으로 대체 0)."""
        self.ops_record({'kind': 'RUN', 'status': 'PARENT', 'producer': 'OPS', 'runId': self.parent, 'baselineSeq': baseline_seq(),
                         'buildIdentity': dict(identity, appImageDigest=app_digest or 'UNVERIFIED', acceptanceImageDigest=acceptance_digest or 'UNVERIFIED')}, 'parent-manifest')

    def helm_arguments(self, mode):
        args = ['bash', 'operations/deploy/aigw-helm.sh', '--release', self.release, '--mode', mode,
                '--runconfig', self.options['--runconfig'], '--image-digests', self.options['--image-digests'], '--suite', self.suite,
                '--spec-file', self.spec, '--parent-run-id', self.parent]
        for flag in ('--fixture-catalog', '--clock-initial', '--outage-phase', '--set-extra', '--rubric-targets'):
            if self.options.get(flag):
                args += [flag, self.options[flag]]
        return args

    def execute(self):
        runconfig_profile(self.options['--runconfig'])
        identity = image_identity(self.options['--image-digests'])
        chart_checkout(self.options['--image-digests'])
        if run(['bash', './gradlew', ':gateway-eval:run', '--args=spec-guard --spec ' + self.spec + ' --baseline docs/gates/spec-baseline.md', '--no-daemon']).returncode != 0:
            raise Stop('SPEC_GUARD_FAILED', 3)
        new_parent = self.parent is None
        if new_parent:
            self.parent = f'{self.stage}-{self.utc}-{secrets.token_hex(4)}'
            print('AIGW_PARENT_RUN_ID=' + self.parent, flush=True)
        else:
            # 한 부모 회차 안 이미지·빌드 신원 혼합 0(호환 재사용 예외 0).
            manifest = parent_manifest(self.stage, self.parent).get('buildIdentity', {})
            if any(manifest.get(key) != identity.get(key) for key in ('appImageDigest', 'acceptanceImageDigest', 'buildIdentity')):
                raise Stop('PARENT_IDENTITY_MISMATCH', 2)
        if run(['bash', 'operations/gates/aigw-verify-credentials.sh', 'check', '--stage', self.verify_stage]).returncode != 0:
            return self.aborted('VERIFY_CREDENTIALS_MISSING', 12)
        runtime_env = dict(os.environ, AIGW_RUNCONFIG=self.options['--runconfig'])
        if run(['bash', 'operations/gates/aigw-runtime-credentials.sh', 'check', '--stage', self.runtime_stage], env=runtime_env).returncode != 0:
            return self.aborted('RUNTIME_CREDENTIALS_MISSING', 13)
        if self.mode == 'suite-only':
            # 재배포 0 suite: 앱 Pod UID·pod-template-hash·restartCount 가 전후로 같아야 한다.
            try:
                before = pods(self.release)
                deployed = run(self.helm_arguments('suite-only')).returncode == 0
                if deployed and pods(self.release) != before:
                    return self.aborted('APP_POD_CHANGED', 11)
            except Stop:
                return self.aborted('POD_QUERY_FAILED', 11)
        else:
            deployed = run(self.helm_arguments('deploy')).returncode == 0
        if not deployed:
            return self.aborted('DEPLOY_FAILED', 11)
        # 배포·suite-only 모두: 실제 앱 Pod imageID = 입력 digest 여야 helm test 를 돌린다(불일치 exit 11, Plan L-6 수용 하네스 절).
        app_digest = pod_digest('app=' + self.release)
        if app_digest != identity['appImageDigest']:
            if new_parent:
                self.parent_manifest(identity, app_digest, None)
            return self.aborted('DEPLOYED_DIGEST_MISMATCH', 11)
        timeout = int(self.options['--test-timeout-seconds'])
        test = run(['helm', 'test', self.release, '-n', 'tc', '--timeout', f'{timeout}s', '--logs'], timeout=timeout + 120)
        if new_parent:
            # 수용 이미지의 실제 digest 는 test 뒤에만 있다(수용 Job hook-delete-policy = before-hook-creation 이라 Pod 가 남음).
            self.parent_manifest(identity, app_digest, pod_digest('job-name=' + self.release + '-acceptance'))
        log = pathlib.Path(self.log_format.format(utc=self.utc, phase=self.options.get('--outage-phase', '')))
        log.parent.mkdir(parents=True, exist_ok=True)
        lines = test.stdout.decode('utf-8', 'replace').splitlines()
        kept, inside = [], False
        for line in lines:
            # 증거 블록(BEGIN·base64·END)과 helm 상태 줄만 남긴다. 그 밖 원문은 버린다.
            if line.startswith('AIGW-EVIDENCE-BEGIN '):
                inside = True; kept.append(line)
            elif line == 'AIGW-EVIDENCE-END':
                inside = False; kept.append(line)
            elif inside and re.fullmatch(r'[A-Za-z0-9+/=]+', line) or HELM_STATUS.match(line):
                kept.append(line)
        log.write_text(''.join(line + '\n' for line in kept), encoding='utf-8')
        pathlib.Path(str(log) + '.sha256').write_text(hashlib.sha256(log.read_bytes()).hexdigest() + '  ' + log.name + '\n', encoding='utf-8')
        self.ingest_rc = gradle(f'ingest --stage {self.stage} --parent-run-id {self.parent} --input {log} --source acceptance --spec {self.spec}')
        return self.finish(10 if test.returncode != 0 else 0)


def main(argv, environ):
    try:
        suite, options = parse(argv, environ)
        return Gate(suite, options).execute()
    except Stop as stop:
        print(stop.code, file=sys.stderr)
        return stop.exit_code


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:], os.environ))
