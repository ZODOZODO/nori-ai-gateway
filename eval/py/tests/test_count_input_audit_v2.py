"""입력 audit 계수기는 렌더 행 계약을 엄격히 검사하고 pin 불일치에서 계수하지 않습니다."""
import importlib.util
import json
import pathlib
import tempfile
import unittest

SCRIPT = pathlib.Path(__file__).resolve().parents[1] / "count_input_audit_v2.py"
SPEC = importlib.util.spec_from_file_location("input_audit", SCRIPT)
TOOL = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(TOOL)


def row(row_id="B/G01/first", **changes):
    value = {"id": row_id, "messagesSha256": "a" * 64, "inputLimit": 3072, "maxTokens": 192, "rendered": "<|im_start|>user\nx<|im_end|>\n"}
    value.update(changes)
    return value


class InputAuditTest(unittest.TestCase):
    def load(self, rows):
        with tempfile.TemporaryDirectory() as work:
            file = pathlib.Path(work) / "rendered.jsonl"
            file.write_text("".join(json.dumps(item, ensure_ascii=False) + "\n" for item in rows), encoding="utf-8")
            return TOOL.load_rendered(file)

    def test_rows_must_be_unique_complete_and_integer_budgets(self):
        self.assertEqual(len(self.load([row(), row("B/G01/retry/JSON_PARSE_FAILED")])), 2)
        for bad in ([row(), row()], [row(maxTokens=192.0)], [row(inputLimit="3072")], [row(messagesSha256="z")],
                    [{k: v for k, v in row().items() if k != "rendered"}], [dict(row(), extra=1)], []):
            with self.assertRaises(TOOL.Failure):
                self.load(bad)

    def test_groups_separate_first_retry_and_probes(self):
        self.assertEqual(TOOL.group_of("A/G01/first"), "A/first")
        self.assertEqual(TOOL.group_of("boundary/MAX-ASCII/retry/QREF_INVALID"), "boundary/retry")
        self.assertEqual(TOOL.group_of("token/TG12-NEAR-COMPOSER-LIMIT"), "token/probe")

    def test_pin_mismatch_stops_before_counting(self):
        with tempfile.TemporaryDirectory() as work:
            base = pathlib.Path(work)
            (base / "tok.json").write_bytes(b"{}")
            (base / "pin").write_text("0" * 64 + "\n", encoding="utf-8")
            with self.assertRaises(TOOL.Failure) as caught:
                TOOL.run(["--tokenizer", str(base / "tok.json"), "--expect-sha256-file", str(base / "pin"), "--rendered", str(base / "none"),
                          "--template-sha256", "1" * 64, "--out", str(base / "out.json")])
            self.assertEqual(caught.exception.exit_code, 3)
            self.assertFalse((base / "out.json").exists())


if __name__ == "__main__":
    unittest.main()
