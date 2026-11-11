#!/usr/bin/env bash
# aigw-ps-summary.sh 셀프시험(Plan P17): fixture JSON 을 --input-file 로만 넣는다. 서버·네트워크 접속 0.
# 로컬에 python3 가 없으면 AIGW_PS_PYTHON 으로 인터프리터를 지정한다.
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
script="$here/../aigw-ps-summary.sh"
fixtures="$here/fixtures"
python_bin="${AIGW_PS_PYTHON:-python3}"
expected=1111111111111111111111111111111111111111111111111111111111111111
other=9999999999999999999999999999999999999999999999999999999999999999
failures=0

# 출력이 요약 필드(models[].name·digest·context_length, resident_count, ok)만 담는지 확인한다.
assert_summary_shape() {
  printf '%s' "$1" | "$python_bin" -I -c '
import json, sys
s = json.loads(sys.stdin.read())
assert set(s) == {"models", "resident_count", "ok"}
assert all(set(m) == {"name", "digest", "context_length"} for m in s["models"])
assert s["resident_count"] == len(s["models"])
print("true" if s["ok"] else "false")'
}

run_case() {
  local label="$1" fixture="$2" digest="$3" want_rc="$4" want_ok="$5"
  local out rc=0 got_ok="-" verdict=PASS
  out="$(bash "$script" "$digest" --input-file "$fixtures/$fixture" 2>&1)" || rc=$?
  if [[ "$want_ok" == "-" ]]; then
    [[ "$out" == "PS_SUMMARY_PARSE_FAILED" ]] || verdict=FAIL
  else
    got_ok="$(assert_summary_shape "$out" 2>/dev/null | tr -d '\r')" || verdict=FAIL
    [[ "$got_ok" == "$want_ok" ]] || verdict=FAIL
  fi
  [[ "$rc" == "$want_rc" ]] || verdict=FAIL
  if grep -q 'SENTINEL' <<<"$out"; then verdict=FAIL; fi
  [[ "$verdict" == PASS ]] || failures=$((failures + 1))
  printf '%s %s rc=%s ok=%s\n' "$verdict" "$label" "$rc" "$got_ok"
}

run_usage_case() {
  local label="$1"; shift
  local out rc=0 verdict=PASS
  out="$(bash "$script" "$@" 2>&1)" || rc=$?
  [[ "$rc" == 2 && "$out" == PS_SUMMARY_USAGE:* ]] || verdict=FAIL
  [[ "$verdict" == PASS ]] || failures=$((failures + 1))
  printf '%s %s rc=%s\n' "$verdict" "$label" "$rc"
}

run_case normal                 ps-normal.json             "$expected" 0 true
run_case generation-missing     ps-generation-missing.json "$expected" 1 false
run_case embedding-missing      ps-embedding-missing.json  "$expected" 1 false
run_case context-not-8192       ps-context-4096.json       "$expected" 1 false
run_case digest-mismatch        ps-normal.json             "$other"    1 false
run_case sentinel-not-echoed    ps-sentinel.json           "$expected" 0 true
run_case non-json               ps-non-json.txt            "$expected" 2 -
run_case empty-input            ps-empty.txt               "$expected" 2 -
run_case bad-digest-format      ps-bad-digest-format.json  "$expected" 2 -
run_usage_case usage-no-args
run_usage_case usage-short-digest 1111
run_usage_case usage-missing-file "$expected" --input-file "$fixtures/does-not-exist.json"

if [[ "$failures" -ne 0 ]]; then
  printf 'SELFTEST_FAILED failures=%s\n' "$failures"
  exit 1
fi
printf 'SELFTEST_PASSED\n'
