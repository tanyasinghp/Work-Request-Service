# Cap Low-priority requests per batch

- **Story:** `low-priority-cap`
- **Outcome:** passed
- **Branch:** `agent/low-priority-cap` (never merged or pushed by the pipeline)
- **First-try gate pass:** no
- **Attempts:** 2
- **LLM:** claude-cli (claude-opus-5)
- **Time taken:** 945 s; model cost $3.61
- **Final diff size:** 771 changed lines

## Story

# Cap Low-priority requests per batch

As an operations lead, I want a per-batch cap on Low-priority requests, so that one sender
flooding a batch with Low work can't crowd out everything else.

The cap is configurable. Requests over the cap are reported as problems, never silently
dropped, and which Low requests are kept must not depend on the order of the input.

## Generated acceptance criteria

1. **Given** a cap of 2 and a batch of 5 LOW requests with distinct requestedAt plus 3 non-LOW requests, all passing validation and deduplication, **when** WorkRequestProcessor.process(batch) runs, **then** the accepted queue holds the 2 LOW requests with the earliest requestedAt plus all 3 non-LOW requests, and the other 3 LOW requests are reported as problems with code ProblemCode.OVER_LOW_PRIORITY_CAP (never silently dropped); only Priority.LOW counts against the cap.
2. **Given** a cap of 1 and LOW requests 'B-2' and 'A-1' both at 2026-10-20T08:00:00Z, **when** the cap is applied, **then** 'A-1' is kept and 'B-2' is reported over cap: ties on requestedAt break by requestId ascending, the same ordering the sorter already uses.
3. **Given** any batch containing more LOW requests than the cap, **when** the same batch is shuffled and processed 100 times, **then** the accepted queue, the set of over-cap requestIds and the trace are identical every time.
4. **Given** a cap of 0, **when** a batch of 2 LOW and 1 HIGH request is processed, **then** only the HIGH request is accepted and both LOW requests get OVER_LOW_PRIORITY_CAP problems; a cap of 0 is legal and means no Low work is accepted.
5. **Given** a cap of 0 and two duplicates whose group's highest priority is MEDIUM, so the kept request is escalated from LOW to MEDIUM, **when** the batch is processed, **then** the kept request is accepted with no cap problem: the cap uses the priority as it stands after the deduplicate stage.
6. **Given** a LOW request 'WR-9' at input index 4 over a cap of 2, **when** Problem.overLowPriorityCap(4, "WR-9", 2) is called, **then** the Problem has inputIndex 4, requestId 'WR-9', code OVER_LOW_PRIORITY_CAP, null field, relatedRequestId, escalatedFrom and escalatedTo, and message exactly "WR-9 exceeds the per-batch Low-priority cap of 2"; OVER_LOW_PRIORITY_CAP is appended last in ProblemCode and BatchObserver still increments one counter per ProblemCode value.
7. **Given** a cap of 1, a batch that also yields validation and duplicate problems, and over-cap LOW requests 'L-2' at input index 7 and 'L-3' at input index 3, **when** the ProcessingResult is read, **then** cap problems appear in the prioritize stage, after validate and deduplicate problems and ordered by input index (index 3 before index 7); the prioritize StageTrace has stage "prioritize", out == in - 2, and notes exactly ["L-2 dropped: over Low-priority cap of 1", "L-3 dropped: over Low-priority cap of 1"] sorted naturally with no input indices.
8. **Given** a batch with no LOW request over the cap, an empty batch, or a null batch, **when** the batch is processed, **then** no OVER_LOW_PRIORITY_CAP problem is produced, the prioritize trace has in == out and no cap note, and the trace still has exactly three entries: validate, deduplicate, prioritize.
9. **Given** an application context with no app.batch.max-low-priority configured, **when** BatchProperties is bound, **then** maxLowPriority() is 1000 (@DefaultValue("1000") on the new second component) and maxSize() is still 5000, and ProcessingConfig builds the WorkRequestSorter bean with BatchProperties.maxLowPriority().
10. **Given** app.batch.max-low-priority = -1, **when** BatchProperties is bound or constructed, **then** IllegalArgumentException is thrown with message "app.batch.max-low-priority must be at least 0, was -1"; 0 and positive values are accepted.
11. **Given** existing callers of new BatchProperties(5000), new WorkRequestSorter(), new WorkRequestProcessor(validator, resolver, sorter) and the 13-request sample batch, **when** they are compiled and run after the change, **then** they still compile and behave as before: BatchProperties(int maxSize) delegates to maxLowPriority 1000, the no-arg WorkRequestSorter() applies no cap, capping is reached only via new WorkRequestSorter(int maxLowPriority), and the sample batch still gives the same queue, problems and counts 13 → 10 → 6.

