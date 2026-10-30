# Report future timestamps

- **Story:** `suspicious-timestamp`
- **Outcome:** passed
- **Branch:** `agent/suspicious-timestamp` (never merged or pushed by the pipeline)
- **First-try gate pass:** yes
- **Attempts:** 1
- **LLM:** claude-cli (claude-opus-5)
- **Time taken:** 714 s; model cost $2.73
- **Final diff size:** 2409 changed lines

## Story

# Report future timestamps

As an integrator sending batches from several sites, I want a SUSPICIOUS_TIMESTAMP problem
for any request whose requestedAt is in the future, so that clock or time-zone bugs in a
sending system are caught instead of distorting the queue order.

"In the future" is relative to the time the batch is processed, and the check must be
testable without depending on the real clock.

## Generated acceptance criteria

1. **Given** ProblemCode and Problem, **when** a new problem is needed for future timestamps, **then** ProblemCode has a new constant SUSPICIOUS_TIMESTAMP declared last (after CONFLICTING_REQUEST_ID) and Problem gains a static factory Problem.suspiciousTimestamp(int inputIndex, String requestId, Instant requestedAt) producing code SUSPICIOUS_TIMESTAMP, field "requestedAt", null relatedRequestId/escalatedFrom/escalatedTo, and message "<requestId> has requestedAt <instant> in the future" using Instant.toString(), or "Request has requestedAt <instant> in the future" when requestId is null.
2. **Given** a WorkRequestValidator built with Clock.fixed(Instant.parse("2026-10-20T12:00:00Z"), ZoneOffset.UTC), **when** validate is called on one otherwise-valid request with requestedAt 2026-10-21T08:30:00Z, **then** exactly one problem is returned, with code SUSPICIOUS_TIMESTAMP, field "requestedAt", inputIndex 0 and the request's requestId, and the request is absent from StageResult.requests().
3. **Given** the same clock fixed at 2026-10-20T12:00:00Z, **when** requests with requestedAt 2026-10-20T12:00:00Z (exactly equal) or 2026-10-20T11:59:59.999Z are validated, **then** no SUSPICIOUS_TIMESTAMP problem is reported and both are accepted; only an instant strictly after the clock instant is future, with no tolerance window and no configuration property.
4. **Given** a request whose requestedAt is null, or whose requestedAt is listed in invalidFieldsByIndex as an unreadable raw value, **when** it is validated with a fixed clock, **then** only MISSING_FIELD or INVALID_VALUE for field "requestedAt" is reported and no SUSPICIOUS_TIMESTAMP problem, since the check needs a parsed instant.
5. **Given** a request WR-1 missing equipmentId and also dated in the future, **when** it is validated, **then** both problems are reported for that input index, the existing field-order problems first and SUSPICIOUS_TIMESTAMP last, and the validate trace note is the index-free "WR-1 rejected: missing equipmentId, future requestedAt" (label "Request without id" when the id is blank), with notes still sorted and out counting only accepted requests.
6. **Given** a batch with identical content in a past-dated and a future-dated request, processed by WorkRequestProcessor with a fixed clock, **when** process(batch) runs and the batch is shuffled repeatedly, **then** the future-dated request is rejected in validate, never reaches deduplicate (no DUPLICATE problem or escalation for it), the trace still has exactly three entries validate/deduplicate/prioritize, and queue, problems and notes are identical for every ordering with SUSPICIOUS_TIMESTAMP problems ordered by inputIndex inside the validate block.
7. **Given** existing callers of new WorkRequestValidator(), **when** the clock is introduced, **then** the no-arg constructor still compiles and behaves as if given Clock.systemUTC(), a new WorkRequestValidator(Clock clock) overload exists that throws NullPointerException with message "clock" on null, and clock.instant() is read once per validate call so all requests in a batch share one instant.
8. **Given** the Spring context, **when** it starts with no overriding bean, **then** ProcessingConfig exposes a java.time.Clock bean named clock returning Clock.systemUTC() and builds the workRequestValidator bean with it.
9. **Given** the pinned 13-request sample batch (all requestedAt on 2026-10-20) and the generated order-independence batch, **when** they are processed after 2026-10-20 with the system clock, **then** the sample batch produces no SUSPICIOUS_TIMESTAMP and keeps its pinned queue, problems and counts 13 → 10 → 6, while the generated batch is extended to include a future-dated request so every ProblemCode value, including SUSPICIOUS_TIMESTAMP, is still covered and BatchObserver reports one counter per code.

Affected classes: ProblemCode, Problem, WorkRequestValidator, ProcessingConfig, OrderIndependenceTest, BatchObserver
Out of scope: Any configurable clock-skew tolerance or app.* property for the future check; Flagging implausibly old requestedAt values; Making DuplicateResolver, WorkRequestSorter or WorkRequestProcessor clock-aware; Changing the pinned sample-batch.json data or its expected output; Changing the API DTO shape or adding a new HTTP endpoint/response field; Changing existing ProblemCode constant order or existing problem messages

## Gate result per attempt

| Attempt | Result | Detail | Seconds |
| --- | --- | --- | --- |
| 1 | passed |  | 7 |

## Tests written from the criteria

