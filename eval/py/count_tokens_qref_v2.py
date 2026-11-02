"""F0 출력 경계 케이스의 기대 JSON 문자열을 pin 된 tokenizer.json 으로 계수한다.

- 모든 인자는 필수이며 누락·형식 오류는 exit 2 로 끝난다.
- tokenizer 파일 바이트의 SHA-256 이 pin 파일 값과 다르면 계수 없이 exit 3 으로 끝난다.
- tokenizer 를 대체·추정하는 경로는 없다. 지정 파일을 실제 tokenizers 엔진으로 읽지 못하면 exit 4.
- 케이스 문자열(질문·인자 원문)은 콘솔·결과 파일에 쓰지 않는다. 결과는 caseId·kind·토큰 수와 SHA 만 담는다.
"""
import argparse
import hashlib
import json
import os
import re
import sys
import tempfile

# SPEC §14.5 과거 검증 예시(구 예시 to=23:59) — 공식 tokenizer 57토큰, q:null 생략 시 53토큰으로 기록된 값.
CALIBRATION = [
    ("SPEC-14.5-OLD-EXAMPLE", '{"t":"INC","eqp":"PHOTO_001","from":"2026-10-03T00:00","to":"2026-10-03T23:59","q":null}', 57),
    ("SPEC-14.5-OLD-EXAMPLE-NO-Q", '{"t":"INC","eqp":"PHOTO_001","from":"2026-10-03T00:00","to":"2026-10-03T23:59"}', 53),
]
KINDS = {"NORMAL", "MAX", "ESCAPE"}
FIELDS = ("t", "eqp", "from", "to", "qRef")


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


def canonical(value):
    # 기대 출력은 문자열·null 값만 갖는 평면 객체이므로 키 정렬·최소 구분자 직렬화가 RFC 8785 와 같다.
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


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
    # Java 쪽(STRICT_DUPLICATE_DETECTION)과 같이 같은 객체 안 중복 키를 거부한다.
    keys = [key for key, _ in pairs]
    if len(keys) != len(set(keys)):
        raise Failure(2, "CASES_INVALID")
    return dict(pairs)


def reject_constant(name):
    # NaN·Infinity 는 JSON 이 아니다.
    raise Failure(2, "CASES_INVALID")


def has_surrogate(text):
    return any(0xD800 <= ord(char) <= 0xDFFF for char in text)


def candidates(question):
    # Java QRefContext 와 같이 인용구 밖의 짝 없는 surrogate 도 스캔 전에 거부한다.
    if has_surrogate(question):
        raise Failure(2, "CASES_INVALID")
    values = []
    at = 0
    while at < len(question):
        if question[at] != '"':
            at += 1
            continue
        start = at
        encoded = question[max(0, at - 5):at] == "json:"
        at += 1
        while at < len(question):
            if encoded and question[at] == "\\":
                at += 2
                continue
            if question[at] == '"':
                break
            at += 1
        if at >= len(question):
            raise Failure(2, "CASES_INVALID")
        try:
            value = json.loads(question[start:at + 1]) if encoded else question[start + 1:at]
        except ValueError:
            raise Failure(2, "CASES_INVALID")
        if not isinstance(value, str) or not value or len(value) > 256 or has_surrogate(value):
            raise Failure(2, "CASES_INVALID")
        values.append(value)
        at += 1
    return values


def validate_binding(row):
    resolved = row.get("expectedResolvedOutput")
    if (set(row) != {"caseId", "kind", "expectedOutput", "expectedResolvedOutput", "expectedJson", "question", "context"}
            or not isinstance(row.get("question"), str) or not isinstance(resolved, dict)
            or set(resolved) != {"t", "eqp", "from", "to", "q"}
            or any(value is not None and not isinstance(value, str) for value in resolved.values())):
        raise Failure(2, "CASES_INVALID")
    values = candidates(row["question"])
    wire = row["expectedOutput"]
    reference = wire["qRef"]
    if reference is None:
        if values or resolved["q"] is not None:
            raise Failure(2, "CASES_INVALID")
    elif (not isinstance(reference, str) or not re.fullmatch(r"Q[1-9][0-9]*", reference)
            or int(reference[1:]) > len(values) or resolved["q"] != values[int(reference[1:]) - 1]):
        raise Failure(2, "CASES_INVALID")
    if any(wire[field] != resolved[field] for field in ("t", "eqp", "from", "to")):
        raise Failure(2, "CASES_INVALID")


