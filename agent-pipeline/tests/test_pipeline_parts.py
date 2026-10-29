"""Pure parts of the pipeline: gate parsing, reporting, the CLI backend and git safety. No model calls."""

import json
import re
import subprocess
from pathlib import Path
from typing import Any, ClassVar

import pytest

import gitops
import llm
from codebase import files_named_in
from pipeline import load_story
from report import summary_markdown, what_failed
from stages.gate import failure_summary, first_failure

SUREFIRE_FAILURE = """\
[ERROR] Tests run: 3, Failures: 1, Errors: 0, Skipped: 0, Time elapsed: 0.1 s <<< FAILURE! -- in StaleRequestTest
[ERROR] com.tanya.workrequests.processing.StaleRequestTest.flagsOldRequests -- Time elapsed: 0.01 s <<< FAILURE!
org.opentest4j.AssertionFailedError: expected: STALE_REQUEST but was: MISSING_FIELD
[ERROR] Failures:
[ERROR]   StaleRequestTest.flagsOldRequests:42 expected: STALE_REQUEST but was: MISSING_FIELD
[ERROR] Tests run: 175, Failures: 1, Errors: 0, Skipped: 0
[ERROR] -> [Help 1]
[ERROR] To see the full stack trace of the errors, re-run Maven with the -e switch.
"""

COMPILE_FAILURE = """\
[ERROR] COMPILATION ERROR :
[ERROR] /Users/me/repo/backend/src/main/java/com/tanya/workrequests/domain/Problem.java:[42,17] cannot find symbol
  symbol:   variable STALE_REQUEST
[ERROR] -> [Help 1]
"""


class TestGateOutput:
    def test_names_the_first_failing_test(self):
        assert first_failure(SUREFIRE_FAILURE) == "StaleRequestTest.flagsOldRequests"

    def test_names_the_first_compile_error(self):
        assert first_failure(COMPILE_FAILURE) == "compilation failed: Problem.java:42"

    def test_returns_none_when_nothing_failed(self):
        assert first_failure("") is None

    def test_feedback_keeps_the_failure_and_drops_maven_boilerplate(self):
        summary = failure_summary("lots of Spring logs\n" + SUREFIRE_FAILURE)
        assert summary.startswith("[ERROR] Tests run: 3")
        assert "expected: STALE_REQUEST but was: MISSING_FIELD" in summary
        assert "[Help 1]" not in summary and "re-run Maven" not in summary
        assert "Spring logs" not in summary

    def test_feedback_is_capped(self):
        assert len(failure_summary("[ERROR] " + "x" * 50_000, limit=100)) < 130


class TestFailureFiles:
    def test_finds_failing_test_classes_and_compile_errors_in_the_repo(self):
        output = (COMPILE_FAILURE + "[ERROR]   SampleBatchTest.processesSampleBatchIntoExpectedQueue:45 expected\n"
                  "[ERROR]   NoSuchTest.x:1 gone\n")
        assert files_named_in(output) == [
            "backend/src/main/java/com/tanya/workrequests/domain/Problem.java",
            "backend/src/test/java/com/tanya/workrequests/processing/SampleBatchTest.java",
        ]


class TestReport:
    RUN: ClassVar[dict[str, Any]] = {
        "storyId": "stale-requests", "storyTitle": "Flag stale requests", "story": "As a supervisor ...",
        "outcome": "stopped", "stoppedAt": "gate", "reason": "no attempt passed the gate in 2 tries",
        "branch": "agent/stale-requests", "llm": {"backend": "claude-cli", "models": ["claude-opus-5"]},
        "durationSeconds": 300, "totalCostUsd": 1.234, "finalDiffLines": 80, "cleanAfterRun": True,
        "criteria": {"title": "t", "criteria": [{"given": "g", "when": "w", "then": "t"}],
                     "affectedClasses": ["ProblemCode"], "outOfScope": []},
        "implementAttempts": [{"attempt": 1, "warnings": []}, {"attempt": 2, "warnings": ["X.java: changes 1"]}],
        "tests": {"testNames": ["StaleRequestTest.flagsOldRequests"], "warnings": []},
        "gateAttempts": [
            {"attempt": 1, "passed": False, "rejectedBeforeGate": "git apply failed: corrupt patch"},
            {"attempt": 2, "passed": False, "failingTest": "StaleRequestTest.flagsOldRequests", "durationSeconds": 30},
        ],
    }

    def test_lists_every_failure_honestly(self):
        assert what_failed(self.RUN) == [
            "Attempt 1: rejected before the gate: git apply failed: corrupt patch",
            "Attempt 2: gate failed at StaleRequestTest.flagsOldRequests",
            "Run stopped at gate: no attempt passed the gate in 2 tries",
        ]

    def test_summary_has_the_run_log_sections(self):
        text = summary_markdown(self.RUN)
        for heading in ("## Story", "## Generated acceptance criteria", "## Gate result per attempt",
                        "## What failed", "## Manual fixes"):
            assert heading in text
        assert "**First-try gate pass:** no" in text
        assert "**Attempts:** 2" in text
        assert "model cost $1.23" in text
        assert "| 1 | rejected before the gate | git apply failed: corrupt patch |" in text
        assert "X.java: changes 1" in text
        assert "- Not reviewed yet." in text

    def test_summary_includes_review_notes_once_given(self):
        assert "- Fixed the clock" in summary_markdown(self.RUN, manual_fixes=["Fixed the clock"])


