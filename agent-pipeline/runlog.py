"""Everything a run did, on disk: every prompt, response, diff and gate output, plus run.json."""

from __future__ import annotations

import json
from dataclasses import asdict, is_dataclass
from datetime import UTC, datetime
from pathlib import Path
from typing import Any

from config import RUNS_DIR
from llm import LlmResult


def run_directory(story_id: str, date: str, root: Path = RUNS_DIR) -> Path:
    """runs/<date>-<story-id>/, with -2, -3 ... when the story already ran that day."""
    candidate = root / f"{date}-{story_id}"
    suffix = 2
    while candidate.exists():
        candidate = root / f"{date}-{story_id}-{suffix}"
        suffix += 1
    return candidate


class RunLog:
    def __init__(self, story_id: str, root: Path = RUNS_DIR):
        self.started = datetime.now(UTC)
        self.directory = run_directory(story_id, self.started.date().isoformat(), root)
        self.directory.mkdir(parents=True)
        self._sequence = 0
        self.data: dict[str, Any] = {"storyId": story_id, "startedAt": self.started.isoformat(timespec="seconds"),
                                     "llmCalls": [], "implementAttempts": [], "gateAttempts": []}

    def _next(self, name: str) -> str:
        self._sequence += 1
        return f"{self._sequence:02d}-{name}"

    def write(self, name: str, content: str) -> Path:
        path = self.directory / name
        path.write_text(content if content.endswith("\n") else content + "\n", encoding="utf-8")
        return path

    def record_call(self, stage: str, system: str, prompt: str, result: LlmResult | None, error: str | None = None,
                    schema: dict[str, Any] | None = None) -> str:
        """Writes NN-stage.prompt.md and NN-stage.response.json; returns the NN-stage prefix."""
        prefix = self._next(stage)
        schema_note = f"\n\n## Output schema\n\n```json\n{json.dumps(schema, indent=2)}\n```" if schema else ""
        self.write(f"{prefix}.prompt.md",
                   f"# {stage}: prompt\n\n## System\n\n{system}\n\n## User\n\n{prompt}{schema_note}")
        response = {"error": error} if result is None else {
            "model": result.model, "costUsd": result.cost_usd, "text": result.text, "data": result.data,
            "raw": result.raw}
        self.write(f"{prefix}.response.json", json.dumps(response, indent=2, default=str))
        self.data["llmCalls"].append({"file": prefix, "stage": stage, "model": result.model if result else None,
                                      "costUsd": result.cost_usd if result else None, "error": error})
        return prefix

    def total_cost(self) -> float | None:
        costs = [call["costUsd"] for call in self.data["llmCalls"] if call.get("costUsd") is not None]
        return round(sum(costs), 4) if costs else None

    def save(self) -> Path:
        finished = datetime.now(UTC)
        self.data["finishedAt"] = finished.isoformat(timespec="seconds")
        self.data["durationSeconds"] = round((finished - self.started).total_seconds())
        self.data["totalCostUsd"] = self.total_cost()
        return self.write("run.json", json.dumps(self.data, indent=2, default=_jsonable))


def _jsonable(value: Any) -> Any:
    if is_dataclass(value):
        return asdict(value)
    return str(value)
