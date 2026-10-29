"""count_tokens.py 실제 동작 시험.

시험용 tokenizer 는 tokenizers 엔진으로 즉석 생성한 tiny WordLevel fixture 이며 공식 Qwen tokenizer 가 아니다.
이 시험은 실제 엔진 경로·SHA pin·인자·원문 비노출 계약만 확인하고 Qwen 토큰 수를 주장하지 않는다.
실행: <venv python> -I -m unittest discover -s eval/py/tests
"""
import hashlib
import json
import os
import subprocess
import sys
import tempfile
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
SCRIPT = os.path.join(os.path.dirname(HERE), "count_tokens.py")
SENTINEL = "SENTINEL_원문_7f3a"


def canonical(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


class CountTokensTest(unittest.TestCase):
    def setUp(self):
        self.work = tempfile.TemporaryDirectory(prefix="aigw-count-")
        self.dir = self.work.name
        from tokenizers import Tokenizer, models, pre_tokenizers
        tokenizer = Tokenizer(models.WordLevel({"[UNK]": 0, "a": 1, "b": 2}, unk_token="[UNK]"))
        tokenizer.pre_tokenizer = pre_tokenizers.Whitespace()
        self.tokenizer = os.path.join(self.dir, "tiny.tokenizer.json")
        tokenizer.save(self.tokenizer)
        self.pin = os.path.join(self.dir, "tiny.tokenizer.json.sha256")
        with open(self.tokenizer, "rb") as handle:
            self.sha = hashlib.sha256(handle.read()).hexdigest()
        self.write(self.pin, self.sha + "  tiny.tokenizer.json\n")
        expected = {"t": "INC", "eqp": "PHOTO_001", "from": "2026-10-03T00:00", "to": "2026-10-04T00:00", "q": SENTINEL + '"\\\u0001'}
        row = {"caseId": "ESCAPE-01", "kind": "ESCAPE", "expectedOutput": expected, "expectedJson": canonical(expected),
               "question": SENTINEL, "context": {"timezone": "+09:00", "referenceTime": "2026-10-05T09:00:00+09:00", "timeWindow": None, "selectedResourceRefs": []}}
        self.cases = os.path.join(self.dir, "cases.jsonl")
        self.write(self.cases, json.dumps(row, ensure_ascii=False) + "\n")
        self.out = os.path.join(self.dir, "out", "official-count.json")

    def tearDown(self):
        self.work.cleanup()

    def write(self, path, text):
        with open(path, "w", encoding="utf-8", newline="\n") as handle:
            handle.write(text)

    def run_tool(self, *args):
        return subprocess.run([sys.executable, "-I", SCRIPT, *args], capture_output=True, timeout=120)

    def full_args(self, **override):
        values = {"--tokenizer": self.tokenizer, "--expect-sha256-file": self.pin, "--cases": self.cases, "--out": self.out}
        values.update(override)
        result = []
        for key, value in values.items():
            if value is not None:
                result += [key, value]
        return result

    def test_real_engine_counts_and_never_echoes_raw_text(self):
        result = self.run_tool(*self.full_args())
        self.assertEqual(result.returncode, 0, result.stderr)
        with open(self.out, "rb") as handle:
            data = handle.read()
        payload = json.loads(data)
        self.assertEqual(payload["tokenizerSha256"], self.sha)
        self.assertEqual([r["caseId"] for r in payload["rows"]], ["ESCAPE-01"])
        self.assertGreater(payload["rows"][0]["officialTokens"], 0)
        self.assertEqual({c["id"] for c in payload["calibration"]}, {"SPEC-14.5-OLD-EXAMPLE", "SPEC-14.5-OLD-EXAMPLE-NO-Q"})
        for blob in (data, result.stdout, result.stderr):
            self.assertNotIn(SENTINEL.encode("utf-8"), blob)
            self.assertNotIn(SENTINEL.encode("unicode_escape"), blob)

    def test_each_missing_argument_exits_2(self):
        for key in ("--tokenizer", "--expect-sha256-file", "--cases", "--out"):
            result = self.run_tool(*self.full_args(**{key: None}))
            self.assertEqual(result.returncode, 2, key)
            self.assertEqual(result.stderr.decode().strip(), "ARGUMENT_MISSING")
        self.assertFalse(os.path.exists(self.out))

    def test_sha_mismatch_exits_3_without_output(self):
        self.write(self.pin, "0" * 64 + "\n")
        result = self.run_tool(*self.full_args())
        self.assertEqual(result.returncode, 3)
        self.assertEqual(result.stderr.decode().strip(), "TOKENIZER_SHA_MISMATCH")
        self.assertFalse(os.path.exists(self.out))

    def test_missing_tokenizer_or_invalid_pin_fails_closed(self):
        result = self.run_tool(*self.full_args(**{"--tokenizer": os.path.join(self.dir, "absent.json")}))
        self.assertEqual(result.returncode, 2)
        self.write(self.pin, "not-a-sha\n")
        result = self.run_tool(*self.full_args())
        self.assertEqual(result.returncode, 2)
        self.assertEqual(result.stderr.decode().strip(), "TOKENIZER_PIN_INVALID")

    def test_non_tokenizer_file_with_matching_pin_is_not_counted(self):
        fake = os.path.join(self.dir, "fake.json")
        self.write(fake, "{}")
        with open(fake, "rb") as handle:
            self.write(self.pin, hashlib.sha256(handle.read()).hexdigest() + "\n")
        result = self.run_tool(*self.full_args(**{"--tokenizer": fake}))
        self.assertEqual(result.returncode, 4)
        self.assertFalse(os.path.exists(self.out))

    def test_case_with_tampered_expected_json_is_rejected(self):
        with open(self.cases, encoding="utf-8") as handle:
            row = json.loads(handle.read())
        row["expectedJson"] = row["expectedJson"].replace("PHOTO_001", "PHOTO_00")
        self.write(self.cases, json.dumps(row, ensure_ascii=False) + "\n")
        result = self.run_tool(*self.full_args())
        self.assertEqual(result.returncode, 2)
        self.assertEqual(result.stderr.decode().strip(), "CASES_INVALID")

    def test_non_object_rows_exit_2_cases_invalid(self):
        for line in ("[]", "42", "null", '"text"', "true"):
            self.write(self.cases, line + "\n")
            result = self.run_tool(*self.full_args())
            self.assertEqual(result.returncode, 2, line)
            self.assertEqual(result.stderr.decode().strip(), "CASES_INVALID", line)
            self.assertFalse(os.path.exists(self.out))

    def test_contract_violations_exit_2_cases_invalid(self):
        with open(self.cases, encoding="utf-8") as handle:
            line = handle.read().strip()
        row = json.loads(line)
        numeric = dict(row["expectedOutput"], eqp=7)
        variants = {
            "duplicate caseId": line + "\n" + line,
            "duplicate row key": line[:-1] + ',"kind":"ESCAPE"}',
            "duplicate nested key": line.replace('"expectedOutput": {', '"expectedOutput": {"t": "UNS", ', 1),
            "non-string value": json.dumps(dict(row, expectedOutput=numeric, expectedJson=canonical(numeric)), ensure_ascii=False),
            "NaN value": line.replace('"t": "INC"', '"t": NaN', 1),
        }
        for name, text in variants.items():
            self.assertNotEqual(text, line, name)
            self.write(self.cases, text + "\n")
            result = self.run_tool(*self.full_args())
            self.assertEqual(result.returncode, 2, name)
            self.assertEqual(result.stderr.decode().strip(), "CASES_INVALID", name)
            self.assertFalse(os.path.exists(self.out), name)

    def test_existing_output_is_not_overwritten(self):
        self.assertEqual(self.run_tool(*self.full_args()).returncode, 0)
        with open(self.out, "rb") as handle:
            before = handle.read()
        result = self.run_tool(*self.full_args())
        self.assertEqual(result.returncode, 2)
        with open(self.out, "rb") as handle:
            self.assertEqual(handle.read(), before)


if __name__ == "__main__":
    unittest.main()
