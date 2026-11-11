"""승인된 검증 역할 HBA와 차트/실행 중 서버 배선을 값 노출 없이 검사합니다."""
import hashlib
import json
from pathlib import Path
import subprocess
import sys

import yaml

SOURCE = Path('ops/postgres/acceptance-pg_hba.conf')
PGDATA = '/var/lib/postgresql/18/docker'
ORIGINAL = PGDATA + '/pg_hba.conf'
OVERLAY = '/etc/aigw/postgres/pg_hba.conf'
ROLE = 'aigw_acceptance_ro'
RELEASES = ('ai-gateway-db', 'ai-gateway-db-restore')
ARGS = ['postgres', '-c', 'hba_file=' + OVERLAY]


def require(condition):
    if not condition:
        raise ValueError('HBA_POLICY_INVALID')


def source():
    content = SOURCE.read_bytes()
    require(content.endswith(b'\n') and b'\r' not in content)
    lines = content.decode('utf-8').splitlines()
    require(len(lines) == 5)
    # 정책 정본을 복제하지 않고 승인된 행 순서와 토큰만 제한합니다.
    for index, line in enumerate(lines[:4]):
        kind = 'local' if index < 2 else 'host'
        database = 'ai_gateway' if index % 2 == 0 else 'all'
        method = 'scram-sha-256' if index % 2 == 0 else 'reject'
        tokens = [kind, database, ROLE] + (['all'] if kind == 'host' else []) + [method]
        require(line.split() == tokens and line == ' '.join(tokens))
    require(lines[4] == 'include "' + ORIGINAL + '"')
    return content


def rendered(path, release):
    require(release in RELEASES)
    content = source()
    objects = list(yaml.safe_load_all(Path(path).read_text(encoding='utf-8')))
    # P15 백업 CronJob 은 주 DB release 에만 최대 1개(release-backup)이며 HBA 검사 대상 밖이다.
    backups = [obj for obj in objects if isinstance(obj, dict) and obj.get('kind') == 'CronJob']
    require(len(backups) <= (1 if release == 'ai-gateway-db' else 0) and all(obj['metadata']['name'] == release + '-backup' for obj in backups))
    objects = [obj for obj in objects if obj not in backups]
    require(len(objects) == 6 and all(isinstance(obj, dict) for obj in objects))
    names = {(obj['kind'], obj['metadata']['name']): obj for obj in objects}
    require(len(names) == 6)
    require({obj['kind'] for obj in objects} == {'StatefulSet', 'Service', 'NetworkPolicy', 'ConfigMap', 'Job'})
    for obj in objects:
        require(obj['metadata']['name'] in (release, release + '-bootstrap', release + '-hba'))
        require(obj['metadata']['labels']['aigw.nori/db-instance'] == release)
    hba = names[('ConfigMap', release + '-hba')]
    require(hba['kind'] == 'ConfigMap' and hba['data'] == {'pg_hba.conf': content.decode('utf-8')})
    sts = next(obj for obj in objects if obj['kind'] == 'StatefulSet')
    pod = sts['spec']['template']
    require(pod['metadata']['annotations']['checksum/hba-overlay'] == hashlib.sha256(content).hexdigest())
    container = pod['spec']['containers'][0]
    require(container['args'] == ARGS and 'command' not in container)
    require(container['volumeMounts'] == [
        {'name': 'data', 'mountPath': '/var/lib/postgresql'},
        {'name': 'hba-overlay', 'mountPath': '/etc/aigw/postgres', 'readOnly': True}])
    require(pod['spec']['volumes'] == [{'name': 'hba-overlay', 'configMap': {
        'name': release + '-hba', 'optional': False, 'defaultMode': 0o444,
        'items': [{'key': 'pg_hba.conf', 'path': 'pg_hba.conf'}]}}])
    require(next(env['value'] for env in container['env'] if env['name'] == 'PGDATA') == PGDATA)
    return objects


LIVE_SQL = """SELECT json_build_object(
  'dataDirectory', current_setting('data_directory'),
  'hbaFile', current_setting('hba_file'),
  'overlaySha256', encode(sha256(pg_read_binary_file(current_setting('hba_file'))), 'hex'),
  'originalHbaSha256', encode(sha256(pg_read_binary_file('/var/lib/postgresql/18/docker/pg_hba.conf')), 'hex'),
  'rules', (SELECT json_agg(r ORDER BY rule_number) FROM
    (SELECT rule_number, file_name, line_number, type, database, user_name, address, auth_method, error
     FROM pg_hba_file_rules) r));
"""


def live_result(raw):
    data = json.loads(raw)
    require(data['dataDirectory'] == PGDATA and data['hbaFile'] == OVERLAY)
    require(data['overlaySha256'] == hashlib.sha256(source()).hexdigest())
    rules = data['rules']
    require(len(rules) > 4 and all(rule['error'] is None for rule in rules))
    for index, rule in enumerate(rules[:4]):
        require(rule['rule_number'] == index + 1 and rule['line_number'] == index + 1)
        require(rule['file_name'] == OVERLAY and rule['user_name'] == [ROLE])
        require(rule['type'] == ('local' if index < 2 else 'host'))
        require(rule['database'] == (['ai_gateway'] if index % 2 == 0 else ['all']))
        require(rule['auth_method'] == ('scram-sha-256' if index % 2 == 0 else 'reject'))
        require(rule['address'] == (None if index < 2 else 'all'))
    # 마지막 필수 include는 정본 SHA로 확인합니다. 원본 내부 include의 기존 인증도 허용합니다.
    return {'status': 'PASS', 'hbaOverlaySha256': data['overlaySha256'],
            'originalHbaPath': ORIGINAL, 'originalHbaSha256': data['originalHbaSha256'],
            'rulesValid': True}


def deployed(release):
    require(release in RELEASES)
    # 자격은 Pod 내부 env에서만 사용하며 원시 psql 오류를 외부로 전달하지 않습니다.
    command = ['kubectl', '-n', 'tc', 'exec', '-i', release + '-0', '--', 'sh', '-c',
               'PGPASSWORD="$POSTGRES_PASSWORD" psql -X -w -h 127.0.0.1 -U postgres -d ai_gateway -v ON_ERROR_STOP=1 -Atf -']
    result = subprocess.run(command, input=LIVE_SQL.encode(), capture_output=True)
    require(result.returncode == 0)
    return live_result(result.stdout)


def main():
    try:
        mode, *args = sys.argv[1:]
        if mode == 'source' and not args:
            source()
        elif mode == 'render' and len(args) == 2:
            rendered(*args)
        elif mode == 'live' and len(args) == 2:
            Path(args[1]).write_text(json.dumps(deployed(args[0])) + '\n', encoding='utf-8')
        else:
            raise ValueError('HBA_POLICY_INVALID')
    except (OSError, ValueError, KeyError, TypeError, StopIteration, IndexError, yaml.YAMLError):
        print('HBA_POLICY_INVALID', file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
