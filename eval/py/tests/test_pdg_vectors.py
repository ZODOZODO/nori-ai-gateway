"""pdg_vectors.py 시험 — Plan P08 고정 기대값(root 교차 확인값)과 hashlib 계산 일치, 음성 거부, 변조 검출."""
import json
import os
import subprocess
import sys
import tempfile
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
SCRIPT = os.path.join(os.path.dirname(HERE), "pdg_vectors.py")
VECTORS = os.path.join(HERE, "fixtures", "pdg-vectors-plan-p08.json")


class PdgVectorsTest(unittest.TestCase):
    def run_tool(self, *args):
        return subprocess.run([sys.executable, "-I", SCRIPT, *args], capture_output=True, timeout=60)

    def test_plan_fixed_vectors_match_and_negatives_rejected(self):
        result = self.run_tool("--vectors", VECTORS, "--check")
        self.assertEqual(result.returncode, 0, result.stdout)
        lines = result.stdout.decode().split()
        self.assertEqual(lines.count("PASS"), 4)
        self.assertEqual(lines.count("REJECTED"), 3)

    def test_tampered_expected_value_fails(self):
        with open(VECTORS, encoding="utf-8") as handle:
            data = json.load(handle)
        data["vectors"][0]["expectedPdg"] = "0" * 64
        with tempfile.TemporaryDirectory(prefix="aigw-pdg-") as directory:
            path = os.path.join(directory, "vectors.json")
            with open(path, "w", encoding="utf-8") as handle:
                json.dump(data, handle)
            result = self.run_tool("--vectors", path, "--check")
        self.assertEqual(result.returncode, 1)

    def test_missing_arguments_exit_2(self):
        self.assertEqual(self.run_tool("--vectors", VECTORS).returncode, 2)
        self.assertEqual(self.run_tool("--check").returncode, 2)


if __name__ == "__main__":
    unittest.main()
