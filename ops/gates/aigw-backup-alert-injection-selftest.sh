#!/usr/bin/env bash
# aigw-backup-alert-injection.sh 셀프시험(Plan P15 ④'): stub kubectl·stub gradlew·loopback 가짜 Prometheus 로만 실행한다. 실제 클러스터·경보 전달 0.
set -euo pipefail
export PYTHONDONTWRITEBYTECODE=1
exec python3 -B - "$@" <<'PY'
import base64, http.server, json, os, pathlib, shutil, subprocess, tempfile, threading, urllib.parse

root = pathlib.Path.cwd()
checks = []
prom_state = {'failed': ['1'], 'alert': ['1']}

class Prometheus(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        query = urllib.parse.parse_qs(urllib.parse.urlparse(self.path).query)['query'][0]
        series = prom_state['alert'] if 'ALERTS' in query else prom_state['failed']
        value = series.pop(0) if len(series) > 1 else series[0]
        body = json.dumps({'status': 'success', 'data': {'result': [] if value == '' else [{'value': [0, value]}]}}).encode()
        self.send_response(200); self.end_headers(); self.wfile.write(body)
    def log_message(self, *args):
        pass

server = http.server.ThreadingHTTPServer(('127.0.0.1', 0), Prometheus)
threading.Thread(target=server.serve_forever, daemon=True).start()
url = f'http://127.0.0.1:{server.server_address[1]}'
with tempfile.TemporaryDirectory(prefix='aigw-alert-injection-test-') as temporary:
    work = pathlib.Path(temporary)
    shutil.copytree(root / 'ops', work / 'ops')
    bin_dir = work / 'bin'; bin_dir.mkdir()
    (bin_dir / 'kubectl').write_text('#!/bin/sh\nexec python3 -B ' + str(work / 'ops/gates/selftest_kubectl_stub.py') + ' "$@"\n'); (bin_dir / 'kubectl').chmod(0o755)
    (bin_dir / 'sleep').write_text('#!/bin/sh\nexit 0\n'); (bin_dir / 'sleep').chmod(0o755)
    (work / 'gradlew').write_text('#!/usr/bin/env bash\nprintf "%s\\n" "$*" >> "$STUB_GRADLE_LOG"\nexit "${STUB_GRADLE_RC:-0}"\n')
    spec = work / 'spec.md'; spec.write_text('# 시험 SPEC 바이트\n')
    state_path, log_path, gradle_log = work / 'state.json', work / 'calls.jsonl', work / 'gradle.log'
    env = dict(os.environ, PATH=str(bin_dir) + os.pathsep + os.environ['PATH'], STUB_STATE=str(state_path), STUB_LOG=str(log_path), STUB_GRADLE_LOG=str(gradle_log))
    env.pop('AIGW_SPEC_FILE', None)
    secret_value = 'S3CRET-DESTINATION-SENTINEL-VALUE'
    cron = {'spec': {'jobTemplate': {'spec': {'template': {'spec': {'containers': [{'name': 'backup', 'env': [{'name': 'AIGW_BACKUP_DESTINATION', 'value': 'https://dest/x'}]}]}}}}}}
    def reset(**extra):
        state = {'cronjobs': {'ai-gateway-db-backup': cron}, 'secrets': {'ai-gateway-backup-destination': {'secret-key': base64.b64encode(secret_value.encode()).decode()}},
                 'backup_rows': ['7', '1:8:FAILED:UPLOAD', '1:9:SUCCESS:'], 'fail': {}}
        state.update(extra)
        state_path.write_text(json.dumps(state)); log_path.write_text(''); gradle_log.write_text('')
        shutil.rmtree(work / 'evidence', ignore_errors=True)
    def run(args, expected, stderr=None):
        result = subprocess.run(['bash', 'ops/gates/aigw-backup-alert-injection.sh', *args], cwd=work, env=env, capture_output=True, text=True)
        assert result.returncode == expected, (args, result.returncode, result.stdout, result.stderr)
        if stderr is not None:
            assert result.stderr.strip() == stderr, result.stderr
        checks.append({'args': [a if not a.startswith('http') else '<prometheus>' for a in args], 'exit': result.returncode})
        return result
    calls = lambda: [json.loads(line) for line in log_path.read_text().splitlines()]
    base = ['--prometheus-url', url, '--wait-seconds', '2', '--spec', str(spec)]

    # 입력 경계: 부모 회차·SPEC 누락은 exit 2·Job 생성 0.
    reset()
    run(base, 2, 'PARENT_RUN_ID_MISSING'); assert calls() == []
    run(['--parent-run-id', 'F4-20261107T000000Z-0a1b2c3d', '--prometheus-url', url, '--wait-seconds', '2'], 2, 'SPEC_INPUT_MISSING'); assert calls() == []
    run(['--parent-run-id', 'p1', '--wait-seconds', '2', '--spec', str(spec)], 2, 'PROMETHEUS_URL_MISSING'); assert calls() == []

    # 관측 전부 참: override 는 생성 Job env 에만, CronJob·Secret 변경 호출 0. firing 은 수신 확인이 아니므로 결과 = NOT_RUN(ALERT_RECEIPT_PATH_UNAPPROVED)·exit 2.
    parent = 'F4-20261107T000000Z-0a1b2c3d'
    record_file = lambda: json.loads(next((work / 'evidence/F4/ops').glob('*.json')).read_text())
    reset(); prom_state.update(failed=['1', '0'], alert=['1'])
    out = run(['--parent-run-id', parent] + base, 2)
    log = calls()
    created = [json.loads(c['stdin']) for c in log if c['args'][:3] == ['create', '-f', '-']]
    assert len(created) == 2
    overrides = [[e for e in job['spec']['template']['spec']['containers'][0]['env'] if e['name'] == 'AIGW_BACKUP_DEST_OVERRIDE'] for job in created]
    assert overrides == [[{'name': 'AIGW_BACKUP_DEST_OVERRIDE', 'value': 'unreachable-probe'}], []]
    assert not any(c['args'][0] in ('apply', 'patch', 'edit', 'replace', 'delete', 'set', 'annotate', 'label') for c in log), '공유 리소스 변경 0'
    assert json.loads(state_path.read_text())['cronjobs']['ai-gateway-db-backup'] == cron
    # 새 행 결속: 기준 id 조회 → 기준 이후 행 → 주입 행 이후 행(고정 SQL 의 id 경계).
    sql = [c['stdin'] for c in log if c['args'][0] == 'exec']
    assert len(sql) == 3 and 'max(id)' in sql[0] and 'WHERE id > 7' in sql[1] and 'WHERE id > 8' in sql[2]
    files = list((work / 'evidence/F4/ops').glob('*.json'))
    record = record_file()
    assert len(files) == 1 and isinstance(record, dict)
    assert (record['producer'], record['componentId'], record['leafId'], record['parentRunId'], record['result'], record['reasonCode']) == \
        ('OPS', 'OP:backup-alert', 'AC-24-BACKUP-ALERT', parent, 'NOT_RUN', 'ALERT_RECEIPT_PATH_UNAPPROVED')
    assert record['values']['alertFiring'] is True and record['values']['alertReceiptConfirmed'] is False
    assert record['specShaSource'] == 'FILE_HASHED' and len(record['contentSha256']) == 64
    text = json.dumps(record) + out.stdout + out.stderr
    assert secret_value not in text and 'https://dest' not in text
    ingest = gradle_log.read_text()
    assert f'ingest --stage F4 --parent-run-id {parent}' in ingest and '--source ops' in ingest and str(spec) in ingest

    # 실패 관측(경보 미발생·복귀 실패)은 FAIL 레코드를 남기고 exit 1.
    reset(); prom_state.update(failed=['1', '1'], alert=[''])
    run(['--parent-run-id', parent] + base, 1)
    record = record_file()
    assert record['result'] == 'FAIL' and 'reasonCode' not in record and record['values']['alertFiring'] is False and record['values']['failedMetricZero'] is False
    reset(backup_rows=['7', '1:8:SUCCESS:', '1:9:SUCCESS:']); prom_state.update(failed=['1', '0'], alert=['1'])
    run(['--parent-run-id', parent] + base, 1)
    assert record_file()['values']['failedRowObserved'] is False
    # 주입 사이 다른 행(동시 CronJob 실행)이 끼면 새 행이 1건이 아니므로 거짓 → FAIL.
    reset(backup_rows=['7', '2:9:FAILED:UPLOAD', '1:10:SUCCESS:']); prom_state.update(failed=['1', '0'], alert=['1'])
    run(['--parent-run-id', parent] + base, 1)
    assert record_file()['values']['failedRowObserved'] is False
    # 기준 id 조회 실패는 0 행이 아니라 중단: Job 생성 0·레코드 0.
    reset(fail={'exec:ai-gateway-db-0': 1})
    run(['--parent-run-id', parent] + base, 1, 'BACKUP_RUN_QUERY_FAILED')
    assert not any(c['args'][0] == 'create' for c in calls()) and not (work / 'evidence/F4/ops').exists()
    # Job 생성 실패는 레코드 없이 exit 1.
    reset(fail={'create': 1})
    run(['--parent-run-id', parent] + base, 1, 'JOB_CREATE_FAILED')
    assert not (work / 'evidence/F4/ops').exists() or not list((work / 'evidence/F4/ops').glob('*.json'))
server.shutdown()
print(json.dumps({'status': 'PASS', 'boundary': 'LOCAL stub kubectl/gradlew/loopback Prometheus only', 'count': len(checks)}))
PY
