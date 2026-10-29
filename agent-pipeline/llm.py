"""Pluggable model backends. Each one is text in, text (and optional JSON) out.

Neither backend can touch the repository: the CLI runs with every tool disabled from an
empty temporary directory, and the API backend has no tools at all. The pipeline, not the
model, reads files, applies diffs and runs the gate.
"""

from __future__ import annotations

import contextlib
import json
import os
import subprocess
import tempfile
import time
from dataclasses import dataclass, field
from typing import Any, Protocol


class LlmError(RuntimeError):
    """The model call itself failed (transport, refusal, truncation); not a bad answer."""


@dataclass
class LlmResult:
    text: str
    data: Any | None
    model: str
    cost_usd: float | None
    raw: dict[str, Any] = field(default_factory=dict)


class Llm(Protocol):
    name: str

    def complete(self, system: str, prompt: str, schema: dict[str, Any] | None = None) -> LlmResult: ...


def _parse_json(text: str) -> Any | None:
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        return None


class ClaudeCli:
    """`claude -p` headless, using whatever account the CLI is logged in with."""

    name = "claude-cli"

    def __init__(self, model: str | None = None, timeout_seconds: int = 900, max_budget_usd: float | None = 5.0,
                 poll_seconds: float = 5.0):
        self.model = model
        self.timeout_seconds = timeout_seconds
        self.poll_seconds = poll_seconds
        self.max_budget_usd = max_budget_usd

    def command(self, system: str, schema: dict[str, Any] | None) -> list[str]:
        command = ["claude", "-p", "--output-format", "json", "--tools", "", "--no-session-persistence",
                   "--system-prompt", system]
        if schema is not None:
            command += ["--json-schema", json.dumps(schema)]
        if self.model:
            command += ["--model", self.model]
        if self.max_budget_usd is not None:
            command += ["--max-budget-usd", str(self.max_budget_usd)]
        return command

    def complete(self, system: str, prompt: str, schema: dict[str, Any] | None = None) -> LlmResult:
        # An empty working directory: no project CLAUDE.md or files leak into the prompt.
        with tempfile.TemporaryDirectory(prefix="agent-pipeline-") as empty_dir:
            try:
                process = subprocess.Popen(self.command(system, schema), stdin=subprocess.PIPE,
                                           stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, cwd=empty_dir)
            except FileNotFoundError as error:
                raise LlmError("the `claude` CLI is not installed or not on PATH") from error
            returncode, stdout, stderr = self._wait(process, prompt)
        if returncode != 0:
            failure = _parse_json(stdout.splitlines()[0]) if stdout.strip() else None
            if isinstance(failure, dict) and failure.get("api_error_status") == 429:
                raise LlmError(f"usage limit reached (HTTP 429): {failure.get('result')}")
            raise LlmError(f"claude -p exited {returncode}: {(stderr or stdout)[-2000:]}")
        payload = _parse_json(stdout)
        if not isinstance(payload, dict):
            raise LlmError(f"claude -p did not return JSON: {stdout[:500]}")
        if payload.get("is_error"):
            detail = str(payload.get("result"))[:1000]
            raise LlmError(f"claude -p reported an error ({payload.get('subtype')}): {detail}")
        text = payload.get("result") or ""
        data = None
        if schema is not None:
            data = payload.get("structured_output")
            if data is None:
                data = _parse_json(text)
        return LlmResult(text=text, data=data, model=self._main_model(payload), cost_usd=payload.get("total_cost_usd"),
                         raw=payload)

    def _wait(self, process: subprocess.Popen[str], prompt: str) -> tuple[int, str, str]:
        """Waits for the CLI with a wall-clock deadline.

        subprocess timeouts use a monotonic clock, which stops while a laptop sleeps; a call
        whose network stream died during sleep then hangs indefinitely after waking. Checking
        time.time() instead makes such a call fail promptly on wake.
        """
        deadline = time.time() + self.timeout_seconds
        pending_input: str | None = prompt
        while True:
            try:
                stdout, stderr = process.communicate(pending_input, timeout=self.poll_seconds)
                return process.returncode, stdout, stderr
            except subprocess.TimeoutExpired:
                pending_input = None  # communicate() keeps writing the input it was first given.
                if time.time() > deadline:
                    process.kill()
                    with contextlib.suppress(subprocess.TimeoutExpired):
                        process.communicate(timeout=10)
                    raise LlmError(f"claude -p gave no answer within {self.timeout_seconds}s of wall-clock time "
                                   "(the machine may have slept)") from None

    @staticmethod
    def _main_model(payload: dict[str, Any]) -> str:
        # The CLI may also run a small helper model; report the one that produced the most output.
        usage = payload.get("modelUsage") or {}
        if not usage:
            return "unknown"
        return max(usage, key=lambda name: (usage[name] or {}).get("outputTokens", 0))


