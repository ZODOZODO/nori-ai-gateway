"""승인 입력을 만들지 않고 임시 fixture로 입력 경계와 실제 Helm을 검사합니다."""
import copy
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile


root = Path.cwd()
exec_dir = Path(os.environ.get('AIGW_SELFTEST_EXEC_DIR', str(root.parent / 'nori-tc-exec')))
checks = []
with tempfile.TemporaryDirectory(prefix='aigw-runconfig-test-') as temporary:
    work = Path(temporary)
    shutil.copytree(root / 'ops', work / 'ops')
    fixture = json.loads((work / 'ops/runconfig/fixtures/lint-verify.json').read_text())
    def run(data, expected=0, error=None, release='ai-gateway-db', mode='lint', sha='', raw=False):
        (work / 'input.json').write_text(data if raw else json.dumps(data))
        args = ['python3', '-B', 'ops/deploy/runconfig.py', 'input.json', release, mode, 'values.json', sha]
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
    run(fixture, release='ai-gateway-db-restore')
    for release, key in [('ai-gateway-db', 'pvcSizeGi'), ('ai-gateway-db-restore', 'restore')]:
        for size in [1.0, 1.1, 0, -1]:
            data = copy.deepcopy(fixture)
            node = data[key] if key == 'pvcSizeGi' else data[key]['pvcSizeGi']
            node['value'] = size
            path = 'pvcSizeGi.value' if key == 'pvcSizeGi' else 'restore.pvcSizeGi.value'
            if size != 1.0:
                run(data, 2, 'RUNCONFIG_INVALID:' + path, release=release)
                continue
            values = run(data, release=release)
            assert values['persistence']['size'] == '1Gi'
            result = subprocess.run(['bash', 'ops/deploy/aigw-helm.sh', '--release', release, '--mode', 'render', '--exec-dir', str(exec_dir), '--runconfig', 'input.json'], cwd=work, capture_output=True, text=True)
            assert result.returncode == 0, result.stderr
            import yaml
            statefulset = next(d for d in yaml.safe_load_all(result.stdout) if d['kind'] == 'StatefulSet')
            assert statefulset['spec']['volumeClaimTemplates'][0]['spec']['resources']['requests']['storage'] == '1Gi'
            checks.append(dict(kind='actual Helm integral size render', release=release, exit=0))
    data = copy.deepcopy(fixture); data['purpose'] = 'deploy'; data['profile'] = 'restore'
    run(data, 2, 'RUNCONFIG_PROFILE_MISMATCH')
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
    run(data, 2, 'EXEC_SHA_MISMATCH', mode='deploy', sha='a' * 40)
    for name in ['exec-chart-ref', 'postgres-image', 'corpus-inventory', 'pvc-capacity', 'pvc-protection', 'backup-destination']:
        (gate / (name + '.md')).write_text('Status: APPROVED\n')
    run(data, mode='deploy', sha='0' * 40)
print(json.dumps(dict(status='PASS', boundary='LOCAL actual Helm / temporary synthetic approvals only', count=len(checks), checks=checks)))
