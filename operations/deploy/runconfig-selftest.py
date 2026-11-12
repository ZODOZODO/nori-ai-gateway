"""승인 입력을 만들지 않고 임시 fixture로 입력 경계와 실제 Helm을 검사합니다(차트 = 이 checkout 의 operations/deploy/helm)."""
import copy
import json
from pathlib import Path
import shutil
import subprocess
import tempfile

root = Path.cwd()
checks = []
with tempfile.TemporaryDirectory(prefix='aigw-runconfig-test-') as temporary:
    work = Path(temporary)
    shutil.copytree(root / 'operations', work / 'operations')
    fixture = json.loads((work / 'operations/runconfig/fixtures/lint-verify.json').read_text())
    def run(data, expected=0, error=None, release='ai-gateway', mode='lint', raw=False):
        (work / 'input.json').write_text(data if raw else json.dumps(data))
        args = ['python3', '-B', 'operations/deploy/runconfig.py', 'input.json', release, mode, 'values.json']
        result = subprocess.run(args, cwd=work, capture_output=True, text=True)
        assert result.returncode == expected, (args, result.returncode, result.stdout, result.stderr)
        assert 'Traceback' not in result.stderr and 'untrusted-sentinel' not in result.stdout + result.stderr
        if error:
            assert result.stderr.strip() == error, result.stderr
        checks.append(dict(kind='input', exit=result.returncode, error=error))
        return json.loads((work / 'values.json').read_text()) if expected == 0 else None
    for data in ['{', 'null', '1', '"untrusted-sentinel"', '[]', '["untrusted-sentinel"]']:
        run(data, 2, 'RUNCONFIG_INVALID', raw=True)
    run({}, 2, 'RUNCONFIG_MISSING:purpose')
    run({'purpose': 'lint'}, 2, 'RUNCONFIG_MISSING:profile')
    for key in ['purpose', 'profile']:
        data = copy.deepcopy(fixture); data[key] = []
        run(data, 2, 'RUNCONFIG_INVALID:' + key)
    run(fixture)
    # 운영 DB 는 공유 서버라 PVC 키가 없고, 자동 백업·복원 입력도 없다. 남아 있으면 schema(additionalProperties) 위반이다.
    for key, node in (('pvcSizeGi', {'value': 1, 'approvalRef': 'operations/runconfig/fixtures/LINT-ONLY.md'}),
                      ('restore', {'pvcSizeGi': {'value': 1, 'approvalRef': 'operations/runconfig/fixtures/LINT-ONLY.md'}}),
                      ('backup', {'enabled': {'value': True, 'approvalRef': 'operations/runconfig/fixtures/LINT-ONLY.md'}})):
        data = copy.deepcopy(fixture); data[key] = node
        run(data, 2, 'RUNCONFIG_INVALID:')
    values = run(fixture)
    assert 'backup' not in values and 'persistence' not in values
    data = copy.deepcopy(fixture); data['profile'] = 'restore'
    run(data, 2, 'RUNCONFIG_INVALID:profile')
    for release in ('ai-gateway-restore', 'ai-gateway-db-restore'):
        run(fixture, 2, 'RENDER_INVALID_RELEASE', release=release)
        result = subprocess.run(['bash', 'operations/deploy/aigw-helm.sh', '--release', release, '--mode', 'lint', '--runconfig', 'input.json'], cwd=work, capture_output=True, text=True)
        assert result.returncode == 2 and result.stderr.strip() == 'RENDER_INVALID_RELEASE', result.stderr
        checks.append(dict(kind='retired release rejected', release=release, exit=result.returncode))
    gate = work / 'docs/gates/f1'; gate.mkdir(parents=True)
    (gate / 'synthetic.md').write_text('Status: APPROVED\n')
    data = copy.deepcopy(fixture); data['purpose'] = 'deploy'
    def refs(node):
        for value in node.values():
            if isinstance(value, dict):
                if 'value' in value:
                    value['approvalRef'] = 'docs/gates/f1/synthetic.md'
                else:
                    refs(value)
    refs(data)
    run(data, 2, 'RUNCONFIG_MISSING:postgres-image', mode='deploy', release='ai-gateway')
    for name in ['postgres-image', 'corpus-inventory']:
        (gate / (name + '.md')).write_text('Status: APPROVED\n')
    run(data, 2, 'RUNCONFIG_MISSING:netpol-targets', mode='deploy', release='ai-gateway')
    (gate / 'netpol-targets.md').write_text('Status: APPROVED\n')
    run(data, mode='deploy', release='ai-gateway')
    # exec-chart-ref 승인 문서는 더 이상 필요하지 않다(G-EXEC-CHART-REF 이력).
    assert not (gate / 'exec-chart-ref.md').exists()
print(json.dumps(dict(status='PASS', boundary='LOCAL actual Helm / temporary synthetic approvals only', count=len(checks), checks=checks)))
