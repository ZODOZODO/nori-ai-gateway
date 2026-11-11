"""승인 입력을 만들지 않고 임시 fixture로 입력 경계와 실제 Helm을 검사합니다(차트 = 이 checkout 의 operations/deploy/helm)."""
import copy
import json
from pathlib import Path
import shutil
import subprocess
import tempfile

import yaml

root = Path.cwd()
checks = []
with tempfile.TemporaryDirectory(prefix='aigw-runconfig-test-') as temporary:
    work = Path(temporary)
    shutil.copytree(root / 'operations', work / 'operations')
    fixture = json.loads((work / 'operations/runconfig/fixtures/lint-verify.json').read_text())
    def run(data, expected=0, error=None, release='ai-gateway-db-restore', mode='lint', raw=False):
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
    # 운영 DB 는 공유 서버라 주 profile 의 PVC 키는 없다(Plan U6 ③ — restore.pvcSizeGi 만). 남아 있으면 schema(additionalProperties) 위반이다.
    data = copy.deepcopy(fixture); data['pvcSizeGi'] = {'value': 1, 'approvalRef': 'operations/runconfig/fixtures/LINT-ONLY.md'}
    run(data, 2, 'RUNCONFIG_INVALID:', release='ai-gateway')
    for size in [1.0, 1.1, 0, -1]:
        data = copy.deepcopy(fixture)
        data['restore']['pvcSizeGi']['value'] = size
        if size != 1.0:
            run(data, 2, 'RUNCONFIG_INVALID:restore.pvcSizeGi.value')
            continue
        values = run(data)
        assert values['persistence']['size'] == '1Gi'
        result = subprocess.run(['bash', 'operations/deploy/aigw-helm.sh', '--release', 'ai-gateway-db-restore', '--mode', 'render', '--runconfig', 'input.json'], cwd=work, capture_output=True, text=True)
        assert result.returncode == 0, result.stderr
        statefulset = next(d for d in yaml.safe_load_all(result.stdout) if d['kind'] == 'StatefulSet')
        assert statefulset['spec']['volumeClaimTemplates'][0]['spec']['resources']['requests']['storage'] == '1Gi'
        checks.append(dict(kind='actual Helm integral size render', release='ai-gateway-db-restore', exit=0))
    # 주 앱 release 는 runconfig backup.* 를 차트 값으로 넘긴다(공유 서버 백업 CronJob — 주 release 만).
    values = run(fixture, release='ai-gateway')
    assert values['backup'] == {'enabled': False, 'type': 's3', 'schedule': '15 3 * * *', 'destination': 'https://lint-only.invalid/aigw-backup/verify', 'ageRecipient': 'age1lintonly', 'retentionDays': 7}
    assert 'backup' not in run(fixture, release='ai-gateway-restore') and 'persistence' not in run(fixture, release='ai-gateway')
    data = copy.deepcopy(fixture); data['purpose'] = 'deploy'; data['profile'] = 'restore'
    run(data, 2, 'RUNCONFIG_PROFILE_MISMATCH', release='ai-gateway')
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
    for name in ['postgres-image', 'corpus-inventory', 'pvc-capacity', 'pvc-protection', 'backup-destination']:
        (gate / (name + '.md')).write_text('Status: APPROVED\n')
    run(data, 2, 'RUNCONFIG_MISSING:netpol-targets', mode='deploy', release='ai-gateway')
    (gate / 'netpol-targets.md').write_text('Status: APPROVED\n')
    run(data, mode='deploy', release='ai-gateway')
    # exec-chart-ref 승인 문서는 더 이상 필요하지 않다(G-EXEC-CHART-REF 이력).
    assert not (gate / 'exec-chart-ref.md').exists()
print(json.dumps(dict(status='PASS', boundary='LOCAL actual Helm / temporary synthetic approvals only', count=len(checks), checks=checks)))
