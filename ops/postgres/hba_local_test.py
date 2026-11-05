"""실제 차트 HBA를 네트워크 없는 자기 소유 PG18에서 보안 회귀 검사합니다."""
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import secrets
import subprocess
import sys
import tempfile
import time
import uuid

import yaml

ROOT = Path.cwd().resolve()
spec = importlib.util.spec_from_file_location('hba_policy', ROOT / 'ops/deploy/hba_policy.py')
policy = importlib.util.module_from_spec(spec)
spec.loader.exec_module(policy)


class CheckFailure(Exception):
    pass


class LocalTest:
    def __init__(self, image, work):
        self.image = image
        self.work = work
        self.owner = 'aigw-p06-' + uuid.uuid4().hex
        self.name = self.owner + '-postgres'
        self.volume = self.owner + '-data'
        self.container = None
        self.volume_created = False
        self.environment = dict(os.environ, PYTHONDONTWRITEBYTECODE='1')
        self.passwords = {role: secrets.token_urlsafe(32) + "'\\\"" for role in
                          ('postgres', 'aigw_migrator', 'aigw_app', 'aigw_backup', policy.ROLE)}
        self.environment.update(POSTGRES_PASSWORD=self.passwords['postgres'], POSTGRES_DB='ai_gateway')
        for role, key in [('aigw_migrator', 'AIGW_MIGRATOR_PASSWORD'), ('aigw_app', 'AIGW_APP_PASSWORD'),
                          ('aigw_backup', 'AIGW_BACKUP_PASSWORD'), (policy.ROLE, 'AIGW_ACCEPTANCE_PASSWORD')]:
            self.environment[key] = self.passwords[role]
        self.record = dict(status='FAIL', scope='LOCAL_ONLY', image=image, imageDigest=image.split('@')[1],
                           operationalGates='BLOCKED/NOT_RUN', phaseComplete=False, p07Started=False,
                           limitation='공식 로컬 candidate이며 Harbor 승인 이미지·운영 수용 증거가 아닙니다.',
                           checks=[], cleanup={})

    def check(self, name, condition, **details):
        self.record['checks'].append(dict(name=name, status='PASS' if condition else 'FAIL', **details))
        if len(self.record['checks']) % 25 == 0:
            print('LOCAL_SECURITY_CHECKS=' + str(len(self.record['checks'])), flush=True)
        if not condition:
            raise CheckFailure(name)

    def run(self, args, data=None, env=None, must=True):
        result = subprocess.run(args, input=data, capture_output=True, env=env or self.environment,
                                cwd=ROOT, timeout=180)
        if any(value[:32].encode() in result.stdout + result.stderr for value in self.passwords.values()):
            raise CheckFailure('CREDENTIAL_OUTPUT_DETECTED')
        if must and result.returncode:
            raise CheckFailure('LOCAL_COMMAND_FAILED')
        return result

    def sql(self, query, user='postgres', database='ai_gateway', host='127.0.0.1', wrong=False, must=True):
        env = self.environment | {'PGPASSWORD': 'wrong-' + secrets.token_urlsafe(32) if wrong else self.passwords[user]}
        args = ['docker', 'exec', '-i', '-e', 'PGPASSWORD', self.container, 'psql', '-X', '-w',
                '-h', host, '-U', user, '-d', database, '-v', 'ON_ERROR_STOP=1', '-v', 'VERBOSITY=sqlstate',
                '-Atq', '-f', '-']
        return self.run(args, query.encode(), env, must)

    def text(self, query, **kwargs):
        return self.sql(query, **kwargs).stdout.decode('utf-8').strip()

    def render(self, release, disabled=False):
        exec_dir = Path(os.environ.get('AIGW_SELFTEST_EXEC_DIR', str(ROOT.parent / 'nori-tc-exec'))).resolve()
        arguments = ['bash', 'ops/deploy/aigw-helm.sh', '--release', release, '--mode', 'render',
                     '--exec-dir', str(exec_dir), '--runconfig', 'ops/runconfig/fixtures/lint-verify.json']
        tools_image = os.environ.get('AIGW_HBA_TOOLS_IMAGE')
        if tools_image:
            # Windows에서도 같은 Linux 배포 스크립트를 실행하며 두 checkout은 읽기 전용입니다.
            arguments[arguments.index('--exec-dir') + 1] = '/exec'
            arguments = ['docker', 'run', '--rm', '--pull=never', '--network', 'none',
                         '--mount', f'type=bind,source={ROOT},target=/ai,readonly',
                         '--mount', f'type=bind,source={exec_dir},target=/exec,readonly',
                         '-w', '/ai', tools_image, *arguments]
        if disabled:
            arguments += ['--acceptance-role', 'disabled']
        result = self.run(arguments)
        output = self.work / (release + ('-disabled' if disabled else '') + '.yaml')
        output.write_bytes(result.stdout)
        objects = policy.rendered(output, release)
        hba = next(obj for obj in objects if obj['kind'] == 'ConfigMap' and obj['metadata']['name'].endswith('-hba'))
        sts = next(obj for obj in objects if obj['kind'] == 'StatefulSet')
        job = next(obj for obj in objects if obj['kind'] == 'Job')
        boot = job['spec']['template']['spec']['containers'][0]
        self.check('render-' + release + ('-disabled' if disabled else '-enabled'),
                   ('acceptance_role_enabled=' + ('false' if disabled else 'true')) in ' '.join(boot['args'])
                   and any(env['name'] == 'AIGW_ACCEPTANCE_PASSWORD' for env in boot['env']) != disabled,
                   resources=len(objects), backupCronJobs=0)
        return hba['data']['pg_hba.conf'].encode(), sts['spec']['template']['spec']['containers'][0]['args']

    def remove_container(self):
        if self.container:
            labels = json.loads(self.run(['docker', 'inspect', '--format', '{{json .Config.Labels}}', self.container]).stdout)
            self.check('cleanup-container-owner', labels.get('aigw.p06.owner') == self.owner)
            try:
                self.run(['docker', 'logs', self.container])
            finally:
                # 로그 검사 실패도 자기 컨테이너 정리를 건너뛰지 않도록 합니다.
                self.run(['docker', 'rm', '--force', self.container])
                self.container = None

    def create(self, args, directory):
        command = ['docker', 'create', '--pull=never', '--name', self.name, '--label', 'aigw.p06.owner=' + self.owner,
                   '--network', 'none', '--mount', f'type=volume,source={self.volume},target=/var/lib/postgresql',
                   '--mount', f'type=bind,source={directory},target=/etc/aigw/postgres,readonly',
                   '-e', 'POSTGRES_PASSWORD', '-e', 'POSTGRES_DB', '-e', 'PGDATA=' + policy.PGDATA,
                   self.image, *args]
        self.container = self.run(command).stdout.decode().strip()
        self.run(['docker', 'start', self.container])

    def ready(self, name):
        for _ in range(90):
            state = json.loads(self.run(['docker', 'inspect', '--format', '{{json .State}}', self.container]).stdout)
            if not state['Running']:
                break
            result = self.sql('SELECT 1;', must=False)
            if result.returncode == 0 and result.stdout.strip() == b'1':
                self.check(name, True)
                return
            time.sleep(0.5)
        self.check(name, False)

    def bootstrap(self, enabled):
        args = ['docker', 'exec', '-i', '-e', 'PGPASSWORD']
        for key in ('AIGW_MIGRATOR_PASSWORD', 'AIGW_APP_PASSWORD', 'AIGW_BACKUP_PASSWORD', 'AIGW_ACCEPTANCE_PASSWORD'):
            args += ['-e', key]
        args += [self.container, 'psql', '-X', '-w', '-h', '127.0.0.1', '-U', 'postgres', '-d', 'ai_gateway',
                 '-v', 'ON_ERROR_STOP=1', '-v', 'acceptance_role_enabled=' + str(enabled).lower(), '-f', '-']
        result = self.run(args, (ROOT / 'ops/postgres/bootstrap.sql').read_bytes(),
                          self.environment | {'PGPASSWORD': self.passwords['postgres']})
        self.check('bootstrap-' + str(enabled).lower(), result.returncode == 0)

    def original_hash(self):
        return self.text("SELECT encode(sha256(pg_read_binary_file('" + policy.ORIGINAL + "')), 'hex');")

    def settings(self):
        # 값은 비교할 때만 메모리에 보유하고 증거에는 변경된 설정 이름만 남깁니다.
        return json.loads(self.text('SELECT json_object_agg(name, setting) FROM pg_settings;'))

    def other_auth(self):
        results = {}
        for role in ('postgres', 'aigw_app', 'aigw_migrator', 'aigw_backup'):
            for host in ('127.0.0.1', '/var/run/postgresql'):
                for wrong in (False, True):
                    result = self.sql('SELECT current_user;', user=role, host=host, wrong=wrong, must=False)
                    passed = result.returncode == 0 and result.stdout.strip() == role.encode()
                    results[(role, host, wrong)] = (passed, b'password authentication failed' in result.stderr)
                    if not wrong:
                        self.check('other-role-login-' + role + '-' + host, passed)
                    if wrong:
                        # initdb 원본의 loopback trust/peer도 보존 대상이므로 대조군과 결과를 비교합니다.
                        self.check('other-role-auth-result-' + role + '-' + host,
                                   passed or results[(role, host, wrong)][1])
        self.check('admin-maintenance', self.text('VACUUM; SELECT 1;', database='postgres') == '1')
        self.check('migrator-schema-maintenance', self.sql('CREATE SCHEMA p06_migrator; DROP SCHEMA p06_migrator;',
                                                         user='aigw_migrator').returncode == 0)
        return results

    def boundary(self, stage, ipv6):
        live = policy.live_result(self.sql(policy.LIVE_SQL).stdout)
        self.record['hba'] = live
        self.check(stage + '-live-policy', live['status'] == 'PASS')
        hosts = ['127.0.0.1', '/var/run/postgresql'] + (['::1'] if ipv6 else [])
        for host in hosts:
            result = self.sql('SELECT value FROM p06_private.probe;', user=policy.ROLE, host=host)
            self.check(stage + '-target-read-' + host, result.stdout.strip() == b'7')
            bad = self.sql('SELECT 1;', user=policy.ROLE, host=host, wrong=True, must=False)
            self.check(stage + '-wrong-password-' + host, bad.returncode != 0 and b'password authentication failed' in bad.stderr)
            for database in ('postgres', 'p06_outside', 'template1'):
                result = self.sql('SELECT 1;', user=policy.ROLE, database=database, host=host, must=False)
                self.check(stage + '-outside-' + database + '-' + host,
                           result.returncode != 0 and b'pg_hba.conf rejects connection' in result.stderr,
                           exit=result.returncode, reason='HBA_REJECT')
        queries = {
            'insert': 'INSERT INTO p06_private.probe VALUES(8)',
            'update': 'UPDATE p06_private.probe SET value=8',
            'delete': 'DELETE FROM p06_private.probe',
            'truncate': 'TRUNCATE p06_private.probe',
            'create': 'CREATE TABLE public.p06_forbidden(value integer)',
            'create-schema': 'CREATE SCHEMA p06_forbidden',
            'alter': 'ALTER TABLE p06_private.probe ADD COLUMN forbidden integer',
            'drop': 'DROP TABLE p06_private.probe',
            'temp': 'CREATE TEMP TABLE p06_forbidden(value integer)',
        }
        for name, query in queries.items():
            # 읽기 전용 트랜잭션 오류(25006)를 실제 권한 거부로 세지 않습니다.
            result = self.sql('SET default_transaction_read_only=off; ' + query + ';', user=policy.ROLE, must=False)
            self.check(stage + '-permission-' + name, result.returncode != 0 and b'42501' in result.stderr
                       and b'25006' not in result.stderr, sqlstate='42501', exit=result.returncode)

    def connection_limit(self):
        processes = []
        try:
            for index in range(2):
                env = self.environment | {'PGPASSWORD': self.passwords[policy.ROLE], 'PGAPPNAME': 'p06-limit-' + str(index)}
                args = ['docker', 'exec', '-i', '-e', 'PGPASSWORD', '-e', 'PGAPPNAME', self.container, 'psql',
                        '-X', '-w', '-h', '127.0.0.1', '-U', policy.ROLE, '-d', 'ai_gateway', '-Atqc', 'SELECT pg_sleep(60)']
                processes.append(subprocess.Popen(args, env=env, cwd=ROOT, stdout=subprocess.PIPE, stderr=subprocess.PIPE))
            for _ in range(60):
                if self.text("SELECT count(*) FROM pg_stat_activity WHERE usename='aigw_acceptance_ro' AND application_name LIKE 'p06-limit-%';") == '2':
                    break
                time.sleep(0.1)
            else:
                raise CheckFailure('CONNECTION_LIMIT_PRECONDITION')
            result = self.sql('SELECT 1;', user=policy.ROLE, must=False)
            self.check('connection-limit-two-actual', result.returncode != 0 and b'too many connections for role' in result.stderr)
        finally:
            self.sql("SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE usename='aigw_acceptance_ro' AND application_name LIKE 'p06-limit-%';")
            for process in processes:
                stdout, stderr = process.communicate(timeout=20)
                if any(value[:32].encode() in stdout + stderr for value in self.passwords.values()):
                    raise CheckFailure('CREDENTIAL_OUTPUT_DETECTED')

    def negative_start(self, name, directory, args, reason):
        self.remove_container()
        self.create(args, directory)
        for _ in range(60):
            state = json.loads(self.run(['docker', 'inspect', '--format', '{{json .State}}', self.container]).stdout)
            if not state['Running']:
                break
            time.sleep(0.2)
        logs = self.run(['docker', 'logs', self.container]).stdout + self.run(['docker', 'logs', self.container]).stderr
        self.check(name, not state['Running'] and state['ExitCode'] != 0 and reason in logs
                   and b'could not load' in logs and policy.OVERLAY.encode() in logs,
                   exit=state['ExitCode'], fallbackStarted=state['Running'])
        self.remove_container()

    def execute(self):
        content, args = self.render('ai-gateway-db')
        for release in policy.RELEASES:
            other, other_args = self.render(release, disabled=True)
            self.check('disabled-overlay-preserved-' + release, other == content and other_args == args)
        restore, restore_args = self.render('ai-gateway-db-restore')
        self.check('release-policy-identical', restore == content and restore_args == args)
        self.record['hbaOverlaySha256'] = hashlib.sha256(content).hexdigest()
        self.record['toolsImage'] = os.environ.get('AIGW_HBA_TOOLS_IMAGE', 'host Helm/Python')
        good = self.work / 'good'; good.mkdir()
        (good / 'pg_hba.conf').write_bytes(content)
        self.run(['docker', 'volume', 'create', '--label', 'aigw.p06.owner=' + self.owner, self.volume])
        self.volume_created = True
        self.create(args, good)
        self.ready('fresh-pgdata-start')
        self.record['originalHbaBeforeSha256'] = self.original_hash()
        self.bootstrap(True); self.bootstrap(True)
        self.check('bootstrap-no-gw-schema', self.text("SELECT count(*) FROM pg_namespace WHERE nspname='gw';") == '0')
        self.record['postgresVersion'] = self.text('SELECT version();')
        self.record['extensions'] = json.loads(self.text('SELECT json_agg(row_to_json(e)) FROM (SELECT extname, extversion FROM pg_extension ORDER BY extname) e;'))
        self.check('pg18-vector', self.text('SHOW server_version_num;').startswith('18')
                   and self.text("SELECT extversion FROM pg_extension WHERE extname='vector';") == '0.8.7')
        self.check('pg-trgm-available-unused', self.text("SELECT installed_version IS NULL FROM pg_available_extensions WHERE name='pg_trgm';") == 't')
        self.check('acceptance-role-attributes', self.text("SELECT rolcanlogin AND NOT rolsuper AND NOT rolcreatedb AND NOT rolcreaterole AND NOT rolreplication AND NOT rolbypassrls AND rolconnlimit=2 FROM pg_roles WHERE rolname='aigw_acceptance_ro';") == 't')
        self.check('acceptance-membership', self.text("SELECT pg_has_role('aigw_acceptance_ro','pg_read_all_data','MEMBER');") == 't')
        self.check('target-no-create-temp', self.text("SELECT NOT has_database_privilege('aigw_acceptance_ro','ai_gateway','CREATE') AND NOT has_database_privilege('aigw_acceptance_ro','ai_gateway','TEMP');") == 't')
        self.check('readonly-default-defense-only', self.text('SHOW default_transaction_read_only;', user=policy.ROLE) == 'on')
        self.check('public-and-other-db-acl-preserved', self.text("SELECT has_database_privilege('aigw_acceptance_ro','postgres','CONNECT') AND has_database_privilege('aigw_acceptance_ro','postgres','TEMP') AND NOT has_schema_privilege('aigw_app','public','CREATE') AND NOT has_database_privilege('aigw_app','ai_gateway','CREATE') AND has_database_privilege('aigw_migrator','ai_gateway','CREATE');") == 't')
        self.sql('CREATE SCHEMA p06_private; REVOKE ALL ON SCHEMA p06_private FROM PUBLIC; CREATE TABLE p06_private.probe(value integer); INSERT INTO p06_private.probe VALUES(7);')
        self.sql('CREATE DATABASE p06_outside;')
        self.sql('CREATE SCHEMA p06_private; REVOKE ALL ON SCHEMA p06_private FROM PUBLIC; CREATE TABLE p06_private.probe(value integer); INSERT INTO p06_private.probe VALUES(7);', database='p06_outside')
        ipv6_result = self.sql('SELECT 1;', host='::1', must=False)
        ipv6 = ipv6_result.returncode == 0
        self.record['ipv6'] = {'status': 'PASS' if ipv6 else 'NOT_RUN', 'reason': 'LOOPBACK_AVAILABLE' if ipv6 else 'LOCAL_IPV6_UNAVAILABLE_DEPLOYMENT_RECHECK_REQUIRED'}
        self.boundary('fresh', ipv6)
        self.connection_limit()
        overlay_auth = self.other_auth()
        overlay_settings = self.settings()
        self.remove_container()
        # 원본 인증과 서버 기본값을 같은 볼륨에서 비교하는 격리 대조군입니다.
        self.create(['postgres'], good)
        self.ready('original-policy-control-start')
        original_settings = self.settings()
        self.check('other-role-auth-original-equal', self.other_auth() == overlay_auth)
        self.record['otherRoleAuthentication'] = [dict(role=role, host=host, wrongPassword=wrong,
                                                       allowed=result[0], matchesOriginal=True)
                                                   for (role, host, wrong), result in overlay_auth.items()]
        changed = sorted(key for key in overlay_settings if overlay_settings[key] != original_settings[key])
        self.check('server-default-settings-preserved', changed == ['hba_file'], changedSettingNames=changed)
        self.remove_container()
        self.create(args, good)
        self.ready('existing-pgdata-overlay-start')
        self.boundary('restart', ipv6)
        self.check('existing-pgdata-auth-preserved', self.other_auth() == overlay_auth)
        self.check('timeout-unchanged', self.settings()['statement_timeout'] == original_settings['statement_timeout'])
        for enabled in (False, False):
            self.bootstrap(enabled)
            self.check('disabled-role-absent', self.text("SELECT count(*) FROM pg_roles WHERE rolname='aigw_acceptance_ro';") == '0')
            result = self.sql('SELECT 1;', user=policy.ROLE, must=False)
            self.check('disabled-role-reconnect-denied', result.returncode != 0)
        self.bootstrap(True)
        self.boundary('reenabled', ipv6)
        self.check('fixture-unchanged', self.text('SELECT value FROM p06_private.probe;') == '7')
        self.check('outside-admin-read-preserved', self.text('SELECT value FROM p06_private.probe;', database='p06_outside') == '7')
        self.record['originalHbaAfterSha256'] = self.original_hash()
        self.check('original-hba-bytes-preserved', self.record['originalHbaBeforeSha256'] == self.record['originalHbaAfterSha256'])
        self.remove_container()
        missing = self.work / 'missing'; missing.mkdir()
        self.negative_start('missing-overlay-file-start-fails', missing, args, b'No such file or directory')
        bad = self.work / 'invalid'; bad.mkdir()
        (bad / 'pg_hba.conf').write_bytes(b'invalid p06 policy\n')
        self.negative_start('invalid-syntax-start-fails', bad, args, b'invalid connection type')
        (bad / 'pg_hba.conf').write_bytes(content.replace(policy.ORIGINAL.encode(), (policy.PGDATA + '/p06_absent.conf').encode()))
        self.negative_start('missing-original-include-start-fails', bad, args, b'p06_absent.conf')
        self.create(args, good)
        self.ready('valid-policy-recovery-start')
        self.boundary('recovery', ipv6)
        self.check('recovery-original-hba-preserved', self.original_hash() == self.record['originalHbaBeforeSha256'])
        self.check('recovery-server-defaults-preserved', self.settings() == overlay_settings)
        host = json.loads(self.run(['docker', 'inspect', '--format', '{{json .HostConfig}}', self.container]).stdout)
        mounts = json.loads(self.run(['docker', 'inspect', '--format', '{{json .Mounts}}', self.container]).stdout)
        self.check('isolation-and-readonly-mount', host['NetworkMode'] == 'none'
                   and not host['PortBindings'] and any(m['Destination'] == '/etc/aigw/postgres' and not m['RW'] for m in mounts))
        self.check('credentials-not-in-temporary-files', not any(value[:32].encode() in path.read_bytes()
                   for path in self.work.rglob('*') if path.is_file() for value in self.passwords.values()))
        self.run(['docker', 'logs', self.container])
        self.check('credentials-not-in-captured-output', True)
        self.record['status'] = 'LOCAL_VALIDATED'

    def cleanup(self):
        removed = True
        try:
            self.remove_container()
        except (CheckFailure, OSError, ValueError, subprocess.SubprocessError):
            removed = self.container is None
            self.record['status'] = 'FAIL'
        volume_removed = not self.volume_created
        if self.volume_created:
            info = self.run(['docker', 'volume', 'inspect', self.volume], must=False)
            if info.returncode == 0 and json.loads(info.stdout)[0]['Labels'].get('aigw.p06.owner') == self.owner:
                volume_removed = self.run(['docker', 'volume', 'rm', self.volume], must=False).returncode == 0
        self.record['cleanup'] = dict(containerRemoved=removed, volumeRemoved=volume_removed)
        if not removed or not volume_removed:
            self.record['status'] = 'FAIL'