class TestClaudeCliBackend:
    def test_runs_headless_with_no_tools_and_the_schema(self):
        command = llm.ClaudeCli(model="opus").command("system text", {"type": "object"})
        assert command[:3] == ["claude", "-p", "--output-format"]
        assert command[command.index("--tools") + 1] == ""
        assert "--no-session-persistence" in command
        assert json.loads(command[command.index("--json-schema") + 1]) == {"type": "object"}
        assert command[command.index("--model") + 1] == "opus"

    def test_reads_structured_output_model_and_cost(self, monkeypatch):
        payload = {"is_error": False, "result": '{"a": 1}', "structured_output": {"a": 1}, "total_cost_usd": 0.5,
                   "modelUsage": {"claude-haiku-4-5": {"outputTokens": 10}, "claude-opus-5": {"outputTokens": 900}}}
        seen = {}

        def fake_run(command, **kwargs):
            seen["cwd"] = kwargs["cwd"]
            return subprocess.CompletedProcess(command, 0, stdout=json.dumps(payload), stderr="")

        monkeypatch.setattr(llm.subprocess, "run", fake_run)
        result = llm.ClaudeCli().complete("s", "p", {"type": "object"})
        assert (result.data, result.model, result.cost_usd) == ({"a": 1}, "claude-opus-5", 0.5)
        assert not Path(seen["cwd"]).exists()  # an empty temp dir, removed afterwards

    def test_turns_cli_errors_into_llm_errors(self, monkeypatch):
        monkeypatch.setattr(llm.subprocess, "run", lambda command, **kwargs: subprocess.CompletedProcess(
            command, 0, stdout=json.dumps({"is_error": True, "subtype": "error_max_budget", "result": "x"}), stderr=""))
        with pytest.raises(llm.LlmError, match="error_max_budget"):
            llm.ClaudeCli().complete("s", "p")

    def test_defaults_to_the_api_only_when_a_key_is_set(self, monkeypatch):
        monkeypatch.delenv("ANTHROPIC_API_KEY", raising=False)
        assert llm.default_backend_name() == "claude-cli"
        monkeypatch.setenv("ANTHROPIC_API_KEY", "test-key")
        assert llm.default_backend_name() == "anthropic"

    def test_refuses_the_api_backend_without_a_key(self, monkeypatch):
        monkeypatch.delenv("ANTHROPIC_API_KEY", raising=False)
        with pytest.raises(llm.LlmError, match="ANTHROPIC_API_KEY"):
            llm.create_backend("anthropic")


class TestGitSafety:
    def test_gitops_never_merges_pushes_rebases_or_resets(self):
        source = Path(gitops.__file__).read_text(encoding="utf-8")
        calls = re.findall(r'git\(\s*"([a-z-]+)"', source)
        assert calls, "expected git(...) calls"
        assert not {"merge", "push", "rebase", "reset", "pull", "fetch", "remote"} & set(calls)

    def test_only_creates_branches_under_agent(self):
        with pytest.raises(gitops.GitError, match="agent/"):
            gitops.create_branch("main-copy", "main")

    def test_run_logs_never_count_as_agent_changes(self, monkeypatch):
        porcelain = "?? agent-pipeline/runs/2026-10-29-x/run.json\0 M backend/src/main/java/A.java\0"
        monkeypatch.setattr(gitops, "git", lambda *args, **kwargs: subprocess.CompletedProcess(args, 0, porcelain, ""))
        assert gitops.changed_paths() == ["backend/src/main/java/A.java"]
        assert len(gitops.changed_paths(include_run_logs=True)) == 2


class TestStories:
    def test_loads_each_story_with_its_title(self):
        story_id, title, text = load_story("stale-requests")
        assert story_id == "stale-requests"
        assert title == "Flag stale requests"
        assert "30 days" in text

    def test_ships_the_four_stories_from_the_spec(self):
        stories = sorted(path.stem for path in (Path(__file__).parent.parent / "stories").glob("*.md"))
        assert stories == ["low-priority-cap", "safety-critical-escalation", "stale-requests", "suspicious-timestamp"]
