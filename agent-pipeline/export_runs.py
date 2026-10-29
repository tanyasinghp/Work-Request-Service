#!/usr/bin/env python3
"""Builds web/src/data/agentRuns.json from the real run logs in runs/*/run.json.

Only recorded facts are exported. Manual fixes and review notes come from an optional
review.json the human reviewer writes next to run.json. Each run's summary.md is regenerated
from run.json and review.json, so the summaries carry the review too.

    python export_runs.py [--output ../web/src/data/agentRuns.json]
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any

from config import REPO_ROOT, RUNS_DIR
from report import summary_markdown

DEFAULT_OUTPUT = REPO_ROOT / "web/src/data/agentRuns.json"
OUTPUT_LIMIT = 3000


def story_body(story: str) -> str:
    """The story without its '# Title' line, which the UI already shows."""
    lines = story.strip().splitlines()
    if lines and lines[0].startswith("# "):
        lines = lines[1:]
    return "\n".join(lines).strip()


def load_review(run_dir: Path) -> dict[str, list[str]] | None:
    """{'manualFixes': [...], 'notes': [...]} from review.json, or None if not reviewed yet."""
    review = run_dir / "review.json"
    if not review.exists():
        return None
    data = json.loads(review.read_text(encoding="utf-8"))
    return {"manualFixes": list(data.get("manualFixes", [])), "notes": list(data.get("notes", []))}


def gate_attempt(attempt: dict[str, Any]) -> dict[str, Any]:
    exported: dict[str, Any] = {"attempt": attempt["attempt"], "passed": bool(attempt.get("passed"))}
    if attempt.get("rejectedBeforeGate"):
        exported["rejectedBeforeGate"] = attempt["rejectedBeforeGate"][:OUTPUT_LIMIT]
    if attempt.get("failingTest"):
        exported["failingTest"] = attempt["failingTest"]
    if attempt.get("output"):
        exported["output"] = attempt["output"][:OUTPUT_LIMIT]
    return exported


def summary_text(data: dict[str, Any]) -> str:
    if data.get("prDescription"):
        return data["prDescription"]
    where = data.get("stoppedAt") or "an unexpected error"
    return f"No PR description: the run {data.get('outcome', 'ended')} at {where}. {data.get('reason', '')}".strip()


def to_agent_run(run_dir: Path, data: dict[str, Any]) -> dict[str, Any]:
    criteria = data.get("criteria") or {}
    review = load_review(run_dir)
    llm = data.get("llm") or {}
    return {
        "id": run_dir.name,
        "title": data.get("storyTitle") or criteria.get("title") or data.get("storyId", run_dir.name),
        "date": (data.get("startedAt") or "")[:10],
        "story": story_body(data.get("story", "")),
        "outcome": data.get("outcome", "error"),
        "stoppedAt": data.get("stoppedAt"),
        "branch": data.get("branch"),
        "llm": {"backend": llm.get("backend"), "models": llm.get("models", [])},
        "durationSeconds": data.get("durationSeconds"),
        "costUsd": data.get("totalCostUsd"),
        "enrich": {
            "criteria": criteria.get("criteria", []),
            "affectedClasses": criteria.get("affectedClasses", []),
            "outOfScope": criteria.get("outOfScope", []),
        },
        "implement": {"attempts": [
            {"attempt": attempt["attempt"],
             "summary": attempt.get("summary") or "; ".join(attempt.get("violations", [])) or "no diff produced",
             "diffLines": attempt.get("diffLines", 0)}
            for attempt in data.get("implementAttempts", [])]},
        "writeTests": {"testNames": (data.get("tests") or {}).get("testNames", [])},
        "gate": {"attempts": [gate_attempt(attempt) for attempt in data.get("gateAttempts", [])]},
        "summary": {"text": summary_text(data), "manualFixes": review["manualFixes"] if review else [],
                    "reviewed": review is not None, "reviewNotes": review["notes"] if review else []},
    }


def collect(runs_dir: Path = RUNS_DIR, refresh_summaries: bool = True) -> list[dict[str, Any]]:
    runs = []
    for run_file in sorted(runs_dir.glob("*/run.json")):
        data = json.loads(run_file.read_text(encoding="utf-8"))
        if refresh_summaries:
            review = load_review(run_file.parent)
            (run_file.parent / "summary.md").write_text(
                summary_markdown(data, review["manualFixes"] if review else None, review["notes"] if review else None),
                encoding="utf-8")
        runs.append((data.get("startedAt", ""), to_agent_run(run_file.parent, data)))
    return [run for _, run in sorted(runs, key=lambda pair: pair[0])]


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    args = parser.parse_args(argv)
    runs = collect(RUNS_DIR)
    if not runs:
        print(f"No runs found in {RUNS_DIR}; leaving {args.output} unchanged.", file=sys.stderr)
        return 1
    args.output.write_text(json.dumps({"runs": runs}, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"Wrote {len(runs)} run(s) to {args.output}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
