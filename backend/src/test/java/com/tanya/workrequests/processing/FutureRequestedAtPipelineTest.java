package com.tanya.workrequests.processing;

import com.tanya.workrequests.domain.InvalidField;
import com.tanya.workrequests.domain.Priority;
import com.tanya.workrequests.domain.Problem;
import com.tanya.workrequests.domain.ProblemCode;
import com.tanya.workrequests.domain.ProcessingResult;
import com.tanya.workrequests.domain.StageTrace;
import com.tanya.workrequests.domain.WorkRequest;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The whole pipeline with a fixed clock: a future-dated request is rejected in validate and never
 * reaches deduplicate, and the output stays the same whatever the input order.
 */
class FutureRequestedAtPipelineTest {

    private static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");
    private static final Clock FIXED = Clock.fixed(NOW, ZoneOffset.UTC);

    // Same equipment and description as PAST, so it would be a duplicate if it ever got that far.
    private static final WorkRequest PAST = TestRequests.request("WR-PAST", "EQ-7", Priority.MEDIUM,
            "2026-10-20T08:00:00Z", "Replace bearing");
    private static final WorkRequest FUTURE_TWIN = TestRequests.request("WR-FUTURE", "EQ-7", Priority.CRITICAL,
            "2026-10-21T08:00:00Z", "Replace bearing");
    private static final WorkRequest FUTURE_OTHER = TestRequests.request("WR-LATER", "EQ-8", Priority.HIGH,
            "2026-10-22T09:00:00Z", "Inspect valve");
    private static final WorkRequest OTHER_PAST = TestRequests.valid("WR-OK");

    private final WorkRequestProcessor processor = new WorkRequestProcessor(
            new WorkRequestValidator(FIXED),
            new DuplicateResolver(new DescriptionNormalizer()),
            new WorkRequestSorter());

    @Test
    void reportsOnlySuspiciousTimestampProblemsForTheFutureDatedRequests() {
        ProcessingResult result = processor.process(batch());

        assertThat(result.problems()).extracting(Problem::code)
                .containsOnly(ProblemCode.SUSPICIOUS_TIMESTAMP);
    }

    @Test
    void reportsNoDuplicateProblemForTheFutureTwin() {
        ProcessingResult result = processor.process(batch());

        assertThat(result.problems()).noneMatch(problem -> problem.code() == ProblemCode.DUPLICATE);
    }

    @Test
    void neverEscalatesAPriorityFromTheFutureTwin() {
        ProcessingResult result = processor.process(batch());

        assertThat(result.problems()).extracting(Problem::escalatedTo).containsOnlyNulls();
        assertThat(result.accepted()).contains(PAST);
    }

    @Test
    void keepsTheThreeStageTraceWhenAFutureRequestIsRejected() {
        ProcessingResult result = processor.process(batch());

        assertThat(result.trace()).extracting(StageTrace::stage)
                .containsExactly("validate", "deduplicate", "prioritize");
    }

    @Test
    void passesOnlyTheAcceptedRequestsIntoDeduplicate() {
        ProcessingResult result = processor.process(batch());

        assertThat(result.trace().get(1).in()).isEqualTo(2);
    }

    @Test
    void producesTheSameQueueForEveryInputOrder() {
        ProcessingResult baseline = processor.process(batch());

        for (int seed = 0; seed < 100; seed++) {
            assertThat(processor.process(shuffled(seed)).accepted()).isEqualTo(baseline.accepted());
        }
    }

    @Test
    void producesTheSameProblemsForEveryInputOrder() {
        List<String> baseline = describe(processor.process(batch()).problems());

        for (int seed = 0; seed < 100; seed++) {
            assertThat(describe(processor.process(shuffled(seed)).problems())).isEqualTo(baseline);
        }
    }

    @Test
    void producesTheSameTraceNotesForEveryInputOrder() {
        List<List<String>> baseline = notes(processor.process(batch()));

        for (int seed = 0; seed < 100; seed++) {
            assertThat(notes(processor.process(shuffled(seed)))).isEqualTo(baseline);
        }
    }

    @Test
    void ordersSuspiciousTimestampProblemsByInputIndex() {
        for (int seed = 0; seed < 100; seed++) {
            List<Integer> indexes = processor.process(shuffled(seed)).problems().stream()
                    .filter(problem -> problem.code() == ProblemCode.SUSPICIOUS_TIMESTAMP)
                    .map(Problem::inputIndex)
                    .toList();

            assertThat(indexes).hasSize(2).isSorted();
        }
    }

    @Test
    void keepsEveryProblemCodeIncludingSuspiciousTimestampReachable() {
        List<WorkRequest> covering = new ArrayList<>();
        covering.add(null);
        covering.add(TestRequests.request("WR-MISS", null, Priority.LOW, "2026-10-20T08:00:00Z", "No equipment"));
        covering.add(TestRequests.request("WR-DUP-1", "EQ-9", Priority.MEDIUM, "2026-10-20T08:00:00Z", "Replace seal"));
        covering.add(TestRequests.request("WR-DUP-2", "EQ-9", Priority.HIGH, "2026-10-20T09:00:00Z", "Replace seal"));
        covering.add(TestRequests.request("WR-CONF", "EQ-1", Priority.LOW, "2026-10-20T08:00:00Z", "Alpha"));
        covering.add(TestRequests.request("WR-CONF", "EQ-2", Priority.LOW, "2026-10-20T08:00:00Z", "Beta"));
        covering.add(FUTURE_OTHER);

        Set<ProblemCode> codes = EnumSet.noneOf(ProblemCode.class);
        processor.process(covering).problems().forEach(problem -> codes.add(problem.code()));
        // INVALID_VALUE needs a raw value that never parsed into a WorkRequest field.
        new WorkRequestValidator(FIXED)
                .validate(List.of(TestRequests.request("WR-RAW", "EQ-3", Priority.LOW, null, "Unreadable date")),
                        Map.of(0, List.of(new InvalidField("requestedAt", "yesterday"))))
                .problems().forEach(problem -> codes.add(problem.code()));

        assertThat(codes).containsExactlyInAnyOrderElementsOf(EnumSet.allOf(ProblemCode.class));
    }

    private static List<WorkRequest> batch() {
        return new ArrayList<>(List.of(PAST, FUTURE_TWIN, FUTURE_OTHER, OTHER_PAST));
    }

    private static List<WorkRequest> shuffled(int seed) {
        List<WorkRequest> shuffled = batch();
        Collections.shuffle(shuffled, new Random(seed));
        return shuffled;
    }

    /** Problem content without the input index, which legitimately moves with the input order. */
    private static List<String> describe(List<Problem> problems) {
        return problems.stream()
                .map(problem -> problem.code() + "|" + problem.requestId() + "|" + problem.field()
                        + "|" + problem.message())
                .sorted()
                .toList();
    }

    private static List<List<String>> notes(ProcessingResult result) {
        return result.trace().stream().map(StageTrace::notes).map(List::copyOf).toList();
    }
}
