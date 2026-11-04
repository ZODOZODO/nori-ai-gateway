"""v4 입력 audit 계수기는 v2 와 같은 계수 규칙이되 context 8192 경계와 v4 렌더러 기록을 쓴다(pin 된 실제 Thinking-2507 tokenizer 로 확인)."""
import importlib.util
import json
import pathlib
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[3]
SCRIPT = pathlib.Path(__file__).resolve().parents[1] / "count_input_audit_v4.py"
SPEC = importlib.util.spec_from_file_location("input_audit_v4", SCRIPT)
TOOL = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(TOOL)
TOKENIZER = ROOT / "eval/f0/tokenizer/qwen3-4b-thinking-2507.tokenizer.json"
PIN = ROOT / "eval/f0/tokenizer/qwen3-4b-thinking-2507.tokenizer.json.sha256"
RENDERED = "<|im_start|>user\nx<|im_end|>\n<|im_start|>assistant\n<think>\n"


class InputAuditV4Test(unittest.TestCase):
    def test_context_boundary_is_8192_and_renderer_is_recorded(self):
        import tokenizers
        tokens = len(tokenizers.Tokenizer.from_file(str(TOKENIZER)).encode(RENDERED, add_special_tokens=False).ids)
        rows = [
            {"id": "B/G01/first", "messagesSha256": "a" * 64, "inputLimit": 3072, "maxTokens": 8192 - tokens, "rendered": RENDERED},
            {"id": "B/G02/first", "messagesSha256": "b" * 64, "inputLimit": 3072, "maxTokens": 8193 - tokens, "rendered": RENDERED},
        ]
        with tempfile.TemporaryDirectory() as work:
            base = pathlib.Path(work)
            (base / "rendered.jsonl").write_text("".join(json.dumps(row) + "\n" for row in rows), encoding="utf-8")
            written, over = TOOL.run(["--tokenizer", str(TOKENIZER), "--expect-sha256-file", str(PIN), "--rendered", str(base / "rendered.jsonl"),
                                      "--template-sha256", "2" * 64, "--out", str(base / "out.json")])
            payload = json.loads((base / "out.json").read_text(encoding="utf-8"))
        self.assertEqual((written, over), (2, 1))
        self.assertEqual(payload["overBudgetIds"], ["B/G02/first"])
        self.assertEqual(payload["renderer"], "OLLAMA_GO_QWEN3_THINKING_V1")
        self.assertEqual(payload["tool"], "count_input_audit_v4.py")
        self.assertEqual(payload["schema"], "f0-input-audit-v2")
        self.assertEqual(TOOL.CONTEXT_TOKENS, 8192)


if __name__ == "__main__":
    unittest.main()
