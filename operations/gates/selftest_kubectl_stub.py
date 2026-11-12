#!/usr/bin/env python3
"""operations/gates selftest 전용 kubectl stub(실제 클러스터 호출 0). 상태 = STUB_STATE JSON, 호출 기록 = STUB_LOG jsonl.

state 키: secrets{name:{key:b64}}, present{kind:[names]}, fail{"<동작>:<대상>": rc}, jobs{name: obj},
job_conditions{name: Complete|Failed}, pods[[pod...] 호출 순서별 목록], selector_pods{"<-l selector>": [pod...]}(있으면 그 selector 조회에 고정 응답 — 시퀀스 소비 0).
`patch secret <name> --type merge --patch-file /dev/stdin` 은 data 키를 병합한다(값 null = 키 삭제, 없는 Secret 은 실패).
`get secret <name> -o 'go-template={{index .data "<키>" | base64decode}}'` 는 그 키 값 하나를 출력한다.
"""
import base64
import json
import os
import re
import sys

state_path, log_path = os.environ['STUB_STATE'], os.environ['STUB_LOG']
state = json.load(open(state_path, encoding='utf-8'))
args = sys.argv[1:]
if args[:2] == ['-n', 'tc']:
    args = args[2:]
stdin = sys.stdin.buffer.read() if ('-' in args or '/dev/stdin' in args) else b''
with open(log_path, 'a', encoding='utf-8') as log:
    log.write(json.dumps({'args': args, 'stdin': stdin.decode('utf-8', 'replace')}) + '\n')


def save():
    json.dump(state, open(state_path, 'w', encoding='utf-8'))


def failed(key):
    rc = state.get('fail', {}).get(key)
    if rc:
        sys.exit(rc)


verb = args[0]
if verb == 'get':
    kind, name = args[1], args[2]
    failed(f'get-{kind}:{name}')
    if kind == 'secret':
        data = state.get('secrets', {}).get(name)
        output = args[args.index('-o') + 1] if '-o' in args else ''
        single = re.fullmatch(r'go-template=\{\{index \.data "([^"]+)" \| base64decode\}\}', output)
        if data is not None and output == 'json':
            print(json.dumps({'metadata': {'name': name}, 'data': data}))
        elif data is not None and single:
            # 키 하나의 값(kubectl go-template base64decode 와 같음). 키가 없으면 빈 출력.
            sys.stdout.write(base64.b64decode(data[single.group(1)]).decode() if single.group(1) in data else '')
        elif data is not None:
            print('\n'.join(sorted(data)))
    elif kind in ('statefulset', 'deployment') and '-o' in args and args[args.index('-o') + 1] == 'name':
        if name in state.get('present', {}).get(kind, []):
            print(f'{kind}.apps/{name}')
    elif kind == 'job':
        obj = state.get('jobs', {}).get(name)
        if obj is None:
            sys.exit(1)
        print(json.dumps(obj))
    elif kind in ('pod', 'pods') and '-l' in args and args[args.index('-l') + 1] in state.get('selector_pods', {}):
        print(json.dumps({'items': state['selector_pods'][args[args.index('-l') + 1]]}))
    elif kind in ('pod', 'pods'):
        sequence = state.get('pods', [])
        print(json.dumps({'items': sequence.pop(0) if len(sequence) > 1 else (sequence[0] if sequence else [])}))
        save()
    else:
        sys.exit(1)
elif verb in ('apply', 'create'):
    failed(verb)
    obj = json.loads(stdin)
    if obj['kind'] == 'Secret':
        state.setdefault('secrets', {})[obj['metadata']['name']] = obj.get('data', {})
    else:
        state.setdefault('jobs', {})[obj['metadata']['name']] = obj
    save()
elif verb == 'patch':
    kind, name = args[1], args[2]
    failed(f'patch-{kind}:{name}')
    target = state.get('secrets', {}).get(name) if kind == 'secret' else None
    if target is None or args[3:7] != ['--type', 'merge', '--patch-file', '/dev/stdin']:
        sys.exit(1)
    for key, value in json.loads(stdin).get('data', {}).items():
        if value is None:
            target.pop(key, None)
        else:
            target[key] = value
    save()
elif verb == 'delete':
    failed(f'delete-{args[1]}:{args[2]}')
    state.setdefault('secrets', {}).pop(args[2], None) if args[1] == 'secret' else state.setdefault('jobs', {}).pop(args[2], None)
    save()
elif verb == 'rollout':
    failed(f'rollout-{args[1]}:{args[2]}')
elif verb == 'wait':
    # 조건은 이름 접미사로 정한다: state.job_conditions{"<이름 접미 또는 전체>": Complete|Failed}, 없으면 -recovery 는 Complete·그 밖 Failed.
    target = next(a.split('/', 1)[1] for a in args if a.startswith('job/'))
    conditions = state.get('job_conditions', {})
    condition = next((v for k, v in conditions.items() if target.endswith(k)), 'Complete' if target.endswith('-recovery') else 'Failed')
    wanted = 'Complete' if 'condition=complete' in ' '.join(args) else 'Failed'
    sys.exit(0 if condition == wanted else 1)
else:
    sys.exit(1)
