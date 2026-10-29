"""Checks every agent diff must pass before it can reach the test gate.

Two layers: `check_diff` reads the diff text before `git apply`, and `check_changed_paths`
plus `count_test_methods` check what actually changed in the tree afterwards, so a gap in
the diff parser can't let a forbidden change through.
"""

from __future__ import annotations

import re
from dataclasses import dataclass, field
from pathlib import Path

ALLOWED_PREFIX = "backend/src/"
TEST_PREFIX = "backend/src/test/"

# Named separately so a rejection says *why*, even though all of them are outside backend/src.
FORBIDDEN_PATTERNS: tuple[tuple[re.Pattern[str], str], ...] = (
    (re.compile(r"(^|/)pom\.xml$"), "build file pom.xml"),
    (re.compile(r"^\.github/"), "CI configuration"),
    (re.compile(r"(^|/)(mvnw(\.cmd)?|\.mvn/)"), "Maven wrapper"),
    (re.compile(r"(^|/)(Dockerfile|docker-compose\.ya?ml|\.dockerignore)$"), "container configuration"),
    (re.compile(r"(^|/)\.gitignore$"), "git configuration"),
)

TEST_ANNOTATION = re.compile(r"@(Test|ParameterizedTest|RepeatedTest|TestFactory)\b")
DISABLING_ANNOTATION = re.compile(r"@(Disabled|Ignore)\b|\bassumeTrue\(|\bassumeFalse\(|\bAssumptions\.")
ASSERTION = re.compile(r"\b(assertThat|assertEquals|assertTrue|assertFalse|assertNull|assertNotNull|assertThrows|"
                       r"assertThatThrownBy|assertThatExceptionOfType|assertThatIllegalArgumentException|"
                       r"assertThatNullPointerException|verify|fail)\s*\(|\.andExpect\(")


@dataclass
class FilePatch:
    old_path: str | None
    new_path: str | None
    added_lines: list[str] = field(default_factory=list)
    removed_lines: list[str] = field(default_factory=list)
    is_binary: bool = False

    @property
    def is_new(self) -> bool:
        return self.old_path is None

    @property
    def is_deleted(self) -> bool:
        return self.new_path is None

    @property
    def is_rename(self) -> bool:
        return self.old_path is not None and self.new_path is not None and self.old_path != self.new_path

    @property
    def paths(self) -> list[str]:
        return [path for path in (self.old_path, self.new_path) if path is not None]


@dataclass
class CheckResult:
    violations: list[str] = field(default_factory=list)
    warnings: list[str] = field(default_factory=list)

    @property
    def ok(self) -> bool:
        return not self.violations


def _strip_prefix(path: str) -> str | None:
    path = path.strip().split("\t", 1)[0]
    if path == "/dev/null":
        return None
    return path[2:] if path.startswith(("a/", "b/")) else path


def parse_diff(diff: str) -> list[FilePatch]:
    """Tolerant unified-diff parser: needs file headers, ignores hunk line counts."""
    patches: list[FilePatch] = []
    current: FilePatch | None = None
    in_hunk = False
    lines = diff.splitlines()
    for index, line in enumerate(lines):
        if line.startswith("diff --git "):
            match = re.match(r"diff --git a/(\S+) b/(\S+)", line)
            current = FilePatch(match.group(1), match.group(2)) if match else FilePatch(None, None)
            patches.append(current)
            in_hunk = False
            continue
        # "--- x" directly followed by "+++ y" is a file header, even right after a hunk.
        next_line = lines[index + 1] if index + 1 < len(lines) else ""
        if line.startswith("--- ") and next_line.startswith("+++ "):
            if current is None or current.removed_lines or current.added_lines or in_hunk:
                current = FilePatch(None, None)
                patches.append(current)
            current.old_path = _strip_prefix(line[4:])
            in_hunk = False
            continue
        if line.startswith("+++ ") and not in_hunk and current is not None:
            current.new_path = _strip_prefix(line[4:])
            continue
        if current is None:
            continue
        if line.startswith("new file mode"):
            current.old_path = None
        elif line.startswith("deleted file mode"):
            current.new_path = None
        elif line.startswith("rename from "):
            current.old_path = line[len("rename from "):].strip()
        elif line.startswith("rename to "):
            current.new_path = line[len("rename to "):].strip()
        elif line.startswith("GIT binary patch") or line.startswith("Binary files "):
            current.is_binary = True
        elif line.startswith("@@"):
            in_hunk = True
        elif in_hunk and line.startswith("+"):
            current.added_lines.append(line[1:])
        elif in_hunk and line.startswith("-"):
            current.removed_lines.append(line[1:])
        elif in_hunk and line.startswith("diff "):
            in_hunk = False
    return patches


