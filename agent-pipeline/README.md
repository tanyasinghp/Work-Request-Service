# Agent pipeline

A plain-English story goes in; an `agent/<story-id>` branch and a PR description come out,
but only once the backend's full Maven test suite passes. The gate decides what counts as
done, not the model, and a person reviews every branch before anything is merged.

```
story ─▶ Enrich ─▶ Implement ─▶ Write tests ─▶ Gate (./mvnw -q test) ─▶ Summarize
          JSON      unified       from the          │   fail: back to Implement once
          criteria  diff          criteria only     └── second failure: stop and log
```

| Stage | Input the model sees | Output |
| --- | --- | --- |
| Enrich | the story, `prompts/codebase-notes.md`, the file list, a few key classes | acceptance criteria JSON, validated against `schemas/acceptance_criteria.schema.json` |
| Implement | the criteria, the file list, and **only** the files of the classes the criteria name (on a retry, also the files the failure points at) | a unified diff, applied with `git apply` |
| Write tests | the criteria and the **main-branch** versions of those classes; never the implementation diff | a diff that adds new test files |
| Gate | (no model) `./mvnw -q -B test` in `backend/` on JDK 21 | pass, or the failing output sent back to Implement once |
| Summarize | the story, criteria, gate results, test names and the final diff | `pr-description.md` |

Every model reply is JSON checked against a schema. An invalid reply, or a failed call, gets
**one** retry; a second failure stops the stage. Implement gets **two** attempts in total: a
gate failure, a guardrail rejection or a diff that won't apply all count as a failed attempt.

## Guardrails (enforced in code, before the gate)

Checked on the diff text **and again on the working tree after `git apply`**, so a gap in
the diff parser can't let a change through (`guardrails.py`, `pipeline.py`):

- Only files under `backend/src/` may change (Write tests: only `backend/src/test/`).
- Diffs touching `pom.xml`, `.github/`, the Maven wrapper, Docker or `.gitignore` are rejected,
  as are binary patches and paths that escape the repository.
- Tests may not be deleted, renamed, disabled (`@Disabled`, `@Ignore`, assumptions) or weakened:
  a diff may not remove more test methods or assertions than it adds, and the total number of
  test methods in the tree may never go down. Edits to existing assertions are flagged as
  warnings in `summary.md` for the reviewer.
- Git: the run refuses to start unless it is on a clean `main`; commits go only to
  `agent/<story-id>` and stage only `backend/src`; the run stops if its branch or HEAD changes
  underneath it; it always switches back to `main`. There is no merge and no push anywhere in
  the code (a test checks this).

## Running it

```bash
cd agent-pipeline
python3 -m venv .venv
.venv/bin/pip install -r requirements.txt          # requirements-dev.txt adds pytest and ruff
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home   # or PIPELINE_JAVA_HOME
.venv/bin/python pipeline.py stale-requests        # a story id from stories/, or a path to a .md file
```

Options: `--llm anthropic|claude-cli`, `--model <id or alias>`, `--replace-branch` (delete an
existing `agent/<story-id>` first).

**LLM backends.** The default is `anthropic` when `ANTHROPIC_API_KEY` is set, otherwise
`claude-cli`. The key is only ever read from the environment.

- `anthropic`: the Messages API (`claude-opus-5-5`, effort `high`, streaming) with JSON-schema
  structured output (`output_config.format`) and server-side refusal fallbacks.
- `claude-cli`: `claude -p --output-format json --json-schema … --tools "" --no-session-persistence`,
  run from an empty temporary directory, so the model can't read the repository or run
  commands and no project `CLAUDE.md` is loaded. It uses the account the CLI is logged in with.

The schema file is the strict source of truth. The copy sent to the model drops keywords that
structured output rejects (`minItems`, `pattern`, …), and every reply is validated against the
full schema afterwards.

**While a run is in progress:** don't commit or switch branches in the checkout (the run will
stop itself), and keep the machine awake (`caffeinate -dimsu` on macOS). Model calls have a
15-minute wall-clock deadline.

## Output

Each run writes `runs/<UTC date>-<story-id>[-N]/`:

- `NN-<stage>.prompt.md` and `NN-<stage>.response.json` for every model call (system prompt,
  user prompt, output schema, raw response, model, cost)
- `criteria.json`, `implement-N.diff`, `tests.diff`, `final.diff` (backend/src only, from the
  run's base commit), `gate-N.log` (Maven output), `pr-description.md`
- `run.json` (machine-readable record) and `summary.md` (story, criteria, gate result per
  attempt, what failed, manual fixes, diff size, time taken)

After reviewing a branch, write `review.json` next to `run.json`:

```json
{ "manualFixes": ["what had to change by hand"], "notes": ["anything else worth recording"] }
```

Then `.venv/bin/python export_runs.py` refreshes every `summary.md` with its review and rebuilds
`web/src/data/agentRuns.json` for the Agent runs tab.

## Tests

```bash
.venv/bin/ruff check . && .venv/bin/pytest
```

No model calls and no Maven: guardrails, schemas, gate-output parsing, the CLI backend (with a
stubbed process), git safety, reporting and the export. CI runs the same two commands.

## Runs so far

All on `claude-cli` (claude-opus-5). "Completed" means the run reached a final gate verdict;
the other three were cut short by infrastructure or by the Enrich schema check. Every run's
full log, `summary.md` and `review.json` are in `runs/`; every branch is unmerged.

| Run | Story | Completed | First-try gate pass | Attempts | What failed | Manual fixes needed |
| --- | --- | --- | --- | --- | --- | --- |
| `2026-10-29-suspicious-timestamp` | Report future timestamps (`SUSPICIOUS_TIMESTAMP`) | yes, **passed** | **yes** | 1 | nothing | Web UI doesn't know the new problem code (lane, colours, stage mapping); the agent may only edit `backend/src` |
| `2026-10-29-stale-requests` | Flag stale requests | no (aborted) | – | 1 | laptop slept mid-call; the CLI call hung and the old monotonic timeout never fired (fixed: wall-clock deadline) | none (infrastructure) |
| `2026-10-29-stale-requests-2` | Flag stale requests | no | no | 2 (2nd never ran) | gate: `OrderIndependenceTest.generatedBatchCoversEveryKindOfProblem` (new `STALE` code not in the generated batch); the retry then hit the CLI account's usage limit (HTTP 429) | extend the generator; fix a date time bomb (sample dated 2026-10-20 + 30-day limit on the real clock); web UI code |
| `2026-10-29-stale-requests-3` | Flag stale requests | yes, **stopped** | no | 2 | attempt 1 returned `{"diff": "placeholder"}` (rejected before the gate; now counts as invalid output instead); attempt 2 failed the same coverage test | same three as run 2 |
| `2026-10-29-safety-critical-escalation` | Auto-escalate safety-critical equipment | no | – | 0 | Enrich: first call timed out while the machine slept; the retry returned 15 criteria, over the schema's limit of 12 | none (no code written) |
| `2026-10-30-low-priority-cap` | Cap Low-priority requests per batch | yes, **passed** | no | 2 | attempt 1: a second `BatchProperties` record constructor broke Spring's binding, so every Spring context failed (caught by an independently written test); attempt 2 fixed it from the failure output | Web UI code (mapped to the prioritize stage); a design question on capping inside the sorter |

What the manual-fix column shows: the gate caught real defects twice (Spring binding, missing
coverage of a new code), and review caught three things the gate couldn't, because no test
was positioned to see them: the front end not knowing the new codes, a test that only fails
after a future date, and a design choice worth a second opinion.
