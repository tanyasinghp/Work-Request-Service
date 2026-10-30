package com.tanya.workrequests.processing;

import com.tanya.workrequests.domain.Priority;
import com.tanya.workrequests.domain.Problem;
import com.tanya.workrequests.domain.ProblemCode;
import com.tanya.workrequests.domain.ProcessingResult;
import com.tanya.workrequests.domain.StageTrace;
import com.tanya.workrequests.domain.WorkRequest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static com.tanya.workrequests.processing.TestRequests.request;
import static com.tanya.workrequests.processing.TestRequests.valid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * The per-batch Low-priority cap, driven through the whole pipeline so the assertions
 * depend on the published result rather than on where the cap is applied.
 */
class LowPriorityCapTest {

    @Test
    void keepsTheEarliestLowRequestsUpToTheCapAndEveryNonLowRequest() {
        ProcessingResult result = processorWithCap(2).process(fiveLowAndThreeOtherRequests());

        assertThat(result.accepted()).extracting(WorkRequest::requestId)
                .containsExactly("C-1", "H-1", "M-1", "L-1", "L-2");
    }

    @Test
    void reportsEveryLowRequestBeyondTheCapAsAProblem() {
        ProcessingResult result = processorWithCap(2).process(fiveLowAndThreeOtherRequests());

        assertThat(result.problems()).extracting(Problem::requestId, Problem::code)
                .containsExactly(
                        tuple("L-3", ProblemCode.OVER_LOW_PRIORITY_CAP),
                        tuple("L-4", ProblemCode.OVER_LOW_PRIORITY_CAP),
                        tuple("L-5", ProblemCode.OVER_LOW_PRIORITY_CAP));
    }

    @Test
    void countsOnlyLowPriorityRequestsAgainstTheCap() {
        List<WorkRequest> batch = List.of(
                at("M-1", Priority.MEDIUM, "2026-10-20T08:00:00Z"),
                at("M-2", Priority.MEDIUM, "2026-10-20T08:01:00Z"),
                at("H-1", Priority.HIGH, "2026-10-20T08:02:00Z"),
                low("L-1", "2026-10-20T08:03:00Z"));

        ProcessingResult result = processorWithCap(1).process(batch);

        assertThat(result.accepted()).extracting(WorkRequest::requestId)
                .containsExactly("H-1", "M-1", "M-2", "L-1");
    }

    @Test
    void keepsTheLowestRequestIdWhenRequestedAtTies() {
        ProcessingResult result = processorWithCap(1).process(tiedLowRequests());

        assertThat(result.accepted()).extracting(WorkRequest::requestId).containsExactly("A-1");
    }

    @Test
    void reportsTheHigherRequestIdOfATieAsOverCap() {
        ProcessingResult result = processorWithCap(1).process(tiedLowRequests());

        assertThat(overCapIds(result)).containsExactly("B-2");
    }

    @Test
    void acceptsTheSameQueueWhateverTheInputOrder() {
        WorkRequestProcessor processor = processorWithCap(2);
        List<WorkRequest> batch = fiveLowAndThreeOtherRequests();
        List<WorkRequest> expected = processor.process(batch).accepted();
        Random random = new Random(20261020L);

        for (int run = 0; run < 100; run++) {
            List<WorkRequest> shuffled = new ArrayList<>(batch);
            Collections.shuffle(shuffled, random);

            assertThat(processor.process(shuffled).accepted())
                    .as("run %d", run)
                    .containsExactlyElementsOf(expected);
        }
    }

    @Test
    void reportsTheSameOverCapRequestIdsWhateverTheInputOrder() {
        WorkRequestProcessor processor = processorWithCap(2);
        List<WorkRequest> batch = fiveLowAndThreeOtherRequests();
        List<String> expected = overCapIds(processor.process(batch));
        Random random = new Random(7L);

        for (int run = 0; run < 100; run++) {
            List<WorkRequest> shuffled = new ArrayList<>(batch);
            Collections.shuffle(shuffled, random);

            assertThat(overCapIds(processor.process(shuffled)))
                    .as("run %d", run)
                    .containsExactlyInAnyOrderElementsOf(expected);
        }
    }

    @Test
    void producesTheSameTraceWhateverTheInputOrder() {
        WorkRequestProcessor processor = processorWithCap(2);
        List<WorkRequest> batch = fiveLowAndThreeOtherRequests();
        List<StageTrace> expected = processor.process(batch).trace();
        Random random = new Random(99L);

        for (int run = 0; run < 100; run++) {
            List<WorkRequest> shuffled = new ArrayList<>(batch);
            Collections.shuffle(shuffled, random);

            assertThat(processor.process(shuffled).trace()).as("run %d", run).isEqualTo(expected);
        }
    }

    @Test
    void capOfZeroAcceptsOnlyRequestsThatAreNotLowPriority() {
        ProcessingResult result = processorWithCap(0).process(twoLowRequestsAndAHighRequest());

        assertThat(result.accepted()).extracting(WorkRequest::requestId).containsExactly("H-1");
    }