def path_violations(path: str, allowed_prefix: str = ALLOWED_PREFIX) -> list[str]:
    problems = [f"{path}: touches {label}" for pattern, label in FORBIDDEN_PATTERNS if pattern.search(path)]
    if ".." in Path(path).parts or path.startswith("/"):
        problems.append(f"{path}: path escapes the repository")
    if not path.startswith(allowed_prefix):
        problems.append(f"{path}: outside {allowed_prefix}")
    return problems


def check_diff(diff: str, allowed_prefix: str = ALLOWED_PREFIX) -> CheckResult:
    """Rejects diffs that leave `allowed_prefix`, touch build/CI config, or delete or weaken tests."""
    result = CheckResult()
    if not diff.strip():
        result.violations.append("diff is empty")
        return result
    patches = parse_diff(diff)
    if not patches:
        result.violations.append("no file changes found in the diff (missing 'diff --git' or '---/+++' headers)")
        return result

    for patch in patches:
        if not patch.paths:
            result.violations.append("a file section has no readable path")
            continue
        for path in dict.fromkeys(patch.paths):
            result.violations.extend(path_violations(path, allowed_prefix))
        if patch.is_binary:
            result.violations.append(f"{patch.paths[0]}: binary patches are not allowed")

        test_paths = [path for path in patch.paths if path.startswith(TEST_PREFIX)]
        if not test_paths:
            continue
        name = test_paths[0]
        if patch.is_deleted:
            result.violations.append(f"{name}: deletes a test file")
        if patch.is_rename:
            result.violations.append(f"{name}: renames a test file")
        if any(DISABLING_ANNOTATION.search(line) for line in patch.added_lines):
            result.violations.append(f"{name}: disables or skips tests (@Disabled/@Ignore/assumptions)")
        removed_tests = sum(bool(TEST_ANNOTATION.search(line)) for line in patch.removed_lines)
        added_tests = sum(bool(TEST_ANNOTATION.search(line)) for line in patch.added_lines)
        if removed_tests > added_tests:
            result.violations.append(f"{name}: removes {removed_tests - added_tests} test method(s)")
        removed_asserts = sum(bool(ASSERTION.search(line)) for line in patch.removed_lines)
        added_asserts = sum(bool(ASSERTION.search(line)) for line in patch.added_lines)
        if removed_asserts > added_asserts:
            result.violations.append(f"{name}: removes {removed_asserts - added_asserts} assertion(s)")
        elif not patch.is_new and removed_asserts > 0:
            # Same number of assertions but different expectations can still weaken a test.
            result.warnings.append(f"{name}: changes {removed_asserts} existing assertion line(s); review them")
    return result


def check_changed_paths(paths: list[str], allowed_prefix: str = ALLOWED_PREFIX) -> list[str]:
    """Same path rules, applied to what git reports as changed after the diff was applied."""
    return [problem for path in paths for problem in path_violations(path, allowed_prefix)]


def count_test_methods(test_root: Path) -> int:
    """Total test annotations under `test_root`; it must never go down across a run."""
    return sum(len(TEST_ANNOTATION.findall(file.read_text(encoding="utf-8"))) for file in test_root.rglob("*.java"))


def added_test_names(diff: str) -> list[str]:
    """'ClassName.method' for every test method a diff adds."""
    names: list[str] = []
    for patch in parse_diff(diff):
        if not patch.new_path or not patch.new_path.startswith(TEST_PREFIX):
            continue
        class_name = Path(patch.new_path).stem
        pending = False
        for line in patch.added_lines:
            if TEST_ANNOTATION.search(line):
                pending = True
            method = re.search(r"\bvoid\s+(\w+)\s*\(", line)
            if pending and method:
                names.append(f"{class_name}.{method.group(1)}")
                pending = False
    return names


def diff_size(diff: str) -> int:
    """Changed lines (added + removed), the number reported as 'diff size'."""
    return sum(len(patch.added_lines) + len(patch.removed_lines) for patch in parse_diff(diff))
