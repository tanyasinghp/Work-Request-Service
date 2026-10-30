# Flag stale requests

- **Story:** `stale-requests`
- **Outcome:** stopped (stopped at implement-2)
- **Branch:** `agent/stale-requests` (never merged or pushed by the pipeline)
- **First-try gate pass:** no
- **Attempts:** 2
- **LLM:** claude-cli (claude-opus-5)
- **Time taken:** 551 s; model cost $1.68
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

1. **Given** the ProblemCode enum, **when** the new code is added, **then** ProblemCode contains a constant named STALE in addition to the existing NULL_REQUEST, MISSING_FIELD, INVALID_VALUE, DUPLICATE, CONFLICTING_REQUEST_ID (order of declaration is irrelevant); because BatchObserver derives one metrics counter per ProblemCode value, no change to BatchObserver is required for a counter named after STALE to appear.
2. **Given** the Problem factories, **when** Problem.stale(int inputIndex, String requestId, long maxAgeDays) is called, e.g. Problem.stale(3, "R-7", 30), **then** it returns a Problem with inputIndex 3, requestId "R-7", code STALE, null field, null relatedRequestId, null escalatedFrom/escalatedTo, and message exactly "R-7 is older than 30 days" (pattern: requestId + " is older than " + maxAgeDays + " days"); the message never mentions the input index.
3. **Given** a WorkRequestValidator built with the existing no-argument constructor new WorkRequestValidator(), **when** validate is called with requests whose requestedAt is arbitrarily far in the past, **then** no STALE problem is ever produced and the stage result is exactly as before this story (staleness checking is off unless a Clock is supplied), so existing direct-construction tests such as SampleBatchTest keep passing regardless of the real date.
4. **Given** a validator built with the new overload new WorkRequestValidator(Clock clock, Duration maxAge), clock = Clock.fixed(Instant.parse("2026-10-29T00:00:00Z"), ZoneOffset.UTC) and maxAge = Duration.ofDays(30), **when** validate is called with an otherwise fully valid request "R-1" whose requestedAt is 2026-09-01T00:00:00Z (58 days before now), **then** the returned StageResult problems contain exactly one problem, Problem.stale(inputIndex, "R-1", 30), and the request is NOT in StageResult.requests(), so it never reaches deduplication, prioritization, or the accepted queue.
5. **Given** the same clock-backed validator (now = 2026-10-29T00:00:00Z, maxAge = 30 days), **when** validate is called with valid requests whose requestedAt is exactly 2026-09-29T00:00:00Z (age exactly 30 days), 2026-09-28T23:59:59Z (age 30 days + 1 second), and 2026-11-05T00:00:00Z (dated in the future), **then** only the 30-days-plus-1-second request is reported STALE and excluded; the exactly-30-days request and the future-dated request are accepted with no problem (the rule is Duration.between(requestedAt, clock.instant()).compareTo(maxAge) > 0).
6. **Given** the clock-backed validator and a request that is both stale and already invalid (for example requestedAt null, or missing equipmentId and dated 100 days ago), **when** validate is called, **then** only the existing MISSING_FIELD/INVALID_VALUE/NULL_REQUEST problems are reported for that request and no STALE problem is added; staleness is evaluated only for requests that pass all field checks (so a null requestedAt can never trigger a STALE problem).
7. **Given** the clock-backed validator and a batch of 4 valid requests of which 2 are older than maxAge, **when** validate is called, **then** the single validate StageTrace reports stage "validate", in = 4, out = 2 (stale requests are counted out), and its notes include one entry per stale request of the exact form requestId + " rejected: stale" (e.g. "R-1 rejected: stale"); notes remain sorted naturally and contain no input indices, and the result still has exactly the three stages validate, deduplicate, prioritize.
8. **Given** a WorkRequestProcessor built over a clock-backed validator, and a batch containing a stale request that is a content duplicate of a fresh kept request plus other requests with validation and duplicate problems, **when** process is called, and again with the same batch shuffled, **then** the stale request produces only the STALE problem (no DUPLICATE problem, because deduplication never sees it), ProcessingResult.problems() lists all validate-stage problems (including STALE) before deduplicate-stage problems and, within the validate stage, in ascending inputIndex order, and the accepted queue, problems and trace are identical for every input order.
9. **Given** configuration binding, **when** the context starts with no app.staleness.* properties set, **then** a @ConfigurationProperties("app.staleness") record StalenessProperties(@DefaultValue("30d") Duration maxAge) binds maxAge to Duration.ofDays(30); setting app.staleness.max-age=7d binds Duration.ofDays(7); a non-positive value (0 or negative, e.g. app.staleness.max-age=0s) fails with an IllegalArgumentException whose message is "app.staleness.max-age must be positive".
10. **Given** the Spring wiring, **when** the application context is started, **then** ProcessingConfig exposes a java.time.Clock bean returning Clock.systemUTC() and builds the WorkRequestValidator bean as new WorkRequestValidator(clock, stalenessProperties.maxAge()), so the deployed service flags staleness while the no-arg constructor stays available for tests; the existing constructors new WorkRequestValidator(), new DuplicateResolver(new DescriptionNormalizer()), new WorkRequestSorter() and new WorkRequestProcessor(validator, resolver, sorter) still compile and behave unchanged.
11. **Given** the clock-backed validator with maxAge = Duration.ofDays(7), **when** validate is called with a valid request dated 10 days before the fixed now, **then** the problem message is exactly requestId + " is older than 7 days", i.e. the day count in the message comes from the configured maxAge.toDays() and not from the request's actual age.