def load_cases(path):
    rows = []
    ids = set()
    try:
        lines = read_bytes(path).decode("utf-8").split("\n")
    except UnicodeDecodeError:
        raise Failure(2, "CASES_INVALID")
    for line in lines:
        if not line.strip():
            continue
        try:
            row = json.loads(line, object_pairs_hook=unique_object, parse_constant=reject_constant)
        except ValueError:
            raise Failure(2, "CASES_INVALID")
        # 행은 객체여야 한다(배열·숫자·null·문자열 행은 필드 접근 전에 거부).
        if not isinstance(row, dict):
            raise Failure(2, "CASES_INVALID")
        expected = row.get("expectedOutput")
        if (not isinstance(row.get("caseId"), str) or not re.fullmatch(r"[A-Za-z0-9_.-]{1,64}", row["caseId"]) or row["caseId"] in ids
                or row.get("kind") not in KINDS or not isinstance(expected, dict) or set(expected) != set(FIELDS)
                or any(value is not None and not isinstance(value, str) for value in expected.values())
                or not isinstance(row.get("expectedJson"), str) or canonical(expected) != row["expectedJson"]):
            raise Failure(2, "CASES_INVALID")
        validate_binding(row)
        ids.add(row["caseId"])
        rows.append(row)
    if not rows:
        raise Failure(2, "CASES_INVALID")
    return rows


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
    for name in ("--tokenizer", "--expect-sha256-file", "--cases", "--out"):
        parser.add_argument(name, required=True)
    args = parser.parse_args(argv)
    if any(not value.strip() for value in (args.tokenizer, args.expect_sha256_file, args.cases, args.out)):
        raise Failure(2, "ARGUMENT_MISSING")
    pinned = read_pin(args.expect_sha256_file)
    tokenizer_bytes = read_bytes(args.tokenizer)
    actual = sha256_bytes(tokenizer_bytes)
    if actual != pinned:
        raise Failure(3, "TOKENIZER_SHA_MISMATCH")
    cases_bytes = read_bytes(args.cases)
    rows = load_cases(args.cases)
    try:
        import tokenizers
        tokenizer = tokenizers.Tokenizer.from_str(tokenizer_bytes.decode("utf-8"))
    except Exception:
        raise Failure(4, "TOKENIZER_LOAD_FAILED")

    def count(text):
        return len(tokenizer.encode(text, add_special_tokens=False).ids)

    payload = {
        "tool": "count_tokens_qref_v2.py",
        "tokenizerSha256": actual,
        "tokenizersVersion": tokenizers.__version__,
        "casesSha256": sha256_bytes(cases_bytes),
        "rows": [{"caseId": row["caseId"], "kind": row["kind"], "officialTokens": count(row["expectedJson"])} for row in rows],
        "calibration": [{"id": cid, "officialTokens": count(text), "expectedTokens": expected} for cid, text, expected in CALIBRATION],
    }
    write_new(args.out, payload)
    return len(rows)


def main(argv=None):
    try:
        written = run(sys.argv[1:] if argv is None else argv)
        print("OFFICIAL_COUNT_WRITTEN " + str(written))
        return 0
    except Failure as failure:
        print(failure.code, file=sys.stderr)
        return failure.exit_code
    except Exception:
        print("COUNT_TOKENS_FAILED", file=sys.stderr)
        return 5


if __name__ == "__main__":
    sys.exit(main())
