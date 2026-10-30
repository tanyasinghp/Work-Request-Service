# Flag stale requests

- **Story:** `stale-requests`
- **Outcome:** stopped (stopped at gate)
- **Branch:** `agent/stale-requests` (never merged or pushed by the pipeline)
- **First-try gate pass:** no
- **Attempts:** 2
- **LLM:** claude-cli (claude-opus-5)
- **Time taken:** 888 s; model cost $2.92
- **Final diff size:** 846 changed lines

## Story

# Flag stale requests

As a maintenance supervisor, I want requests older than 30 days to be flagged as stale in
the problems list, so that old reports that may no longer be relevant get reviewed instead
of silently joining the queue.

"Older than 30 days" is measured against the time the batch is processed. A stale request
is reported as a problem; whether it is also kept out of the queue is a decision the
acceptance criteria must state explicitly.

## Generated acceptance criteria

1. **Given** the ProblemCode enum, **when** its values are listed, **then** a sixth constant STALE exists, appended last so existing constants keep their order: NULL_REQUEST, MISSING_FIELD, INVALID_VALUE, DUPLICATE, CONFLICTING_REQUEST_ID, STALE.
2. **Given** the new static factory Problem.stale(int inputIndex, String requestId, Instant requestedAt, Duration maxAge), **when** called with 3, "R-7", 2026-09-01T08:00:00Z and Duration.ofDays(30), **then** it returns a Problem with inputIndex 3, requestId "R-7", code STALE, field "requestedAt", null relatedRequestId/escalatedFrom/escalatedTo and message exactly "R-7 is stale: requestedAt 2026-09-01T08:00:00Z is more than 30 days old" (day count from maxAge.toDays(); no input index in the message).
3. **Given** a validator built with the new constructor WorkRequestValidator(Clock clock, Duration maxAge) using Clock.fixed(2026-10-20T08:00:00Z, UTC) and Duration.ofDays(30), **when** validate is called with otherwise valid R-1 requestedAt 2026-09-01T08:00:00Z and R-2 requestedAt 2026-10-19T08:00:00Z, **then** StageResult.requests holds only R-2 and problems hold exactly one STALE problem for R-1 at its input index: a stale request is both reported as a problem and kept out of the queue.
4. **Given** the same validator (fixed clock, 30 days), with clock.instant() read once per validate call so all requests share one "now", **when** validate is called with requests requestedAt 2026-09-20T08:00:00Z (exactly 30 days), 2026-09-20T07:59:59Z (one second older) and 2026-11-01T08:00:00Z (future), **then** only the one-second-older request is STALE; staleness is strictly requestedAt.isBefore(now.minus(maxAge)), and future dates are accepted with no problem.
5. **Given** a validator with a fixed clock and 30 day max age, **when** validate is called with (a) a null element, (b) a 90-day-old request also missing equipmentId, (c) a 90-day-old request whose requestedAt is reported unreadable via invalidFieldsByIndex, **then** (a) yields only NULL_REQUEST, (b) only the MISSING_FIELD problem for equipmentId, (c) only the INVALID_VALUE problem for requestedAt — field checks run first and a request with any field problem is never checked for staleness, so a STALE problem always has a non-null requestId.
6. **Given** a validator with Clock.fixed(2026-10-20T08:00:00Z, UTC) and 30 days, **when** validate is called with 3 requests of which R-7 is 90 days old and R-3 is missing description, **then** the StageTrace is stage "validate", in 3, out 1, notes exactly ["R-3 rejected: missing description", "R-7 rejected: stale"]: one naturally sorted note per rejected request, stale notes read "<requestId> rejected: stale" and contain no input index.
7. **Given** the preserved no-argument constructor new WorkRequestValidator() and the other existing constructors of DuplicateResolver, WorkRequestSorter and WorkRequestProcessor, **when** a batch of years-old but otherwise valid requests is validated through new WorkRequestValidator(), **then** no STALE problem is produced and every request is accepted — the no-arg constructor performs no staleness check, so existing tests including SampleBatchTest's 13 → 10 → 6 counts are unaffected; new WorkRequestValidator(null, Duration.ofDays(30)) and new WorkRequestValidator(Clock.systemUTC(), null) throw NullPointerException ("clock", "maxAge") and a maxAge under one day throws IllegalArgumentException.
8. **Given** a new @ConfigurationProperties("app.staleness") record StalenessProperties(@DefaultValue("30d") Duration maxAge) with the default also written in application.yml, **when** the context starts with no app.staleness property, then with app.staleness.max-age=7d, then with app.staleness.max-age=0d, **then** maxAge binds to Duration.ofDays(30), then Duration.ofDays(7); 0d or any value under one day fails startup with IllegalArgumentException containing "app.staleness.max-age must be at least 1 day".
9. **Given** ProcessingConfig, **when** the Spring context starts, **then** it exposes exactly one java.time.Clock bean named "clock" defaulting to Clock.systemUTC() (replaceable by Clock.fixed(...) in tests) and builds the WorkRequestValidator bean from that Clock and StalenessProperties.maxAge, so a batch posted to the API containing a request requestedAt more than 30 days before the clock returns a ProblemDto with code "STALE" and field "requestedAt" and omits that request from the queue.
10. **Given** a processor built from a staleness-aware validator (fixed clock, 30 days), the real DuplicateResolver and WorkRequestSorter, and a batch where recent MEDIUM R-1 and 90-day-old CRITICAL R-2 share equipmentId and normalized description, alongside invalid requests and duplicates, **when** the batch is processed 100 times in shuffled order, **then** every run gives the identical queue, problems and trace: R-2 produces only a STALE problem with no DUPLICATE problem, R-1 stays in the queue at MEDIUM with no escalation, the trace still has exactly three entries validate/deduplicate/prioritize with validate out == deduplicate in, validate-stage problems (stale and field problems interleaved by input index) precede deduplicate-stage problems, and BatchObserver has a counter for STALE following the existing per-ProblemCode naming scheme that increments once per stale problem.

