## What changed

Adds a configurable per-batch cap on `Priority.LOW` requests, applied in the existing prioritize stage.

- **`ProblemCode`**: new `OVER_LOW_PRIORITY_CAP`, appended last so existing ordinals are unchanged.
- **`Problem`**: new factory `overLowPriorityCap(inputIndex, requestId, cap)` producing message `"<id> exceeds the per-batch Low-priority cap of <cap>"` with `field`, `relatedRequestId`, `escalatedFrom`, `escalatedTo` all null.
- **`WorkRequestSorter`**: new `WorkRequestSorter(int maxLowPriority)` constructor (rejects negatives); the existing no-arg constructor delegates to a `NO_CAP = Integer.MAX_VALUE` sentinel. Sorting is unchanged; after sorting, LOW requests past the cap are moved out of the accepted list into problems (sorted by input index). Trace notes become one `"<id> dropped: over Low-priority cap of <cap>"` line per capped request, sorted naturally; when nothing is capped the previous single sort note is returned unchanged.
- **`BatchProperties`**: becomes a two-component record `(maxSize, maxLowPriority)` with `@DefaultValue("1000")` on the new component and a `maxLowPriority >= 0` check; the canonical constructor is now annotated `@ConstructorBinding` because a retained one-arg `BatchProperties(int maxSize)` convenience constructor makes constructor selection ambiguous.
- **`ProcessingConfig`**: the `WorkRequestSorter` bean is now built with `batchProperties.maxLowPriority()`.
- **`BatchObserver`**: no production change; it already derives one counter per `ProblemCode` value, and a new test pins that.
- **`OrderIndependenceTest`** (existing test, modified): `generatedBatchCoversEveryKindOfProblem` now runs through a second processor with a Low cap of 1, since a cap is the only way to produce the new code. The pre-existing uncapped processor and the sample-batch shuffle tests are untouched.

## Criteria coverage

