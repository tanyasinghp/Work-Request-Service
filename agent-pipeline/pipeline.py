#!/usr/bin/env python3
"""Story → criteria → diff on agent/<story-id> → independent tests → Maven gate → PR description.

The gate, not the model, decides what counts as done. Every agent diff passes the guardrails
before it is applied, the run always ends back on main with a clean tree, and nothing is ever
merged or pushed: a human reviews the agent/* branch.

    python pipeline.py stale-requests [--llm anthropic|claude-cli] [--model NAME] [--replace-branch]
"""

from __future__ import annotations

import argparse
import json
import sys
import traceback
from pathlib import Path
from typing import Any

import gitops
from codebase import files_named_in
from config import BASE_BRANCH, BRANCH_PREFIX, MAX_IMPLEMENT_ATTEMPTS, STORIES_DIR, TEST_JAVA, java_home
from guardrails import (
    ALLOWED_PREFIX,
    TEST_PREFIX,
    added_test_names,
    check_changed_paths,
    check_diff,
    count_test_methods,
    diff_size,
)
from llm import Llm, LlmError, create_backend
from report import summary_markdown
from runlog import RunLog
from stages import enrich, implement, summarize, write_tests
from stages.common import StageFailed
from stages.gate import run_gate

COMMIT_TRAILER = "Generated-by: agent-pipeline\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"


def load_story(argument: str) -> tuple[str, str, str]:
    """(story id, title, text) from a story id in stories/ or a path to a .md file."""
    path = Path(argument) if argument.endswith(".md") else STORIES_DIR / f"{argument}.md"
    if not path.exists():
        available = ", ".join(sorted(story.stem for story in STORIES_DIR.glob("*.md")))
        raise SystemExit(f"No story {argument!r}. Available: {available}")
    text = path.read_text(encoding="utf-8").strip()
    first_line = text.splitlines()[0] if text else path.stem
    title = first_line.removeprefix("# ").strip() if first_line.startswith("# ") else path.stem
    return path.stem, title, text


def preflight(story_id: str, replace_branch: bool) -> list[str]:
    problems = []
    if gitops.current_branch() != BASE_BRANCH:
        problems.append(f"check out {BASE_BRANCH} first (on {gitops.current_branch()})")
    if not gitops.is_clean():
        problems.append("the working tree must be clean: " + ", ".join(gitops.changed_paths()[:5]))
    if not (Path(java_home()) / "bin/java").exists():
        problems.append(f"JDK 21 not found at {java_home()}; set PIPELINE_JAVA_HOME")
    if gitops.branch_exists(BRANCH_PREFIX + story_id) and not replace_branch:
        problems.append(f"{BRANCH_PREFIX}{story_id} already exists; review or delete it, or pass --replace-branch")
    return problems


def apply_checked(log: RunLog, name: str, diff: str, allowed_prefix: str, minimum_tests: int
                  ) -> tuple[bool, list[str], list[str]]:
    """Guardrails on the text, git apply, then the same rules on what actually changed."""
    check = check_diff(diff, allowed_prefix)
    if not check.ok:
        return False, check.violations, check.warnings
    applied, message = gitops.apply_diff(log.write(name, diff))
    if not applied:
        return False, [f"git apply failed: {message}"], check.warnings
    problems = check_changed_paths(gitops.changed_paths(), allowed_prefix)
    test_count = count_test_methods(TEST_JAVA)
    if test_count < minimum_tests:
        problems.append(f"test methods went down from {minimum_tests} to {test_count}")
    if problems:
        gitops.discard_uncommitted_backend_src()
        return False, problems, check.warnings
    return True, [], check.warnings


def stop(data: dict[str, Any], stage: str, reason: str) -> None:
    data.update(outcome="stopped", stoppedAt=stage, reason=reason)


