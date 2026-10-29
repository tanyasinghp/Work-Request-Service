"""What every model-calling stage shares: prompt rendering and the one-retry JSON rule."""

from __future__ import annotations

from pathlib import Path
from string import Template
from typing import Any

from config import PROMPTS_DIR, SCHEMAS_DIR
from criteria import load_schema, validate_criteria, wire_schema
from llm import Llm, LlmError
from runlog import RunLog

IMPLEMENT_SCHEMA = SCHEMAS_DIR / "implement_output.schema.json"
TESTS_SCHEMA = SCHEMAS_DIR / "tests_output.schema.json"
SUMMARY_SCHEMA = SCHEMAS_DIR / "summary_output.schema.json"


class StageFailed(RuntimeError):
    def __init__(self, stage: str, reason: str):
        super().__init__(f"{stage}: {reason}")
        self.stage = stage
        self.reason = reason


def render(template: str, **values: str) -> str:
    # $placeholders rather than {braces}: prompts are full of JSON and Java.
    return Template((PROMPTS_DIR / template).read_text(encoding="utf-8")).safe_substitute(**values)


def notes() -> str:
    return (PROMPTS_DIR / "codebase-notes.md").read_text(encoding="utf-8")


def ask_json(llm: Llm, log: RunLog, stage: str, system: str, prompt: str, schema_path: Path) -> dict[str, Any]:
    """Asks for JSON matching `schema_path`; one retry with the errors, then the stage fails."""
    schema = load_schema(schema_path)
    wire = wire_schema(schema)
    attempt_prompt = prompt
    errors: list[str] = []
    for attempt in (1, 2):
        label = stage if attempt == 1 else f"{stage}-retry"
        try:
            result = llm.complete(system, attempt_prompt, wire)
        except LlmError as error:
            log.record_call(label, system, attempt_prompt, None, error=str(error), schema=wire)
            raise StageFailed(stage, f"model call failed: {error}") from error
        log.record_call(label, system, attempt_prompt, result, schema=wire)
        data = result.data
        errors = ["the response was not valid JSON"] if data is None else validate_criteria(data, schema)
        if not errors:
            return data
        attempt_prompt = (prompt + "\n\n# Your previous response was rejected\n\n"
                          + "\n".join(f"- {error}" for error in errors)
                          + "\n\nReturn JSON that matches the schema exactly.")
    raise StageFailed(stage, "invalid JSON twice: " + "; ".join(errors))
