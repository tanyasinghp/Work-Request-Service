"""Write tests: from the criteria only. The implementation diff is never part of this prompt."""

from __future__ import annotations

import json
from typing import Any

from codebase import base_file_blocks, find_class, resolve_classes, source_paths
from config import BASE_BRANCH
from llm import Llm
from runlog import RunLog
from stages.common import TESTS_SCHEMA, ask_json, notes, render

HELPERS = ["TestRequests"]


def run(llm: Llm, log: RunLog, criteria: dict[str, Any]) -> dict[str, Any]:
    existing, _ = resolve_classes(criteria["affectedClasses"])
    main_files = [path for path in existing if "/src/main/" in path]
    helpers = [path for path in (find_class(name) for name in HELPERS) if path]
    # Files come from the base branch, so the agent's own changes can't leak into the tests.
    prompt = render("write_tests.md", criteria=json.dumps(criteria, indent=2), notes=notes(),
                    file_list="\n".join(source_paths()), files=base_file_blocks(main_files),
                    helpers=base_file_blocks(helpers))
    output = ask_json(llm, log, "write-tests", render("write_tests.system.md"), prompt, TESTS_SCHEMA)
    log.write("tests.diff", output["diff"])
    log.data["writeTestsSource"] = f"criteria + {BASE_BRANCH} versions of {len(main_files)} file(s); no diff"
    return output
