"""P06 실행 입력 검증. 출력은 검증된 비밀 아닌 차트 값 파일뿐입니다."""
import json
import re
import sys
from pathlib import Path

import jsonschema


class InputError(Exception):
    pass


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


def validate(data, release, mode, exec_sha=None):
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
    if data.get('purpose') == 'deploy' and profile == 'restore':
        for key in data:
            if key not in ('purpose', 'profile', 'execChartSha', 'pgImage', 'restore', 'seaweed'):
                fail('RUNCONFIG_KEY_CONFLICT:' + key)
        if set(data.get('seaweed', {})) - {'s3Endpoint'}:
            fail('RUNCONFIG_KEY_CONFLICT:seaweed')
    required = ['execChartSha', 'pgImage.tag', 'pgImage.digest']
    size_key = 'restore.pvcSizeGi' if profile == 'restore' else 'pvcSizeGi'
    required += [size_key]
    # 음성 렌더 fixture만 누락 PVC를 차트 required까지 전달합니다.
    missing_size = data.get('purpose') == 'lint' and size_key not in dict(leaves(data))
    for key in required:
        if key != size_key or not missing_size:
            item(data, key, deploy)
    for key, value in leaves(data):
        if not isinstance(value, dict) or 'value' not in value or 'approvalRef' not in value:
            fail('RUNCONFIG_MISSING:' + key)
        approval(value['approvalRef'], key, deploy)
    schema = json.loads(Path('ops/runconfig/verify.schema.json').read_text(encoding='utf-8'))
    errors = sorted(jsonschema.Draft202012Validator(schema).iter_errors(data), key=lambda e: str(list(e.path)))
    if errors:
        fail('RUNCONFIG_INVALID:' + '.'.join(map(str, errors[0].path)))
    if exec_sha and data['purpose'] != 'lint' and item(data, 'execChartSha', deploy) != exec_sha:
        fail('EXEC_SHA_MISMATCH')
    if deploy:
        for name in ['exec-chart-ref', 'postgres-image', 'corpus-inventory', 'pvc-capacity', 'pvc-protection', 'backup-destination']:
            approval('docs/gates/f1/' + name + '.md', name, True)
    return {
        'pgImage': {'tag': item(data, 'pgImage.tag', deploy), 'digest': item(data, 'pgImage.digest', deploy)},
        'persistence': {'size': '' if missing_size else str(int(item(data, size_key, deploy))) + 'Gi'},
    }


def main():
    try:
        data = load(sys.argv[1])
        values = validate(data, sys.argv[2], sys.argv[3], sys.argv[5] or None)
        Path(sys.argv[4]).write_text(json.dumps(values), encoding='utf-8')
    except InputError as error:
        print(str(error), file=sys.stderr)
        return 2
    return 0


if __name__ == '__main__':
    sys.exit(main())
