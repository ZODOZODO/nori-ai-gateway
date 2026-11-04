"""v4 답변 토큰 로컬 계수기(계수 방법 b) 실제 동작 시험 — pin 된 실제 Thinking-2507 tokenizer 로 알려진 문자열의 토큰 수를 대조하고,
stdin 한 줄 요청·stdout 한 줄 응답 규약, 원문 비노출, pin 불일치 거부를 확인한다.
실행: <venv python> -I -m unittest discover -s eval/py/tests
"""
import hashlib
import importlib.util
import io
import json
import pathlib
import subprocess
import sys
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[3]
SCRIPT = pathlib.Path(__file__).resolve().parents[1] / "count_answer_tokens_v4.py"
SPEC = importlib.util.spec_from_file_location("count_answer_tokens_v4", SCRIPT)
TOOL = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(TOOL)
TOKENIZER = ROOT / "eval/f0/tokenizer/qwen3-4b-thinking-2507.tokenizer.json"
PIN = ROOT / "eval/f0/tokenizer/qwen3-4b-thinking-2507.tokenizer.json.sha256"
SENTINEL = "SENTINEL_원문_9b2e"
# SPEC §14.5 과거 검증 예시(공식 Qwen tokenizer 57토큰, q 생략 53토큰 — count_tokens.py CALIBRATION 과 같은 값). Thinking pin 은 Instruct pin 과 바이트 동일.
KNOWN = [
    ('{"t":"INC","eqp":"PHOTO_001","from":"2026-10-03T00:00","to":"2026-10-03T23:59","q":null}', 57),
    ('{"t":"INC","eqp":"PHOTO_001","from":"2026-10-03T00:00","to":"2026-10-03T23:59"}', 53),
    ("</think>", 1),
    ("", 0),
]


def java_line(values):
    # Java 쪽(Jackson ESCAPE_NON_ASCII)과 같이 비 ASCII 를 \\u 이스케이프로 보낸다(보조 평면 문자는 surrogate 쌍 이스케이프).
    return (json.dumps(values, ensure_ascii=True) + "\n").encode("ascii")


class CountAnswerTokensV4Test(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tokenizer, cls.sha, cls.version = TOOL.load(["--tokenizer", str(TOKENIZER), "--expect-sha256-file", str(PIN)])

    def test_known_strings_match_the_pinned_official_counts(self):
        self.assertEqual(self.sha, PIN.read_text(encoding="utf-8").split()[0])
        for text, tokens in KNOWN:
            self.assertEqual(json.loads(TOOL.answer_line(self.tokenizer, java_line([text, None]))), [tokens, None], text)
        # 사고문과 답변을 한 요청에 같이 센다: 순서대로 [답변, 사고문].
        self.assertEqual(json.loads(TOOL.answer_line(self.tokenizer, java_line([KNOWN[1][0], KNOWN[0][0]]))), [53, 57])

    def test_escaped_non_ascii_is_counted_as_the_same_text_and_lone_surrogates_are_uncountable(self):
        text = "설비 PHOTO_001 \U0001F600 끝"
        expected = len(self.tokenizer.encode(text, add_special_tokens=False).ids)
        self.assertEqual(json.loads(TOOL.answer_line(self.tokenizer, java_line([text, text]))), [expected, expected])
        self.assertEqual(json.loads(TOOL.answer_line(self.tokenizer, b'["\\ud800x", "ok"]\n')), [None, 1])

    def test_invalid_requests_fail_with_a_fixed_code_and_no_raw_text(self):
        for line in (b"not json\n", java_line([SENTINEL]), java_line({"a": SENTINEL}), java_line([1, SENTINEL]), b"\xff\xfe\n"):
            with self.assertRaises(TOOL.Failure) as caught:
                TOOL.answer_line(self.tokenizer, line)
            self.assertEqual((caught.exception.exit_code, caught.exception.code), (2, "REQUEST_INVALID"))
            self.assertNotIn(SENTINEL, str(caught.exception))

    def test_serve_writes_ready_then_one_count_line_per_request_without_raw_text(self):
        stdin = io.BytesIO(java_line([SENTINEL, SENTINEL + " 사고"]) + java_line([None, ""]))
        stdout = io.BytesIO()
        TOOL.serve(self.tokenizer, self.sha, self.version, stdin, stdout)
        lines = stdout.getvalue().decode("ascii").splitlines()
        self.assertEqual(lines[0], "READY " + self.sha + " " + self.version)
        self.assertEqual(len(lines), 3)
        self.assertTrue(all(isinstance(n, int) for n in json.loads(lines[1])))
        self.assertEqual(json.loads(lines[2]), [None, 0])
        self.assertNotIn("SENTINEL", stdout.getvalue().decode("ascii"))

    def test_process_reads_stdin_writes_no_file_and_prints_no_raw_text(self):
        with tempfile.TemporaryDirectory(prefix="aigw-answer-") as work:
            done = subprocess.run([sys.executable, "-I", "-B", str(SCRIPT), "--tokenizer", str(TOKENIZER), "--expect-sha256-file", str(PIN)],
                                  input=java_line([KNOWN[0][0], SENTINEL]), capture_output=True, cwd=work, timeout=120)
            self.assertEqual(list(pathlib.Path(work).iterdir()), [])
        self.assertEqual(done.returncode, 0)
        lines = done.stdout.decode("ascii").splitlines()
        self.assertEqual(lines[0].split()[:2], ["READY", self.sha])
        self.assertEqual(json.loads(lines[1])[0], 57)
        self.assertEqual(done.stderr, b"")
        self.assertNotIn(b"SENTINEL", done.stdout)

    def test_pin_mismatch_exits_3_before_counting(self):
        with tempfile.TemporaryDirectory(prefix="aigw-answer-") as work:
            pin = pathlib.Path(work) / "wrong.sha256"
            pin.write_text(hashlib.sha256(b"other").hexdigest() + "  x\n", encoding="utf-8")
            done = subprocess.run([sys.executable, "-I", "-B", str(SCRIPT), "--tokenizer", str(TOKENIZER), "--expect-sha256-file", str(pin)],
                                  input=java_line([SENTINEL, None]), capture_output=True, timeout=120)
        self.assertEqual(done.returncode, 3)
        self.assertEqual(done.stdout, b"")
        self.assertEqual(done.stderr.decode("ascii").strip(), "TOKENIZER_SHA_MISMATCH")


if __name__ == "__main__":
    unittest.main()