Affected classes: ProblemCode, Problem, WorkRequestSorter, BatchProperties, ProcessingConfig, BatchObserver
Out of scope: Caps for MEDIUM, HIGH or CRITICAL priorities; Per-sender or per-equipment quotas rather than a per-batch cap; Changing the existing sort order or deduplication rules; Exposing the cap or remaining Low capacity in the API response DTOs; Rejecting a whole batch when the cap is exceeded; Introducing a Clock bean or any time-based staleness logic

## Gate result per attempt

| Attempt | Result | Detail | Seconds |
| --- | --- | --- | --- |
| 1 | failed | BatchPropertiesMaxLowPriorityTest.rejectsANegativeMaxLowPriorityWhenBound | 5 |
| 2 | passed |  | 6 |

## Tests written from the criteria

- `LowPriorityCapTest.keepsTheEarliestLowRequestsUpToTheCapAndEveryNonLowRequest`
- `LowPriorityCapTest.reportsEveryLowRequestBeyondTheCapAsAProblem`
- `LowPriorityCapTest.countsOnlyLowPriorityRequestsAgainstTheCap`
- `LowPriorityCapTest.keepsTheLowestRequestIdWhenRequestedAtTies`
- `LowPriorityCapTest.reportsTheHigherRequestIdOfATieAsOverCap`
- `LowPriorityCapTest.acceptsTheSameQueueWhateverTheInputOrder`
- `LowPriorityCapTest.reportsTheSameOverCapRequestIdsWhateverTheInputOrder`
- `LowPriorityCapTest.producesTheSameTraceWhateverTheInputOrder`
- `LowPriorityCapTest.capOfZeroAcceptsOnlyRequestsThatAreNotLowPriority`
- `LowPriorityCapTest.capOfZeroReportsEveryLowRequestAsOverCap`
- `LowPriorityCapTest.acceptsADuplicateSurvivorEscalatedOutOfLowPriorityDespiteACapOfZero`
- `LowPriorityCapTest.reportsNoCapProblemForADuplicateSurvivorEscalatedOutOfLowPriority`
- `LowPriorityCapTest.listsCapProblemsAfterValidateAndDeduplicateProblemsOrderedByInputIndex`
- `LowPriorityCapTest.prioritizeTraceLosesOneRequestPerCappedRequest`
- `LowPriorityCapTest.cappingStageIsTracedAsPrioritize`
- `LowPriorityCapTest.prioritizeTraceNotesEachCappedRequestByIdWithoutItsInputIndex`
- `LowPriorityCapTest.prioritizeTraceNotesAreSortedNaturally`
- `LowPriorityCapTest.cappedBatchStillTracesValidateDeduplicateAndPrioritize`
- `LowPriorityCapTest.reportsNoCapProblemWhenEveryLowRequestFitsWithinTheCap`
- `LowPriorityCapTest.prioritizeTraceKeepsEveryRequestWhenNothingIsCapped`
- `LowPriorityCapTest.prioritizeTraceHasNoCapNoteWhenNothingIsCapped`
- `LowPriorityCapTest.emptyBatchProducesNoCapProblem`
- `LowPriorityCapTest.emptyBatchTracesAPrioritizeStageWithEqualInAndOut`
- `LowPriorityCapTest.emptyBatchStillTracesValidateDeduplicateAndPrioritize`
- `LowPriorityCapTest.nullBatchProducesNoCapProblem`
- `LowPriorityCapTest.nullBatchTracesAPrioritizeStageWithEqualInAndOut`
- `LowPriorityCapTest.nullBatchStillTracesValidateDeduplicateAndPrioritize`
- `LowPriorityCapTest.sorterBuiltWithoutACapAcceptsEveryLowRequest`
- `LowPriorityCapTest.sorterBuiltWithoutACapReportsNoProblems`
- `OverLowPriorityCapProblemTest.carriesTheInputIndexOfTheCappedRequest`
- `OverLowPriorityCapProblemTest.carriesTheRequestIdOfTheCappedRequest`
- `OverLowPriorityCapProblemTest.usesTheOverLowPriorityCapCode`
- `OverLowPriorityCapProblemTest.leavesFieldNullBecauseNoSingleFieldIsAtFault`
- `OverLowPriorityCapProblemTest.leavesRelatedRequestIdNullBecauseNoOtherRequestIsInvolved`
- `OverLowPriorityCapProblemTest.leavesTheEscalationFieldsNull`
- `OverLowPriorityCapProblemTest.messageNamesTheRequestAndTheCapAndNeverTheInputIndex`
- `OverLowPriorityCapProblemTest.messageReportsTheCapItWasGiven`
- `OverLowPriorityCapProblemTest.overLowPriorityCapIsAppendedLastSoExistingCodesKeepTheirOrder`
- `BatchPropertiesMaxLowPriorityTest.defaultsMaxLowPriorityToOneThousandWhenNotConfigured`
- `BatchPropertiesMaxLowPriorityTest.keepsTheMaxSizeDefaultOfFiveThousandWhenNotConfigured`
- `BatchPropertiesMaxLowPriorityTest.bindsAConfiguredMaxLowPriority`
- `BatchPropertiesMaxLowPriorityTest.rejectsANegativeMaxLowPriorityWhenConstructed`
- `BatchPropertiesMaxLowPriorityTest.rejectsANegativeMaxLowPriorityWhenBound`
- `BatchPropertiesMaxLowPriorityTest.acceptsAMaxLowPriorityOfZero`
- `BatchPropertiesMaxLowPriorityTest.acceptsAPositiveMaxLowPriority`
- `BatchPropertiesMaxLowPriorityTest.singleArgumentConstructorStillDefaultsMaxLowPriorityToOneThousand`
- `BatchPropertiesMaxLowPriorityTest.singleArgumentConstructorKeepsTheMaxSizeItIsGiven`
- `BatchPropertiesMaxLowPriorityTest.stillRejectsAMaxSizeBelowOne`
- `ProcessingConfigLowPriorityCapTest.sorterBeanCapsLowRequestsAtTheConfiguredMaxLowPriority`
- `ProcessingConfigLowPriorityCapTest.sorterBeanReportsLowRequestsOverTheConfiguredMaxLowPriority`
- `ProcessingConfigLowPriorityCapTest.sorterBeanAcceptsEveryLowRequestUnderTheDefaultCap`
- `BatchObserverOverLowPriorityCapTest.registersOneRejectedCounterPerProblemCode`
- `BatchObserverOverLowPriorityCapTest.reportsZeroCapProblemsBeforeAnyBatchIsObserved`
- `BatchObserverOverLowPriorityCapTest.countsAnOverLowPriorityCapProblem`

## What failed

- Attempt 1: gate failed at BatchPropertiesMaxLowPriorityTest.rejectsANegativeMaxLowPriorityWhenBound

## Manual fixes

- Not reviewed yet.