def run_stages(llm: Llm, log: RunLog, story_id: str, story: str, replace_branch: bool) -> None:
    data = log.data
    try:
        criteria = enrich.run(llm, log, story)
    except StageFailed as failure:
        return stop(data, failure.stage, failure.reason)
    data["criteria"] = criteria

    branch = BRANCH_PREFIX + story_id
    gitops.create_branch(branch, BASE_BRANCH, replace=replace_branch)
    minimum_tests = count_test_methods(TEST_JAVA)
    feedback: implement.Feedback | None = None
    tests_written = False

    for attempt in range(1, MAX_IMPLEMENT_ATTEMPTS + 1):
        record: dict[str, Any] = {"attempt": attempt, "summary": "", "diffLines": 0, "applied": False,
                                  "violations": [], "warnings": [], "uncertainties": []}
        data["implementAttempts"].append(record)
        try:
            output = implement.run(llm, log, criteria, attempt, feedback)
        except StageFailed as failure:
            record["violations"] = [failure.reason]
            return stop(data, failure.stage, failure.reason)
        record.update(summary=output["summary"], uncertainties=output["uncertainties"],
                      diffLines=diff_size(output["diff"]))

        applied, problems, warnings = apply_checked(log, f"implement-{attempt}.diff", output["diff"], ALLOWED_PREFIX,
                                                    minimum_tests)
        record["warnings"] = warnings
        if not applied:
            record["violations"] = problems
            reason = "; ".join(problems)
            data["gateAttempts"].append({"attempt": attempt, "passed": False, "rejectedBeforeGate": reason,
                                         "failingTest": None})
            feedback = implement.Feedback(attempt, "Rejected before the gate:\n" + "\n".join(problems), output["diff"])
            continue
        record["applied"] = True
        gitops.commit_backend_src(f"agent({story_id}): implement, attempt {attempt}\n\n{output['summary']}\n\n"
                                  f"{COMMIT_TRAILER}")
        minimum_tests = count_test_methods(TEST_JAVA)

        if not tests_written:
            try:
                tests = write_tests.run(llm, log, criteria)
            except StageFailed as failure:
                return stop(data, failure.stage, failure.reason)
            applied, problems, warnings = apply_checked(log, "tests.diff", tests["diff"], TEST_PREFIX, minimum_tests)
            data["tests"] = {"summary": tests["summary"], "testNames": added_test_names(tests["diff"]),
                             "diffLines": diff_size(tests["diff"]), "violations": problems, "warnings": warnings}
            if not applied:
                return stop(data, "write-tests", "; ".join(problems))
            gitops.commit_backend_src(f"agent({story_id}): tests from the acceptance criteria\n\n{tests['summary']}"
                                      f"\n\n{COMMIT_TRAILER}")
            minimum_tests = count_test_methods(TEST_JAVA)
            tests_written = True

        result = run_gate()
        log.write(f"gate-{attempt}.log", result.output or "(no output: every test passed)")
        data["gateAttempts"].append({"attempt": attempt, "passed": result.passed, "failingTest": result.failing_test,
                                     "exitCode": result.exit_code, "durationSeconds": result.duration_seconds,
                                     "output": result.failure_summary[:4000]})
        if result.passed:
            final_diff = gitops.diff_between(BASE_BRANCH)
            log.write("final.diff", final_diff)
            data["finalDiffLines"] = diff_size(final_diff)
            data["outcome"] = "passed"
            try:
                data["prDescription"] = summarize.run(llm, log, story, criteria, data["gateAttempts"],
                                                      data["tests"]["testNames"], final_diff)
            except StageFailed as failure:
                data["prDescription"] = None
                data["reason"] = f"gate passed, but the PR description failed: {failure.reason}"
            return None
        feedback = implement.Feedback(attempt, result.failure_summary, output["diff"], files_named_in(result.output))

    final_diff = gitops.diff_between(BASE_BRANCH)
    log.write("final.diff", final_diff or "(nothing committed)")
    data["finalDiffLines"] = diff_size(final_diff)
    return stop(data, "gate", f"no attempt passed the gate in {MAX_IMPLEMENT_ATTEMPTS} tries")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("story", help="story id in stories/ (e.g. stale-requests) or a path to a .md file")
    parser.add_argument("--llm", choices=["anthropic", "claude-cli"],
                        help="default: anthropic if ANTHROPIC_API_KEY is set, else claude-cli")
    parser.add_argument("--model", help="model id or alias passed to the backend")
    parser.add_argument("--replace-branch", action="store_true", help=f"delete an existing {BRANCH_PREFIX}<id> first")
    args = parser.parse_args(argv)

    story_id, title, story = load_story(args.story)
    problems = preflight(story_id, args.replace_branch)
    if problems:
        print("Not starting:\n" + "\n".join(f"  - {problem}" for problem in problems), file=sys.stderr)
        return 2
    try:
        llm = create_backend(args.llm, args.model)
    except LlmError as error:
        print(f"Not starting: {error}", file=sys.stderr)
        return 2

    log = RunLog(story_id)
    log.data.update(storyTitle=title, story=story, branch=BRANCH_PREFIX + story_id, baseCommit=gitops.head_sha(),
                    llm={"backend": llm.name, "requestedModel": args.model})
    print(f"Running {story_id} with {llm.name}; logging to {log.directory}")
    try:
        run_stages(llm, log, story_id, story, args.replace_branch)
    except Exception as error:  # Anything unexpected is recorded, then cleanup still runs.
        log.write("error.log", traceback.format_exc())
        log.data.update(outcome="error", reason=f"{type(error).__name__}: {error}")
    finally:
        gitops.return_to(BASE_BRANCH)
        log.data["endedOnBranch"] = gitops.current_branch()
        log.data["cleanAfterRun"] = gitops.is_clean()
        log.data["llm"]["models"] = sorted({call["model"] for call in log.data["llmCalls"] if call.get("model")})
        log.save()
        log.write("summary.md", summary_markdown(log.data))

    print(f"{story_id}: {log.data.get('outcome')} ({log.data.get('reason', 'gate passed')})")
    print(f"Summary: {log.directory / 'summary.md'}")
    print(json.dumps({"outcome": log.data.get("outcome"), "branch": log.data.get("branch")}))
    return 0 if log.data.get("outcome") == "passed" else 1


if __name__ == "__main__":
    sys.exit(main())