def main():
    parser = argparse.ArgumentParser(description='P06 격리 로컬 HBA 보안 회귀')
    parser.add_argument('--image', required=True)
    parser.add_argument('--out', required=True)
    args = parser.parse_args()
    if not re.fullmatch(r'[A-Za-z0-9./:_-]*pg18[A-Za-z0-9._-]*@sha256:[a-f0-9]{64}', args.image):
        print('LOCAL_IMAGE_PIN_REQUIRED', file=sys.stderr)
        return 2
    output = Path(args.out)
    if output.exists() or not output.parent.is_dir():
        print('NEW_OUTPUT_PATH_REQUIRED', file=sys.stderr)
        return 2
    with tempfile.TemporaryDirectory(prefix='aigw-hba-local-') as temporary:
        test = LocalTest(args.image, Path(temporary))
        try:
            test.execute()
        except CheckFailure as error:
            test.record['status'] = 'FAIL'
            test.record['error'] = 'LOCAL_SECURITY_REGRESSION_FAILED'
            test.record['failedCheck'] = str(error)
        except (OSError, ValueError, KeyError, TypeError, StopIteration, subprocess.SubprocessError, yaml.YAMLError):
            # SQL/외부 응답의 원시 예외에는 자격이 섞일 수 있어 고정 메시지만 기록합니다.
            test.record['status'] = 'FAIL'
            test.record['error'] = 'LOCAL_SECURITY_REGRESSION_FAILED'
        finally:
            test.cleanup()
        record = test.record
    record['cleanup']['temporaryDirectoryRemoved'] = not Path(temporary).exists()
    with output.open('x', encoding='utf-8', newline='\n') as stream:
        json.dump(record, stream, ensure_ascii=False, indent=2)
        stream.write('\n')
    print(json.dumps(dict(status=record['status'], checks=len(record['checks']), evidence=str(output)), ensure_ascii=False))
    return 0 if record['status'] == 'LOCAL_VALIDATED' else 1


if __name__ == '__main__':
    sys.exit(main())
