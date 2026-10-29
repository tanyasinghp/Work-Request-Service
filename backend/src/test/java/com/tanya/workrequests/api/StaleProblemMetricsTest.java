package com.tanya.workrequests.api;

import com.tanya.workrequests.domain.Problem;
import com.tanya.workrequests.domain.ProcessingResult;
import com.tanya.workrequests.domain.StageTrace;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The STALE problem code gets its own counter, following the existing per-code naming. */
class StaleProblemMetricsTest {

    private static final Instant REQUESTED_AT = Instant.parse("2026-07-22T08:00:00Z");

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final BatchObserver observer = new BatchObserver(registry);

    @Test
    void registersTheStaleRejectionCounterBeforeAnyStaleProblemArrives() {
        assertThat(registry.find("workrequests.rejected").tag("code", "STALE").counter()).isNotNull();
    }

    @Test
    void countsOneStaleRejectionPerStaleProblem() {
        ProcessingResult result = new ProcessingResult(List.of(),
                List.of(staleProblem(0, "R-1"), staleProblem(1, "R-2")), emptyTrace());

        observer.observe(2, () -> result);

        assertThat(registry.get("workrequests.rejected").tag("code", "STALE").counter().count())
                .isEqualTo(2.0);
    }

    private static Problem staleProblem(int inputIndex, String requestId) {
        return Problem.stale(inputIndex, requestId, REQUESTED_AT, Duration.ofDays(30));
    }

    private static List<StageTrace> emptyTrace() {
        return List.of(new StageTrace("validate", 2, 0, List.of()),
                new StageTrace("deduplicate", 0, 0, List.of()),
                new StageTrace("prioritize", 0, 0, List.of()));
    }
}
