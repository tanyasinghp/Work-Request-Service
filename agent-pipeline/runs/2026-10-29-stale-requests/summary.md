# Flag stale requests

- **Story:** `stale-requests`
- **Outcome:** stopped (stopped at write-tests)
- **Branch:** `agent/stale-requests` (never merged or pushed by the pipeline)
- **First-try gate pass:** no
- **Attempts:** 1
- **LLM:** claude-cli (claude-opus-5)
- **Time taken:** 2453 s; model cost $0.56
- **Final diff size:** 0 changed lines

## Story

# Flag stale requests

As a maintenance supervisor, I want requests older than 30 days to be flagged as stale in
the problems list, so that old reports that may no longer be relevant get reviewed instead
of silently joining the queue.

"Older than 30 days" is measured against the time the batch is processed. A stale request
is reported as a problem; whether it is also kept out of the queue is a decision the
acceptance criteria must state explicitly.

## Generated acceptance criteria

1. **Given** the ProblemCode enum, **when** the story is implemented, **then** a new constant STALE exists, declared last (after CONFLICTING_REQUEST_ID), so ProblemCode.values() is NULL_REQUEST, MISSING_FIELD, INVALID_VALUE, DUPLICATE, CONFLICTING_REQUEST_ID, STALE.
2. **Given** the Problem record, **when** Problem.stale(7, "WR-100", 45L) is called (signature: static Problem stale(int inputIndex, String requestId, long ageInDays)), **then** it returns a Problem with inputIndex 7, requestId "WR-100", code STALE, field "requestedAt", relatedRequestId null, escalatedFrom null, escalatedTo null, and message exactly "WR-100 is stale: requested 45 days before processing" (no input index in the message).
3. **Given** a WorkRequestValidator built with new WorkRequestValidator(Clock.fixed(Instant.parse("2026-10-29T00:00:00Z"), ZoneOffset.UTC), 30) and a batch of one otherwise-valid request whose requestedAt is "2026-09-01T00:00:00Z" (58 days earlier), **when** validate(batch) is called, **then** the StageResult requests list is empty, problems contains exactly one Problem equal to Problem.stale(0, thatRequestId, 58L), and the trace is new StageTrace("validate", 1, 0, List.of("<requestId> rejected: stale (58 days old)")).
4. **Given** a validator with a fixed clock at "2026-10-29T00:00:00Z" and maxAgeDays 30, **when** it validates otherwise-valid requests whose requestedAt is exactly 30 days earlier ("2026-09-29T00:00:00Z"), 30 days minus one second earlier, and 30 days plus one second earlier, **then** only the 30-days-plus-one-second request is flagged STALE (staleness is strictly greater than maxAgeDays, measured as Duration.between(requestedAt, clock.instant()).compareTo(Duration.ofDays(maxAgeDays)) > 0); the other two are kept in the requests list with no problems.
5. **Given** a validator with a fixed clock and maxAgeDays 30, **when** it validates an otherwise-valid request whose requestedAt is after the clock instant (future-dated), **then** the request is kept and no STALE problem is reported (negative age is never stale).
6. **Given** a validator with a fixed clock and maxAgeDays 30, **when** it validates a request that is 60 days old but also has a null equipmentId, and separately a null request, and separately a request with requestedAt reported as an unreadable InvalidField, **then** only the existing MISSING_FIELD / NULL_REQUEST / INVALID_VALUE problems are reported and no STALE problem is added; the staleness check runs only on requests that have no other validation problem, so it never dereferences a null requestedAt.
7. **Given** existing callers that construct the validator with the no-argument constructor new WorkRequestValidator() (and processors built with new WorkRequestProcessor(validator, resolver, sorter)), **when** any batch is validated, including requests years old, **then** no STALE problem is produced and no request is dropped for age: the no-arg constructor leaves the staleness check disabled, and the clock/threshold constructor WorkRequestValidator(Clock clock, int maxAgeDays) is the only way to enable it; the clock is rejected with NullPointerException when null and maxAgeDays < 1 is rejected with IllegalArgumentException.
8. **Given** a WorkRequestProcessor whose validator has a fixed clock at "2026-10-29T00:00:00Z" and maxAgeDays 30, and a batch containing one stale request (90 days old) that is otherwise valid and is a content duplicate of a fresh request, plus two fresh non-duplicate requests, **when** process(batch) is called, **then** the queue contains only the fresh requests (the stale one is excluded), the STALE problem appears among the validate-stage problems ordered by input index before any deduplicate-stage problem, the stale request produces no DUPLICATE problem and causes no priority escalation of the kept request, and the trace still has exactly three entries validate/deduplicate/prioritize with the validate out count excluding the stale request.
9. **Given** a batch containing stale and fresh requests and a processor with a fixed clock, **when** the same batch is shuffled and processed repeatedly, **then** the queue, the problem list and the trace notes are identical every time (staleness depends only on requestedAt and the clock, and the stale note is sorted into the validate notes with no input index in it).
10. **Given** the Spring context with default configuration, **when** it starts, **then** a Clock bean returning Clock.systemUTC() is defined in ProcessingConfig, a StalenessProperties record @ConfigurationProperties("app.staleness") with component maxAgeDays and @DefaultValue("30") binds app.staleness.max-age-days, the workRequestValidator bean is built from that Clock and StalenessProperties.maxAgeDays, and application.yml contains app.staleness.max-age-days: 30.
11. **Given** the property app.staleness.max-age-days bound to 7, **when** the context starts and a request 10 days old is processed through the wired processor, **then** the request is reported STALE and excluded, showing the threshold is configurable; binding a value below 1 fails with IllegalArgumentException whose message is "app.staleness.max-age-days must be at least 1, was <value>".
12. **Given** the BatchObserver metrics wiring, **when** a processing result containing two STALE problems is observed, **then** a counter for ProblemCode.STALE is registered using the same naming scheme as the existing per-code counters and is incremented by 2, so there is still exactly one counter per ProblemCode value.

Affected classes: ProblemCode, Problem, WorkRequestValidator, StalenessProperties, ProcessingConfig, BatchObserver
Out of scope: Adding a fourth pipeline stage or a fourth trace entry for staleness; Changing the timestamps in sample-batch.json or the sample batch's pinned expectations; An API parameter or request header that overrides the staleness threshold per call; A review/acknowledge workflow, un-staling, or persistence of stale requests; Rejecting or warning about future-dated requestedAt values; Per-priority or per-equipment staleness thresholds; Frontend display of the new problem code

## Gate result per attempt

| Attempt | Result | Detail | Seconds |
| --- | --- | --- | --- |
| - | did not reach the gate | | |

## What failed

- Run stopped at write-tests: model call failed: claude -p exited 143: 

## Manual fixes

- None needed.

## Review notes

- Infrastructure failure, not an agent failure: the laptop lid was closed at 21:56, three minutes into the Write-tests call, and the Mac slept until 22:28 (pmset log). The API stream died during sleep and the CLI call never returned; the pipeline's 15-minute timeout used a monotonic clock that stops during sleep, so it never fired.
- Stopped by hand after about 37 minutes by killing the hung claude -p process (exit 143); the pipeline then recorded the failure and returned to main cleanly.
- Fixed in the pipeline afterwards: the CLI deadline is now wall-clock time and a failed model call gets the same single retry as invalid JSON.
- Enrich and Implement attempt 1 had completed (101-line diff, applied and committed). The story was rerun from scratch; that rerun replaced agent/stale-requests, and this attempt's diff is kept here as implement-1.diff.
