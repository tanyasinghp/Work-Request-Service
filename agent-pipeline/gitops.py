"""The only git operations the pipeline performs. There is deliberately no merge and no push."""

from __future__ import annotations

import subprocess
from pathlib import Path

from config import BACKEND_SRC, BRANCH_PREFIX, REPO_ROOT


class GitError(RuntimeError):
    pass


def git(*args: str, check: bool = True) -> subprocess.CompletedProcess[str]:
    process = subprocess.run(["git", *args], cwd=REPO_ROOT, capture_output=True, text=True, check=False)
    if check and process.returncode != 0:
        raise GitError(f"git {' '.join(args)} failed: {(process.stderr or process.stdout).strip()}")
    return process


def current_branch() -> str:
    return git("rev-parse", "--abbrev-ref", "HEAD").stdout.strip()


def head_sha() -> str:
    return git("rev-parse", "HEAD").stdout.strip()


RUN_LOGS = "agent-pipeline/runs/"


def changed_paths(include_run_logs: bool = False) -> list[str]:
    """Every modified, added, deleted or untracked path (gitignored files excluded).

    The pipeline's own run logs are left out by default: they are written during a run and
    committed to main afterwards, and must not count as an agent change.
    """
    output = git("status", "--porcelain", "-z", "--untracked-files=all").stdout
    entries = [entry for entry in output.split("\0") if entry]
    paths: list[str] = []
    index = 0
    while index < len(entries):
        status, path = entries[index][:2], entries[index][3:]
        paths.append(path)
        if status[0] in "RC":  # Renames and copies carry the original path as the next entry.
            index += 1
            paths.append(entries[index])
        index += 1
    return paths if include_run_logs else [path for path in paths if not path.startswith(RUN_LOGS)]


def is_clean() -> bool:
    return not changed_paths()


def branch_exists(name: str) -> bool:
    return git("rev-parse", "--verify", "--quiet", f"refs/heads/{name}", check=False).returncode == 0


def create_branch(name: str, base: str, replace: bool = False) -> None:
    if not name.startswith(BRANCH_PREFIX):
        raise GitError(f"refusing to create {name!r}: agent branches must start with {BRANCH_PREFIX}")
    if branch_exists(name):
        if not replace:
            raise GitError(f"{name} already exists; review or delete it, or pass --replace-branch")
        git("branch", "-D", name)
    git("switch", "-c", name, base)


def apply_diff(diff_file: Path) -> tuple[bool, str]:
    """git apply, all or nothing. --recount tolerates the wrong hunk line counts models often write."""
    options = ["--recount", "--whitespace=nowarn"]
    check = git("apply", "--check", *options, str(diff_file), check=False)
    if check.returncode != 0:
        return False, (check.stderr or check.stdout).strip()
    applied = git("apply", *options, str(diff_file), check=False)
    return applied.returncode == 0, (applied.stderr or applied.stdout).strip()


def commit_backend_src(message: str) -> str:
    """Stages only backend/src, so nothing else can ride along into an agent commit."""
    git("add", "--all", "--", BACKEND_SRC)
    git("commit", "--quiet", "-m", message)
    return head_sha()


def discard_uncommitted_backend_src() -> None:
    git("checkout", "--", BACKEND_SRC, check=False)
    git("clean", "-fdq", "--", BACKEND_SRC, check=False)


def return_to(base: str) -> None:
    """Leaves the agent branch exactly as committed and switches back to the base branch."""
    if current_branch().startswith(BRANCH_PREFIX):
        discard_uncommitted_backend_src()
    git("switch", "--quiet", base)


def diff_between(base: str, ref: str = "HEAD") -> str:
    """The agent's change: backend/src only, from the run's base commit, so nothing else is counted."""
    return git("diff", base, ref, "--", BACKEND_SRC).stdout


def show_file(ref: str, path: str) -> str | None:
    process = git("show", f"{ref}:{path}", check=False)
    return process.stdout if process.returncode == 0 else None
