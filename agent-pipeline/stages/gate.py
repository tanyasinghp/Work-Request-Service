"""Gate: the full Maven test suite decides whether an attempt counts. Agents don't."""

from __future__ import annotations

import os
import re
import subprocess
import time
from dataclasses import dataclass

from config import BACKEND_DIR, GATE_TIMEOUT_SECONDS, java_home

FEEDBACK_LIMIT = 12_000
TEST_FAILURE = re.compile(r"^\[ERROR\]\s{2,}(\w+)\.(\w+)", re.MULTILINE)
COMPILE_ERROR = re.compile(r"^\[ERROR\] \S*?/(\w+\.java):\[(\d+),\d+\] (.+)$", re.MULTILINE)
BOILERPLATE = re.compile(r"^\[ERROR\] (-> \[Help 1\]|$|To see the full stack trace|Re-run Maven|For more information"
                         r"|\[Help 1\]|See .*surefire-reports|See dump files).*$", re.MULTILINE)


@dataclass
class GateResult:
    passed: bool
    exit_code: int
    duration_seconds: int
    output: str
    failing_test: str | None
    failure_summary: str


def first_failure(output: str) -> str | None:
    """The first failing test as Class.method, or the first compile error."""
    compile_error = COMPILE_ERROR.search(output)
    if compile_error:
        return f"compilation failed: {compile_error.group(1)}:{compile_error.group(2)}"
    test = TEST_FAILURE.search(output)
    if test:
        return f"{test.group(1)}.{test.group(2)}"
    return None


def failure_summary(output: str, limit: int = FEEDBACK_LIMIT) -> str:
    """The part of Maven's output worth sending back: from the first [ERROR], minus boilerplate."""
    start = output.find("[ERROR]")
    relevant = output[start:] if start >= 0 else output
    relevant = BOILERPLATE.sub("", relevant)
    relevant = re.sub(r"\n{3,}", "\n\n", relevant).strip()
    return relevant if len(relevant) <= limit else relevant[:limit] + "\n... (truncated)"


def run_gate() -> GateResult:
    """./mvnw -q test in backend/ on JDK 21. Quiet mode prints only failures."""
    env = {**os.environ, "JAVA_HOME": java_home()}
    started = time.monotonic()
    try:
        process = subprocess.run(["./mvnw", "-q", "-B", "test"], cwd=BACKEND_DIR, env=env, capture_output=True,
                                 text=True, timeout=GATE_TIMEOUT_SECONDS, check=False)
        exit_code, output = process.returncode, process.stdout + process.stderr
    except subprocess.TimeoutExpired as error:
        exit_code, output = -1, f"[ERROR] gate timed out after {GATE_TIMEOUT_SECONDS}s\n{error.stdout or ''}"
    duration = round(time.monotonic() - started)
    passed = exit_code == 0
    return GateResult(passed=passed, exit_code=exit_code, duration_seconds=duration, output=output,
                      failing_test=None if passed else (first_failure(output) or "unknown failure"),
                      failure_summary="" if passed else failure_summary(output))
