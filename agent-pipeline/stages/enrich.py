"""Enrich: story → acceptance criteria JSON, validated against the schema."""

from __future__ import annotations

import json
from typing import Any

from codebase import file_blocks, find_class, source_paths
from criteria import CRITERIA_SCHEMA_PATH
from llm import Llm
from runlog import RunLog
from stages.common import ask_json, notes, render

# Enough to name identifiers precisely without sending the whole codebase.
KEY_CLASSES = ["ProblemCode", "Problem", "WorkRequest", "WorkRequestValidator", "WorkRequestProcessor",
               "ProcessingConfig", "BatchProperties"]


def run(llm: Llm, log: RunLog, story: str) -> dict[str, Any]:
    key_paths = [path for path in (find_class(name) for name in KEY_CLASSES) if path]
    prompt = render("enrich.md", story=story, notes=notes(), file_list="\n".join(source_paths()),
                    key_files=file_blocks(key_paths))
    criteria = ask_json(llm, log, "enrich", render("enrich.system.md"), prompt, CRITERIA_SCHEMA_PATH)
    log.write("criteria.json", json.dumps(criteria, indent=2))
    return criteria
