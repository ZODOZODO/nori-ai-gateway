"""배포 실행 입력(runconfig) 검증. 출력은 검증된 비밀 아닌 차트 값 파일뿐입니다. DB 접속 값은 runconfig 가 아니라 Secret ai-gateway-config 다(Plan §4.1 2026-11-10)."""
import json
import re
import sys
from pathlib import Path

import jsonschema


class InputError(Exception):
    pass


APP_RELEASES = ('ai-gateway', 'ai-gateway-restore')
RESTORE_KEYS = ('purpose', 'profile', 'pgImage', 'restore', 'seaweed', 'netpol', 'egressProbe')


def fail(code):
    raise InputError(code)


def approval(ref, key, deploy=False):
    root = Path.cwd().resolve()
    if not isinstance(ref, str):
        fail('RUNCONFIG_MISSING:' + key)
    path = (root / ref).resolve()
    if not path.is_relative_to(root) or not path.is_file():
        fail('RUNCONFIG_MISSING:' + key)
    if deploy:
        gate_root = root / 'docs/gates'
        if not path.is_relative_to(gate_root):
            fail('RUNCONFIG_NOT_APPROVED:' + key)
        # 대기 문서의 존재를 승인으로 오인하지 않도록 명시 상태를 요구합니다.
        content = path.read_text(encoding='utf-8')
        if not re.search(r'^Status: APPROVED\s*$', content, re.M) or re.search(r'^Status: (BLOCKED|NOT_RUN|PENDING)\s*$', content, re.M):
            fail('RUNCONFIG_NOT_APPROVED:' + key)
    return path


def load(path):
    try:
        data = json.loads(Path(path).read_text(encoding='utf-8'))
    except (OSError, ValueError):
        fail('RUNCONFIG_INVALID')
    if not isinstance(data, dict):
        fail('RUNCONFIG_INVALID')
    return data


def item(data, key, deploy=False):
    node = data
    for part in key.split('.'):
        if not isinstance(node, dict) or part not in node:
            fail('RUNCONFIG_MISSING:' + key)
        node = node[part]
    if not isinstance(node, dict) or 'value' not in node or 'approvalRef' not in node:
        fail('RUNCONFIG_MISSING:' + key)
    approval(node['approvalRef'], key, deploy)
    return node['value']


def leaves(data, prefix=''):
    if not isinstance(data, dict):
        return
    for key, value in data.items():
        path = prefix + key
        if isinstance(value, dict) and 'value' not in value:
            yield from leaves(value, path + '.')
        elif path not in ('purpose', 'profile'):
            yield path, value


def validate(data, release, mode):
    if not isinstance(data, dict):
        fail('RUNCONFIG_INVALID')
    for key in ('purpose', 'profile'):
        if key not in data:
            fail('RUNCONFIG_MISSING:' + key)
    for key, allowed in (('purpose', ('lint', 'deploy')), ('profile', ('main', 'restore'))):
        if data[key] not in allowed:
            fail('RUNCONFIG_INVALID:' + key)
    deploy = mode == 'deploy'
    profile = 'restore' if release.endswith('-restore') else 'main'
    if data.get('purpose') != 'lint' and data.get('profile') != profile:
        fail('RUNCONFIG_PROFILE_MISMATCH')
    if deploy and data.get('purpose') != 'deploy':
        fail('RUNCONFIG_NOT_APPROVED:purpose')
    if data.get('profile') == 'restore':
        # §11.9 복원 runconfig = 공통 키·restore.*·seaweed.s3Endpoint 만. 첫 위반 leaf 이름만 낸다(값 0).
        for key in data:
            if key not in RESTORE_KEYS:
                fail('RUNCONFIG_KEY_CONFLICT:' + next((path for path, _ in leaves({key: data[key]})), key))
        for key in data.get('seaweed', {}) if isinstance(data.get('seaweed'), dict) else ():
            if key != 's3Endpoint':
                fail('RUNCONFIG_KEY_CONFLICT:seaweed.' + key)
    # PVC 는 복원 리허설 임시 인스턴스에만 있다(주 profile 의 pvcSizeGi 없음 — 운영 DB 는 공유 서버).
    size_key = 'restore.pvcSizeGi' if profile == 'restore' else None
    # 음성 렌더 fixture만 누락 PVC를 차트 required까지 전달합니다.
    missing_size = size_key is not None and data.get('purpose') == 'lint' and size_key not in dict(leaves(data))
    for key in ['pgImage.tag', 'pgImage.digest'] + ([size_key] if size_key and not missing_size else []):
        item(data, key, deploy)
    for key, value in leaves(data):
        if not isinstance(value, dict) or 'value' not in value or 'approvalRef' not in value:
            fail('RUNCONFIG_MISSING:' + key)
        approval(value['approvalRef'], key, deploy)
    schema = json.loads(Path('operations/runconfig/verify.schema.json').read_text(encoding='utf-8'))
    errors = sorted(jsonschema.Draft202012Validator(schema).iter_errors(data), key=lambda e: str(list(e.path)))
    if errors:
        fail('RUNCONFIG_INVALID:' + '.'.join(map(str, errors[0].path)))
    if deploy:
        gates = ['postgres-image', 'corpus-inventory', 'pvc-capacity', 'pvc-protection', 'backup-destination']
        if release in APP_RELEASES:
            gates.append('netpol-targets')
        for name in gates:
            approval('docs/gates/f1/' + name + '.md', name, True)
    if release == 'ai-gateway':
        return dict(app_values(data), backup=backup_values(data, deploy))
    if release in APP_RELEASES:
        return app_values(data)
    return {
        'pgImage': {'tag': item(data, 'pgImage.tag', deploy), 'digest': item(data, 'pgImage.digest', deploy)},
        'persistence': {'size': '' if missing_size else str(int(item(data, size_key, deploy))) + 'Gi'},
    }