class AnthropicApi:
    """Messages API with JSON-schema structured output. Needs ANTHROPIC_API_KEY in the environment."""

    name = "anthropic"
    DEFAULT_MODEL = "claude-opus-5-5"

    def __init__(self, model: str | None = None, effort: str = "high", max_tokens: int = 64000):
        import anthropic  # Imported here so the claude-cli backend works without the SDK installed.

        self._anthropic = anthropic
        self.client = anthropic.Anthropic()
        self.model = model or self.DEFAULT_MODEL
        # Opus 5.5 defaults to medium effort; code generation is worth the higher setting.
        self.effort = effort
        self.max_tokens = max_tokens

    def complete(self, system: str, prompt: str, schema: dict[str, Any] | None = None) -> LlmResult:
        output_config: dict[str, Any] = {"effort": self.effort}
        if schema is not None:
            output_config["format"] = {"type": "json_schema", "schema": schema}
        try:
            # Streaming: diffs can be long, and a non-streaming call that large risks HTTP timeouts.
            # Server-side fallbacks re-run a refused request on a fallback model within the same call.
            with self.client.beta.messages.stream(
                model=self.model,
                max_tokens=self.max_tokens,
                system=system,
                messages=[{"role": "user", "content": prompt}],
                output_config=output_config,
                betas=["server-side-fallback-2026-07-01"],
                fallbacks="default",
            ) as stream:
                message = stream.get_final_message()
        except self._anthropic.RateLimitError as error:
            raise LlmError(f"rate limited by the API after retries: {error}") from error
        except self._anthropic.APIStatusError as error:
            raise LlmError(f"API returned {error.status_code}: {error.message}") from error
        except self._anthropic.APIConnectionError as error:
            raise LlmError(f"could not reach the API: {error}") from error

        if message.stop_reason == "refusal":
            raise LlmError(f"the model declined the request: {getattr(message, 'stop_details', None)}")
        if message.stop_reason == "max_tokens":
            raise LlmError(f"the response hit max_tokens ({self.max_tokens}) and was cut off")
        text = "".join(block.text for block in message.content if block.type == "text")
        return LlmResult(text=text, data=_parse_json(text) if schema is not None else None, model=message.model,
                         cost_usd=None, raw={"id": message.id, "stop_reason": message.stop_reason,
                                             "usage": message.usage.model_dump(mode="json")})


def default_backend_name() -> str:
    return "anthropic" if os.environ.get("ANTHROPIC_API_KEY") else "claude-cli"


def create_backend(name: str | None, model: str | None = None) -> Llm:
    chosen = name or default_backend_name()
    if chosen == "anthropic":
        if not os.environ.get("ANTHROPIC_API_KEY"):
            raise LlmError("--llm anthropic needs ANTHROPIC_API_KEY in the environment")
        return AnthropicApi(model=model)
    if chosen == "claude-cli":
        return ClaudeCli(model=model)
    raise LlmError(f"unknown backend {chosen!r}; use 'anthropic' or 'claude-cli'")
