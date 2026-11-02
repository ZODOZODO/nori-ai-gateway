"""F0 v2 호출 전 입력 audit: Java `f0 input-audit-render` 가 렌더한 chat 입력을 pin 된 공식 tokenizer.json 으로 계수한다.

- 모든 인자는 필수이며 누락·형식 오류는 exit 2 로 끝난다.
- tokenizer 파일 바이트의 SHA-256 이 pin 파일 값과 다르면 계수 없이 exit 3 으로 끝난다.
- tokenizer 를 대체·추정하는 경로는 없다. 지정 파일을 실제 tokenizers 엔진으로 읽지 못하면 exit 4.
- 렌더 문자열(질문·프롬프트 원문)은 콘솔·결과 파일에 쓰지 않는다. 결과는 위치 id·메시지 SHA·토큰 수와 예산 초과 id 만 담는다.
- 이 표는 미등록 준비 정적 계수이며 모델 생성·실제 usage 증거가 아니다.
"""
import argparse
import hashlib
import json
import os
import re
import sys
import tempfile

CONTEXT_TOKENS = 4096
ROW_KEYS = {"id", "messagesSha256", "inputLimit", "maxTokens", "rendered"}


class Failure(Exception):
    def __init__(self, exit_code, code):
        super().__init__(code)
        self.exit_code = exit_code
        self.code = code


class Parser(argparse.ArgumentParser):
    # argparse 기본 오류 문구 대신 고정 코드만 낸다.
    def error(self, message):
        raise Failure(2, "ARGUMENT_MISSING")


def sha256_bytes(data):
    return hashlib.sha256(data).hexdigest()


def read_bytes(path):
    try:
        with open(path, "rb") as handle:
            return handle.read()
    except OSError:
        raise Failure(2, "INPUT_MISSING")


def read_pin(path):
    text = read_bytes(path).decode("utf-8", errors="strict").strip()
    token = text.split()[0] if text else ""
    if not re.fullmatch(r"[0-9a-f]{64}", token):
        raise Failure(2, "TOKENIZER_PIN_INVALID")
    return token


def unique_object(pairs):
    keys = [key for key, _ in pairs]
    if len(keys) != len(set(keys)):
        raise Failure(2, "RENDERED_INVALID")
    return dict(pairs)


def load_rendered(path):
    rows = []
    ids = set()
    try:
        lines = read_bytes(path).decode("utf-8").split("\n")
    except UnicodeDecodeError:
        raise Failure(2, "RENDERED_INVALID")
    for line in lines:
        if not line.strip():
            continue
        try:
            row = json.loads(line, object_pairs_hook=unique_object)
        except ValueError:
            raise Failure(2, "RENDERED_INVALID")
        if (not isinstance(row, dict) or set(row) != ROW_KEYS or not isinstance(row["id"], str) or row["id"] in ids
                or not re.fullmatch(r"[0-9a-f]{64}", str(row["messagesSha256"])) or not isinstance(row["rendered"], str)
                or any(type(row[key]) is not int or row[key] < 1 for key in ("inputLimit", "maxTokens"))):
            raise Failure(2, "RENDERED_INVALID")
        ids.add(row["id"])
        rows.append(row)
    if not rows:
        raise Failure(2, "RENDERED_INVALID")
    return rows


def group_of(row_id):
    # 위치 id = <route|boundary|usage|token>/<문항>/<first|retry/CODE>. 요약은 경로·시도 종류별 최대값만 둔다.
    parts = row_id.split("/")
    attempt = "retry" if "retry" in parts else ("first" if parts[-1] == "first" else "probe")
    return parts[0] + "/" + attempt


def write_new(path, payload):
    if os.path.exists(path):
        raise Failure(2, "OUTPUT_EXISTS")
    directory = os.path.dirname(os.path.abspath(path))
    os.makedirs(directory, exist_ok=True)
    data = (json.dumps(payload, ensure_ascii=True, sort_keys=True, separators=(",", ":")) + "\n").encode("utf-8")
    handle, temp = tempfile.mkstemp(prefix=".aigw-", suffix=".tmp", dir=directory)
    try:
        with os.fdopen(handle, "wb") as stream:
            stream.write(data)
        os.replace(temp, path)
    finally:
        if os.path.exists(temp):
            os.remove(temp)


def run(argv):
    parser = Parser(add_help=False)
    for name in ("--tokenizer", "--expect-sha256-file", "--rendered", "--template-sha256", "--out"):
        parser.add_argument(name, required=True)
    args = parser.parse_args(argv)
    if not re.fullmatch(r"[0-9a-f]{64}", args.template_sha256):
        raise Failure(2, "ARGUMENT_MISSING")
    pinned = read_pin(args.expect_sha256_file)
    tokenizer_bytes = read_bytes(args.tokenizer)
    actual = sha256_bytes(tokenizer_bytes)
    if actual != pinned:
        raise Failure(3, "TOKENIZER_SHA_MISMATCH")
    rows = load_rendered(args.rendered)
    try:
        import tokenizers
        tokenizer = tokenizers.Tokenizer.from_str(tokenizer_bytes.decode("utf-8"))
    except Exception:
        raise Failure(4, "TOKENIZER_LOAD_FAILED")

    counted = []
    summary = {}
    over = []
    for row in rows:
        tokens = len(tokenizer.encode(row["rendered"], add_special_tokens=False).ids)
        counted.append({"id": row["id"], "messagesSha256": row["messagesSha256"], "inputTokens": tokens})
        group = group_of(row["id"])
        summary[group] = max(summary.get(group, 0), tokens)
        if tokens > row["inputLimit"] or tokens + row["maxTokens"] > CONTEXT_TOKENS:
            over.append(row["id"])
    payload = {
        "schema": "f0-input-audit-v2",
        "kind": "UNREGISTERED_OFFICIAL_INPUT_AUDIT_PREPARATION",
        "tool": "count_input_audit_v2.py",
        "tokenizerSha256": actual,
        "tokenizersVersion": tokenizers.__version__,
        "templateSha256": args.template_sha256,
        "renderer": "CHATML_NO_SYSTEM_DEFAULT",
        "maxInputTokensByGroup": summary,
        "overBudgetIds": over,
        "rows": counted,
    }
    write_new(args.out, payload)
    return len(counted), len(over)


def main(argv=None):
    try:
        written, over = run(sys.argv[1:] if argv is None else argv)
        print("INPUT_AUDIT_WRITTEN " + str(written) + " OVER_BUDGET " + str(over))
        return 0
    except Failure as failure:
        print(failure.code, file=sys.stderr)
        return failure.exit_code
    except Exception:
        print("COUNT_INPUT_AUDIT_FAILED", file=sys.stderr)
        return 5


if __name__ == "__main__":
    sys.exit(main())