- `SuspiciousTimestampProblemTest.declaresSuspiciousTimestampLastAfterConflictingRequestId`
- `SuspiciousTimestampProblemTest.buildsASuspiciousTimestampProblemForTheRequestedAtField`
- `SuspiciousTimestampProblemTest.keepsTheInputIndexAndRequestIdItWasGiven`
- `SuspiciousTimestampProblemTest.leavesRelatedRequestIdAndEscalationFieldsNull`
- `SuspiciousTimestampProblemTest.namesTheRequestAndTheInstantInTheMessage`
- `SuspiciousTimestampProblemTest.fallsBackToAGenericSubjectWhenTheRequestIdIsNull`
- `SuspiciousTimestampProblemTest.rendersTheInstantWithInstantToStringSoSubSecondPrecisionIsKept`
- `FutureRequestedAtValidationTest.reportsOneSuspiciousTimestampProblemForAFutureDatedRequest`
- `FutureRequestedAtValidationTest.dropsTheFutureDatedRequestFromTheStageResultRequests`
- `FutureRequestedAtValidationTest.countsOnlyAcceptedRequestsInTheValidateTrace`
- `FutureRequestedAtValidationTest.acceptsARequestDatedExactlyAtTheClockInstant`
- `FutureRequestedAtValidationTest.acceptsARequestDatedOneMillisecondBeforeTheClockInstant`
- `FutureRequestedAtValidationTest.flagsARequestDatedOneMillisecondAfterTheClockInstantSoThereIsNoToleranceWindow`
- `FutureRequestedAtValidationTest.reportsOnlyMissingFieldWhenRequestedAtIsNull`
- `FutureRequestedAtValidationTest.reportsOnlyInvalidValueWhenRequestedAtCouldNotBeParsed`
- `FutureRequestedAtValidationTest.reportsExistingFieldProblemsBeforeTheSuspiciousTimestampProblem`
- `FutureRequestedAtValidationTest.reportsBothProblemsForTheSameInputIndex`
- `FutureRequestedAtValidationTest.writesAnIndexFreeNoteListingMissingAndFutureReasons`
- `FutureRequestedAtValidationTest.writesAFutureRequestedAtNoteForAnOtherwiseValidRequest`
- `FutureRequestedAtValidationTest.labelsABlankIdRequestAsRequestWithoutIdInTheNote`
- `FutureRequestedAtValidationTest.keepsNotesSortedWhenAFutureRequestIsRejected`
- `FutureRequestedAtValidationTest.noArgConstructorFlagsARequestDatedAfterTheRealNow`
- `FutureRequestedAtValidationTest.noArgConstructorAcceptsARequestDatedBeforeTheRealNow`
- `FutureRequestedAtValidationTest.acceptsSampleBatchDatesUnderTheSystemClockBecauseTheyAreInThePast`
- `FutureRequestedAtValidationTest.rejectsANullClock`
- `FutureRequestedAtValidationTest.readsTheClockOncePerValidateCall`
- `FutureRequestedAtValidationTest.comparesEveryRequestInABatchAgainstTheSameInstant`
- `FutureRequestedAtPipelineTest.reportsOnlySuspiciousTimestampProblemsForTheFutureDatedRequests`
- `FutureRequestedAtPipelineTest.reportsNoDuplicateProblemForTheFutureTwin`
- `FutureRequestedAtPipelineTest.neverEscalatesAPriorityFromTheFutureTwin`
- `FutureRequestedAtPipelineTest.keepsTheThreeStageTraceWhenAFutureRequestIsRejected`
- `FutureRequestedAtPipelineTest.passesOnlyTheAcceptedRequestsIntoDeduplicate`
- `FutureRequestedAtPipelineTest.producesTheSameQueueForEveryInputOrder`
- `FutureRequestedAtPipelineTest.producesTheSameProblemsForEveryInputOrder`
- `FutureRequestedAtPipelineTest.producesTheSameTraceNotesForEveryInputOrder`
- `FutureRequestedAtPipelineTest.ordersSuspiciousTimestampProblemsByInputIndex`
- `FutureRequestedAtPipelineTest.keepsEveryProblemCodeIncludingSuspiciousTimestampReachable`
- `ClockBeanWiringTest.exposesAClockBeanNamedClock`
- `ClockBeanWiringTest.theClockBeanIsTheSystemUtcClock`
- `ClockBeanWiringTest.buildsTheValidatorWithTheContextClockSoFutureRequestsAreFlagged`
- `ClockBeanWiringTest.buildsTheValidatorWithTheContextClockSoPastRequestsAreAccepted`
- `BatchObserverSuspiciousTimestampTest.registersOneRejectionCounterPerProblemCode`
- `BatchObserverSuspiciousTimestampTest.countsAFutureDatedRequestUnderTheSuspiciousTimestampCode`

## Guardrail warnings (review these)

- backend/src/test/java/com/tanya/workrequests/processing/OrderIndependenceTest.java: changes 1 existing assertion line(s); review them

## What failed

- Nothing.

## Manual fixes

- Web UI: add SUSPICIOUS_TIMESTAMP to web/src/types.ts, PROBLEM_CODES/PROBLEM_LABELS (logic/problems.ts), STAGE_OF_CODE (logic/stepping.ts) and PROBLEM_STYLES (components/styles.ts). Without it the Problems lane silently drops the new problems and the input panel throws on the missing colour. The agent could not do this: it may only edit backend/src.

## Review notes

- Backend change reviewed and fine as is: Clock bean plus a WorkRequestValidator(Clock) overload, the no-arg constructor kept, now read once per batch, strictly-after comparison, unreadable dates stay INVALID_VALUE.
- The OrderIndependenceTest edit flagged by the guardrails is legitimate: one future-dated request added to the generator, so the size assertion moved from 200 to 201.
- Two tests use the real clock (now ± 1 hour) only to prove the no-arg constructor uses the system clock; acceptable.
- Commit 644ffc6 ('add agentic pipeline') was made outside the pipeline during this run while the agent branch was checked out; it contains only this run's first five log files. The recorded final diff size (2409 lines) includes those 1688 log lines; the agent's own change is 721 lines in backend/src. The pipeline now stops if its branch changes underneath it.
