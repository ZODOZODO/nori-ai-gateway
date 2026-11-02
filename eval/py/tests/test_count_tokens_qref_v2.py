"""v2 wire 계수는 qRef를 세고 원문 복원 oracle의 결속을 검증합니다."""
import importlib.util
import json
import pathlib
import tempfile
import unittest

SCRIPT = pathlib.Path(__file__).resolve().parents[1] / "count_tokens_qref_v2.py"
SPEC = importlib.util.spec_from_file_location("qref_count", SCRIPT)
TOOL = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(TOOL)


class QRefCountTest(unittest.TestCase):
    def row(self, value, encoded=False):
        resolved = {"t": "INC", "eqp": None, "from": None, "to": None, "q": value}
        wire = {key: item for key, item in resolved.items() if key != "q"}
        wire["qRef"] = "Q1"
        question = "json:" + json.dumps(value, ensure_ascii=False) if encoded else '"' + value + '"'
        return {"caseId": "ESCAPE-01", "kind": "ESCAPE", "expectedOutput": wire,
                "expectedResolvedOutput": resolved, "expectedJson": TOOL.canonical(wire),
                "question": question, "context": {}}

    def load(self, row):
        with tempfile.TemporaryDirectory() as work:
            file = pathlib.Path(work) / "cases.jsonl"
            file.write_text(json.dumps(row, ensure_ascii=False) + "\n", encoding="utf-8")
            return TOOL.load_cases(file)

    def test_raw_windows_paths_and_explicit_json_are_preserved(self):
        for value, encoded in [(r"C:\temp\new\u0001", False), ('"\\\u0001😀' * 40, True)]:
            row = self.row(value, encoded)
            self.assertEqual(self.load(row)[0]["expectedResolvedOutput"]["q"], value)
            self.assertNotIn('"q":', row["expectedJson"])

    def test_wrong_ref_wrong_resolved_and_raw_wire_are_rejected(self):
        for mutation in ("reference", "resolved", "wire", "null"):
            row = self.row("private-value")
            if mutation == "reference":
                row["expectedOutput"]["qRef"] = "Q2"
            elif mutation == "resolved":
                row["expectedResolvedOutput"]["q"] = "invented"
            elif mutation == "wire":
                row["expectedOutput"]["q"] = "private-value"
            else:
                row["expectedOutput"]["qRef"] = None
            row["expectedJson"] = TOOL.canonical(row["expectedOutput"])
            with self.assertRaises(TOOL.Failure):
                self.load(row)

    def test_empty_malformed_oversized_and_unpaired_unicode_fail(self):
        for question in ('""', '"unfinished', 'json:"\\z"', '"' + '😀' * 257 + '"', 'json:"\\ud800"'):
            with self.assertRaises(TOOL.Failure):
                TOOL.candidates(question)

    def test_decoded_request_surrogates_match_java(self):
        # 요청 JSON 을 디코드한 질문 문자열 기준(Java QRefContextTest 와 같은 입력).
        def decoded(text):
            return json.loads(text)["question"]
        rejected = [r'{"question":"검색 json:\"a\ud800b\" 끝"}', r'{"question":"검색 \udc00 \"ok\""}',
                    r'{"question":"검색 \"a\udc00\""}', r'{"question":"검색 json:\"\\ud800\""}',
                    r'{"question":"검색 json:\"\\udc00x\""}']
        for text in rejected:
            with self.assertRaises(TOOL.Failure) as caught:
                TOOL.candidates(decoded(text))
            self.assertEqual(caught.exception.code, "CASES_INVALID")
        self.assertEqual(TOOL.candidates(decoded(r'{"question":"검색 json:\"\\ud83d\\ude00가\""}')), ["😀가"])
        self.assertEqual(TOOL.candidates(decoded(r'{"question":"검색 \"\ud83d\ude00\""}')), ["😀"])
        self.assertEqual(TOOL.candidates(decoded(r'{"question":"검색 \"C:\\temp\\new\\u0041\""}')), [r"C:\temp\new\u0041"])


if __name__ == "__main__":
    unittest.main()
