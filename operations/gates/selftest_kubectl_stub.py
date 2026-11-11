#!/usr/bin/env python3
"""operations/gates selftest 전용 kubectl stub(실제 클러스터 호출 0). 상태 = STUB_STATE JSON, 호출 기록 = STUB_LOG jsonl.

state 키: secrets{name:{key:b64}}, present{kind:[names]}, fail{"<동작>:<대상>": rc}, sql_outputs{pod: stdout}, probe_outputs{pod: 수용 접속 분류 출력},
cronjobs{name: obj}, jobs{name: obj}, job_conditions{name: Complete|Failed}, pods[[pod...] 호출 순서별 목록],
selector_pods{"<-l selector>": [pod...]}(있으면 그 selector 조회에 고정 응답 — 시퀀스 소비 0).
"""
import json
import os
import sys

state_path, log_path = os.environ['STUB_STATE'], os.environ['STUB_LOG']
state = json.load(open(state_path, encoding='utf-8'))
args = sys.argv[1:]
if args[:2] == ['-n', 'tc']:
    args = args[2:]
stdin = sys.stdin.buffer.read() if ('-' in args or '-i' in args) else b''
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
        if data is not None and '-o' in args and args[args.index('-o') + 1] == 'json':
            print(json.dumps({'metadata': {'name': name}, 'data': data}))
        elif data is not None:
            print('\n'.join(sorted(data)))
    elif kind in ('statefulset', 'deployment', 'cronjob') and '-o' in args and args[args.index('-o') + 1] == 'name':
        if name in state.get('present', {}).get(kind, []):
            print(f'{kind}.apps/{name}')
    elif kind == 'cronjob':
        obj = state.get('cronjobs', {}).get(name)
        if obj is None:
            sys.exit(1)
        print(json.dumps(obj))
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
elif verb == 'create' and any(a.startswith('--from=cronjob/') for a in args):
    failed('create-from-cronjob')
    source = next(a for a in args if a.startswith('--from=cronjob/')).split('/', 1)[1]
    cron = state.get('cronjobs', {}).get(source)
    if cron is None:
        sys.exit(1)
    print(json.dumps({'apiVersion': 'batch/v1', 'kind': 'Job', 'metadata': {'name': next(a for a in args[2:] if not a.startswith('-'))}, 'spec': json.loads(json.dumps(cron['spec']['jobTemplate']['spec']))}))
elif verb in ('apply', 'create'):
    failed(verb)
    obj = json.loads(stdin)
    if obj['kind'] == 'Secret':
        state.setdefault('secrets', {})[obj['metadata']['name']] = obj.get('data', {})
    else:
        state.setdefault('jobs', {})[obj['metadata']['name']] = obj
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
elif verb == 'exec':
    pod = args[2]
    failed(f'exec:{pod}')
    if any('probe()' in a for a in args):
        # 수용 역할 접속 분류(기본 = 역할 부재: ai_gateway 실패·다른 DB HBA 거부). state.probe_outputs{pod: 출력}.
        default = 'select FAILED\ntcp-postgres HBA_REJECT\ntcp-template1 HBA_REJECT\nunix-postgres HBA_REJECT\nunix-template1 HBA_REJECT\n'
        sys.stdout.write(state.get('probe_outputs', {}).get(pod, default))
    elif b'count(*) FROM pg_roles' in stdin:
        sys.stdout.write(state.get('sql_outputs', {}).get(pod, '0'))
    elif b'gw.backup_run' in stdin:
        rows = state.setdefault('backup_rows', [])
        sys.stdout.write(rows.pop(0) if len(rows) > 1 else (rows[0] if rows else ''))
        save()
else:
    sys.exit(1)