Affected classes: ProblemCode, Problem, WorkRequestValidator, StalenessProperties, ProcessingConfig
Out of scope: Adding a fourth pipeline stage or a fourth StageTrace entry; staleness is reported inside the existing validate stage; Adding fields to Problem, ProblemDto or BatchResponse (such as the request's actual age in days); An API flag or query parameter to include stale requests in the queue anyway; Per-priority or per-equipment staleness thresholds, or business-day/time-zone aware ageing; Auto-closing, re-dating or archiving stale requests, and any persistence of review decisions; Changes to DuplicateResolver, WorkRequestSorter or DescriptionNormalizer behaviour; Updating the sample-batch.json timestamps or any frontend/UI work

## Gate result per attempt

| Attempt | Result | Detail | Seconds |
| --- | --- | --- | --- |
| 1 | failed | OrderIndependenceTest.generatedBatchCoversEveryKindOfProblem | 7 |

## Tests written from the criteria

- `StalenessProblemTest.problemCodeContainsStaleAlongsideTheExistingCodes`
- `StalenessProblemTest.staleCarriesTheInputIndexItWasGiven`
- `StalenessProblemTest.staleCarriesTheRequestIdItWasGiven`
- `StalenessProblemTest.staleUsesTheStaleProblemCode`
- `StalenessProblemTest.staleMessageNamesTheRequestIdAndTheMaxAgeInDays`
- `StalenessProblemTest.staleMessageUsesTheMaxAgeDayCountItWasGiven`
- `StalenessProblemTest.staleLeavesTheFieldUnset`
- `StalenessProblemTest.staleLeavesTheRelatedRequestIdUnset`
- `StalenessProblemTest.staleLeavesBothEscalationFieldsUnset`
- `StalenessValidationTest.validatorBuiltWithoutAClockNeverReportsStaleProblems`
- `StalenessValidationTest.validatorBuiltWithoutAClockKeepsAncientRequests`
- `StalenessValidationTest.validatorBuiltWithoutAClockTracesAncientRequestsAsAccepted`
- `StalenessValidationTest.reportsExactlyOneStaleProblemForARequestOlderThanMaxAge`
- `StalenessValidationTest.keepsAStaleRequestOutOfTheRequestsThatReachTheNextStage`
- `StalenessValidationTest.acceptsARequestWhoseAgeIsExactlyMaxAge`
- `StalenessValidationTest.reportsStaleForARequestOneSecondOlderThanMaxAge`
- `StalenessValidationTest.acceptsAFutureDatedRequest`
- `StalenessValidationTest.doesNotReportStaleForARequestWithoutARequestedAt`
- `StalenessValidationTest.doesNotAddStaleToARequestThatAlreadyFailsAFieldCheck`
- `StalenessValidationTest.doesNotAddStaleToANullRequest`
- `StalenessValidationTest.tracesStaleRequestsAsRejectedWithinTheValidateStage`
- `StalenessValidationTest.keepsOnlyTheFreshRequestsOfAPartlyStaleBatch`
- `StalenessValidationTest.reportsStaleProblemsInAscendingInputIndexOrder`
- `StalenessValidationTest.takesTheDayCountInTheMessageFromTheConfiguredMaxAge`
- `StaleRequestProcessingTest.reportsOnlyStaleForAStaleDuplicateBecauseDeduplicationNeverSeesIt`
- `StaleRequestProcessingTest.keepsTheFreshOriginalRatherThanTheEarlierStaleCopy`
- `StaleRequestProcessingTest.listsValidateStageProblemsBeforeDeduplicateStageProblems`
- `StaleRequestProcessingTest.tracesExactlyTheThreeExistingStages`
- `StaleRequestProcessingTest.countsStaleRequestsAsRejectedByTheValidateStage`
- `StaleRequestProcessingTest.notesTheStaleRejectionByRequestIdInTheValidateStage`
- `StaleRequestProcessingTest.producesTheSameAcceptedQueueForEveryInputOrder`
- `StaleRequestProcessingTest.producesTheSameTraceForEveryInputOrder`
- `StaleRequestProcessingTest.producesTheSameProblemsForEveryInputOrder`
- `StaleRequestProcessingTest.keepsTheDeduplicateStageProblemLastForEveryInputOrder`
- `StaleRequestProcessingTest.ordersValidateStageProblemsByInputIndexForEveryInputOrder`
- `StalenessPropertiesTest.defaultsMaxAgeToThirtyDaysWhenNoPropertyIsSet`
- `StalenessPropertiesTest.bindsMaxAgeFromAppStalenessMaxAge`
- `StalenessPropertiesTest.rejectsAZeroMaxAge`
- `StalenessPropertiesTest.rejectsANegativeMaxAge`
- `StalenessWiringTest.exposesASystemUtcClockBean`
- `StalenessWiringTest.buildsTheValidatorBeanWithTheConfiguredMaxAge`
- `StalenessWiringTest.keepsStaleRequestsOutOfTheQueueOfTheWiredProcessor`
- `StalenessWiringTest.stillOffersAStalenessFreeValidatorThroughTheNoArgumentConstructor`
- `StalenessWiringTest.stillBuildsAWorkingPipelineFromTheExistingConstructors`

## What failed

- Attempt 1: gate failed at OrderIndependenceTest.generatedBatchCoversEveryKindOfProblem
- Run stopped at implement-2: model call failed: claude -p exited 1: {"is_error":true,"duration_api_ms":0,"num_turns":1,"stop_reason":"stop_sequence","session_id":"d08f3c37-96ea-41f4-bfff-31dfa5c79f4b","total_cost_usd":0,"usage":{"output_tokens_details":{"thinking_tokens":0},"input_tokens":0,"cache_creation_input_tokens":0,"cache_read_input_tokens":0,"output_tokens":0,"server_tool_use":{"web_search_requests":0,"web_fetch_requests":0},"service_tier":"standard","cache_creation":{"ephemeral_1h_input_tokens":0,"ephemeral_5m_input_tokens":0},"inference_geo":"","iterations":[],"speed":"standard"},"modelUsage":{},"permission_denials":[],"terminal_reason":"api_error","fast_mode_state":"off","subtype":"success","api_error_status":429,"result":"You've hit your session limit · resets 11:20pm (Asia/Calcutta)","type":"result","duration_ms":622,"uuid":"f593cab1-d129-4d73-854e-f8b5b2d377c4"}
Client.listTools() called but server does not advertise tools capability - returning empty list


## Manual fixes

- Extend OrderIndependenceTest's generated batch with a stale request so generatedBatchCoversEveryKindOfProblem covers STALE (the gate failure; attempt 2 would have received exactly this failure but never ran).
- Time bomb: the Spring-wired validator uses the system clock with the 30-day default, and sample-batch.json is dated 2026-10-20, so from 2026-11-20 the demo sample and the Spring-context API tests would report every request as STALE. Pin a clock (or max-age) for those tests and make the sample dates relative, or exempt the sample.
- Web UI: add STALE to the front end's problem codes, labels, stage mapping and colours (the agent may only edit backend/src).

## Review notes

- Stopped at implement-2 because the Claude account behind claude -p hit its session usage limit (HTTP 429, 'resets 11:20pm'); both the call and its one retry failed instantly. Not an agent failure, but the run did not complete, so the story was rerun once the limit reset.
- Design choices worth keeping: app.staleness.max-age as a Duration with a positive-only check; staleness off for the no-arg constructor so existing fixtures stay valid; stale requests rejected in validate so they never reach deduplication; messages quote the configured threshold rather than the request's age, so they don't change day to day.
