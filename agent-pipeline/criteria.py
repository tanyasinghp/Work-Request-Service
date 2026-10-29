"""Acceptance-criteria schema: strict validation locally, a reduced copy for the model.

The schema file is the source of truth and may use keywords (minItems, pattern, minLength)
that structured-output APIs don't accept. `wire_schema` strips those for the model call;
`validate_criteria` always checks the full schema afterwards, so nothing is loosened.
"""

from __future__ import annotations

import json
from functools import cache
from pathlib import Path
from typing import Any

from jsonschema import Draft202012Validator

from config import SCHEMAS_DIR

CRITERIA_SCHEMA_PATH = SCHEMAS_DIR / "acceptance_criteria.schema.json"

# Keywords the structured-output endpoints reject; validation still enforces them locally.
UNSUPPORTED_BY_STRUCTURED_OUTPUT = frozenset({
    "$schema", "$id", "minItems", "maxItems", "uniqueItems", "minLength", "maxLength",
    "pattern", "minimum", "maximum", "exclusiveMinimum", "exclusiveMaximum", "multipleOf",
})


@cache
def load_schema(path: Path = CRITERIA_SCHEMA_PATH) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def wire_schema(schema: dict[str, Any]) -> dict[str, Any]:
    """A copy of `schema` without keywords structured output rejects, at every depth."""
    def strip(node: Any) -> Any:
        if isinstance(node, dict):
            return {key: strip(value) for key, value in node.items() if key not in UNSUPPORTED_BY_STRUCTURED_OUTPUT}
        if isinstance(node, list):
            return [strip(item) for item in node]
        return node
    return strip(schema)


def validate_criteria(data: Any, schema: dict[str, Any] | None = None) -> list[str]:
    """Every schema violation as 'path: message'; an empty list means valid."""
    validator = Draft202012Validator(schema or load_schema())
    errors = sorted(validator.iter_errors(data), key=lambda error: list(error.absolute_path))
    return [f"{'/'.join(str(part) for part in error.absolute_path) or '(root)'}: {error.message}" for error in errors]


def parse_criteria(text: str) -> tuple[dict[str, Any] | None, list[str]]:
    """Parses model output and validates it. Returns (criteria, errors); criteria is None if invalid."""
    try:
        data = json.loads(text)
    except json.JSONDecodeError as error:
        return None, [f"not valid JSON: {error.msg} at line {error.lineno} column {error.colno}"]
    errors = validate_criteria(data)
    return (data if not errors else None), errors
