"""export_runs builds the Agent runs tab's data from real run logs, in the shape the web app parses."""

import json
from pathlib import Path

import export_runs

RUN = {
    "storyId": "stale-requests", "storyTitle": "Flag stale requests",
    "story": "# Flag stale requests\n\nAs a supervisor, I want old requests flagged.",
    "startedAt": "2026-10-29T20:30:00+00:00", "outcome": "stopped", "stoppedAt": "gate",
    "reason": "no attempt passed the gate in 2 tries", "branch": "agent/stale-requests",
    "llm": {"backend": "claude-cli", "models": ["claude-opus-5"]}, "durationSeconds": 888, "totalCostUsd": 2.92,
    "criteria": {"title": "Flag stale requests", "criteria": [{"given": "g", "when": "w", "then": "t"}],
                 "affectedClasses": ["ProblemCode"], "outOfScope": ["x"]},
    "implementAttempts": [
        {"attempt": 1, "summary": "placeholder", "diffLines": 0, "violations": ["no file changes found"]},
        {"attempt": 2, "summary": "Adds STALE.", "diffLines": 240, "violations": []},
    ],
    "tests": {"testNames": ["StaleProblemTest.usesTheStaleCode"]},
    "gateAttempts": [
        {"attempt": 1, "passed": False, "rejectedBeforeGate": "no file changes found", "failingTest": None},
        {"attempt": 2, "passed": False, "failingTest": "OrderIndependenceTest.covers", "output": "x" * 10_000},
    ],
}

# Keys the web app's parseAgentRuns requires on every run.
REQUIRED_BY_WEB = {"id", "title", "story", "enrich", "implement", "writeTests", "gate", "summary"}


def write_run(root: Path, name: str, data: dict, review: dict | None = None) -> Path:
    run_dir = root / name
    run_dir.mkdir(parents=True)
    (run_dir / "run.json").write_text(json.dumps(data), encoding="utf-8")
    if review is not None:
        (run_dir / "review.json").write_text(json.dumps(review), encoding="utf-8")
    return run_dir


def test_exports_every_field_the_web_app_requires(tmp_path):
    write_run(tmp_path, "2026-10-29-stale-requests", RUN)
    [run] = export_runs.collect(tmp_path)
    assert run.keys() >= REQUIRED_BY_WEB
    assert "placeholder" not in run
    assert run["id"] == "2026-10-29-stale-requests"
    assert run["date"] == "2026-10-29"


def test_keeps_failures_and_rejections_as_recorded(tmp_path):
    write_run(tmp_path, "r", RUN)
    [run] = export_runs.collect(tmp_path)
    first, second = run["gate"]["attempts"]
    assert first == {"attempt": 1, "passed": False, "rejectedBeforeGate": "no file changes found"}
    assert second["failingTest"] == "OrderIndependenceTest.covers"
    assert len(second["output"]) == export_runs.OUTPUT_LIMIT
    assert run["outcome"] == "stopped" and run["stoppedAt"] == "gate"
    assert run["summary"]["text"].startswith("No PR description: the run stopped at gate.")


def test_strips_the_title_line_from_the_story(tmp_path):
    write_run(tmp_path, "r", RUN)
    [run] = export_runs.collect(tmp_path)
    assert run["story"] == "As a supervisor, I want old requests flagged."


def test_takes_manual_fixes_and_notes_only_from_a_review(tmp_path):
    write_run(tmp_path, "unreviewed", RUN)
    write_run(tmp_path, "reviewed", {**RUN, "startedAt": "2026-10-30T00:00:00+00:00"},
              review={"manualFixes": ["Extend the generator"], "notes": ["Sound design"]})
    unreviewed, reviewed = export_runs.collect(tmp_path)
    assert unreviewed["summary"] == {"text": unreviewed["summary"]["text"], "manualFixes": [], "reviewed": False,
                                     "reviewNotes": []}
    assert reviewed["summary"]["manualFixes"] == ["Extend the generator"]
    assert reviewed["summary"]["reviewNotes"] == ["Sound design"]
    assert reviewed["summary"]["reviewed"] is True


def test_refreshes_each_summary_with_its_review(tmp_path):
    run_dir = write_run(tmp_path, "r", RUN, review={"manualFixes": ["Extend the generator"], "notes": []})
    export_runs.collect(tmp_path)
    assert "- Extend the generator" in (run_dir / "summary.md").read_text(encoding="utf-8")


def test_orders_runs_by_start_time(tmp_path):
    write_run(tmp_path, "b-later", {**RUN, "startedAt": "2026-10-30T00:00:00+00:00"})
    write_run(tmp_path, "a-earlier", {**RUN, "startedAt": "2026-10-29T00:00:00+00:00"})
    assert [run["id"] for run in export_runs.collect(tmp_path)] == ["a-earlier", "b-later"]


def test_uses_the_pr_description_when_the_gate_passed(tmp_path):
    write_run(tmp_path, "r", {**RUN, "outcome": "passed", "prDescription": "## What changed\nA thing."})
    [run] = export_runs.collect(tmp_path)
    assert run["summary"]["text"] == "## What changed\nA thing."


def test_leaves_the_output_untouched_when_there_are_no_runs(tmp_path, monkeypatch):
    output = tmp_path / "agentRuns.json"
    output.write_text('{"runs": ["unchanged"]}', encoding="utf-8")
    monkeypatch.setattr(export_runs, "RUNS_DIR", tmp_path / "empty")
    (tmp_path / "empty").mkdir()
    assert export_runs.main(["--output", str(output)]) == 1
    assert output.read_text(encoding="utf-8") == '{"runs": ["unchanged"]}'