    @Test
    void capOfZeroReportsEveryLowRequestAsOverCap() {
        ProcessingResult result = processorWithCap(0).process(twoLowRequestsAndAHighRequest());

        assertThat(result.problems()).extracting(Problem::requestId, Problem::code)
                .containsExactly(
                        tuple("L-1", ProblemCode.OVER_LOW_PRIORITY_CAP),
                        tuple("L-2", ProblemCode.OVER_LOW_PRIORITY_CAP));
    }

    @Test
    void acceptsADuplicateSurvivorEscalatedOutOfLowPriorityDespiteACapOfZero() {
        ProcessingResult result = processorWithCap(0).process(escalatingDuplicates());

        assertThat(result.accepted()).extracting(WorkRequest::requestId, WorkRequest::priority)
                .containsExactly(tuple("D-1", Priority.MEDIUM));
    }

    @Test
    void reportsNoCapProblemForADuplicateSurvivorEscalatedOutOfLowPriority() {
        ProcessingResult result = processorWithCap(0).process(escalatingDuplicates());

        assertThat(overCapIds(result)).isEmpty();
    }

    @Test
    void listsCapProblemsAfterValidateAndDeduplicateProblemsOrderedByInputIndex() {
        ProcessingResult result = processorWithCap(1).process(batchWithProblemsAtEveryStage());

        assertThat(result.problems()).extracting(Problem::code, Problem::inputIndex)
                .containsExactly(
                        tuple(ProblemCode.NULL_REQUEST, 1),
                        tuple(ProblemCode.DUPLICATE, 4),
                        tuple(ProblemCode.OVER_LOW_PRIORITY_CAP, 3),
                        tuple(ProblemCode.OVER_LOW_PRIORITY_CAP, 7));
    }

    @Test
    void prioritizeTraceLosesOneRequestPerCappedRequest() {
        StageTrace trace = prioritizeTrace(processorWithCap(1).process(batchWithProblemsAtEveryStage()));

        assertThat(trace.out()).isEqualTo(trace.in() - 2);
    }

    @Test
    void cappingStageIsTracedAsPrioritize() {
        ProcessingResult result = processorWithCap(1).process(batchWithProblemsAtEveryStage());

        assertThat(prioritizeTrace(result).stage()).isEqualTo("prioritize");
    }

    @Test
    void prioritizeTraceNotesEachCappedRequestByIdWithoutItsInputIndex() {
        StageTrace trace = prioritizeTrace(processorWithCap(1).process(batchWithProblemsAtEveryStage()));

        assertThat(capNotes(trace)).containsExactly(
                "L-2 dropped: over Low-priority cap of 1",
                "L-3 dropped: over Low-priority cap of 1");
    }

    @Test
    void prioritizeTraceNotesAreSortedNaturally() {
        StageTrace trace = prioritizeTrace(processorWithCap(1).process(batchWithProblemsAtEveryStage()));

        assertThat(trace.notes()).isSorted();
    }

    @Test
    void cappedBatchStillTracesValidateDeduplicateAndPrioritize() {
        ProcessingResult result = processorWithCap(1).process(batchWithProblemsAtEveryStage());

        assertThat(result.trace()).extracting(StageTrace::stage)
                .containsExactly("validate", "deduplicate", "prioritize");
    }

    @Test
    void reportsNoCapProblemWhenEveryLowRequestFitsWithinTheCap() {
        ProcessingResult result = processorWithCap(2).process(twoLowRequestsAndAHighRequest());

        assertThat(result.problems()).isEmpty();
    }

    @Test
    void prioritizeTraceKeepsEveryRequestWhenNothingIsCapped() {
        StageTrace trace = prioritizeTrace(processorWithCap(2).process(twoLowRequestsAndAHighRequest()));

        assertThat(trace.out()).isEqualTo(trace.in());
    }

    @Test
    void prioritizeTraceHasNoCapNoteWhenNothingIsCapped() {
        StageTrace trace = prioritizeTrace(processorWithCap(2).process(twoLowRequestsAndAHighRequest()));

        assertThat(capNotes(trace)).isEmpty();
    }

    @Test
    void emptyBatchProducesNoCapProblem() {
        ProcessingResult result = processorWithCap(0).process(List.of());

        assertThat(overCapIds(result)).isEmpty();
    }

    @Test
    void emptyBatchTracesAPrioritizeStageWithEqualInAndOut() {
        StageTrace trace = prioritizeTrace(processorWithCap(0).process(List.of()));

        assertThat(trace.out()).isEqualTo(trace.in());
    }

    @Test
    void emptyBatchStillTracesValidateDeduplicateAndPrioritize() {
        ProcessingResult result = processorWithCap(0).process(List.of());

        assertThat(result.trace()).extracting(StageTrace::stage)
                .containsExactly("validate", "deduplicate", "prioritize");
    }

    @Test
    void nullBatchProducesNoCapProblem() {
        List<WorkRequest> batch = null;

        assertThat(overCapIds(processorWithCap(0).process(batch))).isEmpty();
    }

