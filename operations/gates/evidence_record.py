"""운영자 스크립트(stage gate·경보 주입)가 쓰는 OPS/RUN 증거 레코드 봉인. eval EvidenceRecord.create 와 같은 필드 규칙이다.

사용: evidence_record.py seal <spec 파일> <레코드 객체 json> <출력 json>
- 파일 하나 = 레코드 객체 하나. eval gate 는 evidence/<stage>/ 아래 모든 .json 을 레코드 객체로 읽어 검사하므로(EvidenceRepositoryFs.readAll)
  그 아래에 두는 ops 파일도 배열이 아니라 객체여야 한다.
- specSha = SPEC 실제 바이트 SHA-256(FILE_HASHED, LOCAL_FILE), recordedAt 없으면 현재 UTC, contentSha256 = 정규 JSON(키 정렬·공백 0) SHA-256.
- 값은 식별자·해시·시각·건수만 넣는다(eval 이 ingest 때 다시 검사한다).
"""
import datetime
import hashlib
import json
import pathlib
import sys


def canonical(node):
    return json.dumps(node, sort_keys=True, separators=(',', ':'), ensure_ascii=False).encode('utf-8')


def seal(spec_file, record):
    if not isinstance(record, dict):
        raise TypeError('record must be an object')
    node = dict(record, specSha=hashlib.sha256(pathlib.Path(spec_file).read_bytes()).hexdigest(), specShaSource='FILE_HASHED', specInputOrigin='LOCAL_FILE')
    node.setdefault('recordedAt', datetime.datetime.now(datetime.timezone.utc).strftime('%Y-%m-%dT%H:%M:%S.%fZ'))
    node.pop('contentSha256', None)
    node['contentSha256'] = hashlib.sha256(canonical(node)).hexdigest()
    return node


def main(argv):
    if len(argv) != 4 or argv[0] != 'seal':
        print('ARGUMENT_INVALID', file=sys.stderr)
        return 2
    try:
        record = json.loads(pathlib.Path(argv[2]).read_text(encoding='utf-8'))
        pathlib.Path(argv[3]).write_text(json.dumps(seal(argv[1], record), indent=2) + '\n', encoding='utf-8')
    except (OSError, ValueError, TypeError):
        print('EVIDENCE_RECORD_INVALID', file=sys.stderr)
        return 2
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