Affected classes: ProblemCode, Problem, WorkRequestValidator, StalenessProperties, ProcessingConfig, BatchObserver
Out of scope: Changing sample-batch.json or the pinned SampleBatchTest expectations; Flagging requests whose requestedAt is in the future, or any clock-skew tolerance; Marking stale requests as a non-blocking warning that still enters the queue; A per-priority or per-equipment staleness threshold; Re-checking staleness in the deduplicate or prioritize stages, or a new API field/endpoint for stale counts; Replacing Instant.now() usages elsewhere in the codebase with the new Clock bean

## Gate result per attempt

| Attempt | Result | Detail | Seconds |
| --- | --- | --- | --- |
| 1 | rejected before the gate | no file changes found in the diff (missing 'diff --git' or '---/+++' headers) |  |
| 2 | failed | OrderIndependenceTest.generatedBatchCoversEveryKindOfProblem | 7 |

## Tests written from the criteria

- `StaleProblemTest.listsStaleAsSixthAndLastProblemCode`
- `StaleProblemTest.staleFactoryKeepsInputIndexAndRequestId`
- `StaleProblemTest.staleFactoryUsesStaleCodeAndRequestedAtField`
- `StaleProblemTest.staleFactoryLeavesRelationAndEscalationFieldsUnset`
- `StaleProblemTest.staleMessageNamesRequestIdTimestampAndMaxAgeInDays`
- `StaleProblemTest.staleMessageTakesItsDayCountFromTheGivenMaxAge`
- `StaleProblemTest.staleMessageOmitsTheInputIndex`
- `StalenessValidationTest.keepsStaleRequestOutOfTheValidatedQueue`
- `StalenessValidationTest.reportsOneStaleProblemForTheStaleRequestAtItsInputIndex`
- `StalenessValidationTest.acceptsRequestExactlyAtTheMaxAge`
- `StalenessValidationTest.flagsOnlyTheRequestOneSecondOlderThanTheMaxAge`
- `StalenessValidationTest.acceptsRequestDatedInTheFuture`
- `StalenessValidationTest.readsTheClockOncePerValidateCallSoEveryRequestSharesOneNow`
- `StalenessValidationTest.reportsOnlyNullRequestForANullElement`
- `StalenessValidationTest.reportsOnlyTheMissingFieldForAnOldRequestThatIsAlsoIncomplete`
- `StalenessValidationTest.reportsOnlyTheInvalidValueForAnOldRequestWithAnUnreadableRequestedAt`
- `StalenessValidationTest.givesEveryStaleProblemANonNullRequestId`
- `StalenessValidationTest.tracesOneSortedNotePerRejectedRequestIncludingStaleOnes`
- `StalenessValidationTest.tracesValidateCountsWithStaleRequestsLeftOutOfTheOutput`
- `StalenessValidationTest.noArgumentValidatorAcceptsYearsOldRequests`
- `StalenessValidationTest.processorBuiltFromTheExistingConstructorsAcceptsYearsOldRequests`
- `StalenessValidationTest.rejectsANullClock`
- `StalenessValidationTest.rejectsANullMaxAge`
- `StalenessValidationTest.rejectsAMaxAgeShorterThanOneDay`
- `StalenessOrderIndependenceTest.producesTheSameQueueForEveryInputOrder`
- `StalenessOrderIndependenceTest.producesTheSameProblemsForEveryInputOrder`
- `StalenessOrderIndependenceTest.producesTheSameTraceForEveryInputOrder`
- `StalenessOrderIndependenceTest.reportsTheStaleTwinOnlyAsStaleAndNeverAsDuplicate`
- `StalenessOrderIndependenceTest.keepsTheRecentTwinInTheQueueAtItsOwnPriority`
- `StalenessOrderIndependenceTest.escalatesNothingWhenTheOnlyHigherPriorityTwinIsStale`
- `StalenessOrderIndependenceTest.keepsTheThreeStageTraceWithValidateOutputFeedingDeduplicateInput`
- `StalenessOrderIndependenceTest.listsValidateStageProblemsBeforeDeduplicateStageProblems`
- `StalenessOrderIndependenceTest.ordersValidateStageProblemsByInputIndexWithStaleProblemsInterleaved`
- `StalenessPropertiesTest.maxAgeDefaultsToThirtyDaysWhenThePropertyIsAbsent`
- `StalenessPropertiesTest.bindsMaxAgeFromTheProperty`
- `StalenessPropertiesTest.failsStartupWhenMaxAgeIsZero`
- `StalenessPropertiesTest.failsStartupWhenMaxAgeIsUnderOneDay`
- `ClockBeanTest.exposesExactlyOneClockBeanNamedClock`
- `ClockBeanTest.clockBeanDefaultsToTheSystemUtcClock`
- `ClockBeanTest.buildsASingleWorkRequestValidatorBean`
- `StaleRequestWiringTest.defaultsTheConfiguredMaxAgeToThirtyDays`
- `StaleRequestWiringTest.reportsAStaleProblemForARequestOlderThanThirtyDaysBeforeTheClock`
- `StaleRequestWiringTest.leavesTheStaleRequestOutOfTheQueue`
- `ConfiguredStalenessMaxAgeWiringTest.flagsARequestOlderThanTheConfiguredSevenDays`
- `ConfiguredStalenessMaxAgeWiringTest.acceptsARequestYoungerThanTheConfiguredSevenDays`
- `StaleProblemMetricsTest.registersTheStaleRejectionCounterBeforeAnyStaleProblemArrives`
- `StaleProblemMetricsTest.countsOneStaleRejectionPerStaleProblem`

