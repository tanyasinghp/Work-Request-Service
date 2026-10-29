"""summary.md for one run, built only from what run.json recorded."""

from __future__ import annotations

from typing import Any


def _attempt_row(attempt: dict[str, Any]) -> str:
    if attempt.get("rejectedBeforeGate"):
        result = "rejected before the gate"
        detail = attempt["rejectedBeforeGate"]
    elif attempt.get("passed"):
        result, detail = "passed", ""
    else:
        result, detail = "failed", attempt.get("failingTest") or ""
    seconds = attempt.get("durationSeconds")
    shown_seconds = "" if seconds is None else seconds
    return f"| {attempt['attempt']} | {result} | {detail.replace('|', '/')[:200]} | {shown_seconds} |"


def what_failed(data: dict[str, Any]) -> list[str]:
    failures = [
        f"Attempt {attempt['attempt']}: " + (f"rejected before the gate: {attempt['rejectedBeforeGate']}"
                                             if attempt.get("rejectedBeforeGate")
                                             else f"gate failed at {attempt.get('failingTest')}")
        for attempt in data.get("gateAttempts", []) if not attempt.get("passed")]
    if data.get("outcome") != "passed" and data.get("reason"):
        where = data.get("stoppedAt", "an unexpected error")
        failures.append(f"Run {data.get('outcome')} at {where}: {data['reason']}")
    return failures


def summary_markdown(data: dict[str, Any], manual_fixes: list[str] | None = None,
                     review_notes: list[str] | None = None) -> str:
    criteria = data.get("criteria") or {}
    gate = data.get("gateAttempts", [])
    llm = data.get("llm", {})
    lines = [
        f"# {data.get('storyTitle', data.get('storyId'))}",
        "",
        f"- **Story:** `{data.get('storyId')}`",
        f"- **Outcome:** {data.get('outcome', 'unknown')}"
        + (f" (stopped at {data['stoppedAt']})" if data.get("stoppedAt") else ""),
        f"- **Branch:** `{data.get('branch')}` (never merged or pushed by the pipeline)",
        f"- **First-try gate pass:** {'yes' if gate and gate[0].get('passed') else 'no'}",
        f"- **Attempts:** {len(data.get('implementAttempts', []))}",
        f"- **LLM:** {llm.get('backend')} ({', '.join(llm.get('models', [])) or 'model unknown'})",
        f"- **Time taken:** {data.get('durationSeconds', '?')} s"
        + (f"; model cost ${data['totalCostUsd']:.2f}" if data.get("totalCostUsd") is not None else ""),
        f"- **Final diff size:** {data.get('finalDiffLines', 0)} changed lines",
        "",
        "## Story",
        "",
        data.get("story", ""),
        "",
        "## Generated acceptance criteria",
        "",
    ]
    if criteria:
        lines += [f"{index}. **Given** {item['given']}, **when** {item['when']}, **then** {item['then']}."
                  for index, item in enumerate(criteria.get("criteria", []), start=1)]
        lines += ["", f"Affected classes: {', '.join(criteria.get('affectedClasses', []))}",
                  f"Out of scope: {'; '.join(criteria.get('outOfScope', [])) or 'none'}"]
    else:
        lines.append("(none: the run stopped before criteria were accepted)")
    lines += ["", "## Gate result per attempt", "",
              "| Attempt | Result | Detail | Seconds |", "| --- | --- | --- | --- |"]
    lines += [_attempt_row(attempt) for attempt in gate] or ["| - | did not reach the gate | | |"]
    tests = data.get("tests") or {}
    if tests.get("testNames"):
        lines += ["", "## Tests written from the criteria", ""] + [f"- `{name}`" for name in tests["testNames"]]
    warnings = [w for attempt in data.get("implementAttempts", []) for w in attempt.get("warnings", [])]
    warnings += tests.get("warnings", [])
    if warnings:
        lines += ["", "## Guardrail warnings (review these)", ""] + [f"- {warning}" for warning in warnings]
    lines += ["", "## What failed", ""] + ([f"- {item}" for item in what_failed(data)] or ["- Nothing."])
    lines += ["", "## Manual fixes", ""]
    if manual_fixes is None and review_notes is None:
        lines.append("- Not reviewed yet.")
    else:
        lines += [f"- {fix}" for fix in manual_fixes or []] or ["- None needed."]
    if review_notes:
        lines += ["", "## Review notes", ""] + [f"- {note}" for note in review_notes]
    if not data.get("cleanAfterRun", True):
        lines += ["", "**Warning:** the working tree was not clean after this run."]
    return "\n".join(lines) + "\n"
