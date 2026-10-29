"""Implement: criteria + only the affected files → a unified diff."""

from __future__ import annotations

import json
from dataclasses import dataclass, field
from typing import Any

from codebase import file_blocks, resolve_classes, source_paths
from llm import Llm
from runlog import RunLog
from stages.common import IMPLEMENT_SCHEMA, ask_json, notes, render


@dataclass
class Feedback:
    """What went wrong last time; sent back once, as the spec allows."""
    attempt: int
    failure: str
    previous_diff: str
    files: list[str] = field(default_factory=list)


def run(llm: Llm, log: RunLog, criteria: dict[str, Any], attempt: int,
        feedback: Feedback | None = None) -> dict[str, Any]:
    existing, missing = resolve_classes(criteria["affectedClasses"])
    feedback_text = ""
    if feedback:
        extra = [path for path in feedback.files if path not in existing]
        feedback_text = render("implement.feedback.md", attempt=str(feedback.attempt), failure=feedback.failure,
                               previous_diff=feedback.previous_diff or "(none)", failure_files=file_blocks(extra))
    prompt = render("implement.md", criteria=json.dumps(criteria, indent=2), notes=notes(),
                    file_list="\n".join(source_paths()), files=file_blocks(existing),
                    missing="\n".join(f"- {name}" for name in missing) or "(none)", feedback=feedback_text)
    output = ask_json(llm, log, f"implement-{attempt}", render("implement.system.md"), prompt, IMPLEMENT_SCHEMA)
    log.write(f"implement-{attempt}.diff", output["diff"])
    return output
