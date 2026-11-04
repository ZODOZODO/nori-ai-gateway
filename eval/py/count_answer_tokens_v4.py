"""F0 v4 답변 토큰 로컬 계수기(계수 방법 b): 하네스가 측정 중 서버 응답의 답변 content 와 사고문(reasoning 필드)을
pin 된 공식 tokenizer.json(Qwen3-4B-Thinking-2507)으로 센다. Java `AnswerTokenCounter` 가 측정 세션마다 한 번 띄우는 상주 프로세스다.

- 인자: --tokenizer, --expect-sha256-file(필수). pin 검사·오류 코드·SHA 함수는 count_input_audit_v4.py 의 것을 그대로 쓴다.
- tokenizer 파일 바이트의 SHA-256 이 pin 값과 다르면 계수 없이 exit 3, 실제 tokenizers 엔진으로 읽지 못하면 exit 4.
- 준비가 끝나면 stdout 에 `READY <tokenizerSha256> <tokenizersVersion>` 한 줄을 쓴다.
- 요청은 stdin 한 줄 = JSON 배열 [답변 문자열|null, 사고문 문자열|null], 응답은 stdout 한 줄 = JSON 배열 [정수|null, 정수|null].
  계수 규칙은 입력 audit 과 같은 `encode(text, add_special_tokens=False)` 개수다. null 과 짝 없는 surrogate 를 가진 문자열은 셀 수 없어 null 이다.
- 문자열은 메모리에서만 세고 디스크·콘솔·오류 메시지에 쓰지 않는다. 형식 오류는 고정 코드만 stderr 에 쓰고 exit 2 로 끝난다.
"""
import importlib.util
import json
import os
import sys

_AUDIT_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "count_input_audit_v4.py")
_AUDIT_SPEC = importlib.util.spec_from_file_location("count_input_audit_v4", _AUDIT_PATH)
AUDIT = importlib.util.module_from_spec(_AUDIT_SPEC)
_AUDIT_SPEC.loader.exec_module(AUDIT)
Failure = AUDIT.Failure


def has_surrogate(text):
    return any(0xD800 <= ord(char) <= 0xDFFF for char in text)


def load(argv):
    parser = AUDIT.Parser(add_help=False)
    for name in ("--tokenizer", "--expect-sha256-file"):
        parser.add_argument(name, required=True)
    args = parser.parse_args(argv)
    pinned = AUDIT.read_pin(args.expect_sha256_file)
    tokenizer_bytes = AUDIT.read_bytes(args.tokenizer)
    actual = AUDIT.sha256_bytes(tokenizer_bytes)
    if actual != pinned:
        raise Failure(3, "TOKENIZER_SHA_MISMATCH")
    try:
        import tokenizers
        tokenizer = tokenizers.Tokenizer.from_str(tokenizer_bytes.decode("utf-8"))
    except Exception:
        raise Failure(4, "TOKENIZER_LOAD_FAILED")
    return tokenizer, actual, tokenizers.__version__


def count(tokenizer, text):
    if text is None or has_surrogate(text):
        return None
    return len(tokenizer.encode(text, add_special_tokens=False).ids)


def answer_line(tokenizer, line):
    try:
        request = json.loads(line.decode("utf-8"))
    except (UnicodeDecodeError, ValueError):
        raise Failure(2, "REQUEST_INVALID")
    if not isinstance(request, list) or len(request) != 2 or any(value is not None and not isinstance(value, str) for value in request):
        raise Failure(2, "REQUEST_INVALID")
    return (json.dumps([count(tokenizer, value) for value in request]) + "\n").encode("ascii")


def serve(tokenizer, sha, version, stdin, stdout):
    stdout.write(("READY " + sha + " " + version + "\n").encode("ascii"))
    stdout.flush()
    for line in stdin:
        stdout.write(answer_line(tokenizer, line))
        stdout.flush()


def main(argv=None):
    try:
        tokenizer, sha, version = load(sys.argv[1:] if argv is None else argv)
        serve(tokenizer, sha, version, sys.stdin.buffer, sys.stdout.buffer)
        return 0
    except Failure as failure:
        print(failure.code, file=sys.stderr)
        return failure.exit_code
    except Exception:
        print("COUNT_ANSWER_TOKENS_FAILED", file=sys.stderr)
        return 5


if __name__ == "__main__":
    sys.exit(main())
