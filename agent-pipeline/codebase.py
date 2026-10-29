"""Chooses which backend files a stage sees. Only listed files are ever sent to the model."""

from __future__ import annotations

import re
from pathlib import Path

from config import BASE_BRANCH, MAIN_JAVA, REPO_ROOT, TEST_JAVA
from gitops import show_file


def relative(path: Path) -> str:
    return path.relative_to(REPO_ROOT).as_posix()


def source_paths() -> list[str]:
    """Every Java file under backend/src: a map of the codebase, without contents."""
    return sorted(relative(path) for root in (MAIN_JAVA, TEST_JAVA) for path in root.rglob("*.java"))


def find_class(name: str) -> str | None:
    """Repository path of a class by simple name, main code first."""
    for root in (MAIN_JAVA, TEST_JAVA):
        matches = sorted(root.rglob(f"{name}.java"))
        if matches:
            return relative(matches[0])
    return None


def resolve_classes(names: list[str]) -> tuple[list[str], list[str]]:
    """(existing paths, names with no file yet, i.e. classes the story will create)."""
    found, missing = [], []
    for name in names:
        path = find_class(name)
        (found if path else missing).append(path or name)
    return list(dict.fromkeys(found)), missing


def file_blocks(paths: list[str], ref: str | None = None) -> str:
    """Files as fenced blocks; from the working tree, or from `ref` (e.g. the base branch)."""
    blocks = []
    for path in paths:
        content = show_file(ref, path) if ref else (REPO_ROOT / path).read_text(encoding="utf-8")
        if content is not None:
            blocks.append(f"=== {path} ===\n```java\n{content.rstrip()}\n```")
    return "\n\n".join(blocks) if blocks else "(none)"


def base_file_blocks(paths: list[str]) -> str:
    return file_blocks(paths, ref=BASE_BRANCH)


COMPILE_ERROR = re.compile(r"\[ERROR\] (?:\S*/)?(backend/src/\S+?\.java|src/\S+?\.java):\[(\d+)")
TEST_FAILURE = re.compile(r"^\[ERROR\]\s{2,}(\w+)\.(\w+)", re.MULTILINE)
ERROR_IN_CLASS = re.compile(r"in (com\.tanya\.workrequests\.[\w.]+)")


def files_named_in(output: str) -> list[str]:
    """Source files a failing gate run points at: compile errors and failing test classes."""
    paths: list[str] = []
    for match in COMPILE_ERROR.finditer(output):
        path = match.group(1)
        paths.append(path if path.startswith("backend/") else f"backend/{path}")
    for match in TEST_FAILURE.finditer(output):
        path = find_class(match.group(1))
        if path:
            paths.append(path)
    for match in ERROR_IN_CLASS.finditer(output):
        path = find_class(match.group(1).rsplit(".", 1)[-1])
        if path:
            paths.append(path)
    return [path for path in dict.fromkeys(paths) if (REPO_ROOT / path).exists()]
