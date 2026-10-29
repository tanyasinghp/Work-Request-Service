"""Summarize: a PR description for the human reviewer."""

from __future__ import annotations

import json
from typing import Any

from llm import Llm
from runlog import RunLog
from stages.common import SUMMARY_SCHEMA, ask_json, render

DIFF_LIMIT = 60_000


def run(llm: Llm, log: RunLog, story: str, criteria: dict[str, Any], gate_attempts: list[dict[str, Any]],
        test_names: list[str], final_diff: str) -> str:
    gate = "\n".join(
        f"- Attempt {attempt['attempt']}: " + ("passed" if attempt["passed"] else f"failed ({attempt['failingTest']})")
        for attempt in gate_attempts)
    diff = final_diff if len(final_diff) <= DIFF_LIMIT else final_diff[:DIFF_LIMIT] + "\n... (diff truncated)"
    prompt = render("summarize.md", story=story, criteria=json.dumps(criteria, indent=2), gate=gate or "(none)",
                    tests="\n".join(f"- {name}" for name in test_names) or "(none found)", diff=diff)
    output = ask_json(llm, log, "summarize", render("summarize.system.md"), prompt, SUMMARY_SCHEMA)
    log.write("pr-description.md", output["markdown"])
    return output["markdown"]