def value(data, key):
    node = data
    for part in key.split('.'):
        if not isinstance(node, dict) or part not in node:
            return None
        node = node[part]
    return node


def backup_values(data, deploy):
    """주 앱 release 의 백업 CronJob 값(공유 서버 백업). lint 누락 키는 차트 required 로 넘겨 RENDER_REQUIRED_backup.<key> 로 실패시킨다."""
    enabled = value(data, 'backup.enabled')
    out = {'enabled': enabled is not None and enabled['value'] is True}
    for key in ('type', 'schedule', 'destination', 'ageRecipient', 'retentionDays'):
        node = value(data, 'backup.' + key)
        if node is None:
            if deploy and out['enabled']:
                fail('RUNCONFIG_MISSING:backup.' + key)
            continue
        out[key] = node['value']
    return out


def app_values(data):
    """앱 차트 값: 승인 참조를 그대로 붙인 NetworkPolicy 대상·egress probe 대상과 비밀 아닌 연결 값. 플래그·Secret 이름은 aigw-helm.sh 가 suite 로 정한다."""
    values = {}
    targets = value(data, 'netpol.targets') or {}
    values['netpol'] = {'targets': {key: dict(node['value'], approvalRef=node['approvalRef']) for key, node in targets.items()}}
    probe = value(data, 'egressProbe.targets')
    if probe is not None:
        values['egressProbe'] = {'targets': [dict(target, approvalRef=probe['approvalRef']) for target in probe['value']]}
    inference = {key: value(data, 'inference.' + key)['value'] for key in ('baseUrl', 'port') if value(data, 'inference.' + key) is not None}
    if inference:
        values['inference'] = inference
    kafka = {key: value(data, 'kafka.' + key)['value'] for key in ('bootstrapServers', 'topic', 'groupId') if value(data, 'kafka.' + key) is not None}
    if kafka:
        values['kafka'] = kafka
    if value(data, 'seaweed.s3Endpoint') is not None:
        values['seaweed'] = {'s3Endpoint': value(data, 'seaweed.s3Endpoint')['value']}
    if value(data, 'seaweed.fixturePrefix') is not None:
        values['fixtureCatalog'] = {'prefix': value(data, 'seaweed.fixturePrefix')['value']}
    readback = {key: value(data, 'acceptance.readback.' + key) for key in ('loki', 'tempo', 'metrics')}
    present = {key: node for key, node in readback.items() if node is not None}
    if present:
        refs = {node['approvalRef'] for node in present.values()}
        if len(present) != 3 or len(refs) != 1:
            fail('RUNCONFIG_INVALID:acceptance.readback')
        values['acceptance'] = {'readback': dict({key: node['value'] for key, node in present.items()}, approvalRef=refs.pop())}
    return values


def main():
    try:
        data = load(sys.argv[1])
        values = validate(data, sys.argv[2], sys.argv[3])
        Path(sys.argv[4]).write_text(json.dumps(values), encoding='utf-8')
    except InputError as error:
        print(str(error), file=sys.stderr)
        return 2
    return 0


if __name__ == '__main__':
    sys.exit(main())
