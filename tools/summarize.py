#!/usr/bin/env python3
"""Summarize verified task outcomes and total latency; no external dependencies."""
import argparse
import json
import math
from collections import defaultdict
from pathlib import Path

KEYS = ("device", "task", "model", "backend", "mode", "condition")


def percentile(values, percent):
    if not values:
        return None
    ordered = sorted(values)
    return ordered[max(0, math.ceil(len(ordered) * percent / 100) - 1)]


def read_runs(path):
    for number, line in enumerate(Path(path).read_text(encoding="utf-8").splitlines(), 1):
        if not line.strip():
            continue
        try:
            run = json.loads(line)
            missing = [k for k in (*KEYS, "run_id", "success", "total_ms") if k not in run]
            if missing:
                raise ValueError("missing keys: " + ", ".join(missing))
            if type(run["success"]) is not bool:
                raise ValueError("success must be boolean")
            ms = run["total_ms"]
            if type(ms) not in (int, float) or not math.isfinite(ms) or ms < 0:
                raise ValueError("total_ms must be finite and nonnegative")
            yield run
        except (ValueError, TypeError, json.JSONDecodeError) as err:
            raise ValueError(f"{path}:{number}: {err}") from err


def aggregate(runs):
    groups = defaultdict(list)
    for run in runs:
        groups[tuple(str(run[k]) for k in KEYS)].append(run)
    report = []
    for key, rows in sorted(groups.items()):
        times = [float(r["total_ms"]) for r in rows]
        n = len(rows)
        successes = sum(1 for r in rows if r["success"])
        report.append({
            **dict(zip(KEYS, key)),
            "n": n,
            "success": successes,
            "success_rate": round(successes / n, 4),
            "timeouts": sum(1 for r in rows if r.get("timeout", False)),
            "mean_ms": round(sum(times) / n, 2),
            "p50_ms": percentile(times, 50),
            "p90_ms": percentile(times, 90),
            "p95_ms": percentile(times, 95),
        })
    return report


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("jsonl")
    parser.add_argument("--json", action="store_true")
    args = parser.parse_args()
    result = aggregate(read_runs(args.jsonl))
    if args.json:
        print(json.dumps(result, indent=2))
    elif not result:
        print("No measurements found")
    else:
        for row in result:
            print(" ".join(f"{k}={row[k]}" for k in KEYS)
                  + f" n={row['n']} success={row['success']}/{row['n']}"
                  + f" timeouts={row['timeouts']} mean={row['mean_ms']}ms"
                  + f" p50={row['p50_ms']}ms p90={row['p90_ms']}ms p95={row['p95_ms']}ms")


if __name__ == "__main__":
    main()
