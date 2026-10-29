package com.tanya.workrequests.processing;

import com.tanya.workrequests.domain.Priority;
import com.tanya.workrequests.domain.Problem;
import com.tanya.workrequests.domain.ProblemCode;
import com.tanya.workrequests.domain.ProcessingResult;
import com.tanya.workrequests.domain.StageTrace;
import com.tanya.workrequests.domain.WorkRequest;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A stale request is dropped by validation, so deduplication never sees it even when it is
 * the earliest copy of a duplicate group.
 */
class StaleRequestProcessingTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-10-29T00:00:00Z"), ZoneOffset.UTC);

    private static final WorkRequest FRESH_ORIGINAL = TestRequests.request(
            "R-1", "EQ-9", Priority.MEDIUM, "2026-10-20T08:00:00Z", "Pump leak");
    /** Older than maxAge and the earliest copy, so it would be kept if dedup ever saw it. */
    private static final WorkRequest STALE_COPY = TestRequests.request(
            "R-2", "EQ-9", Priority.MEDIUM, "2026-09-01T00:00:00Z", "Pump leak");
    private static final WorkRequest FRESH_COPY = TestRequests.request(
            "R-3", "EQ-9", Priority.MEDIUM, "2026-10-21T08:00:00Z", "Pump leak");
    private static final WorkRequest INCOMPLETE = TestRequests.request(
            "R-4", null, Priority.MEDIUM, "2026-10-22T08:00:00Z", "Belt replacement");

    private static final List<WorkRequest> BATCH =
            List.of(FRESH_ORIGINAL, STALE_COPY, FRESH_COPY, INCOMPLETE);

    private final WorkRequestProcessor processor = new WorkRequestProcessor(
            new WorkRequestValidator(FIXED_CLOCK, Duration.ofDays(30)),
            new DuplicateResolver(new DescriptionNormalizer()),
            new WorkRequestSorter());

    @Test
    void reportsOnlyStaleForAStaleDuplicateBecauseDeduplicationNeverSeesIt() {
        List<ProblemCode> codes = processor.process(BATCH).problems().stream()
                .filter(problem -> "R-2".equals(problem.requestId()))
                .map(Problem::code)
                .toList();

        assertThat(codes).containsExactly(ProblemCode.STALE);
    }

    @Test
    void keepsTheFreshOriginalRatherThanTheEarlierStaleCopy() {
        assertThat(ids(processor.process(BATCH).accepted())).containsExactly("R-1");
    }

    @Test
    void listsValidateStageProblemsBeforeDeduplicateStageProblems() {
        assertThat(processor.process(BATCH).problems()).containsExactly(
                Problem.stale(1, "R-2", 30),
                Problem.missingField(3, "R-4", "equipmentId"),
                Problem.duplicate(2, "R-3", "R-1"));
    }

    @Test
    void tracesExactlyTheThreeExistingStages() {
        List<String> stages = processor.process(BATCH).trace().stream()
                .map(StageTrace::stage)
                .toList();

        assertThat(stages).containsExactly("validate", "deduplicate", "prioritize");
    }

    @Test
    void countsStaleRequestsAsRejectedByTheValidateStage() {
        StageTrace validate = processor.process(BATCH).trace().get(0);

        assertThat(validate.in()).isEqualTo(4);
        assertThat(validate.out()).isEqualTo(2);
    }

    @Test
    void notesTheStaleRejectionByRequestIdInTheValidateStage() {
        StageTrace validate = processor.process(BATCH).trace().get(0);

        assertThat(validate.notes()).contains("R-2 rejected: stale");
    }

    @Test
    void producesTheSameAcceptedQueueForEveryInputOrder() {
        List<String> expected = ids(processor.process(BATCH).accepted());

        for (List<WorkRequest> order : permutations(BATCH)) {
            assertThat(ids(processor.process(order).accepted())).isEqualTo(expected);
        }
    }

    @Test
    void producesTheSameTraceForEveryInputOrder() {
        List<StageTrace> expected = processor.process(BATCH).trace();

        for (List<WorkRequest> order : permutations(BATCH)) {
            assertThat(processor.process(order).trace()).isEqualTo(expected);
        }
    }

    @Test
    void producesTheSameProblemsForEveryInputOrder() {
        List<String> expected = signatures(processor.process(BATCH));

        for (List<WorkRequest> order : permutations(BATCH)) {
            assertThat(signatures(processor.process(order)))
                    .containsExactlyInAnyOrderElementsOf(expected);
        }
    }

    @Test
    void keepsTheDeduplicateStageProblemLastForEveryInputOrder() {
        for (List<WorkRequest> order : permutations(BATCH)) {
            List<Problem> problems = processor.process(order).problems();

            assertThat(problems.get(problems.size() - 1).code()).isEqualTo(ProblemCode.DUPLICATE);
        }
    }

    @Test
    void ordersValidateStageProblemsByInputIndexForEveryInputOrder() {
        for (List<WorkRequest> order : permutations(BATCH)) {
            List<Integer> indices = processor.process(order).problems().stream()
                    .filter(problem -> problem.code() != ProblemCode.DUPLICATE)
                    .map(Problem::inputIndex)
                    .toList();

            assertThat(indices).isSorted();
        }
    }

    /** Problem identity without the input index, which legitimately moves with the input order. */
    private static List<String> signatures(ProcessingResult result) {
        return result.problems().stream()
                .map(problem -> problem.code() + "|" + problem.requestId() + "|"
                        + problem.relatedRequestId() + "|" + problem.message())
                .toList();
    }

    private static List<String> ids(List<WorkRequest> requests) {
        return requests.stream().map(WorkRequest::requestId).toList();
    }

    private static List<List<WorkRequest>> permutations(List<WorkRequest> requests) {
        if (requests.isEmpty()) {
            return List.of(List.of());
        }
        List<List<WorkRequest>> permutations = new ArrayList<>();
        for (int index = 0; index < requests.size(); index++) {
            List<WorkRequest> rest = new ArrayList<>(requests);
            WorkRequest head = rest.remove(index);
            for (List<WorkRequest> tail : permutations(rest)) {
                List<WorkRequest> permutation = new ArrayList<>();
                permutation.add(head);
                permutation.addAll(tail);
                permutations.add(permutation);
            }
        }
        return permutations;
    }
}
