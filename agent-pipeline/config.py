"""Paths and settings shared by every stage. Nothing here reads secrets."""

from __future__ import annotations

import os
from pathlib import Path

PIPELINE_DIR = Path(__file__).resolve().parent
REPO_ROOT = PIPELINE_DIR.parent
BACKEND_DIR = REPO_ROOT / "backend"
BACKEND_SRC = "backend/src/"
MAIN_JAVA = BACKEND_DIR / "src/main/java"
TEST_JAVA = BACKEND_DIR / "src/test/java"

SCHEMAS_DIR = PIPELINE_DIR / "schemas"
PROMPTS_DIR = PIPELINE_DIR / "prompts"
STORIES_DIR = PIPELINE_DIR / "stories"
RUNS_DIR = PIPELINE_DIR / "runs"

BASE_BRANCH = "main"
BRANCH_PREFIX = "agent/"

# The gate needs JDK 21 (the pom's enforcer rejects anything else); override per machine.
DEFAULT_JAVA_HOME = "/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
GATE_TIMEOUT_SECONDS = 20 * 60
MAX_IMPLEMENT_ATTEMPTS = 2


def java_home() -> str:
    return os.environ.get("PIPELINE_JAVA_HOME") or os.environ.get("JAVA_HOME") or DEFAULT_JAVA_HOME