    @Test
    void nullBatchTracesAPrioritizeStageWithEqualInAndOut() {
        List<WorkRequest> batch = null;

        StageTrace trace = prioritizeTrace(processorWithCap(0).process(batch));

        assertThat(trace.out()).isEqualTo(trace.in());
    }

    @Test
    void nullBatchStillTracesValidateDeduplicateAndPrioritize() {
        List<WorkRequest> batch = null;

        ProcessingResult result = processorWithCap(0).process(batch);

        assertThat(result.trace()).extracting(StageTrace::stage)
                .containsExactly("validate", "deduplicate", "prioritize");
    }

    @Test
    void sorterBuiltWithoutACapAcceptsEveryLowRequest() {
        ProcessingResult result = processorWithoutCap().process(fiveLowAndThreeOtherRequests());

        assertThat(result.accepted()).hasSize(8);
    }

    @Test
    void sorterBuiltWithoutACapReportsNoProblems() {
        ProcessingResult result = processorWithoutCap().process(fiveLowAndThreeOtherRequests());

        assertThat(result.problems()).isEmpty();
    }

    private static WorkRequestProcessor processorWithCap(int maxLowPriority) {
        return new WorkRequestProcessor(new WorkRequestValidator(),
                new DuplicateResolver(new DescriptionNormalizer()),
                new WorkRequestSorter(maxLowPriority));
    }

    /** The pre-existing constructors, which must keep working and apply no cap. */
    private static WorkRequestProcessor processorWithoutCap() {
        return new WorkRequestProcessor(new WorkRequestValidator(),
                new DuplicateResolver(new DescriptionNormalizer()),
                new WorkRequestSorter());
    }

    private static List<WorkRequest> fiveLowAndThreeOtherRequests() {
        return List.of(
                low("L-1", "2026-10-20T08:00:00Z"),
                low("L-2", "2026-10-20T08:01:00Z"),
                low("L-3", "2026-10-20T08:02:00Z"),
                low("L-4", "2026-10-20T08:03:00Z"),
                low("L-5", "2026-10-20T08:04:00Z"),
                at("M-1", Priority.MEDIUM, "2026-10-20T09:00:00Z"),
                at("H-1", Priority.HIGH, "2026-10-20T09:00:00Z"),
                at("C-1", Priority.CRITICAL, "2026-10-20T09:00:00Z"));
    }

    private static List<WorkRequest> twoLowRequestsAndAHighRequest() {
        return List.of(
                low("L-1", "2026-10-20T08:00:00Z"),
                low("L-2", "2026-10-20T08:01:00Z"),
                at("H-1", Priority.HIGH, "2026-10-20T08:02:00Z"));
    }

    private static List<WorkRequest> tiedLowRequests() {
        return List.of(
                low("B-2", "2026-10-20T08:00:00Z"),
                low("A-1", "2026-10-20T08:00:00Z"));
    }

    /** Duplicates whose group's highest priority is MEDIUM, so the survivor leaves LOW. */
    private static List<WorkRequest> escalatingDuplicates() {
        return List.of(
                request("D-1", "EQ-7", Priority.LOW, "2026-10-20T08:00:00Z", "Pump leak"),
                request("D-2", "EQ-7", Priority.MEDIUM, "2026-10-20T09:00:00Z", "Pump leak"));
    }

    /** One validation problem, one duplicate, and two Low requests over a cap of 1. */
    private static List<WorkRequest> batchWithProblemsAtEveryStage() {
        return Arrays.asList(
                valid("V-0"),
                null,
                request("D-1", "EQ-D", Priority.HIGH, "2026-10-20T08:00:00Z", "Shared work"),
                low("L-3", "2026-10-20T09:00:00Z"),
                request("D-2", "EQ-D", Priority.HIGH, "2026-10-20T08:30:00Z", "Shared work"),
                low("L-1", "2026-10-20T07:00:00Z"),
                valid("V-6"),
                low("L-2", "2026-10-20T10:00:00Z"));
    }

    private static WorkRequest low(String requestId, String requestedAt) {
        return request(requestId, "EQ-" + requestId, Priority.LOW, requestedAt, "Low work for " + requestId);
    }

    private static WorkRequest at(String requestId, Priority priority, String requestedAt) {
        return request(requestId, "EQ-" + requestId, priority, requestedAt, "Work for " + requestId);
    }

    private static StageTrace prioritizeTrace(ProcessingResult result) {
        return result.trace().stream()
                .filter(trace -> "prioritize".equals(trace.stage()))
                .findFirst()
                .orElseThrow();
    }

    private static List<String> overCapIds(ProcessingResult result) {
        return result.problems().stream()
                .filter(problem -> problem.code() == ProblemCode.OVER_LOW_PRIORITY_CAP)
                .map(Problem::requestId)
                .toList();
    }

    private static List<String> capNotes(StageTrace trace) {
        return trace.notes().stream().filter(note -> note.contains("cap")).toList();
    }
}
