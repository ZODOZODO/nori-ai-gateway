#!/usr/bin/env bash
# 추론 서버 읽기 전용 상태 요약(Plan P17, SPEC §14.6). 운영자가 추론 서버에서 실행한다.
# loopback GET /api/ps 응답을 파이프로만 받아 모델 name·digest·context_length·resident_count·ok 만 출력한다.
# 응답 원문은 출력·저장하지 않으며, 파싱 실패 시 고정 문구만 낸다. GET 외 요청은 보내지 않는다.
# 사용: aigw-ps-summary.sh <qwen3:4b 기대 digest(sha256 hex 64자)> [--input-file <path>]
#   --input-file 은 로컬 fixture 시험 전용이다(서버 실행에 쓰지 않는다).
# 종료 코드: 0 = ok=true, 1 = ok=false, 2 = 사용법 오류·조회 실패·파싱 실패.
set -euo pipefail
set +x
export PYTHONDONTWRITEBYTECODE=1
# 로컬 시험에서 python3 가 없는 환경(Windows)을 위한 인터프리터 경로 지정만 허용한다.
PYTHON_BIN="${AIGW_PS_PYTHON:-python3}"

usage() {
  printf 'PS_SUMMARY_USAGE: aigw-ps-summary.sh <expected-digest-sha256-hex> [--input-file <path>]\n' >&2
  exit 2
}

[[ $# -eq 1 || $# -eq 3 ]] || usage
expected_digest="$1"
[[ "$expected_digest" =~ ^[0-9a-f]{64}$ ]] || usage
input_file=""
if [[ $# -eq 3 ]]; then
  [[ "$2" == "--input-file" && -f "$3" && -r "$3" ]] || usage
  input_file="$3"
fi

FILTER=$(cat <<'PY'
import json, re, sys

GENERATION_MODEL = 'qwen3:4b'
# Ollama 는 태그 없는 이름을 ':latest' 로 보고한다.
EMBEDDING_MODEL_NAMES = ('bge-m3', 'bge-m3:latest')
EXPECTED_CONTEXT_LENGTH = 8192
NAME_PATTERN = re.compile(r'[A-Za-z0-9][A-Za-z0-9._:/-]{0,199}')
DIGEST_PATTERN = re.compile(r'[0-9a-f]{64}')


def summarize(raw, expected_digest):
    document = json.loads(raw.decode('utf-8'))
    models = document['models']
    if not isinstance(models, list):
        raise ValueError
    entries = []
    for model in models:
        name = model['name']
        digest = model['digest']
        context_length = model['context_length']
        # 형식이 어긋난 값은 출력하지 않고 파싱 실패로 본다(입력 일부가 새지 않게).
        if not isinstance(name, str) or not NAME_PATTERN.fullmatch(name):
            raise ValueError
        if not isinstance(digest, str) or not DIGEST_PATTERN.fullmatch(digest):
            raise ValueError
        if type(context_length) is not int or context_length < 0:
            raise ValueError
        entries.append({'name': name, 'digest': digest, 'context_length': context_length})
    generation = [entry for entry in entries if entry['name'] == GENERATION_MODEL]
    embedding_resident = any(entry['name'] in EMBEDDING_MODEL_NAMES for entry in entries)
    ok = (bool(generation) and embedding_resident
          and all(entry['digest'] == expected_digest and entry['context_length'] == EXPECTED_CONTEXT_LENGTH
                  for entry in generation))
    return {'models': entries, 'resident_count': len(entries), 'ok': ok}


try:
    summary = summarize(sys.stdin.buffer.read(), sys.argv[1])
except BaseException:
    # 예외 메시지는 입력 일부를 담을 수 있으므로 출력하지 않는다.
    sys.stdout.write('PS_SUMMARY_PARSE_FAILED\n')
    sys.exit(2)
sys.stdout.write(json.dumps(summary, ensure_ascii=True, sort_keys=True) + '\n')
sys.exit(0 if summary['ok'] else 1)
PY
)

rc=0
if [[ -n "$input_file" ]]; then
  "$PYTHON_BIN" -I -c "$FILTER" "$expected_digest" < "$input_file" || rc=$?
else
  # 응답은 메모리 변수에만 두고 필터로 넘긴다. --fail 은 비 2xx 를 조회 실패로 만든다.
  response=""
  if ! response=$(curl -s --fail --max-time 5 http://127.0.0.1:11434/api/ps); then
    printf 'PS_SUMMARY_FETCH_FAILED\n'
    exit 2
  fi
  printf '%s' "$response" | "$PYTHON_BIN" -I -c "$FILTER" "$expected_digest" || rc=$?
fi
exit "$rc"