## What failed

- Attempt 1: rejected before the gate: no file changes found in the diff (missing 'diff --git' or '---/+++' headers)
- Attempt 2: gate failed at OrderIndependenceTest.generatedBatchCoversEveryKindOfProblem
- Run stopped at gate: no attempt passed the gate in 2 tries

## Manual fixes

- Extend OrderIndependenceTest's generated batch with one request older than 30 days so generatedBatchCoversEveryKindOfProblem covers STALE; this is the gate failure on attempt 2. The agent never saw that failure: attempt 1 was rejected before the gate, so the retry's feedback was the rejection, not the test output.
- Time bomb: the Spring-wired validator uses the system clock with max-age 30d, while sample-batch.json is dated 2026-10-20. From 2026-11-20 the demo sample and the Spring-context tests that post it (WorkRequestControllerTest, SmokeTest, ApplicationWiringTest) would report every request as STALE. Pin a clock or max-age in those tests and make the sample dates relative to today (or exempt the sample).
- Web UI: add STALE to the front end's problem codes, labels, stage mapping and colours (the agent may only edit backend/src).

## Review notes

- Attempt 1 was a degenerate model answer: about 9,600 output tokens, then the structured output {"diff": "placeholder", "summary": "placeholder"}. The guardrails rejected it correctly. The output schema now requires a real diff header, so a reply like that uses the stage's one JSON retry instead of an Implement attempt.
- Design is the same as the second run and sound: app.staleness.max-age (Duration, at least 1 day), staleness off for the no-arg constructor so existing fixtures stay valid, stale requests rejected in validate before deduplication, and 12 new test classes written from the criteria only.
