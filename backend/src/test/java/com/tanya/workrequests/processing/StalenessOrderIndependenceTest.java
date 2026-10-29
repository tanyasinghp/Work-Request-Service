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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * A staleness-aware pipeline stays order independent: the stale request is dropped in the
 * validate stage, so it can never escalate the duplicate it would otherwise have matched.
 */
class StalenessOrderIndependenceTest {

    private static final Instant NOW = Instant.parse("2026-10-20T08:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String NINETY_DAYS_OLD = "2026-07-22T08:00:00Z";
    private static final int RUNS = 100;
    private static final Set<ProblemCode> VALIDATE_CODES = Set.of(ProblemCode.NULL_REQUEST,
            ProblemCode.MISSING_FIELD, ProblemCode.INVALID_VALUE, ProblemCode.STALE);

    private final WorkRequestProcessor processor = new WorkRequestProcessor(
            new WorkRequestValidator(FIXED_CLOCK, Duration.ofDays(30)),
            new DuplicateResolver(new DescriptionNormalizer()),
            new WorkRequestSorter());

    @Test
    void producesTheSameQueueForEveryInputOrder() {
        List<List<String>> queues = shuffledRuns().stream()
                .map(StalenessOrderIndependenceTest::queueOf)
                .toList();

        assertThat(queues).containsOnly(queues.get(0));
    }

    @Test
    void producesTheSameProblemsForEveryInputOrder() {
        List<List<String>> problems = shuffledRuns().stream()
                .map(StalenessOrderIndependenceTest::problemsOf)
                .toList();

        assertThat(problems).containsOnly(problems.get(0));
    }

    @Test
    void producesTheSameTraceForEveryInputOrder() {
        List<List<String>> traces = shuffledRuns().stream()
                .map(StalenessOrderIndependenceTest::traceOf)
                .toList();

        assertThat(traces).containsOnly(traces.get(0));
    }

    @Test
    void reportsTheStaleTwinOnlyAsStaleAndNeverAsDuplicate() {
        ProcessingResult result = processor.process(batch());

        assertThat(result.problems())
                .filteredOn(problem -> "R-2".equals(problem.requestId()))
                .extracting(Problem::code)
                .containsExactly(ProblemCode.STALE);
    }

    @Test
    void keepsTheRecentTwinInTheQueueAtItsOwnPriority() {
        ProcessingResult result = processor.process(batch());

        assertThat(result.accepted())
                .extracting(WorkRequest::requestId, WorkRequest::priority)
                .contains(tuple("R-1", Priority.MEDIUM));
    }

    @Test
    void escalatesNothingWhenTheOnlyHigherPriorityTwinIsStale() {
        ProcessingResult result = processor.process(batch());

        assertThat(result.problems()).allSatisfy(problem -> assertThat(problem.escalatedTo()).isNull());
    }

    @Test
    void keepsTheThreeStageTraceWithValidateOutputFeedingDeduplicateInput() {
        ProcessingResult result = processor.process(batch());

        assertThat(result.trace()).extracting(StageTrace::stage)
                .containsExactly("validate", "deduplicate", "prioritize");
        assertThat(result.trace().get(0).out()).isEqualTo(result.trace().get(1).in());
    }

    @Test
    void listsValidateStageProblemsBeforeDeduplicateStageProblems() {
        List<ProblemCode> codes = processor.process(batch()).problems().stream().map(Problem::code).toList();

        long validateProblems = codes.stream().filter(VALIDATE_CODES::contains).count();
        assertThat(codes.subList(0, (int) validateProblems)).allMatch(VALIDATE_CODES::contains);
    }

    @Test
    void ordersValidateStageProblemsByInputIndexWithStaleProblemsInterleaved() {
        ProcessingResult result = processor.process(batch());

        assertThat(result.problems())
                .filteredOn(problem -> VALIDATE_CODES.contains(problem.code()))
                .extracting(Problem::requestId, Problem::code)
                .containsExactly(tuple("R-2", ProblemCode.STALE),
                        tuple("R-5", ProblemCode.MISSING_FIELD),
                        tuple("R-6", ProblemCode.STALE),
                        tuple(null, ProblemCode.NULL_REQUEST));
    }

    private List<ProcessingResult> shuffledRuns() {
        Random random = new Random(20261020L);
        List<ProcessingResult> results = new ArrayList<>();
        for (int run = 0; run < RUNS; run++) {
            List<WorkRequest> shuffled = batch();
            Collections.shuffle(shuffled, random);
            results.add(processor.process(shuffled));
        }
        return results;
    }

    private static List<String> queueOf(ProcessingResult result) {
        return result.accepted().stream()
                .map(request -> request.requestId() + ":" + request.priority())
                .toList();
    }

    /** Input indices move with the shuffle, so they are left out and the rest is sorted. */
    private static List<String> problemsOf(ProcessingResult result) {
        return result.problems().stream()
                .map(problem -> problem.code() + "|" + problem.requestId() + "|" + problem.field()
                        + "|" + problem.relatedRequestId() + "|" + problem.message())
                .sorted()
                .toList();
    }

    private static List<String> traceOf(ProcessingResult result) {
        return result.trace().stream()
                .map(stage -> stage.stage() + ":" + stage.in() + "->" + stage.out() + stage.notes())
                .toList();
    }

    /** Recent MEDIUM R-1 and 90-day-old CRITICAL R-2 are twins; R-3/R-4 are a plain duplicate pair. */
    private static List<WorkRequest> batch() {
        return new ArrayList<>(Arrays.asList(
                TestRequests.request("R-1", "EQ-9", Priority.MEDIUM, "2026-10-19T08:00:00Z", "Pump leaking oil"),
                TestRequests.request("R-2", "EQ-9", Priority.CRITICAL, NINETY_DAYS_OLD, "Pump leaking oil"),
                TestRequests.request("R-3", "EQ-5", Priority.HIGH, "2026-10-10T08:00:00Z", "Replace filter"),
                TestRequests.request("R-4", "EQ-5", Priority.HIGH, "2026-10-12T08:00:00Z", "Replace filter"),
                TestRequests.request("R-5", null, Priority.LOW, "2026-10-18T08:00:00Z", "No equipment id"),
                TestRequests.request("R-6", "EQ-6", Priority.HIGH, NINETY_DAYS_OLD, "Overdue inspection"),
                null));
    }
}
