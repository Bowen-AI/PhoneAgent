import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
from summarize import aggregate, percentile, read_runs


def run(ms, success=True, timeout=False):
    return {"run_id": str(ms), "task": "sample", "device": "Pixel",
            "model": "test-model", "backend": "CPU", "mode": "local",
            "condition": "warm", "success": success, "timeout": timeout,
            "total_ms": ms}


class TestBenchmarkAnalyzer(unittest.TestCase):
    def test_nearest_rank_percentile(self):
        self.assertEqual(percentile([1, 2, 3, 4, 5], 50), 3)
        self.assertEqual(percentile([1, 2, 3, 4, 5], 95), 5)

    def test_includes_failures_and_timeouts(self):
        result = aggregate([run(100), run(200), run(300, False, True)])[0]
        self.assertEqual(result["n"], 3)
        self.assertEqual(result["success"], 2)
        self.assertEqual(result["timeouts"], 1)
        self.assertEqual(result["p95_ms"], 300)

    def test_group_by_backend(self):
        a = run(100)
        b = {**run(200), "backend": "GPU"}
        self.assertEqual(len(aggregate([a, b])), 2)

    def test_invalid_latency_fails(self):
        with tempfile.TemporaryDirectory() as d:
            p = Path(d) / "data.jsonl"
            p.write_text(json.dumps(run(-4)))
            with self.assertRaises(ValueError):
                list(read_runs(p))


if __name__ == "__main__":
    unittest.main()