1. **Cap of 2, 5 LOW + 3 non-LOW** — `LowPriorityCapTest.keepsTheEarliestLowRequestsUpToTheCapAndEveryNonLowRequest`, `reportsEveryLowRequestBeyondTheCapAsAProblem`, `countsOnlyLowPriorityRequestsAgainstTheCap`.
2. **Ties break by requestId ascending** — `LowPriorityCapTest.keepsTheLowestRequestIdWhenRequestedAtTies`, `reportsTheHigherRequestIdOfATieAsOverCap` (uses the criterion's `B-2`/`A-1` at `2026-10-20T08:00:00Z`).
3. **Identical output over 100 shuffles** — `LowPriorityCapTest.acceptsTheSameQueueWhateverTheInputOrder`, `reportsTheSameOverCapRequestIdsWhateverTheInputOrder`, `producesTheSameTraceWhateverTheInputOrder`.
4. **Cap of 0** — `LowPriorityCapTest.capOfZeroAcceptsOnlyRequestsThatAreNotLowPriority`, `capOfZeroReportsEveryLowRequestAsOverCap`.
5. **Escalated duplicate survivor exempt under cap 0** — `LowPriorityCapTest.acceptsADuplicateSurvivorEscalatedOutOfLowPriorityDespiteACapOfZero`, `reportsNoCapProblemForADuplicateSurvivorEscalatedOutOfLowPriority`.
6. **`Problem.overLowPriorityCap` shape, message, enum position, one counter per code** — all nine `OverLowPriorityCapProblemTest` tests (including `overLowPriorityCapIsAppendedLastSoExistingCodesKeepTheirOrder`) plus `BatchObserverOverLowPriorityCapTest.registersOneRejectedCounterPerProblemCode`, `reportsZeroCapProblemsBeforeAnyBatchIsObserved`, `countsAnOverLowPriorityCapProblem`.
7. **Problem ordering across stages, prioritize trace `out == in - 2` and notes** — `LowPriorityCapTest.listsCapProblemsAfterValidateAndDeduplicateProblemsOrderedByInputIndex`, `prioritizeTraceLosesOneRequestPerCappedRequest`, `cappingStageIsTracedAsPrioritize`, `prioritizeTraceNotesEachCappedRequestByIdWithoutItsInputIndex`, `prioritizeTraceNotesAreSortedNaturally`. *Partially covered:* the notes assertion filters notes containing `"cap"` rather than asserting the full `notes()` list, so an extra unrelated note would not fail the test.
8. **Nothing over cap / empty / null batch, three trace entries** — `LowPriorityCapTest.reportsNoCapProblemWhenEveryLowRequestFitsWithinTheCap`, `prioritizeTraceKeepsEveryRequestWhenNothingIsCapped`, `prioritizeTraceHasNoCapNoteWhenNothingIsCapped`, `emptyBatch*` (3), `nullBatch*` (3), `cappedBatchStillTracesValidateDeduplicateAndPrioritize`.
9. **Defaults 1000/5000 and sorter bean wiring** — `BatchPropertiesMaxLowPriorityTest.defaultsMaxLowPriorityToOneThousandWhenNotConfigured`, `keepsTheMaxSizeDefaultOfFiveThousandWhenNotConfigured`; `ProcessingConfigLowPriorityCapTest.sorterBeanCapsLowRequestsAtTheConfiguredMaxLowPriority`, `sorterBeanReportsLowRequestsOverTheConfiguredMaxLowPriority`, `sorterBeanAcceptsEveryLowRequestUnderTheDefaultCap`.
10. **`-1` rejected with exact message; 0 and positives accepted** — `BatchPropertiesMaxLowPriorityTest.rejectsANegativeMaxLowPriorityWhenConstructed`, `rejectsANegativeMaxLowPriorityWhenBound`, `acceptsAMaxLowPriorityOfZero`, `acceptsAPositiveMaxLowPriority`, `bindsAConfiguredMaxLowPriority`.
11. **Existing callers keep compiling/behaving** — `BatchPropertiesMaxLowPriorityTest.singleArgumentConstructorStillDefaultsMaxLowPriorityToOneThousand`, `singleArgumentConstructorKeepsTheMaxSizeItIsGiven`, `stillRejectsAMaxSizeBelowOne`; `LowPriorityCapTest.sorterBuiltWithoutACapAcceptsEveryLowRequest`, `sorterBuiltWithoutACapReportsNoProblems`. The "13 → 10 → 6 sample batch" part has **no new test**; it rests on the pre-existing sample-batch tests that ran green in the passing gate.

## Test results

- **Attempt 1 — failed:** `BatchPropertiesMaxLowPriorityTest.rejectsANegativeMaxLowPriorityWhenBound` (negative value not rejected on the binding path).
- **Attempt 2 — passed:** full gate green with the final diff, which adds `@ConstructorBinding` to the canonical `BatchProperties` constructor so binding goes through the validating constructor.

## Worth a closer look

- **Trace notes change shape when capping happens.** With any capped request, the prioritize notes are *only* the cap lines — the long-standing `"Sorted by priority (highest first), then requestedAt (earliest first), then requestId"` note is dropped from that batch. The criterion asks for "notes exactly [cap lines]", so this matches the spec, but it is a behaviour change for anything reading the sort note; confirm nothing downstream depends on it.
- **`@ConstructorBinding` on a record.** This was the fix for the attempt-1 failure. Worth confirming it behaves the same under the project's Spring Boot version at runtime (not only under `Binder` in the unit test), and that the one-arg convenience constructor is still worth keeping versus updating callers.
- **The bound-negative test is loose.** `rejectsANegativeMaxLowPriorityWhenBound` unwraps to the root cause, so it passes whether Spring wraps the failure or not; it does not pin the exception type that binding actually surfaces to application startup.
- **An existing test was modified.** `OrderIndependenceTest.generatedBatchCoversEveryKindOfProblem` now uses a cap-1 processor so `ProblemCode.values()` are all reachable. That test no longer exercises the uncapped processor on the generated batch; check that the loss of that coverage is acceptable.
- **`NO_CAP = Integer.MAX_VALUE` sentinel.** Means "uncapped" is really "capped very high"; harmless for realistic batches, but it is not a distinct state, and a cap of `Integer.MAX_VALUE` configured explicitly is indistinguishable from no cap.
- **Untested edges.** `WorkRequestSorter`'s own `IllegalArgumentException` for a negative cap has no test, and `Problem.overLowPriorityCap` with a null `requestId` falls back to `subject(...)` producing `"Request exceeds the per-batch Low-priority cap of N"` — neither is required by the criteria, just unverified.
- **Cross-stage problem ordering** relies on how `WorkRequestProcessor` concatenates per-stage problem lists (unchanged in this diff); the sorter only sorts its own problems by input index. Criterion 7's ordering is asserted end-to-end in one test, so verify that assumption holds for batches where other stages emit problems with higher input indices.
